# APBFit — Auto Personal Boost Fit
## Software Requirements Specification (SRS)
### Version 1.1

| Field | Value |
|---|---|
| Project Name | APBFit — Auto Personal Boost Fit |
| Document Type | Software Requirements Specification |
| Version | 1.1 |
| Author | Pixson |
| Date | 2026-06-15 |
| Status | Approved for Development |
| Distribution | Sideload (Phase 1), Google Play Store (Phase 2) |
| Supersedes | [SRS v1.0](APBFit_SRS_v1.0_public.md) |

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
11. Change Log from v1.0

---

## 1. Introduction

### 1.1 Purpose

APBFit is an Android application that writes simulated walking and running activity data into Google Fit on behalf of signed-in Google accounts. Its primary purpose is to generate step count records compatible with fitness-integrated applications.

The application provides a structured run-based workflow with full observability and per-run result logging, allowing users to track whether written data is accepted by downstream fitness applications.

Version 1.1 extends v1.0 with **concurrent multi-account run sessions**, a **simplified Home screen**, **persisted run configuration**, **seven preset intensity levels**, and a **Traditional Chinese user interface**.

### 1.2 Scope

**In scope for v1.1 (includes all v1.0 capabilities unless modified below):**

- Google Sign-In with multiple accounts; concurrent runs across selected accounts in one session
- Google Fit data writing via Android SDK (`HistoryClient`)
- Run-based workflow with configurable intensity and duration
- Foreground Service for background execution (single service, per-account coroutines)
- Per-run history with segment-level detail
- Downstream app result validation logging per run
- Automatic and manual data retention management
- Simplified Home UI per [UI draft](APBFit_v1.1_UI_draft.png)
- Run configuration persistence (intensity, duration, batch size)
- Seven preset intensity levels with retuned SPM/stride values
- Traditional Chinese UI (fixed single language; no runtime locale switching)
- Orphan run-session recovery on app launch; optional manual reset in Settings

**Out of scope for v1.1:**

- Custom intensity parameter editing (user-defined SPM/stride input)
- Runtime multi-language / i18n architecture
- Health Connect write path
- Cloud sync or multi-device dashboard
- Google Play Store publication (still sideload in v1.1)
- Per-account Stop during an active session
- Per-account independent run configuration within one session

### 1.3 Definitions

| Term | Definition |
|---|---|
| Run | One complete execution for a single Google account within a Run Session, from start to finalization, with its own segment queue and Google Fit write path. |
| Run Session | A single user-initiated RUN action that starts one Run per selected account at the same wall-clock time, sharing intensity, duration, and batch size. Identified by a shared `sessionId`. |
| Session Coordinator | In-process component within `RunForegroundService` that shares stop signals, end time, and lifecycle across per-account run coroutines. |
| Segment | A single time-bounded data write unit. Contains one StepCountDelta, one DistanceDelta, and one ActivitySegment record. Duration is randomized per segment **per account**. |
| Batch | A group of completed segments written to Google Fit in a single `insertData()` call. Batch size is configurable (1–10 segments). |
| Intensity Level | A preset combination of cadence (SPM) and stride length (m/step). Seven levels in v1.1. |
| Enabled Account | A signed-in Google account whose checkbox is selected in Account Edit; eligible to participate in the next Run Session. |
| Result Validation | User-reported feedback on whether a completed run's data was accepted by a downstream fitness application. |
| DataSource | A Google Fit data stream registered under the app's package name and the user's Google account. One DataSource per type per account. |
| Flush | Immediate write of all accumulated but not-yet-written segments, triggered on run/session completion or manual stop. |
| Orphan Run | A `Run` row left in `RUNNING` status after the process or service died without finalization. |

---

## 2. System Overview

### 2.1 Goals

