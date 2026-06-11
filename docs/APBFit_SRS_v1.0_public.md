# APBFit — Auto Personal Boost Fit
## Software Requirements Specification (SRS)
### Version 1.0

| Field | Value |
|---|---|
| Project Name | APBFit — Auto Personal Boost Fit |
| Document Type | Software Requirements Specification |
| Version | 1.0 |
| Author | Pixson |
| Date | 2026-06-10 |
| Status | Approved for Development |
| Distribution | Sideload (Phase 1), Google Play Store (Phase 2) |

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

---

## 1. Introduction

### 1.1 Purpose

APBFit is an Android application that writes simulated walking and running activity data into Google Fit on behalf of a signed-in Google account. Its primary purpose is to generate step count records compatible with fitness-integrated applications.

The application provides a structured run-based workflow with full observability and per-run result logging, allowing users to track whether written data is accepted by downstream fitness applications.

### 1.2 Scope

**In scope for v1.0:**
- Google Sign-In with single active account per run
- Google Fit data writing via Android SDK (`HistoryClient`)
- Run-based workflow with configurable intensity and duration
- Foreground Service for background execution
- Per-run history with segment-level detail
- Downstream app result validation logging per run
- Multi-account support (sequential, not concurrent)
- Automatic and manual data retention management

**Out of scope for v1.0:**
- Concurrent multi-account runs
- Custom intensity parameter editing
- Health Connect write path
- Cloud sync or multi-device dashboard
- Google Play Store publication (sideload only in v1.0)

### 1.3 Definitions

| Term | Definition |
|---|---|
| Run | One complete execution session from start to stop, defined by a Google account, intensity level, and duration. |
| Segment | A single time-bounded data write unit. Contains one StepCountDelta, one DistanceDelta, and one ActivitySegment record. Duration is randomized per segment. |
| Batch | A group of completed segments written to Google Fit in a single `insertData()` call. Batch size is configurable (1–10 segments). |
| Intensity Level | A preset combination of cadence (SPM) and stride length (m/step) representing a movement style. |
| Result Validation | User-reported feedback on whether a completed run's data was accepted by a downstream fitness application. |
| DataSource | A Google Fit data stream registered under the app's package name and the user's Google account. One DataSource per type per account. |
| Flush | Immediate write of all accumulated but not-yet-written segments, triggered on run completion or manual stop. |

---

## 2. System Overview

### 2.1 Goals

- Write Google Fit step data compatible with downstream fitness applications
- Provide a reliable Foreground Service that survives screen-off and app backgrounding
- Maintain per-run history with segment-level transparency
- Allow users to log and review downstream validation results

### 2.2 Architecture

