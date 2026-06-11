# APBFit — Cursor Pro Development Prompt

## Role

You are an expert Android developer. You will implement APBFit from scratch based on the SRS and technical context provided below. Ask clarifying questions before writing any code if requirements are ambiguous. Implement one logical unit at a time and confirm before proceeding to the next.

---

## Project Summary

APBFit (Auto Personal Boost Fit) is an Android app that writes simulated walking and running activity data into Google Fit via the **Google Fit Android SDK** (`HistoryClient.insertData()`), generating step records compatible with fitness-integrated applications.

---

## Key Technical Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Write API | Google Fit Android SDK (`HistoryClient`) | Provides reliable local write path; data appears in GF derived streams read by downstream apps |
| DataSource scope | One per data type per Google account | Persisted in SharedPrefs; created once, reused across runs |
| Segment duration | Randomized 25–35 seconds | Produces naturalistic activity patterns |
| Three data types per segment | step_count_delta + distance_delta + activity_segment | Required for complete activity representation |
| Concurrency | Single run at a time per app instance | Simplifies service lifecycle and account management |
| Local DB | Room | MVVM-compatible, supports per-account query filtering |

### Write Path Design Note

The `GoogleFitWriter` component **must** be implemented behind a `FitWriter` interface to allow future substitution if the underlying write path changes. This is a hard architectural requirement, not optional.

---

## Tech Stack

| Component | Choice |
|---|---|
| Language | Kotlin |
| Min SDK | API 31 (Android 12) |
| Target SDK | API 35 (Android 15) |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Database | Room |
| Async | Kotlin Coroutines + Flow |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| Google Auth | `play-services-auth` |
| Google Fit | `play-services-fitness` |
| Build | Gradle (Kotlin DSL) |

---

## Key Dependencies (build.gradle.kts)

```kotlin
// Google
implementation("com.google.android.gms:play-services-auth:21.2.0")
implementation("com.google.android.gms:play-services-fitness:21.1.0")

// Hilt
implementation("com.google.dagger:hilt-android:2.51.1")
kapt("com.google.dagger:hilt-android-compiler:2.51.1")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.05.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.9.0")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// Hilt Navigation
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
implementation("androidx.lifecycle:lifecycle-service:2.8.0")
```

---

## Package Structure

```
com.pixson.apbfit
├── data
│   ├── db
│   │   ├── AppDatabase.kt
│   │   ├── dao
│   │   │   ├── RunDao.kt
│   │   │   └── SegmentRecordDao.kt
│   │   └── entity
│   │       ├── RunEntity.kt
│   │       └── SegmentRecordEntity.kt
│   ├── model
│   │   ├── Run.kt
│   │   ├── SegmentRecord.kt
│   │   ├── IntensityLevel.kt
│   │   └── RunStatus.kt
│   ├── prefs
│   │   └── DataSourcePrefs.kt        ← SharedPrefs for GF DataSource ID cache
│   └── repository
│       ├── RunRepository.kt
│       └── AccountRepository.kt
├── domain
│   └── fit
│       ├── FitWriter.kt              ← interface (required for future-proofing)
│       └── GoogleFitWriter.kt        ← implementation via HistoryClient
├── service
│   └── RunForegroundService.kt
├── ui
│   ├── screen
│   │   ├── SignInScreen.kt
│   │   ├── HomeScreen.kt
│   │   ├── ActiveRunScreen.kt
│   │   ├── HistoryScreen.kt
│   │   └── SettingsScreen.kt
│   ├── viewmodel
│   │   ├── HomeViewModel.kt
│   │   ├── ActiveRunViewModel.kt
│   │   └── HistoryViewModel.kt
│   ├── component
│   │   └── (shared composables)
│   └── theme
│       └── Theme.kt
└── MainActivity.kt
```

---

## Data Model

### RunEntity

```kotlin
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,           // UUID
    val accountId: String,
    val startTime: Long,                   // epoch millis
    val endTime: Long?,
    val durationMinutes: Int,
    val intensityLevel: String,            // IntensityLevel enum name
    val batchSize: Int,
    val status: String,                    // RunStatus enum name
    val totalStepsWritten: Int,
    val validationResult: String?,         // "ACCEPTED" / "REJECTED" / null
    val validationStepCount: Int?,
    val validationTime: Long?,
    val errorMessage: String?
)
```

### SegmentRecordEntity

```kotlin
@Entity(tableName = "segment_records",
        foreignKeys = [ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )],
        indices = [Index("runId")])
data class SegmentRecordEntity(
    @PrimaryKey val id: String,            // UUID
    val runId: String,
    val segmentIndex: Int,
    val startTime: Long,                   // epoch millis
    val endTime: Long,
    val steps: Int,
    val distanceMeters: Float,
    val writeTime: Long,
    val success: Boolean,
    val errorMessage: String?
)
```

---

## Intensity Levels

```kotlin
enum class IntensityLevel(
    val displayName: String,
    val cadenceSpm: Int,
    val strideMeters: Double
) {
    STROLL      ("Stroll",       80,  0.60),
    BRISK_WALK  ("Brisk Walk",  110,  0.72),
    JOG         ("Jog",         150,  0.85),
    MARATHON    ("Marathon",    170,  0.92),
    SPRINT      ("Sprint",      190,  1.00)
}
```

---

## Segment Generation Algorithm

```
segmentDurationSec  = random(25..35)           // uniform
baseSteps           = cadenceSpm / 60.0 * segmentDurationSec
steps               = max(1, round(gaussian(mean=baseSteps, sigma=5)))
distanceMeters      = steps * strideMeters
activityType        = 8                        // running (GF activity enum)

startTimeNanos      = previous segment endTimeNanos (or run startTime for first)
endTimeNanos        = startTimeNanos + segmentDurationSec * 1_000_000_000L
```

