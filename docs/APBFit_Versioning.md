# APBFit — Versioning Convention

## Version Number Design

APBFit uses a dual versioning scheme: a public-facing `versionName` and an internal `versionCode`.

### Public Version (versionName)

Format: `major.minor.releasedate`

```
major       — incremented on breaking changes or major feature milestones
minor       — incremented on new features or significant improvements
releasedate — release date in YYYYMMDD format
```

Examples:
```
1.0.20260610   ← initial release
1.1.20260715   ← new feature added
2.0.20261001   ← breaking change / major milestone
```

### Internal Build Number (versionCode)

Format: `YYMMDDnn` (8-digit integer)

```
YY   — 2-digit year
MM   — 2-digit month
DD   — 2-digit day
nn   — build sequence number for that day, starting at 01
```

Examples:
```
26061001   ← 2026-06-10, first build of the day
26061002   ← 2026-06-10, second build of the day
26071501   ← 2026-07-15, first build of the day
```

Rules:
- `versionCode` must always be monotonically increasing (required by Google Play).
- `nn` resets to `01` each new day.
- Maximum 99 builds per day (sufficient for all practical purposes).
- `versionCode` is updated manually (or via build script) before each release build.
- Debug builds during active development do not need to increment `versionCode`.

---

## Implementation in build.gradle.kts

```kotlin
android {
    defaultConfig {
        // Internal build number: YYMMDDnn
        // Update before each release build
        versionCode = 26061001

        // Public version: major.minor.releasedate
        versionName = "1.0.20260610"
    }
}
```

---

## Document Version Alignment

All versioned documents (SRS, Cursor Prompt, release notes) use the same
`major.minor.releasedate` format to ensure traceability between code and documentation.

Examples:
```
APBFit_SRS_v1.0.20260610.md
APBFit_Cursor_Prompt_v1.0.20260610.md
```

A document version and a code `versionName` sharing the same date string indicates
they are in sync at that point in time.