```
┌─────────────────────────────────────┐
│             MainActivity            │
│   (Account Selector, Run Config,    │
│    History, Settings)               │
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
└──────┬──────┘   │   SharedPrefs)     │
       │          └────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│          ForegroundService          │
│  (Segment generator, batch queue,   │
│   notification, timer)              │
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
| Write API | Google Fit Android SDK (`HistoryClient`) | Compatible with target fitness applications; provides reliable local write path |
| DataSource scope | One per data type per Google account | Persisted in SharedPrefs; created once, reused across runs |
| Segment duration | Randomized 25–35 seconds | Produces naturalistic data patterns |
| Concurrency | Single run at a time per app instance | Simplifies service lifecycle and account management |
| Local DB | Room | MVVM-compatible, supports per-account query filtering |

---

## 3. User Roles

### 3.1 End User

A user who installs APBFit to generate Google Fit step data for compatibility with fitness-integrated applications. Expected to have basic familiarity with Google Fit. No technical expertise required beyond initial Google account sign-in.

---

## 4. Functional Requirements

### FR-001 — Google Sign-In

**Description:** The user must sign in with a Google account before any run can be started. Multiple Google accounts may be added to the app and switched between runs.

**Rules:**
- App launch with no signed-in account must present the sign-in screen immediately.
- Once signed in, the account is persisted across app restarts.
- The user may switch accounts from the main screen; switching is only permitted when no run is active.
- Each account has its own independent set of DataSources and run history.

**Inputs:** Google account via Google Sign-In SDK.

**Outputs:** Authenticated `GoogleSignInAccount` bound to the active session.

---

### FR-002 — DataSource Initialization

**Description:** On first use of a Google account, APBFit creates three Google Fit DataSources under that account. DataSource IDs are cached in SharedPreferences keyed by account ID.

**DataSources created:**

| Stream Name | Data Type | Field | Format |
|---|---|---|---|
| `apbfit_step_count` | `com.google.step_count.delta` | `steps` | integer |
| `apbfit_distance` | `com.google.distance.delta` | `distance` | floatPoint |
| `apbfit_activity` | `com.google.activity.segment` | `activity` | integer |

**Rules:**
- If a DataSource already exists (HTTP 409), the existing DataSource ID is retrieved and cached.
- DataSource creation is performed before the first run starts, not at app launch.
- If DataSource creation fails, the run is aborted with an error message.

---

### FR-003 — Run Configuration

**Description:** Before starting a run, the user configures the following parameters.

**Parameters:**

| Parameter | Type | Range | Default |
|---|---|---|---|
| Google Account | Selector | Signed-in accounts | Last used |
| Intensity Level | Selector | 5 preset levels | Brisk Walk |
| Duration | Slider | 5 min – 6 hr, step 5 min | 30 min |
| Batch Size | Selector | 1 – 10 segments | 3 |

**Rules:**
- Duration is set via slider only; manual text input is not supported.
- Intensity level selection displays the corresponding SPM and stride length as read-only reference.
- Account switching is disabled while a run is active.

---

### FR-004 — Start Run

**Description:** The user starts a run from the configuration screen.

**Trigger:** Start button tap.

**Rules:**
- Only one run may be active at a time for any given app instance.
- Starting a run creates a `Run` record in the local database with status `RUNNING`.
- Starting a run launches the Foreground Service and displays the persistent notification.
- The run's `startTime` is recorded as the moment the Start button is tapped.

---

### FR-005 — Segment Generation

**Description:** The Foreground Service continuously generates and queues segments for the duration of the run.

**Segment generation rules:**
- Segment duration is randomized uniformly between 25 and 35 seconds.
- Step count is calculated as: `round(gaussRandom(mean=SPM/60 × durationSec, std=5))`, clamped to a minimum of 1.
- Distance is calculated as: `steps × strideMeters`, rounded to 2 decimal places.
- Activity type is fixed at `8` (running) for all intensity levels in v1.0.
- Each segment's `startTimeNanos` equals the previous segment's `endTimeNanos`; the first segment starts at run `startTime`.
- Segments are only written after their `endTime` has passed (retrospective write).

**Timing:**
- The service sleeps for the randomized segment duration, then generates the segment.
- Generation continues until the next scheduled wake-up time would exceed `startTime + durationMinutes`.
- On natural completion, all queued unwritten segments are flushed before the run is finalized.

---

### FR-006 — Batch Write to Google Fit

**Description:** Completed segments are accumulated in a queue and written to Google Fit in batches.

**Rules:**
- A batch write is triggered when the queue reaches `batchSize` segments.
- Each batch write calls `HistoryClient.insertData()` once per DataSource type (three calls per batch: steps, distance, activity).
- All three calls are made for the same set of segments; partial writes within a batch are not supported.
- If any of the three `insertData()` calls fails, the entire run is stopped immediately and marked as `FAILED`. Segments in the failed batch are recorded with `success = false`.
- On run completion (natural or manual stop), any remaining queued segments are flushed regardless of batch size.
- Each segment write attempt is recorded as a `SegmentRecord` in the local database.

---

### FR-007 — Foreground Service and Notification

**Description:** The run executes within a Foreground Service to ensure background execution.

**Notification content:**

| Field | Description |
|---|---|
| Title | "APBFit Running" |
| Line 1 | Intensity level name |
| Line 2 | Total steps written so far |
| Line 3 | Remaining time (mm:ss) |
| Action | Stop button |

**Rules:**
- Notification is updated after each successful batch write.
- Tapping the notification opens the app to the active run screen.
- The Stop action in the notification triggers FR-008 (manual stop).

---

### FR-008 — Stop Run (Manual)

**Description:** The user stops an active run before its scheduled completion.

**Trigger:** Stop button in app UI or notification.

**Rules:**
- Segments already written to Google Fit are retained.
- Segments generated but not yet written (queued) are flushed to Google Fit before the run is finalized.
- The run is marked as `STOPPED` in the database.
- The Foreground Service is stopped.
- The run result is computed from successfully written segments only.

---

### FR-009 — Run Completion (Automatic)

**Description:** The run ends automatically when all scheduled segments have been generated and written.

**Rules:**
- The service detects that the next segment's start time would exceed `startTime + durationMinutes`.
- All queued segments are flushed.
- The run is marked as `COMPLETED`.
- The Foreground Service is stopped.
- The run result summary is computed and stored.

---

### FR-010 — Run Failure Handling

**Description:** If a Google Fit write fails during a run, the run is aborted.

**Rules:**
- The failed batch's segments are recorded with `success = false` and the error message.
- The run status is set to `FAILED`.
- The Foreground Service is stopped.
- The user is shown an error notification and an in-app error state on the run screen.
- No retry is attempted.

---

### FR-011 — Run History

**Description:** The user can view a list of all past runs associated with the active Google account.

**Display per run (summary):**
- Start time and date
- Intensity level
- Duration (configured vs. actual)
- Total steps written
- Run status (`COMPLETED` / `STOPPED` / `FAILED`)
- Result validation badge (if logged)

**Rules:**
- History is filtered by the currently signed-in Google account.
- Runs are sorted by start time, descending.
- Each run row is expandable to show segment-level detail (FR-012).

---

### FR-012 — Segment Detail View

**Description:** The user can expand a run in the history list to view individual segment records.

**Display per segment:**
- Segment index
- Start time – End time
- Steps written
- Distance written
- Write status (`SUCCESS` / `FAILED`)
- Error message (if failed)

---

### FR-013 — Result Validation Logging

**Description:** The user can log the downstream application result for a completed or stopped run.

**Input:**
- Validation result: dropdown (`Accepted` / `Rejected`)
- Step count reported by downstream app: integer input field (optional if `Rejected`)

**Rules:**
- Validation can be logged or updated at any time after the run ends.
- Only the most recent validation entry is retained per run.
- Validation is stored in the `Run` record.
- A validation badge is shown in the run history summary row.

---

### FR-014 — Settings

**Description:** The Settings screen provides account management and data management functions.

**Available actions:**
- Sign in additional Google accounts
- Switch active Google account (only when no run is active)
- Sign out current account
- Clear all history for current account
- Navigate to: Battery Optimization settings, App Details settings, Notification settings, Google Fit app

**Automatic data retention:**
- On app launch, run records and segment records older than 90 days are automatically deleted for all accounts.
- This operation runs silently with no user notification.

---

### FR-015 — Environment Check

**Description:** The app displays a pre-run checklist to surface potential issues.

**Checks performed:**

| Check | Source |
|---|---|
| Battery Optimization disabled for APBFit | `PowerManager` |
| Google Fit installed | Package manager |
| Google Fit connected to the signed-in account | `GoogleSignIn.hasPermissions` |
| Notification permission granted | `NotificationManager` |

**Rules:**
- Failed checks are shown as warnings, not hard blockers (the user may still start a run).
- Each warning provides a direct shortcut to the relevant settings screen.

---

## 5. Non-Functional Requirements

### NFR-001 — Background Execution Reliability

The Foreground Service must continue generating and writing segments while the screen is off, the app is backgrounded, and the device is locked. The app must request battery optimization exemption and guide the user to grant it.

### NFR-002 — Minimum Android Version

Android 12 (API level 31) and above. Google Play Services must be available on the device.

### NFR-003 — Write Latency

Each batch write (three `insertData()` calls) must complete within 10 seconds under normal network conditions. If the call does not complete within this window, it is treated as a failure per FR-010.

### NFR-004 — Data Integrity

A segment must never be written with a future `endTimeNanos`. All segment records in the local database must have a corresponding write attempt recorded.

### NFR-005 — Account Isolation

All local database records (runs, segments, validations) are associated with a Google account ID. Querying history for one account must never surface records belonging to another account.

### NFR-006 — UI Responsiveness

The main thread must not be blocked by Google Fit API calls or database operations. All async operations use Kotlin coroutines with appropriate dispatchers.

### NFR-007 — Maintainability

Architecture follows MVVM with Repository pattern. Room is the single source of truth for all persisted state. The `GoogleFitWriter` component is isolated behind a `FitWriter` interface to allow future substitution if the write path changes.

---

## 6. Data Model

### 6.1 Run

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `accountId` | String | Google account ID |
| `startTime` | Instant | Time the Start button was tapped |
| `endTime` | Instant? | Null while running |
| `durationMinutes` | Int | Configured duration |
| `intensityLevel` | Enum | STROLL / BRISK_WALK / JOG / MARATHON / SPRINT |
| `batchSize` | Int | 1–10 |
| `status` | Enum | RUNNING / COMPLETED / STOPPED / FAILED |
| `totalStepsWritten` | Int | Sum of successful segment steps |
| `validationResult` | Enum? | ACCEPTED / REJECTED / null |
| `validationStepCount` | Int? | User-reported step count from downstream app |
| `validationTime` | Instant? | Time of most recent validation entry |
| `errorMessage` | String? | Set on FAILED status |

### 6.2 SegmentRecord

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `runId` | UUID | Foreign key → Run |
| `segmentIndex` | Int | 0-based index within run |
| `startTime` | Instant | Segment start |
| `endTime` | Instant | Segment end |
| `steps` | Int | Steps written |
| `distanceMeters` | Float | Distance written |
| `writeTime` | Instant | Time `insertData()` was called |
| `success` | Boolean | |
| `errorMessage` | String? | |

### 6.3 AccountDataSourceCache

| Field | Type | Notes |
|---|---|---|
| `accountId` | String | Primary key |
| `dsIdSteps` | String | GF DataSource ID for step_count_delta |
| `dsIdDistance` | String | GF DataSource ID for distance_delta |
| `dsIdActivity` | String | GF DataSource ID for activity_segment |

---

## 7. UI / Screen Inventory

| Screen | Description |
|---|---|
| **SignInScreen** | Shown on first launch or when no account is signed in. Google Sign-In button only. |
| **HomeScreen** | Shows active account, environment check summary, run configuration form, Start button, and shortcut to history. |
| **ActiveRunScreen** | Shows live run stats (intensity, elapsed time, remaining time, steps written, segments written). Stop button. Replaces HomeScreen while run is active. |
| **HistoryScreen** | List of runs for the active account. Each row expandable to show SegmentRecords. Result validation entry accessible per run. |
| **SettingsScreen** | Account management, data retention, external app shortcuts. |

---

## 8. Permission Requirements

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

| Level | Name | Cadence (SPM) | Stride (m/step) | ~Speed (km/h) |
|---|---|---|---|---|
| 1 | Stroll | 80 | 0.60 | 2.9 |
| 2 | Brisk Walk | 110 | 0.72 | 4.7 |
| 3 | Jog | 150 | 0.85 | 7.7 |
| 4 | Marathon | 170 | 0.92 | 9.4 |
| 5 | Sprint | 190 | 1.00 | 11.4 |

Speed is approximate and provided for reference only. Step count generation applies Gaussian noise (σ = 5 steps per segment).

---

## 10. Future Versions

### v1.1 — Concurrent Multi-Account Runs

Allow the user to start simultaneous runs across multiple signed-in Google accounts with identical or independent configurations. Requires multiple independent Foreground Service instances and per-account notification channels.

### v1.2 — Custom Intensity Parameters

Allow the user to edit cadence and stride length for each intensity level, or define custom named levels beyond the five presets.

### v1.3 — Google Play Store Release

Prepare for public distribution: Privacy Policy, OAuth verification submission for Fitness API restricted scopes, Play Store listing assets.

### v1.4 — Write Path Adaptability

Isolate the write path behind the `FitWriter` interface to allow substitution if the underlying Google Fit API changes or alternative write paths become viable.

### v2.0 — Multi-Device Dashboard

Cloud synchronization of run history (Firebase or Google Drive) with a companion web dashboard for cross-device and cross-account comparison of acceptance rates and run parameters.

### v2.1 — Export Function

Export run history and segment records as CSV or JSON for external analysis.
