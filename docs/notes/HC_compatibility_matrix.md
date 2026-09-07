# APBFit ↔ Health Connect ↔ Pikmin — Compatibility Matrix

| Field | Value |
|---|---|
| Updated | 2026-09-07 |
| App version | v1.4.20260903 (`versionCode` 26090301) |
| Related | [Internal test invite](Internal_test_notes.txt), [HC migration](../HC_migration.md) |

---

## Summary

APBFit writes simulated steps to **Health Connect (HC)** on all supported Android versions (API 31+). Downstream **Pikmin Bloom** can read HC steps only on **Android 14+**. On Android 12–13, Pikmin still uses Google Fit (GF); a GF-as-bridge path has been validated but is more manual and delayed.

---

## Compatibility matrix

| Android | APBFit → HC write | Pikmin reads HC | Validated path to Pikmin | Notes |
|---|---|---|---|---|
| **15** | ✅ Pass | ✅ Supported | **APBFit → HC → Pikmin** | Owner smoke: full path OK |
| **14** | ✅ Expected | ✅ Required min for Pikmin HC mode | **APBFit → HC → Pikmin** | Same as 15; recommended for testers |
| **13** | ✅ Expected | ❌ Pikmin HC mode unavailable | Optional: **APBFit → HC → GF → Pikmin** | GF must be opened to foreground; latency |
| **12** (e.g. Samsung S10e) | ✅ Pass | ❌ Pikmin HC mode unavailable | Optional: **APBFit → HC → GF → Pikmin** | Owner: 5 rounds OK; GF foreground + delay |

**APBFit minSdk:** API 31 (Android 12).  
**Recommended for recruiting / end-to-end Pikmin validation:** Android **14 or higher**.

---

## Path A — Direct HC (recommended)

**Devices:** Android 14+

```
APBFit ──write──► Health Connect ──read──► Pikmin Bloom
```

1. Install / update Health Connect.
2. Sign in to APBFit; grant HC permissions (steps R/W, distance W, exercise W).
3. Start a Run; confirm steps in the Health Connect app.
4. Confirm Pikmin Bloom is set to Health Connect and picks up steps.

---

## Path B — Google Fit bridge (Android 12–13 only)

**Devices:** Android 12–13 when Pikmin cannot switch to HC.

```
APBFit ──write──► Health Connect ──read──► Google Fit ──read──► Pikmin Bloom
```

Owner validation (2026-09-07, Samsung S10e / Android 12):

- APBFit writes to HC successfully.
- Google Fit reads HC steps; Pikmin remains on GF for steps.
- **5 rounds** succeeded.
- Operations are **more cumbersome**: Google Fit must be brought to the **foreground**.
- There is **noticeable delay** before Pikmin shows steps.

This path validates APBFit’s HC writer on older OS versions; it is **not** the primary product path after Pikmin’s GF cutoff on devices that can use HC.

---

## What this is / is not

| Claim | Status |
|---|---|
| APBFit HC write works on Android 12 | ✅ Validated |
| APBFit HC write works on Android 15 | ✅ Validated |
| Pikmin HC mode requires Android 14+ | ✅ Observed (product constraint, not APBFit) |
| Android 12–13 can still verify APBFit → HC | ✅ Via HC app; Pikmin optional via GF bridge |
| Cross-device / cloud sync via HC | ❌ Still not available |

---

## Tester guidance (short)

- Prefer **Android 14+** for simplest testing with Pikmin.
- On **Android 12–13**, still useful to test APBFit + Health Connect; Pikmin check is optional and needs Google Fit bridge if attempted.