- Write Google Fit step data compatible with downstream fitness applications
- Allow **multiple Google accounts to run concurrently** with one shared configuration per session
- Provide a reliable Foreground Service that survives screen-off and app backgrounding
- Simplify the Home screen so RUN is immediately accessible without excessive scrolling
- Maintain per-account run history with segment-level transparency
- Present all user-facing text in **Traditional Chinese**
- Recover stale `RUNNING` database rows safely after crashes

### 2.2 Architecture

```
┌─────────────────────────────────────┐
│             MainActivity            │
│  Home, ActiveRuns, History,         │
│  Settings, AccountEditSheet         │
└──────────────┬──────────────────────┘
               │ ViewModel / StateFlow
               ▼
┌─────────────────────────────────────┐
│              ViewModel              │
└──────┬───────────────────┬──────────┘
       │                   │
       ▼                   ▼
┌─────────────┐   ┌────────────────────┐
│  RunRepo    │   │  AccountRepo       │
│  (Room DB)  │   │  (GoogleSignIn +   │
└──────┬──────┘   │   prefs/DataStore) │
       │          └────────────────────┘
       ▼
┌─────────────────────────────────────┐
│       RunForegroundService          │
│  SessionCoordinator                 │
│  ├─ coroutine / account A           │
│  ├─ coroutine / account B           │
│  └─ coroutine / account N           │
│  (segment gen, batch queue, notify) │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│         GoogleFitWriter             │
│  (HistoryClient, DataSource mgmt,   │
│   insertData)                       │
└─────────────────────────────────────┘
```

### 2.3 Key Technical Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Write API | Google Fit Android SDK (`HistoryClient`) | Unchanged from v1.0 |
| DataSource scope | One per data type per Google account | Unchanged |
| Segment duration | Randomized 25–35 seconds **per account** | Independent `Random` seed per account in a session |
| Concurrency | **One Run Session** at a time; **N runs** inside (one per enabled account) | User-selected accounts share config; simplifies UI and notifications |
| Service model | **Single** `RunForegroundService` + SessionCoordinator + per-account coroutines | Synchronized start/end; shared stop signal |
| Random seed | `f(sessionId, accountId)` per account | Same config, different written GF data |
| Local DB | Room with `sessionId` on `Run` | Group runs from one RUN action |
| UI language | Traditional Chinese in `strings.xml` (no `values-zh-rTW` split yet) | v1.1 scope; i18n deferred |
| Failure isolation | Per-account `FAILED`; other accounts continue | Partial session success |
| Preflight | All-or-nothing before session start | Avoid partial start confusion |

---

## 3. User Roles

### 3.1 End User

A user who installs APBFit to generate Google Fit step data for compatibility with fitness-integrated applications. Expected to have basic familiarity with Google Fit. No technical expertise required beyond Google account sign-in. v1.1 UI is in Traditional Chinese.

---

## 4. Functional Requirements

### FR-001 — Google Sign-In and Account Management

**Description:** The user signs in with one or more Google accounts. Account management is available from Home (Account Edit sheet) and Settings.

**Rules:**
- App launch with no signed-in account must present the sign-in screen immediately.
- Signed-in accounts persist across app restarts.
- **Account Edit sheet** (from Home): lists all signed-in accounts with checkbox (enabled for next RUN), delete (sign out), and **新增** (add account via Google Sign-In).
- Checkbox marks whether the account is **enabled** for the next Run Session (FR-017).
- Newly added accounts are **enabled by default**.
- Delete/sign out removes the account from the app; **run history for that account is retained** until the user clears it in Settings.
- Enabled-account checkbox state is persisted across app restarts.
- Account add/remove is disabled while a Run Session is active.

**Inputs:** Google account via Google Sign-In SDK.

**Outputs:** Authenticated `GoogleSignInAccount` records; enabled-account set.

---

### FR-002 — DataSource Initialization

**Description:** Before a Run Session starts, each **enabled** account must have three Google Fit DataSources available. DataSource IDs are cached in SharedPreferences keyed by account ID.

**DataSources created (per account, unchanged types):**

