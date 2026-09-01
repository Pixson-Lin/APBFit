# APBFit — Health Connect Migration Plan

| Field | Value |
|---|---|
| Document | HC-only cutover plan (replaces dual-track strategy) |
| Status | **Approved for review** — do not start implementation until owner signs off |
| Created | 2026-09-01 |
| Target date | **2026-09-04** — first HC-default build ready for internal validation / Play upload |
| Branch | `feature/health-connect-writer` (WIP; not yet merged to `main`) |
| Supersedes | Track A/B split in [APBFit_Agent_Progress.md](APBFit_Agent_Progress.md) for write-path work |
| Related | [APBFit_Health_Connect_Writer.md](APBFit_Health_Connect_Writer.md), [APBFit_GoogleFit_Sync_Investigation.md](APBFit_GoogleFit_Sync_Investigation.md), [APBFit_applicationId_rename_plan.md](APBFit_applicationId_rename_plan.md) |

---

## 1. Why we are changing course

- **External driver:** Pikmin Bloom is discontinuing Google Fit support in ~2 weeks (owner announcement, 2026-09-01). Downstream validation must move to **Health Connect (HC)**.
- **Internal validation:** On `feature/health-connect-writer`, a 5-minute debug run with `healthConnect` build type **passed** — Pikmin reads correct step counts from HC.
- **Strategy change:** Abandon the dual-track plan (GF on `main`, HC on feature branch). **Ship HC as the only write path** before the Pikmin GF cutoff.

HC remains **on-device only** — it does not restore cross-device cloud sync. See [Google Fit Sync Investigation](APBFit_GoogleFit_Sync_Investigation.md).

---

## 2. Product decisions (owner, 2026-09-01)

These three decisions govern scope for the 9/4 milestone.

| # | Decision | Implication |
|---|---|---|
| **D1** | **Do not keep multi-account** | Remove multi-account session UI, parallel per-account runs, account enable/disable sheet, debug “two accounts” requirement, and related prefs/history scoping. One signed-in Google identity per app install. |
| **D2** | **Keep Google Sign-In** | Retain Google account sign-in (email/profile scopes). **Remove** Google Fit / Fitness OAuth scopes. Rationale: Pikmin Bloom’s HC setup also involves sign-in; purpose is not fully understood yet — keep Sign-In for parity and future investigation. HC writes remain **device-scoped** and do not use the GF account binding. |
| **D3** | **Target: 2026-09-04** | Deliver an HC-default build suitable for internal testing / Play internal upload and updated tester-facing copy. Not necessarily public production. |

### D1 — Single account: what goes away

| Area | Current (v1.x) | After cutover |
|---|---|---|
| Multiple enabled accounts | `EnabledAccountsPrefs`, account edit sheet | One active account |
| Multi-account Run Session | `startSession(accountIds: List)` with N parallel runs | Single run per session |
| History account picker | `HistoryScreen` dropdown | Single account (or remove picker) |
| Debug run | Required 2 known accounts | Single account |
| `FitWriter.writeSegments(account, …)` | Per-account parameter | May keep signature with sole account, or simplify in a follow-up refactor |

### D2 — Google Sign-In: what stays vs goes

| Keep | Remove |
|---|---|
| Google Sign-In (email / basic profile) | `FitnessOptions` / `FITNESS_*` OAuth scopes |
| OAuth Android client (`com.pixsonlin.apbfit`) + SHA-1s | `hasFitnessPermissions()`, `getFitnessPermissionsIntent()` |
| OAuth **Testing** mode + test users (≤100) for internal test | Google Fit app install prerequisite |
| Play internal test distribution | `play-services-fitness` dependency |

### D3 — 9/4 success criteria (definition of done)

- [ ] `debug` / `release` default to **Health Connect writer** (not `healthConnect` build type only).
- [ ] No Google Fit code path required to start or complete a Run.
- [ ] Environment checks and **Start Run** gate on **HC** availability + permissions (not GF install/scopes).
- [ ] Single-account UX shipped (multi-account UI removed or hidden).
- [ ] Privacy policy + internal tester invite updated for HC.
- [ ] Play Console Health Connect data-type declaration submitted (or documented blocker).
- [ ] Manual smoke: Sign-In → HC permissions → Run → steps visible in HC app → Pikmin reads steps.
- [ ] Version bump + Play internal build uploaded (or sideload release if Play blocked).

