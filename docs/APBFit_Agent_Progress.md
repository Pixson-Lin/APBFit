# APBFit — Agent Progress & Workstream Brief

| Field | Value |
|---|---|
| Document | Agent-oriented project progress / workstream split |
| Status | Active |
| Updated | 2026-09-02 |
| Audience | Cursor agents (and humans briefing them) |
| Related | [HC migration plan](HC_migration.md) (authoritative for write path), [README](../README.md), [SRS v1.2](APBFit_SRS_v1.2_public.md), [SDS v1.2](APBFit_SDS_v1.2_public.md), [Fit sync investigation](APBFit_GoogleFit_Sync_Investigation.md), [applicationId / Play plan](APBFit_applicationId_rename_plan.md), [Internal test invite](notes/Internal_test_notes.txt) |

---

## 1. Current product state (read first)

- **Shipped:** v1.4.20260901 (`versionCode` 26090101), `applicationId` / package `com.pixsonlin.apbfit`.
- **Write path today:** **Health Connect** via `FitWriter` → `HealthConnectWriter` (`HealthConnectClient.insertRecords()`). Google Fit writer removed (2026-09).
- **Distribution:** Google Play **internal testing**; sideload also supported.
- **Default branch:** `main` — Health Connect cutover merged from `feature/health-connect-writer`.
- **Authoritative migration doc:** [HC_migration.md](HC_migration.md).
- **Known limitation:** Health Connect is on-device only; does **not** restore cross-device cloud sync. See [Google Fit Sync Investigation](APBFit_GoogleFit_Sync_Investigation.md).

---

## 2. Decision (2026-08-27): two parallel tracks — **SUPERSEDED**

> **Superseded 2026-09-01.** Pikmin Bloom discontinued Google Fit support; owner approved HC-only cutover per [HC_migration.md](HC_migration.md). Track A/B split below is **historical** only. New agents: read `HC_migration.md`, not Track A/B briefs.

Health Connect write-path work was **pulled forward** on the roadmap (formerly “FitWriter adaptability” / README v1.7). Development must **not** block or destabilize tester recruitment on the current Play internal build.

| Track | Git line | Goal | Do not |
|---|---|---|---|
| **A — Tester recruitment** | `main` only | Recruit / onboard Play internal testers; keep install + sign-in + Run path reliable | Large write-path refactors; Health Connect implementation |
| **B — Health Connect write** | Feature branch off `main` | Implement HC simulated step write behind `FitWriter`, plus integration tests | Merge incomplete HC work into `main`; change Play listing / tester ops unless asked |

**Branch naming (Track B):** create from up-to-date `main`:

```text
feature/health-connect-writer
```

If the branch already exists, continue on it; do not invent a second parallel HC branch without owner approval.

**Merge policy:** Track B lands on `main` only via explicit review / PR when HC path + tests are ready enough for internal validation. Until then, `main` stays Google Fit–based for testers.

---

## 3. Track A — Agent brief (`main`: recruit testers)

### Mission

Support Play internal testing: clearer onboarding, invite copy, OAuth/tester roster alignment, small UX/copy/icon fixes that reduce sign-in failures.

### In scope

- Commit / polish uncommitted sign-in prerequisite messaging and internal-test notes if still dirty
- Tester invite text (`docs/notes/Internal_test_notes.txt`) and related UI strings
- Play Console / OAuth hygiene that unblocks testers (document SHA-1 / test-user alignment per [applicationId rename plan](APBFit_applicationId_rename_plan.md) §6 remaining items)
- Non-breaking bugfixes and docs for the **current Google Fit** build
- Version bump / Play upload only when owner requests a new internal build

### Out of scope

- `HealthConnectWriter` or switching default write path
- Breaking changes to `FitWriter` that require HC
- Roadmap features: custom intensity, orphan abandon toggle, i18n split, cloud sync

### Suggested first actions

1. `git checkout main && git pull`；確認 `git status`
2. Read this file + `docs/notes/Internal_test_notes.txt` + rename-plan §6 todos
3. Confirm with owner before Play Console / OAuth credential changes
4. Keep commits small and releasable to internal testers

### Success criteria

- New testers can install from the internal-test link, sign in (with Fit installed/opened), and complete a Run that writes steps on-device via **Google Fit**
- `main` remains shippable for internal testing without HC dependencies