| Stream Name | Data Type | Field | Format |
|---|---|---|---|
| `apbfit_step_count` | `com.google.step_count.delta` | `steps` | integer |
| `apbfit_distance` | `com.google.distance.delta` | `distance` | floatPoint |
| `apbfit_activity` | `com.google.activity.segment` | `activity` | integer |

**Rules:**
- If a DataSource already exists (HTTP 409), the existing DataSource ID is retrieved and cached.
- DataSource creation runs during **session preflight**, not at app launch.
- **All-or-nothing:** if **any** enabled account fails `ensureDataSources`, the **entire** Run Session does not start; the user sees which account failed.
- Unchanged: 409 handling, caching, per-account scope.

---

### FR-003 — Run Configuration

**Description:** Before starting a Run Session, the user configures shared parameters on Home.

**Parameters:**

| Parameter | Control | Range | Default |
|---|---|---|---|
| Intensity Level | Dropdown (7 presets) | See §9 | 快走 (`BRISK_WALK`) |
| Duration | Slider | 5 min – 6 hr, step 5 min | 30 min |
| Batch Size | Slider | 1 – 10 segments (integer steps) | 3 |
| Enabled Accounts | Account Edit checkboxes | Subset of signed-in accounts | Last persisted set |

**Rules:**
- Duration and batch size use sliders only; manual text input is not supported.
- Intensity dropdown shows **Traditional Chinese** display name; selected item shows SPM and stride as read-only reference (e.g. `110 步/分 · 0.63 公尺/步`).
- Batch slider displays the current integer value (1–10).
- Configuration is **global** (one set per session, not per account).
- FR-018 persists intensity, duration, and batch size across app restarts and after runs end.
- RUN is disabled when zero accounts are enabled; at least **one** enabled account is required.

**Layout (Home, top to bottom):**

1. Top navigation: title **APBFit**, **歷史紀錄**, **設定**
2. **RUN** button (primary action, top of config area)
3. Intensity dropdown
4. Duration slider
5. Batch size slider
6. Account section: enabled-account list + **編輯** button
7. Environment icon row (FR-015)

Reference: [APBFit_v1.1_UI_draft.png](APBFit_v1.1_UI_draft.png)

---

### FR-004 — Start Run Session

**Description:** The user starts a Run Session by tapping **RUN** on Home.

**Trigger:** RUN button tap.

**Rules:**
- Only **one Run Session** may be active at a time for the app instance.
- Preflight (FR-002, FR-015 for enabled accounts) must pass for **all** enabled accounts before any run row is created.
- On success, the app creates one `Run` row per enabled account with:
  - Shared `sessionId` (UUID)
  - Shared `startTime` (wall clock when RUN is tapped)
  - Shared `durationMinutes`, `intensityLevel`, `batchSize`
  - Per-row `accountId` and unique `runId`
  - Status `RUNNING`
- A single `RunForegroundService` starts with the list of `runId`s.
- The app **navigates automatically** to ActiveRunsScreen.
- Per-account segment generation uses `Random(seed)` where `seed` is derived from `sessionId` and `accountId` (FR-005).
- The same account must not have more than one `RUNNING` row at any time.

---

### FR-005 — Segment Generation

**Description:** For each account in the session, the Foreground Service runs an independent segment loop with a **dedicated** `SegmentGenerator` instance and random seed.

**Segment generation rules (per account, same formulas as v1.0):**
- Segment duration is randomized uniformly between 25 and 35 seconds (**independent per account**).
- Step count: `round(gaussRandom(mean=SPM/60 × durationSec, std=5))`, minimum 1.
- Distance: `steps × strideMeters`, rounded to 2 decimal places.
- Activity type is fixed at `8` (running).
- Segment time contiguity and retrospective-write rules are unchanged from v1.0.
- All accounts share the same `sessionEndMillis = startTime + durationMinutes`, enforced by SessionCoordinator.