Write condition: `endTimeNanos <= System.currentTimeMillis() * 1_000_000L`
(never write future segments)

---

## GoogleFitWriter Requirements

```kotlin
interface FitWriter {
    suspend fun ensureDataSources(account: GoogleSignInAccount): Result<Unit>
    suspend fun writeSegments(
        account: GoogleSignInAccount,
        segments: List<SegmentData>
    ): Result<Unit>
}
```

Implementation notes for `GoogleFitWriter`:
- Use `Fitness.getHistoryClient(context, account).insertData(dataSet).await()`
- Three `insertData()` calls per batch: steps DataSet, distance DataSet, activity DataSet
- All three calls must succeed; if any fails, return `Result.failure()` with the error
- DataSource IDs are read from `DataSourcePrefs` (cached after `ensureDataSources`)
- DataSource `type = DataSource.TYPE_RAW`
- DataSource `application.packageName = context.packageName`

---

## RunForegroundService Requirements

- Extends `LifecycleService` (from `lifecycle-service`)
- Started with `startForegroundService()` from ViewModel
- Communicates state back to UI via `StateFlow` exposed through a bound service or shared `Repository`
- Segment loop runs in a coroutine on `Dispatchers.Default`
- Loop logic:
  1. Generate segment duration (25–35s random)
  2. Sleep for that duration (`delay()`)
  3. Check if `now > run.startTime + run.durationMinutes * 60_000`; if yes, flush and stop
  4. Otherwise, generate segment, add to queue
  5. If `queue.size >= batchSize`, write batch via `FitWriter`
  6. On write failure, update run status to FAILED and stop service
- On stop (manual or completion): flush remaining queue, then finalize run

---

## Foreground Service Notification

- Channel ID: `apbfit_run_channel`
- Importance: `IMPORTANCE_LOW` (no sound)
- Content:
  - Title: "APBFit Running"
  - Text line 1: intensity level display name
  - Text line 2: "{totalSteps} steps written"
  - Text line 3: "{mm:ss} remaining"
- Action button: "Stop" → sends stop intent to service
- Ongoing: true
- Foreground service type: `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (API 34+) with fallback

---

## Account and DataSource Persistence

`DataSourcePrefs` stores per-account DataSource IDs:

```kotlin
// Keys pattern: "ds_steps_{accountId}", "ds_distance_{accountId}", "ds_activity_{accountId}"
class DataSourcePrefs(context: Context) {
    fun getDataSourceIds(accountId: String): Triple<String?, String?, String?>
    fun saveDataSourceIds(accountId: String, steps: String, distance: String, activity: String)
}
```

---

## Environment Checks (HomeScreen)

Perform at HomeScreen load via ViewModel:

1. Battery optimization: `PowerManager.isIgnoringBatteryOptimizations(packageName)`
2. Google Fit installed: `packageManager.getPackageInfo("com.google.android.apps.fitness", 0)`
3. GF permissions granted: `GoogleSignIn.hasPermissions(account, fitnessOptions)`
4. Notification permission: `NotificationManagerCompat.areNotificationsEnabled()`

Each check result: `PASS` / `WARN` (never blocks run start).

---

## Data Retention

On `Application.onCreate()`:
```kotlin
// Delete runs and cascaded segment_records older than 90 days
runRepository.deleteOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
```

Run silently; no user notification.

---

## Result Validation Entry

On HistoryScreen, each run row has a "Log Result" button (or pencil icon if already logged).
Tapping opens a bottom sheet with:
- Dropdown: `Accepted` / `Rejected`
- Text field: step count reported by downstream app (numeric, optional if Rejected)
- Save button

On save: update `RunEntity.validationResult`, `validationStepCount`, `validationTime`.
Only the latest entry is kept (overwrite in place).

---

## AndroidManifest Permissions

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" />
```

---

## Implementation Order (Suggested)

Implement in this sequence to allow incremental testing at each step:

1. **Project scaffold** — Hilt setup, Room DB, navigation graph, empty screens
2. **Data layer** — Entities, DAOs, Repository, DataSourcePrefs
3. **Google Sign-In** — SignInScreen, AccountRepository, account switching in Settings
4. **GoogleFitWriter** — FitWriter interface + GoogleFitWriter implementation, ensureDataSources
5. **Segment generation logic** — Pure Kotlin, unit-testable, no Android dependency
6. **RunForegroundService** — Service loop, batch queue, notification
7. **HomeScreen** — Config form, environment checks, Start button wiring
8. **ActiveRunScreen** — Live stats from service StateFlow
9. **HistoryScreen** — Run list, segment expansion, result validation entry
10. **SettingsScreen** — Account management, manual data clear, external shortcuts
11. **Data retention** — Auto-delete on app start
12. **Polish** — Error states, edge cases, empty states

---

## Constraints and Rules

- Never write a segment with `endTime > System.currentTimeMillis()`.
- The `FitWriter` interface must not be coupled to any Android context in its method signatures beyond what is strictly necessary. `GoogleSignInAccount` is acceptable; `Context` should be injected at construction time via Hilt.
- Room operations must run on `Dispatchers.IO`.
- Service coroutines must use `lifecycleScope` (from `LifecycleService`).
- All user-facing strings must be in `strings.xml` (English for v1.0).
- Do not use `Thread.sleep()`; use `kotlinx.coroutines.delay()`.
- Minimum Compose version compatible with API 31.

---

## Out of Scope for This Implementation

- Health Connect write path
- Concurrent multi-account runs
- Custom intensity parameter editing
- Cloud sync
- Export (CSV/JSON)
- Any web or REST backend
