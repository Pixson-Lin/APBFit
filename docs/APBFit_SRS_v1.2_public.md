# APBFit — Auto Personal Boost Fit
## Software Requirements Specification (SRS)
### Version 1.2

| Field | Value |
|---|---|
| Project Name | APBFit — Auto Personal Boost Fit |
| Document Type | Software Requirements Specification |
| Version | 1.2 |
| Author | Pixson |
| Date | 2026-06-22 |
| Status | Approved for Development |
| Distribution | Sideload (Phase 1), Google Play Store (Phase 2) |
| Supersedes | [SRS v1.1](APBFit_SRS_v1.1_public.md) |

---

## Table of Contents

1. Introduction
2. System Overview
3. User Roles
4. Functional Requirements
5. Non-Functional Requirements
6. Data Model
7. UI / Screen Inventory
8. Permission Requirements
9. Intensity Level Reference
10. Future Versions
10.x. Pending Capability: Cross-Device Data Sync
11. Change Log from v1.1

---

## 1. Introduction

### 1.1 Purpose

APBFit is an Android application that writes simulated walking and running activity data into Google Fit on behalf of signed-in Google accounts. Its primary purpose is to generate step count records compatible with fitness-integrated applications.

Version 1.2 addresses **background execution reliability** ([GitHub Issue #5](https://github.com/Pixson-Lin/APBFit/issues/5)): step segments must continue to be written while the screen is off and the app is backgrounded. v1.2 replaces the v1.1 **generate-on-delay loop** with a **pre-planned segment schedule**, **deadline-based wakeups**, and **throttled catch-up writes**, while keeping the v1.1 **full-session Foreground Service notification** and concurrent multi-account session model.

All v1.1 capabilities remain in scope unless modified below.

### 1.2 Scope

**In scope for v1.2 (includes all v1.1 capabilities unless modified below):**

- **Segment pre-planning at RUN** — all segments for the configured duration are generated and persisted before the first Google Fit write
- **Scheduled write engine (Scheme C)** — rolling `AlarmManager` deadline, short `PARTIAL_WAKE_LOCK` during writes, `SCREEN_ON` catch-up, no 500 ms stop-polling loop
- **Throttled burst catch-up** when wall clock is behind the plan (Doze, delayed alarms, screen-on)
- **History and ActiveRuns** display of due planned segments (`endTime <= now`) before Fit write completes
- **Orphan session resume** — restart scheduling for stale `RUNNING` sessions before `sessionEnd`; finalize with catch-up after end
- **Environment check** for exact-alarm capability (same UX pattern as battery optimization)
- **Android 15** `dataSync` foreground-service timeout handling (`onTimeout`)
- Immediate `startForeground()` when the service receives `ACTION_START_SESSION`

**Out of scope for v1.2:**

- User toggle to **abandon unwritten PLANNED segments** on orphan recovery (deferred to a future version; v1.2 always attempts resume/catch-up per FR-019)
- Custom intensity parameter editing (deferred to v1.3)
- Cross-device / cloud-sync visibility (unchanged pending; §10.x)
- Google Play Store publication
- Changing the **single ongoing FGS notification for the full session** model
- Automatic retry after a failed Google Fit batch (unchanged from v1.1)

**Related analysis:** [2hr Batch Power Estimate](APBFit_2hr_Batch_Power_Estimate.md)

### 1.3 Definitions

Inherits v1.1 terms, plus:

| Term | Definition |
|---|---|
| Planned Segment | A segment row persisted at RUN start with `writeStatus = PLANNED`, containing final simulated timestamps and step/distance values, not yet written to Google Fit. |
| Due Segment | A planned segment whose `endTime <= min(now, sessionEndMillis)` and is eligible for batching or display. |
| Write Status | Segment lifecycle in Room: `PLANNED`, `WRITTEN`, `FAILED`, or `SKIPPED`. |
| Batch Deadline | The `endTime` of the last segment in the next batch to write; used as the next scheduler wakeup. |
| Catch-up | A burst of due planned segments written after the schedule falls behind wall clock (alarm delay, Doze, `SCREEN_ON`, orphan resume). |
| Catch-up Round | One throttled burst pass subject to FR-021 limits; multiple rounds run back-to-back in the same wakeup until no due segments remain or stop is requested. |
| Session Scheduler | Component that computes the next batch deadline, schedules alarms, and triggers catch-up across all accounts in a session. |

**Updated v1.1 terms:**

| Term | v1.2 note |
|---|---|
| Segment | May exist in Room as `PLANNED` before any Google Fit API call. |
| Flush | On stop or session end: write all **due** planned segments (FR-008); mark remaining `PLANNED` as `SKIPPED`. |
| Orphan Run | A `RUNNING` row whose Foreground Service is not alive; v1.2 **resumes** if before `sessionEnd`, otherwise catch-up + finalize. |

---

## 2. System Overview

### 2.1 Goals

- **Reliable background writes** with screen off and app backgrounded (NFR-001)
- Preserve v1.1 multi-account sessions, Traditional Chinese UI, and full-session FGS notification
- Deterministic segment timelines per account (no wall-clock drift vs simulated timestamps)
- Observable due planned segments in History and ActiveRuns before Fit write completes
- Safe recovery after process death without silently abandoning unwritten due segments

### 2.2 Architecture (v1.2 delta)

```
┌─────────────────────────────────────┐
│             MainActivity            │
│  Home, ActiveRuns, History,         │
│  Settings, AccountEditSheet         │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│       RunForegroundService          │
│  SessionCoordinator                 │
│  SessionScheduler (alarms)          │
│  CatchUpEngine (throttled burst)    │
│  SCREEN_ON receiver (dynamic)       │
│  WakeLock (write + fallback)        │
│  ├─ account A write path            │
│  ├─ account B write path            │
│  └─ account N write path            │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Room: runs + segment_records       │
│  (PLANNED → WRITTEN / FAILED / SKIP)│
└──────────────┬──────────────────────┘
               ▼
┌─────────────────────────────────────┐
│         GoogleFitWriter             │
└─────────────────────────────────────┘
```

**Removed from v1.1 hot path:** per-segment `delay(25–35s)` generation loop with 500 ms stop polling as the primary scheduler.

### 2.3 Key Technical Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Plan timing | All segments at RUN | Single source of truth; catch-up reads DB only |
| Schedule anchor | Batch deadline = last segment `endTime` in batch | Aligns with retrospective Fit write rule (NFR-004) |
| Primary wakeup | Rolling exact alarm when permitted | Low idle wake rate vs 500 ms polling |
| Fallback wakeup | Inexact alarm + `SCREEN_ON` + delay slack | OEM / revoked exact-alarm tolerance |
| WakeLock | Short hold during writes; **session hold if exact alarms unavailable** | Doze-safe delays without 14k polls/run |
| FGS model | **Unchanged** — one notification for full session | Product decision; 6 hr cap accepted (NFR-010) |
| Catch-up throttle | Fixed constants (FR-021) | Protect Fit API and battery |
| Multi-account catch-up | `ceil(3 / N)` batches per account per round, min 1 | Fair share of global burst budget |
| Orphan recovery | Resume if `now < sessionEnd`; else catch-up + finalize | Issue #5; abandon toggle deferred |
| Write failure | Unchanged v1.1 per-account `FAILED` | No retry |

---

## 3. User Roles

Unchanged from v1.1 (§3.1 End User, Traditional Chinese UI).

---

## 4. Functional Requirements

Sections marked **(unchanged)** inherit v1.1 verbatim. Sections marked **(modified)** or **(new)** describe v1.2 delta.

### FR-001 — Google Sign-In and Account Management

**(unchanged from v1.1)**

---

### FR-002 — DataSource Initialization

**(unchanged from v1.1)**

---

### FR-003 — Run Configuration

**(unchanged from v1.1)**

---

### FR-004 — Start Run Session

**(modified)**

**Additional rules (v1.2):**

- After `startSession` creates `RUNNING` rows and **before** `RunForegroundService` starts, the app **pre-plans all segments** for each run (FR-020) in a single transactional persist pass.
- Preflight (FR-002, FR-015) remains all-or-nothing before any run row or planned segment is created.
- `RunForegroundService` must call `startForeground()` **immediately** in `onStartCommand` for `ACTION_START_SESSION` (before async DB load completes), using a minimal placeholder notification if needed, then update to full session notifications after load.

---

### FR-005 — Segment Generation and Pre-Planning

**(modified; replaces v1.1 FR-005 timing sections)**

**Description:** At RUN, each account receives a full segment plan for the session duration. During the session, the service **does not** generate new segments on a delay loop.

**Generation rules (per account, formulas unchanged from v1.1):**

- Segment duration: uniform random 25–35 seconds per segment, independent per account (`seed = f(sessionId, accountId)`).
- Step count, distance, activity type: unchanged from v1.1.
- Segments are **contiguous** on the simulated timeline starting at shared `startTime`.
- Planning **stops** when the next segment would extend beyond `sessionEndMillis = startTime + durationMinutes`.

**Persistence (FR-020):**

- Each planned segment is **inserted into `segment_records` immediately** with `writeStatus = PLANNED`, `writeTime = 0`, and simulated `startTime` / `endTime` / steps / distance.
- **No Google Fit API call** occurs at plan time.

**Session end:**

- Shared `sessionEndMillis` enforced by `SessionCoordinator` (unchanged).
- Segments with `endTime > sessionEndMillis` are not planned.

---

### FR-006 — Batch Write to Google Fit

**(modified)**

**Description:** Due planned segments are written in batches per account when their batch deadline is reached or during catch-up.

**Eligibility:** A segment is eligible when `writeStatus = PLANNED` and `endTime <= System.currentTimeMillis()` (retrospective write, NFR-004).

**Rules:**

- Batch composition: consecutive planned segments by `segmentIndex`, up to `batchSize`.
- Three `insertData()` calls per batch (unchanged).
- On success: update rows to `writeStatus = WRITTEN`, set `writeTime`, `success = true`.
- On failure: update batch rows to `writeStatus = FAILED`, `success = false`, error message; affected run → `FAILED` (FR-010); no retry.
- **Visibility scope** unchanged (same-device guarantee, NFR-009).

---

### FR-007 — Foreground Service and Notification

**(modified minimally)**

**Rules (v1.2 additions):**

- `startForeground()` on session start without waiting for async session load (FR-004).
- Service implements `onTimeout()` for Android 15 `dataSync` limit (NFR-010).
- Notifications update after each successful batch write (unchanged).
- Summary notification remains for the **entire session** until all accounts finalize.

---

### FR-008 — Stop Run Session (Manual)

**(modified)**

**Rules (v1.2):**

- Stop cancels scheduled alarms and active scheduler jobs/coroutines.
- **Before finalize:** for each account, write all **due** planned segments (`endTime <= now`) in throttled catch-up passes (FR-021).
- Remaining `PLANNED` → `SKIPPED`.
- Each run → `STOPPED`; service stops after last account finalizes.
- Unchanged: segments already written to Google Fit are retained.

---

### FR-009 — Run Session Completion (Automatic)

**(modified)**

**Rules (v1.2):**

- When `now >= sessionEndMillis`, scheduler triggers final catch-up for all segments with `endTime <= sessionEndMillis`, then marks remaining `PLANNED` as `SKIPPED`.
- Each account run → `COMPLETED` (unless already `FAILED`).
- Session summary unchanged (`已完成`, `已完成（N 失敗）`).

---

### FR-010 — Per-Account Run Failure Handling

**(unchanged from v1.1)**

---

### FR-011 — Run History

**(modified)**

**Rules (v1.2 additions):**

- Expandable segment list includes:
  - All segments with `writeStatus` in `WRITTEN`, `FAILED`, or `SKIPPED`.
  - **Planned segments only when `endTime <= now`** (due but not yet written).
- Planned segments display a **待寫入** (pending write) status label.
- Skipped segments display **已跳過** after session finalize or stop.

---

### FR-012 — Segment Detail View

**(modified)**

**Per-segment display:**

| Write Status | Shown in History when | Status label (zh-TW) |
|---|---|---|
| `PLANNED` | `endTime <= now` | 待寫入 |
| `WRITTEN` | always (after write) | 成功 |
| `FAILED` | always | 失敗 |
| `SKIPPED` | always (after finalize) | 已跳過 |

Future planned segments (`endTime > now`) are **hidden**.

---

### FR-013 — Result Validation Logging

**(unchanged from v1.1)**

---

### FR-014 — Settings

**(unchanged from v1.1; FR-019 manual recovery behavior updated)**

---

### FR-015 — Environment Check

**(modified)**

**Icons (v1.2):**

| Icon | Checks covered | Tap action |
|---|---|---|
| Battery | Battery optimization disabled | Open system settings |
| FIT | Google Fit installed + fitness permissions | Fix flow |
| Notifications | Notification permission (Android 13+) | Request / settings |
| **Alarm** | `AlarmManager.canScheduleExactAlarms()` (API 31+) | Open **Alarms & reminders** (or equivalent) system settings |

**Rules:**

- Alarm check uses the **same UX pattern as battery optimization**: PASS/WARN icon, tap opens settings, **does not block RUN**.
- WARN when exact alarms cannot be scheduled; copy explains reduced schedule accuracy and reliance on screen-on catch-up.

---

### FR-016 — Active Runs Screen

**(modified)**

**Per-account stats (v1.2):**

- Steps written (successful WRITTEN segments only, unchanged).
- **Segments:** `segmentsWritten / segmentsPlanned` (planned = count of all planned segments for the run).
- Status labels unchanged when finalized.

---

### FR-017 — Enabled Account Selection

**(unchanged from v1.1)**

---

### FR-018 — Run Configuration Persistence

**(unchanged from v1.1)**

---

### FR-019 — Orphan Run Session Recovery

**(modified)**

**Automatic recovery (v1.2):**

On cold app launch, for each orphan session (one or more `RUNNING` rows, no live Foreground Service):

| Condition | Action |
|---|---|
| `now < sessionEndMillis` | **Resume session:** start `RunForegroundService`, reschedule alarms, run catch-up for due segments, continue until stop/complete/timeout. **Do not** finalize as STOPPED solely because of restart. |
| `now >= sessionEndMillis` | **Finalize session:** catch-up write all due segments with `endTime <= sessionEndMillis`; mark remaining `PLANNED` → `SKIPPED`; set appropriate terminal status per account (`COMPLETED` / `FAILED` if prior failure). |

**Manual recovery (Settings — 重設進行中紀錄):**

- Unchanged visibility rules (orphan DB rows, no live session).
- v1.2: same logic as automatic **`now >= sessionEnd`** path (catch-up due + SKIP remainder + finalize), unless a live service is resumed instead.

**Safety:**

- Recovery must not run against runs actively executing in the Foreground Service.
- **Future version:** user setting to abandon unwritten `PLANNED` instead of resume (out of scope v1.2).

**Recovery message:**

- Use Traditional Chinese status/error copy when finalize without resume (e.g. 應用程式重啟後已自動結束) — only when session is **finalized**, not when **resumed**.

---

### FR-020 — Segment Pre-Planning at RUN

**(new)**

**Description:** Persist the full segment schedule to Room when the user starts a session.

**Rules:**

- Runs immediately after successful `startSession`, before service start.
- One plan per `runId`; segment indices contiguous from 0.
- All rows inserted with `writeStatus = PLANNED`.
- If pre-planning fails, no service start; no partial session (transactional).

---

### FR-021 — Scheduled Write and Catch-Up

**(new)**

**Description:** Write due planned segments on schedule and when behind wall clock.

**Scheduler:**

- One rolling alarm per session at the **earliest next batch deadline** across all accounts (minimum of per-account next batch `endTime`, capped by `sessionEndMillis`).
- Use `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` is true.
- Otherwise use inexact `setAndAllowWhileIdle` and session-level `PARTIAL_WAKE_LOCK` (NFR-001).

**Catch-up triggers:**

1. Alarm fired
2. `Intent.ACTION_SCREEN_ON` (dynamic receiver registered for active session)
3. ActiveRuns / app resume (optional supplementary)
4. Scheduler detects `now > nextDeadline + 5s` slack after any wakeup

**Throttle constants (fixed in v1.2):**

| Constant | Value |
|---|---|
| `maxBatchesPerCatchUp` | 3 (global per catch-up round per account allocation — see below) |
| `delayBetweenBatchesMs` | 1000 |
| `maxSegmentsPerCatchUp` | 20 |
| `maxCatchUpWallClockMs` | 30000 |

**Multi-account batch allocation per catch-up round:**

```
batchesPerAccount = max(1, ceil(3 / accountCount))
```

Each account may write up to `batchesPerAccount` batches per round, subject also to `maxSegmentsPerCatchUp` and `maxCatchUpWallClockMs`.

**Multi-round catch-up:**

- If due segments remain after one round, **immediately start the next round** in the same wakeup (same service activation) until no due segments remain, stop is requested, or session ends.
- Do not require another screen-on event to drain backlog.

**Stop interaction:**

- Stop cancels further catch-up rounds and proceeds to FR-008 skip/finalize.

---

## 5. Non-Functional Requirements

### NFR-001 — Background Execution Reliability

**(modified)**

The Run Session must continue writing due segments with the screen off, app backgrounded, and device locked, subject to:

- User has granted notification permission (Android 13+) for FGS visibility.
- Reasonable device conditions (not force-stopped, sufficient battery for alarms).

**Design requirements:**

- No 500 ms polling loop as primary scheduler.
- Catch-up must correct schedule drift after Doze or inexact alarms.
- Accept platform limits (NFR-010) without silent data loss for due segments before `sessionEnd`.

### NFR-002 — Minimum Android Version

Unchanged: Android 12 (API 31)+.

### NFR-003 — Write Latency

Unchanged: 10-second budget per batch.

### NFR-004 — Data Integrity

Unchanged retrospective-write rule, applied to planned segments: **never** call Fit API when `endTime > now`.

### NFR-005 — Account Isolation

Unchanged from v1.1.

### NFR-006 — UI Responsiveness

Unchanged from v1.1.

### NFR-007 — Maintainability

Unchanged; scheduler and catch-up isolated in dedicated components (see SDS).

### NFR-008 — User Interface Language

Unchanged: Traditional Chinese; new strings for 待寫入, 已跳過, alarm environment check.

### NFR-009 — Data Visibility Scope (Same-Device Guarantee)

Unchanged from v1.1.

### NFR-010 — Android 15 Foreground Service Duration Limit

**(new)**

- APBFit targets API 35 and uses `foregroundServiceType="dataSync"`.
- Android 15 limits background `dataSync` FGS to **6 hours per 24 hours**; accepted as a **system constraint**.
- Maximum configured run duration (6 hours) may hit this limit if the app stays backgrounded the entire time.
- On `Service.onTimeout()`: run final catch-up (FR-009 path), finalize runs, `stopSelf()` within system grace period.
- User bringing the app to foreground resets the system timer (platform behavior).

---

## 6. Data Model

### 6.1 Run

Unchanged from v1.1.

### 6.2 SegmentRecord

**(modified)**

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `runId` | UUID | FK → Run |
| `segmentIndex` | Int | 0-based order |
| `startTime` | Long | Simulated epoch ms |
| `endTime` | Long | Simulated epoch ms; schedule deadline |
| `steps` | Int | Planned at RUN |
| `distanceMeters` | Float | Planned at RUN |
| `writeTime` | Long | **0 when PLANNED/SKIPPED**; epoch ms when WRITTEN/FAILED |
| `writeStatus` | Enum | **New:** `PLANNED`, `WRITTEN`, `FAILED`, `SKIPPED` |
| `success` | Boolean | Meaningful when WRITTEN/FAILED; false for FAILED |
| `errorMessage` | String? | Set on FAILED |

**Migration:** v1.1 rows without `writeStatus` → `WRITTEN` if `success = true`, else `FAILED`.

**Queries:**

- Due planned: `writeStatus = PLANNED AND endTime <= :now`
- History display: §FR-012 filter
- `totalStepsWritten`: sum steps where `writeStatus = WRITTEN` (or `success = true`)

### 6.3–6.5

Unchanged (`AccountDataSourceCache`, `EnabledAccountsPrefs`, `RunConfigPrefs`).

---

## 7. UI / Screen Inventory

Unchanged screen list from v1.1; delta:

- **HistoryScreen:** pending/skipped segment status labels (FR-012).
- **ActiveRunsScreen:** `segmentsWritten / segmentsPlanned` (FR-016).
- **HomeScreen environment row:** fourth alarm icon (FR-015).

---

## 8. Permission Requirements

| Permission | Reason | v1.2 |
|---|---|---|
| `FITNESS_ACTIVITY_WRITE` | Google Fit steps/activity | unchanged |
| `FITNESS_LOCATION_WRITE` | Google Fit distance | unchanged |
| `FOREGROUND_SERVICE` | Run session | unchanged |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ FGS type | unchanged |
| `POST_NOTIFICATIONS` | Android 13+ | unchanged |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Battery exemption prompt | unchanged |
| `ACTIVITY_RECOGNITION` | Google Fit SDK | unchanged |
| **`WAKE_LOCK`** | **`PARTIAL_WAKE_LOCK` during writes / session fallback** | **new** |
| **`SCHEDULE_EXACT_ALARM`** | **Exact batch deadlines when user allows** | **new (API 31+)** |

`USE_EXACT_ALARM` is not used (not an alarm/calendar app).

---

## 9. Intensity Level Reference

Unchanged from v1.1 (§9).

---

## 10. Future Versions

### v1.3 — Custom Intensity Parameters

User-edited cadence/stride or custom named levels (formerly planned as v1.2).

### v1.4 — Orphan Recovery Preference

Setting: on orphan recovery, **abandon unwritten PLANNED** vs **resume/catch-up** (v1.2 always resumes when before `sessionEnd`).

### v1.5 — Google Play Store Release

Privacy Policy, OAuth verification, listing assets (formerly v1.3).

### v1.6 — Write Path Adaptability

Alternative `FitWriter` implementations.

### v1.x — Multi-Language Support

i18n resource splitting.

### v2.x — Export, Dashboard, Cloud Sync

Unchanged long-term roadmap items.

---

## 10.x — Pending Capability: Cross-Device Data Sync

Unchanged from v1.1; see [Google Fit Sync Investigation](APBFit_GoogleFit_Sync_Investigation.md).

---

## 11. Change Log from v1.1

| Area | v1.1 | v1.2 |
|---|---|---|
| Segment creation | On delay during session | **Pre-planned at RUN** in Room |
| Scheduler | 500 ms polling + segment delay | **Alarm + deadline sleep + catch-up** |
| Screen off reliability | Best-effort (Issue #5) | **Required** (NFR-001) |
| Segment DB row | Insert after Fit write | **Insert PLANNED first; update on write** |
| History segments | Written/failed only | **+ due PLANNED, SKIPPED** |
| Orphan recovery | Finalize all STOPPED | **Resume or catch-up finalize** |
| Environment check | 3 icons | **+ exact alarm icon** |
| Permissions | — | **WAKE_LOCK, SCHEDULE_EXACT_ALARM** |
| Android 15 | — | **`onTimeout()` handling** |
| FGS notification | Full session | **Unchanged** |
| 6 hr run on API 35 | — | **Accepted system limit (NFR-010)** |

**GitHub Issues addressed:** [#5](https://github.com/Pixson-Lin/APBFit/issues/5) background/screen-off step recording.

---

*End of SRS v1.2*