**Timing:**
- Each account coroutine sleeps for its own randomized segment duration.
- When `sessionEndMillis` is reached or session stop is requested, **all** account coroutines exit their delay loops, flush queues, and finalize (FR-008, FR-009).
- Segment count and total steps **will differ** across accounts in the same session.

---

### FR-006 — Batch Write to Google Fit

**Description:** Per account, completed segments are queued and written in batches (unchanged write mechanics from v1.0).

**Rules:**
- Batch trigger, three `insertData()` calls per batch, and segment DB recording are unchanged from v1.0 **per run**.
- **Failure scope (v1.1):** if a batch write fails for account A, **only** account A's run is marked `FAILED` and its coroutine stops (FR-010). Other accounts in the session **continue** until they complete, are stopped, or fail independently.
- Successful writes for other accounts are retained.
- No retry on failed batch.

---

### FR-007 — Foreground Service and Notification

**Description:** The Run Session executes in one Foreground Service. Notifications use a **summary + group** pattern.

**Summary notification (session-level):**

| Field | Content (Traditional Chinese) |
|---|---|
| Title | APBFit 進行中 |
| Summary | Session progress, e.g. `2/3 進行中` or elapsed/remaining |
| Action | 停止 (session stop → FR-008) |

**Grouped child notifications (per active account):**

| Field | Content |
|---|---|
| Account | Email or shortened label |
| Detail | Steps written, remaining time for that account |
| Group | Same `NotificationGroup` as summary |

**Rules:**
- Service remains foreground until **all** runs in the session have finalized.
- Tapping the summary notification opens ActiveRunsScreen.
- Stop action triggers session-level stop (FR-008).
- Notifications update after successful batch writes (per-account children and summary).

---

### FR-008 — Stop Run Session (Manual)

**Description:** The user stops the entire active session before scheduled completion.

**Trigger:** 停止 on ActiveRunsScreen or notification summary action.

**Rules:**
- SessionCoordinator sets stop requested for **all** account coroutines.
- Each account flushes its queued segments, then its `Run` is marked `STOPPED`.
- Segments already written to Google Fit are retained.
- Foreground Service stops after the last account finalizes.
- No per-account stop control in v1.1.

---

### FR-009 — Run Session Completion (Automatic)

**Description:** The session ends automatically when wall-clock time reaches `startTime + durationMinutes`.

**Rules:**
- SessionCoordinator signals stop to all account coroutines (same flush path as FR-008).
- Each account's queued segments are flushed.
- Each `Run` is marked `COMPLETED`.
- Foreground Service stops after the last account finalizes.
- Session summary UI shows **已完成**, or **已完成（N 失敗）** if one or more accounts ended `FAILED` (FR-010).

---

### FR-010 — Per-Account Run Failure Handling

**Description:** If a Google Fit write fails for one account during a session, that account's run ends in failure; the session continues for other accounts.

**Rules:**
- Failed batch segments are recorded with `success = false` and the error message for that run.
- The affected `Run` status is set to `FAILED`; its coroutine stops.
- Other accounts continue until COMPLETED, STOPPED, or their own FAILED.
- ActiveRunsScreen block 2 shows **失敗** and `errorMessage` for failed accounts.
- Session block 1 continues to show `N/M 進行中` until all accounts have finalized.
- No automatic retry.

---

### FR-011 — Run History

**Description:** The user views past runs per account.

**Display per run (summary):** unchanged fields from v1.0 (start time, intensity, duration, steps, status, validation badge).

**Rules:**
- HistoryScreen provides an **account dropdown** at the top; the list shows runs for the **selected** account only.
- Default dropdown selection: last account viewed, or first signed-in account if none stored.
- Runs sorted by start time, descending.
- Expandable segment detail (FR-012).
- Concurrent session runs appear as separate rows per account (same `sessionId` in data model; optional session indicator in UI).

---

### FR-012 — Segment Detail View

Unchanged from v1.0.

---

### FR-013 — Result Validation Logging

Unchanged from v1.0 (Traditional Chinese UI labels: 接受 / 拒絕).

