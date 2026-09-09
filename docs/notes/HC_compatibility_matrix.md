# APBFit ↔ Health Connect ↔ Third-Party App — Compatibility Matrix

| Field | Value |
|---|---|
| Updated | 2026-09-07 |
| App version | v1.4.20260903 (`versionCode` 26090301) |
| Related | [HC migration](../HC_migration.md) |

---

## Summary

APBFit writes simulated steps to **Health Connect (HC)** on all supported Android versions (API 31+). The owner-tested **third-party step-count app or game that supports Health Connect** can read HC steps on **Android 14+**. On Android 12–13, a historical Google Fit (GF) bridge test also worked, but it was more manual and delayed.

These are observational results for one independently tested downstream app. They do not imply an official integration or guarantee that every third-party app behaves the same way.

---

## Compatibility matrix

| Android | APBFit → HC write | Tested third-party app reads HC | Validated downstream path | Notes |
|---|---|---|---|---|
| **15** | ✅ Pass | ✅ Observed | **APBFit → HC → third-party app** | Owner smoke: full path OK |
| **14** | ✅ Expected | ✅ Observed | **APBFit → HC → third-party app** | Same path as Android 15 |
| **13** | ✅ Expected | ❌ HC mode unavailable in tested app | Historical: **APBFit → HC → GF → third-party app** | GF foreground required; latency |
| **12** (e.g. Samsung S10e) | ✅ Pass | ❌ HC mode unavailable in tested app | Historical: **APBFit → HC → GF → third-party app** | Owner: 5 rounds OK; GF foreground + delay |

**APBFit minSdk:** API 31 (Android 12).  
**Current recruitment target:** Android **14 or higher**.

---

## Path A — Direct HC (recommended)

**Devices:** Android 14+

```
APBFit ──write──► Health Connect ──read──► third-party step-count app or game
```

1. Install / update Health Connect.
2. Sign in to APBFit; grant HC permissions (steps R/W, distance W, exercise W).
3. Start a Run; confirm steps in the Health Connect app.
4. Confirm the third-party step-count app or game is set to Health Connect and picks up steps.

---

## Path B — Google Fit bridge (Android 12–13 only)

**Devices:** Historical Android 12–13 validation when the tested third-party app could not switch to HC.

```
APBFit ──write──► Health Connect ──read──► Google Fit ──read──► third-party app
```

Owner validation (2026-09-07, Samsung S10e / Android 12):

- APBFit writes to HC successfully.
- Google Fit reads HC steps; the tested third-party app remains on GF for steps.
- **5 rounds** succeeded.
- Operations are **more cumbersome**: Google Fit must be brought to the **foreground**.
- There is **noticeable delay** before the tested third-party app shows steps.

This historical path validates APBFit’s HC writer on older OS versions; it is **not part of the current Android 14+ tester recruitment path**.

---

## What this is / is not

| Claim | Status |
|---|---|
| APBFit HC write works on Android 12 | ✅ Validated |
| APBFit HC write works on Android 15 | ✅ Validated |
| Tested third-party app's HC mode requires Android 14+ | ✅ Observed (external-app constraint, not APBFit) |
| Android 12–13 can still verify APBFit → HC | ✅ Via HC app; historical GF bridge also validated |
| Cross-device / cloud sync via HC | ❌ Still not available |

---

## Tester guidance (short)

- Current recruitment is limited to **Android 14+**.
- Testers may optionally verify whether a **third-party step-count app or game that supports Health Connect** reads the written steps.
