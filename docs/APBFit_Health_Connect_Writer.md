# Health Connect write path (Track B)

| Field | Value |
|---|---|
| Branch | `feature/health-connect-writer` |
| Status | Active development |
| Default write path | Google Fit (`GoogleFitWriter`) |

This document describes the Health Connect (`HC`) write implementation behind the existing `FitWriter` seam. HC is **on-device only** — it does not restore cross-device cloud sync. See [Google Fit Sync Investigation](APBFit_GoogleFit_Sync_Investigation.md).

---

## Components

| Class | Role |
|---|---|
| `FitWriter` | Unchanged interface; run engine / UI depend on this |
| `GoogleFitWriter` | Default production writer (Google Fit `HistoryClient.insertData`) |
| `HealthConnectWriter` | HC implementation: writes `StepsRecord`, `DistanceRecord`, `ExerciseSessionRecord` per segment |
| `HealthConnectClientProvider` | Availability check + `HealthConnectClient.getOrCreate()` |
| `HealthConnectPermissions` | Write + read (steps) permission sets for request/check |
| `HealthConnectPermissionRepository` | SDK availability + missing permission checks |
| `HealthConnectDebugReadback` | Enables step read-back log after insert during debug run |
| `FitModule` | Selects writer via `BuildConfig.USE_HEALTH_CONNECT_WRITER` |

### Account model gap

`FitWriter` still accepts `GoogleSignInAccount` for compatibility with the existing session engine. **Health Connect access is device-scoped** and does not use the Google Fit account for writes. Granting Google Sign-In + Fitness scopes does **not** imply HC write permission. HC permissions are requested via `PermissionController` from HomeScreen when the HC writer is active.

---

## Permissions

Manifest declarations (Android 14+ rationale activity included):

- `android.permission.health.READ_STEPS`
- `android.permission.health.WRITE_STEPS`
- `android.permission.health.WRITE_DISTANCE`
- `android.permission.health.WRITE_EXERCISE`

`HealthConnectWriter.ensureDataSources()` verifies read/write permissions are granted. HomeScreen launches the HC permission contract when starting a run or using debug actions on the `healthConnect` build.

---

## Build / run / verify

### Default (Google Fit)

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

### Health Connect build type

The `healthConnect` build type sets `USE_HEALTH_CONNECT_WRITER=true`:

```bash
./gradlew :app:assembleHealthConnect
./gradlew :app:installHealthConnect
```

On a physical device or emulator with Health Connect installed:

1. Install the `healthConnect` APK.
2. Grant HC write permissions for steps, distance, and exercise (Settings → Health Connect → App permissions → APBFit).
3. Sign in with Google as usual (session engine unchanged).
4. Start a Run; segments are written to HC instead of Google Fit.

### Unit / integration tests

Tests use `androidx.health.connect:connect-testing` (`FakeHealthConnectClient`):

```bash
./gradlew :app:testDebugUnitTest --tests "com.pixsonlin.apbfit.domain.fit.HealthConnectWriterTest"
```

Coverage:

- Happy path: inserts steps, distance, and running exercise records (`Metadata.manualEntry()`)
- Permission preflight failure
- Future segment rejection (integrity rule)
- Insert failure propagation
- Debug run: logcat tag `HealthConnectWriter` shows `HC readback` lines after each batch write

---

## Cutover checklist (before merging to `main`)

- [x] HC permission request via `PermissionController` from HomeScreen (healthConnect build)
- [ ] Manual validation on device with `healthConnect` build
- [ ] Owner decision on default writer switch (`USE_HEALTH_CONNECT_WRITER`)
- [ ] Update tester onboarding docs if HC becomes default