---

### FR-014 — Settings

**Description:** Settings provides data management, orphan recovery, and system shortcuts. Primary account enable/add/remove is on Home Account Edit.

**Available actions:**
- Clear all history for a selected account (account selector or same dropdown pattern as History)
- **重設進行中紀錄** — visible **only when** the database contains `RUNNING` row(s) **and** no in-memory active session / service run exists (FR-019)
- Navigate to: Battery Optimization, App Details, Notification settings, Google Fit app
- Sign in additional accounts (optional shortcut; same as Account Edit **新增**)

**Removed from Settings (moved to Home Account Edit):**
- Switch active account for runs
- Per-account enable checkboxes for RUN

**Automatic data retention:** unchanged (90-day silent purge on app launch).

---

### FR-015 — Environment Check

**Description:** Home displays a compact **icon row** instead of a full text checklist.

**Icons:**

| Icon | Checks covered | Tap action |
|---|---|---|
| Battery | Battery optimization disabled for APBFit | Open relevant system settings |
| FIT | Google Fit installed **and** fitness permissions for enabled accounts | Open fix flow (install or permission request) |
| Notifications | Notification permission (Android 13+) | Request permission or open settings |

**Rules:**
- PASS: green indicator; WARN: orange indicator.
- Failed checks are warnings, not hard blockers for RUN (user may still start).
- Before session preflight, fitness permission must succeed for **each enabled account** (FR-002 all-or-nothing applies to permission/DataSource failures at start).

---

### FR-016 — Active Runs Screen

**Description:** While a session is active, ActiveRunsScreen replaces the v1.0 single-account ActiveRunScreen.

**Block 1 — Session (shared):**
- Intensity (Traditional Chinese name)
- Elapsed time
- Remaining time
- Session status: `N/M 進行中`; when finished: `已完成` or `已完成（X 失敗）`

**Block 2 — Per account:**
- Account email (or label)
- Steps written (cumulative)
- Segments written (count)
- Per-run status when finalized: 進行中 / 已完成 / 已停止 / 失敗 (+ error message if 失敗)

**Actions:**
- **停止** — session-level stop (FR-008)
- No per-account stop

**Rules:**
- User is navigated here automatically when RUN succeeds (FR-004).
- Top navigation to History/Settings is available but session continues in background.

---

### FR-017 — Enabled Account Selection

**Description:** The user selects which accounts participate in the next Run Session.

**Rules:**
- Managed in Account Edit sheet from Home.
- Enabled set persisted in DataStore (or equivalent).
- Home Account section lists enabled accounts (email).
- At least one account must be enabled to allow RUN.
- Enabled set cannot be modified during an active session.

---

### FR-018 — Run Configuration Persistence

**Description:** The app remembers the last used intensity, duration, and batch size.

**Rules:**
- Stored in SharedPreferences or DataStore (global, not per account).
- Restored on app launch and after a session ends.
- Enabled-account set is persisted separately (FR-017).
- Does not override in-flight session configuration.

---

### FR-019 — Orphan Run Session Recovery

**Description:** Recover database rows left in `RUNNING` after process death without corrupting active sessions.

**Automatic recovery (required):**
- On **cold app launch**, before normal UI interaction, finalize **all** `RUNNING` runs (entire sessions):
  - `status` → `STOPPED`
  - `endTime` → recovery timestamp
  - `totalStepsWritten` → sum of successful segments already recorded
  - `errorMessage` → recovery message (Traditional Chinese, e.g. 應用程式重啟後已自動結束)

**Manual recovery (Settings):**
- Action **重設進行中紀錄** performs the same finalization.
- Shown only when DB has `RUNNING` rows **and** `RunStateHolder`/service reports **no** active session.
- Requires user confirmation dialog.

**On RUN blocked by stale rows:**
- If start detects orphan `RUNNING` rows while no live session exists, run automatic recovery for the full session, then allow the user to retry.