---

## 3. GCP / Play Console — what actually changes

**Common misconception:** Health Connect does **not** use Google Cloud Fitness API. There is no “HC API” to enable in GCP like Google Fit.

| Item | Needed for HC cutover? | Action |
|---|---|---|
| Enable Fitness API in GCP | **No** | Stop treating as migration work |
| OAuth Fitness restricted scopes | **No** | Remove from app + optional consent-screen cleanup |
| Google Sign-In OAuth client | **Yes** | Keep; package `com.pixsonlin.apbfit`; SHA-1s per [rename plan §6](APBFit_applicationId_rename_plan.md) |
| OAuth Testing mode + test users | **Yes** | Unchanged for internal test sign-in |
| Play app signing SHA-1 → OAuth | **Yes (if Play install)** | Still open per rename plan |
| **Play Console → Health Connect declaration** | **Yes** | Declare write access: steps, distance, exercise |
| Privacy policy URL | **Yes** | Must describe HC writes, not GF |

---

## 4. Current implementation status (`feature/health-connect-writer`)

### Done (local WIP — not fully committed)

| Item | Notes |
|---|---|
| `HealthConnectWriter` | `Metadata.manualEntry()`; steps + distance + exercise per segment |
| HC permissions manifest + rationale activity | `READ_STEPS`, `WRITE_*` |
| `PermissionController` contract on HomeScreen | Runtime HC permission request |
| `healthConnect` build type | `USE_HEALTH_CONNECT_WRITER=true` |
| `SessionPreflight` | Skips GF permission check when HC writer active |
| HC unit tests | `HealthConnectWriterTest` + `FakeHealthConnectClient` |
| Debug run (HC) | Single enabled account; read-back log in debug run |
| Pikmin validation | 5-minute debug run — steps correct |
| Test batch write | Fixed future-time window bug |

### Not done (required for 9/4)

| Item | Priority |
|---|---|
| Default writer = HC in `defaultConfig` / `release` | P0 |
| Replace GF environment checks with HC checks | P0 |
| `isEnvironmentReadyForRun` — stop requiring GF PASS | P0 |
| Remove multi-account UI and session logic | P0 |
| Remove `GoogleFitWriter`, `DataSourcePrefs`, `play-services-fitness` | P1 |
| Sign-In: drop Fitness scopes | P1 |
| All user-facing strings (GF → HC) | P1 |
| Privacy policy + `Internal_test_notes.txt` | P1 |
| Play HC data declaration | P1 |
| Commit + merge feature branch → `main` | P1 |
| Version bump + Play internal upload | P1 |

---

## 5. Work breakdown

### Phase 0 — Review gate (now)

- [V] Owner reviews this document and approves start.
- [ ] Commit existing HC WIP on `feature/health-connect-writer`.

### Phase 1 — P0 app code (blocks 9/4 build)

**1.1 Write path default**

- Set `BuildConfig.USE_HEALTH_CONNECT_WRITER=true` in `defaultConfig` (and `release`).
- `FitModule` binds `HealthConnectWriter` only.
- Remove or deprecate `healthConnect` build type after default flip.

**1.2 Environment & Start Run**

- `EnvironmentChecker`: replace GF installed + Fitness permissions with HC SDK status + HC permissions.
- `HomeViewModel.isEnvironmentReadyForRun`: gate on HC PASS (not `icons.fit` GF logic).
- `onFitIconTap` / settings shortcuts: open Health Connect settings or permission request (not GF / Fitness OAuth).

**1.3 Single account**

- Remove multi-account enable list, account edit sheet, parallel session accounts.
- `startSession` / `RunRepository` / UI: one account per run.
- Remove debug two-account requirement.
- Simplify history (no account dropdown).

**1.4 Permissions UX**

- Ensure HC permission request on: Sign-In completion (or first Home), **Start Run**, and debug actions.
- Strings: sign-in subtitle, env icons, errors — HC wording.

### Phase 2 — P1 removal & cleanup

- Delete `GoogleFitWriter.kt`, `DataSourcePrefs`, GF manifest query (`com.google.android.apps.fitness`).
- Remove `play-services-fitness` from Gradle.
- `AccountRepository`: remove `fitnessOptions`, fitness permission helpers; keep Sign-In.
- Update `RunSessionPowerEstimate` / tests (HC = one `insertRecords` per batch, not 3× GF `insertData`).

