# APBFit — Google Fit Cross-Device Sync Investigation

| Field | Value |
|---|---|
| Project | APBFit — Auto Personal Boost Fit |
| Document | Technical Investigation / Known Limitation |
| Status | Concluded |
| Date | 2026-06-21 |
| Author | Pixson |
| Related | [SRS v1.1](APBFit_SRS_v1.1_public.md), [SDS v1.1](APBFit_SDS_v1.1_public.md) |

---

## 1. Purpose

This note records a controlled investigation into why Google Fit data written by APBFit
on one device (for an account that is **not** the foreground Google Fit account) does **not**
appear on a second device signed in to that same account, even after long waits.

The conclusion drives a documentation change: **all cross-device / cloud-visibility behavior is
treated as a deferred (pending) capability, not a v1.1 guarantee.** APBFit's reliable, supported
guarantee is **local write on the writing device** only.

---

## 2. Test Setup

Three physical phones, one Google Fit (GF) account role each:

| Phone | Role | Google Fit account |
|---|---|---|
| A | Writer (runs APBFit) | switched per test |
| B | Reader (fixed) | `P` |
| C | Reader (fixed) | `L` |

- APBFit on phone A signed in to **both** accounts `L` and `P`.
- All runs used **Debug Run** (the only path that currently starts a concurrent two-account session).
- "Written successfully" below is judged by APBFit's own in-app state and the GF app on the
  writing device, unless stated otherwise.

---

## 3. Observations

| # | Action | Result |
|---|---|---|
| 1 | A's GF = `P`; APBFit runs both `L`+`P`; Debug Run | A's GF shows steps near-real-time. **B (`P`) and C (`L`) show nothing.** |
| 2 | B logout/login `P`; C logout/login `L` | **No update on either** (cloud did not yet have the data). |
| 3 | A's GF logout/login `P` | B still nothing at first; **after B logout/login `P`, B updates to latest.** |
| 4 | A's GF logout `P`, login `L` | C nothing at first; **after C logout/login `L`, C updates to latest.** |

### Interpretation

- **Writer side (upload) is lazy.** Data written by APBFit stays in the **local** Google Fit
  store on phone A and is served immediately to whichever account is foreground in GF on A.
  It is **not** promptly uploaded to the cloud. Re-authenticating the account in GF on phone A
  is what forced an upload (steps 3–4).
- **Reader side (download) is also lazy.** Even once the cloud has the data, phones B/C did not
  pull it until they re-authenticated the account (steps 3–4).
- **Both directions can be forced by an account logout/login, but there is no programmatic trigger.**

---

## 4. Root Cause

Google Fit's legacy **Fitness API (`HistoryClient.insertData`)** writes to a device-local fitness
store. Cloud upload/download is performed by Google Play Services / the Google Fit app on a
**passive, battery-optimized schedule managed by Android `WorkManager`** — influenced by Wi-Fi,
charging, Doze, and App Standby. There is **no public API to force a flush or sync**, and the
behavior is intentional, not a bug in APBFit.

This matches our observations exactly: the local store is immediate; the cloud round-trip is
deferred and effectively only forced by re-login.

---

## 5. Evidence

- **No force-sync API.** The Fitness Android SDK (`HistoryClient`, `RecordingClient`) exposes no
  flush/force-sync method; sync is GMS/`WorkManager`-managed and cannot be forced.
  ([Fitness History API](https://developers.google.com/fit/android/history),
  [HistoryClient reference](https://developers.google.com/android/reference/com/google/android/gms/fitness/HistoryClient))
- **Lazy cloud sync is documented by integrators.** Junction's Google Fit guide states the
  Android SDK serves the latest **local** data immediately regardless of cloud upload, that cloud
  upload "**cannot be forced**" and is `WorkManager`-managed (worst case only when idle/charging/Wi-Fi).
  ([junction.com/wearables/guides/google_fit](https://docs.junction.com/wearables/guides/google_fit))
- **Google's only user-facing force is a manual UI button** (Google Fit app → Journal → Sync),
  primarily for watch↔phone, with the official note that "syncing only happens occasionally … it
  helps your battery last longer."
  ([Fit sync help](https://support.google.com/fit/answer/9649357))

---

## 6. Options Evaluated

| Option | Drives cloud sync? | Verdict |
|---|---|---|
| `HistoryClient` flush / sync API | ❌ does not exist | Not available |
| GF app "Journal → Sync" button | △ semi-manual UI | Not an API; not usable from APBFit |
| Account logout/login | ✅ effective | Manual only; not automatable |
| Wi-Fi + charging + idle/screen-off | △ raises likelihood | Not guaranteed, not immediate |
| Disable battery optimization (GF + GMS) | △ raises likelihood | Not guaranteed, not immediate |
| **Fitness REST API** (writes cloud directly) | ⚠️ technically yes | **Dead end:** no new sign-ups since 2024-05-01; full shutdown end of 2026; "no alternative to the Fit REST API" |
| **Health Connect** | ❌ on-device only | Device-centric, **no cloud sync**; does not solve cross-device |

References for deprecation/turndown:
[Fit migration guide](https://developer.android.com/health-and-fitness/health-connect/migration/fit),
[Fit APIs end-of-service FAQ](https://developer.android.com/health-and-fitness/guides/health-connect/migrate/fit-apis-end-of-service).

---

## 7. Conclusion

1. **APBFit's multi-account write works.** Both accounts received data on the writing device;
   this confirms the Sprint 2 concurrent-session engine functions correctly.
2. **Cross-device visibility is not achievable reliably or promptly on Google Fit**, because both
   upload and download are passive and the only reliable trigger is a manual account re-login.
   This is a **platform limitation**, not an APBFit defect.
3. There is **no API or automatable mechanism** to force the cloud round-trip.
4. The legacy Google Fit API (incl. REST) is **shut down at the end of 2026**; its successors
   (Health Connect = on-device only; Google Health API = not available to us) **do not restore**
   cross-device cloud sync for this use case.

### Impact on APBFit

- **Supported guarantee:** APBFit writes are immediately usable **on the same device** for the
  same account (e.g., a downstream validator app running on the writing phone). This is the
  reliable, primary use case.
- **Not guaranteed:** "Write on phone A, read on phone B for the same account" — deferred to a
  future cross-device capability and described as **pending** in SRS/SDS.

---

## 8. Recommendations / Decisions

- Treat **cross-device / cloud-sync visibility** as **out of scope and pending** for v1.x; do not
  expose any UI or claim implying cross-device propagation.
- Keep the same-device write path as the documented, supported behavior.
- Revisit only as part of a future **platform migration** (post-Google-Fit; e.g., a custom backend
  or an account-centric cloud API), tracked separately from v1.1 feature work.