**Safety:**
- Recovery must **not** run against runs that are actively executing in the Foreground Service.

---

## 5. Non-Functional Requirements

### NFR-001 — Background Execution Reliability

Unchanged from v1.0. Session must survive screen-off, backgrounding, and device lock.

### NFR-002 — Minimum Android Version

Unchanged: Android 12 (API 31)+, Google Play Services required.

### NFR-003 — Write Latency

Unchanged: 10-second budget per batch; failure per FR-010.

### NFR-004 — Data Integrity

Unchanged from v1.0, applied per account run.

### NFR-005 — Account Isolation

Unchanged: runs, segments, and validations are scoped by `accountId`. History never mixes accounts unless the user changes the dropdown.

### NFR-006 — UI Responsiveness

Unchanged from v1.0.

### NFR-007 — Maintainability

Unchanged: MVVM, Repository, Room, `FitWriter` interface.

### NFR-008 — User Interface Language

- All **user-visible** strings in release builds must be **Traditional Chinese**, including screens, dialogs, notifications, toasts/status messages, and intensity display names.
- Home screen title must read **APBFit** (brand name, not translated).
- Debug-only developer tools may remain English.
- Multi-language support and `values-xx` resource splitting are **deferred** to a future version; v1.1 uses a single `values/strings.xml` edited in Traditional Chinese.

---

## 6. Data Model

### 6.1 Run

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `sessionId` | UUID | **New in v1.1.** Shared by all runs started in one RUN action |
| `accountId` | String | Google account ID |
| `startTime` | Instant | Shared within session |
| `endTime` | Instant? | Null while running |
| `durationMinutes` | Int | Configured duration (shared within session) |
| `intensityLevel` | Enum | See §9; stored as enum name string |
| `batchSize` | Int | 1–10 |
| `status` | Enum | RUNNING / COMPLETED / STOPPED / FAILED |
| `totalStepsWritten` | Int | Sum of successful segment steps |
| `validationResult` | Enum? | ACCEPTED / REJECTED / null |
| `validationStepCount` | Int? | |
| `validationTime` | Instant? | |
| `errorMessage` | String? | Failure or recovery message |

**Migration:** add nullable `sessionId` to existing DB; v1.0 runs may have `sessionId = id` or null for display only.

**History display:** if `IntensityLevel.valueOf` fails (removed enum), show stored raw string.

### 6.2 SegmentRecord

Unchanged from v1.0.

### 6.3 AccountDataSourceCache

Unchanged from v1.0.

### 6.4 EnabledAccountsPrefs (new)

| Field | Type | Notes |
|---|---|---|
| `enabledAccountIds` | Set\<String\> | Google account IDs selected for next RUN |

### 6.5 RunConfigPrefs (new)

| Field | Type | Notes |
|---|---|---|
| `intensityLevel` | String | Enum name |
| `durationMinutes` | Int | |
| `batchSize` | Int | |

---

## 7. UI / Screen Inventory

All screens use **Traditional Chinese** copy unless noted.

| Screen | Description |
|---|---|
| **SignInScreen** | First launch or no accounts. Google 登入 button. |
| **HomeScreen** | Title **APBFit**; top nav 歷史紀錄 / 設定; RUN; intensity dropdown; duration/batch sliders; enabled-account list + 編輯; environment icon row. |
| **AccountEditSheet** | Modal/bottom sheet: account list with checkbox, delete, 新增. |
| **ActiveRunsScreen** | Session block + per-account stats; 停止. Auto-shown when session starts. |
| **HistoryScreen** | Account dropdown; run list with segment expand; validation entry. |
| **SettingsScreen** | Clear history; conditional 重設進行中紀錄; system shortcuts. |

**Navigation:**
- Sign-in required if no accounts.
- Active session → ActiveRunsScreen (auto on RUN).
- No v1.0 "replace Home with ActiveRun" gate; user can navigate away while session runs.

---

## 8. Permission Requirements