### Phase 3 — Compliance & distribution

- Rewrite [APBFit_Privacy_Policy.md](APBFit_Privacy_Policy.md) and [docs/privacy/index.html](privacy/index.html) for HC.
- Play Console: Health Connect data types + privacy policy link.
- Update [docs/notes/Internal_test_notes.txt](notes/Internal_test_notes.txt): remove GF prerequisite; add HC permission steps; validation via HC / Pikmin.
- Bump `versionCode` / `versionName`; upload Play internal build.
- Notify testers to reinstall.

### Phase 4 — Docs & merge

- Merge `feature/health-connect-writer` → `main` after smoke pass.
- Update README architecture diagram (HC only).
- Mark [APBFit_Agent_Progress.md](APBFit_Agent_Progress.md) dual-track section as superseded by this doc.
- Optional: SRS/SDS HC amendment (can trail 9/4).

---

## 6. Suggested schedule (target 9/4)

| Date | Focus |
|---|---|
| **9/1 (today)** | Review `HC_migration.md`; approve; commit WIP |
| **9/2** | Phase 1: HC default + env checks + Start Run + permission UX |
| **9/2–9/3** | Phase 1: single-account simplification |
| **9/3** | Phase 2: remove GF code/deps; Sign-In scope trim |
| **9/3–9/4** | Phase 3: privacy, Play declaration, internal notes; device smoke + Pikmin check |
| **9/4** | Version bump, Play upload, tester notification |

Buffer: if Play HC declaration review delays upload, ship **sideload `release` APK** to testers on 9/4 and upload Play when unblocked.

---

## 7. Risks & open questions

| Risk | Mitigation |
|---|---|
| Pikmin Sign-In purpose unknown | Keep Google Sign-In (D2); document as open question; do not block 9/4 on resolving |
| HC permission UX confusion | Copy in invite + in-app status messages; link to HC app permissions |
| `canStartRun` still GF-gated until Phase 1 | Fix in first implementation PR |
| Uncommitted WIP on feature branch | Commit before large refactors |
| Play HC declaration turnaround | Start Play Console form early on 9/3; sideload fallback |
| Single-account refactor touches many files | Dedicated PR; run existing unit tests + manual smoke |
| No cross-device sync | Do not promise in UI; same limitation as GF |

### Open questions (non-blocking unless discovered otherwise)

1. Does Pikmin require Google Sign-In to match HC data origin, or is it independent?
2. Should `FitWriter` drop `GoogleSignInAccount` parameter post-cutover, or keep for minimal diff?
3. Is Play internal upload mandatory on 9/4, or is sideload to known testers acceptable?

---

## 8. Testing checklist (pre-merge)

- [ ] Fresh install: Sign-In → HC permissions → Start Run completes.
- [ ] Steps appear in Health Connect app (steps / distance / exercise as applicable).
- [ ] Pikmin Bloom reads steps within expected sync delay.
- [ ] Screen-off run continues writing (FGS regression).
- [ ] Debug panel: test batch write succeeds (no future-time error).
- [ ] Sign-out / sign-in with different Google account (single slot).
- [ ] `./gradlew :app:testDebugUnitTest` passes (incl. `HealthConnectWriterTest`).

---

## 9. References

| Doc | Role |
|---|---|
| [APBFit_Health_Connect_Writer.md](APBFit_Health_Connect_Writer.md) | Technical HC writer implementation notes |
| [APBFit_Agent_Progress.md](APBFit_Agent_Progress.md) | Historical dual-track brief (superseded for write path) |
| [APBFit_applicationId_rename_plan.md](APBFit_applicationId_rename_plan.md) | OAuth SHA-1, Testing mode, Play signing |
| [notes/Internal_test_notes.txt](notes/Internal_test_notes.txt) | Tester invite (to update) |
| [APBFit_Privacy_Policy.md](APBFit_Privacy_Policy.md) | Privacy policy (to rewrite for HC) |

---

## 10. Approval

| Role | Name | Date | Approved |
|---|---|---|---|
| Owner | | | ☐ Reviewed — OK to start |

After approval, implementation starts on `feature/health-connect-writer` per Phase 1, without merging to `main` until Section 8 tests pass.