---

## 4. Track B — Agent brief (feature branch: Health Connect write)

### Mission

Advance write-path migration: harden / extend `FitWriter`, implement Health Connect writer, add integration tests. Schedule was pulled forward; this is active development, not research-only.

### In scope

1. **`FitWriter` seam** — keep (or adjust) the interface so run engine / UI stay decoupled from Google Fit–specific types where practical; DI binding remains swappable
2. **`HealthConnectWriter` (or equivalent)** — write simulated step (and related) records via Health Connect APIs, suitable for downstream apps that read HC
3. **Integration tests** — verify write path behavior (permissions, insert, failure handling) without relying on fragile full-UI flows where unit/instrumented tests suffice
4. Docs updates on the feature branch describing HC permissions, limitations (on-device only), and how to enable/select the HC path when implemented

### Out of scope (unless owner expands)

- Tester recruitment ops, Play listing copy, invite email lists (Track A)
- Guaranteeing cross-device cloud sync (not available via HC)
- Replacing Google Fit on `main` before merge approval
- Unrelated roadmap (custom intensity, orphan preference UI, export/dashboard)

### Suggested first actions

1. `git checkout main && git pull`
2. `git checkout -b feature/health-connect-writer` (or checkout existing)
3. Read: this file, [Cursor Prompt](APBFit_Cursor_Prompt_public.md) (`FitWriter` section), SRS/SDS NFR on substitutable write path, [Fit sync investigation](APBFit_GoogleFit_Sync_Investigation.md) (HC = on-device only)
4. Inventory current `FitWriter` / `GoogleFitWriter` / Hilt `FitModule` before changing call sites
5. Prefer implementing behind the interface first; do not rip out Google Fit until HC path is testable

### Success criteria (branch-ready for PR discussion)

- [ ] `FitWriter` (or evolved contract) supports an HC implementation without rewriting the session/run engine
- [ ] HC writer can write simulated steps (and agreed companion data types) under required permissions
- [ ] Integration tests cover happy path + key failures
- [ ] Documented how to build/run/verify HC path; Google Fit path still available until cutover is decided
- [ ] No force-push / rewrite of `main`; PR targets `main` when ready

### Design constraints (do not ignore)

- Min SDK 31 / Target 35; Kotlin, Hilt, Room, Compose — match existing stack
- Never write segments with `endTime` in the future (existing integrity rule)
- Account / permission model for HC may differ from Google Sign-In + Fitness scopes — surface gaps early; do not silently assume GF account == HC access
- HC does not provide cloud sync; do not claim cross-device visibility in UI or docs

---

## 5. Coordination rules (both agents)

1. **One track per agent session.** Do not mix Track A Play/tester work and Track B HC implementation in the same change set.
2. **`main` is sacred for testers.** Track B develops only on `feature/health-connect-writer` (or the agreed feature branch).
3. **No silent cutover.** Switching default write path from Google Fit to HC requires explicit owner decision after tests.
4. **Commits:** only when the owner asks, unless the agent session was explicitly told to commit.
5. **Secrets:** never commit `keystore.properties`, upload `.jks`, or OAuth client secrets.
6. If tracks conflict (e.g. Track A needs a hotfix that Track B also touches), prefer: hotfix on `main` → rebase/merge into feature branch.

---

## 6. Roadmap context (not current track scope)

| Item | Notes |
|---|---|
| v1.4 Custom intensity | Deferred |
| v1.5 Orphan recovery preference | Deferred |
| v1.6 Play public release | After internal testing; Track A supports the path |
| FitWriter / HC write | **Pulled forward** → Track B (was README “v1.7”) |
| v2.0+ export / dashboard / custom cloud | Far future |
| Cross-device sync | Pending / platform-limited; not solved by HC |

---

## 7. How to brief a new agent

Paste:

> Read `docs/HC_migration.md` and `docs/APBFit_Health_Connect_Writer.md`. Work on `main` (or a short-lived branch off `main`). Write path is Health Connect only; do not reintroduce Google Fit without owner approval.

**Historical (do not use for new work):**

**Track A** — Play internal tester recruitment on Google Fit build (obsolete).

**Track B** — `feature/health-connect-writer` HC implementation (merged to `main` 2026-09).