Unchanged from v1.0.

| Permission | Reason |
|---|---|
| `FITNESS_ACTIVITY_WRITE` | Write step count and activity segment to Google Fit |
| `FITNESS_LOCATION_WRITE` | Write distance delta to Google Fit |
| `FOREGROUND_SERVICE` | Run background service |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ foreground service type |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Request battery optimization exemption |
| `ACTIVITY_RECOGNITION` | Required by Google Fit SDK |

---

## 9. Intensity Level Reference

| # | Enum (DB) | Display Name (zh-TW) | Cadence (SPM) | Stride (m/step) | ~Speed (km/h) |
|---|---|---|---|---|---|
| 1 | `STROLL` | 散步 | 80 | 0.60 | 2.9 |
| 2 | `BRISK_WALK` | 快走 | 110 | 0.63 | 4.2 |
| 3 | `SUPER_SLOW_JOG` | 超慢跑 | 140 | 0.67 | 5.6 |
| 4 | `JOG` | 慢跑 | 165 | 0.70 | 6.9 |
| 5 | `MARATHON` | 馬拉松 | 180 | 0.78 | 8.4 |
| 6 | `FAST_RUN` | 快跑 | 190 | 0.92 | 10.5 |
| 7 | `SPRINT` | 衝刺 | 210 | 1.00 | 12.6 |

**Default:** `BRISK_WALK` (快走)

**Speed formula:** `SPM × stride × 0.06` (approximate km/h).

**Step noise:** Gaussian σ = 5 steps per segment (unchanged).

**Legacy runs:** v1.0 runs stored with old enum names remain readable; display uses current Traditional Chinese names for known enums.

---

## 10. Future Versions

### v1.2 — Custom Intensity Parameters (conditional)

Allow user-edited cadence/stride or custom named levels. **May be cancelled** if v1.1 seven-level presets satisfy user feedback (GitHub Issue #4).

### v1.3 — Google Play Store Release

Privacy Policy, OAuth verification, Play Store listing assets.

### v1.4 — Write Path Adaptability

Alternative `FitWriter` implementations if Google Fit API changes.

### v1.x — Multi-Language Support

Extract strings to proper i18n resources (`values-zh-rTW`, `values-en`, etc.) with runtime or system locale selection.

### v2.0 — Multi-Device Dashboard

Cloud sync and web dashboard.

### v2.1 — Export Function

Export run history as CSV or JSON.

---

## 11. Change Log from v1.0

| Area | v1.0 | v1.1 |
|---|---|---|
| Accounts per RUN | One active account | Multiple enabled accounts, concurrent session |
| Home layout | Scroll; Start at bottom | RUN at top; simplified sections |
| Intensity UI | 5 FilterChips | 7-level dropdown |
| Batch UI | FilterChips | Slider |
| Account UX | Switch active on Home | Enable checkboxes in Account Edit |
| Active run UI | Single ActiveRunScreen | ActiveRunsScreen (session + per-account) |
| History filter | Active account | Account dropdown |
| Notifications | Single notification | Summary + NotificationGroup per account |
| Write failure | Stops entire run | Fails one account; others continue |
| Session preflight | Single account | All enabled accounts; all-or-nothing |
| Recovery | Single `RUNNING` row (`LIMIT 1`) | Full session / all `RUNNING` rows |
| UI language | English | Traditional Chinese (fixed) |
| Intensity levels | 5 | 7 (retuned values) |
| Config memory | Partial (account only) | Intensity, duration, batch persisted |

**GitHub Issues addressed:** [#1](https://github.com/Pixson-Lin/APBFit/issues/1) multi-account concurrent write, [#2](https://github.com/Pixson-Lin/APBFit/issues/2) UI simplification, [#3](https://github.com/Pixson-Lin/APBFit/issues/3) config persistence, [#4](https://github.com/Pixson-Lin/APBFit/issues/4) expanded intensity presets.

---

*End of SRS v1.1*
