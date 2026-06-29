# APBFit — Auto Personal Boost Fit

> **Status:** v1.3 sideload release shipped — formal applicationId (`com.pixsonlin.apbfit`) for Play Store prep.

[Software Requirements Specification (v1.2)](docs/APBFit_SRS_v1.2_public.md) · [SDS v1.2](docs/APBFit_SDS_v1.2_public.md) · [SRS v1.1](docs/APBFit_SRS_v1.1_public.md) · [Development Guide](docs/APBFit_Cursor_Prompt_public.md)

---

## Overview

**APBFit** is an Android application that writes simulated walking and running activity data into **Google Fit** on behalf of a signed-in Google account. It generates step count records designed to be compatible with fitness-integrated applications that read from Google Fit.

The app provides a structured run-based workflow with full observability: configurable intensity and duration, foreground background execution, per-run history with segment-level detail, and optional logging of whether downstream apps accepted the written data.

**繁體中文**

**APBFit** 是一款 Android 應用程式，代表已登入的 Google 帳號，將模擬的步行與跑步活動資料寫入 **Google Fit**。其目標是產生與讀取 Google Fit 資料的健身整合應用相容的步數紀錄。

本 App 提供結構化的 Run 流程與完整可觀察性：可設定強度與時長、前景服務背景執行、含 segment 層級細節的 Run 歷史紀錄，以及可選填的下游應用驗證結果紀錄。

---

## Features (v1.3)

- Renamed applicationId / package to the formal **`com.pixsonlin.apbfit`** (Google Play Store preparation)
- Environment-check WARN indicator now a fixed **orange** (theme-independent, clearly distinct from PASS green; does not imply RUN is blocked)

**繁體中文**

- applicationId / package 改為正式名稱 **`com.pixsonlin.apbfit`**（Google Play 上架準備）
- 環境檢查 WARN 指示改為固定**橘色**（不受主題影響、與 PASS 綠色明確區分；不代表擋 RUN）

---

## Features (v1.2)

- Segment pre-planning at RUN: all segments inserted to Room as `PLANNED` before Fit writes
- Scheme C scheduler: rolling `AlarmManager` + `PARTIAL_WAKE_LOCK` + `SCREEN_ON` catch-up
- Reliable background writes when the screen is off or the app is backgrounded (Issue #5)
- Throttled multi-round catch-up on alarm, screen-on, stop, and session end
- Orphan session resume if still within duration; finalize with catch-up if past end
- Active Runs: `segmentsWritten / segmentsPlanned` progress
- History: pending (`待寫入`) and skipped (`已跳過`) segment statuses
- Environment checklist: exact-alarm permission icon (warn-only, same UX as battery)

**繁體中文**

- RUN 時預生成全部 segment，先以 `PLANNED` 寫入 Room，再依排程寫入 Fit
- Scheme C 排程：滾動 `AlarmManager` + `PARTIAL_WAKE_LOCK` + 螢幕亮起 catch-up
- 關螢幕／背景時仍可可靠寫入步數（Issue #5）
- alarm、亮螢、停止、Session 結束時多輪節流 catch-up
- 孤兒 Session：未過期則恢復 FGS；已過期則 finalize 並 catch-up
- 進行中畫面：`已寫入 / 預計` segment 進度
- 歷史紀錄：待寫入、已跳過等 segment 狀態
- 環境檢查：精確鬧鐘圖示（僅警告，與電池最佳化相同 UX）

---

## Features (v1.1)

- Concurrent multi-account Run Sessions (one shared config per session)
- Simplified Home UI: RUN-first layout, intensity dropdown, sliders, enabled-account list
- Account Edit sheet: enable/disable accounts, add/remove sign-in
- Active Runs screen with per-account progress and session-level stop
- Run configuration persistence (intensity, duration, batch size)
- History account dropdown with last-selected persistence
- Seven preset intensity levels with Traditional Chinese names
- Grouped foreground notifications (summary + per-account children)
- Orphan session recovery on cold launch; manual reset in Settings

**繁體中文**

- 多帳號並發 Run Session（同一組設定）
- 簡化首頁：RUN 置頂、強度下拉、滑桿、已啟用帳號列表
- 帳號管理：勾選參與 Run、新增／移除登入
- 進行中畫面：各帳號進度 + Session 層級停止
- Run 設定記憶（強度、時長、批次量）
- 歷史紀錄帳號下拉與上次選取記憶
- 七種強度預設（繁體中文）
- 分組前景通知（摘要 + 各帳號）
- 冷啟動孤兒 Session 自動恢復；設定頁可手動重設

---

## Features (v1.0)

- Google Sign-In with multi-account support (one active account per run; no concurrent runs)
- Google Fit data writing via the Android SDK (`HistoryClient.insertData()`)
- Run-based workflow: five intensity presets, duration (5 min – 6 hr), batch size (1–10 segments)
- Foreground Service for reliable background execution with a persistent notification
- Per-run history with expandable segment-level detail
- Result validation logging (`Accepted` / `Rejected`, optional downstream step count)
- Pre-run environment checklist (warnings only — does not block runs)
- Automatic deletion of records older than 90 days on app launch

**繁體中文**

- Google 登入，支援多帳號（每次 Run 一個帳號；不支援並發 Run）
- 透過 Google Fit Android SDK（`HistoryClient.insertData()`）寫入資料
- Run 流程：五種強度預設、時長（5 分鐘–6 小時）、batch 大小（1–10 個 segment）
- Foreground Service 確保背景執行，並顯示持續通知
- Run 歷史紀錄，可展開查看 segment 層級細節
- 驗證結果紀錄（`Accepted` / `Rejected`，可選填下游 App 回報步數）
- Run 前環境檢查（僅警告，不阻擋開始）
- App 啟動時自動刪除超過 90 天的紀錄

---

## Tech Stack

| Component | Choice |
|-----------|--------|
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

**繁體中文**

| 元件 | 選用技術 |
|------|----------|
| 語言 | Kotlin |
| 最低 SDK | API 31（Android 12） |
| 目標 SDK | API 35（Android 15） |
| 架構 | MVVM + Repository |
| 依賴注入 | Hilt |
| 資料庫 | Room |
| 非同步 | Kotlin Coroutines + Flow |
| UI | Jetpack Compose + Material 3 |
| 導覽 | Navigation Compose |
| Google 登入 | `play-services-auth` |
| Google Fit | `play-services-fitness` |

---

## Architecture

```
MainActivity (Compose UI)
    │
    ▼
ViewModel ──► RunRepository (Room)     AccountRepository (Google Sign-In)
    │
    ▼
RunForegroundService
    │  SegmentPlanner → Room (PLANNED)
    │  SessionScheduler + CatchUpEngine → FitWriter
    ▼
FitWriter ──► GoogleFitWriter (HistoryClient.insertData)
```

The write path is isolated behind a `FitWriter` interface so the underlying implementation can be substituted if the Google Fit API or integration requirements change.

**繁體中文**

```
MainActivity（Compose UI）
    │
    ▼
ViewModel ──► RunRepository（Room）     AccountRepository（Google 登入）
    │
    ▼
RunForegroundService
    │  SegmentPlanner → Room（PLANNED）
    │  SessionScheduler + CatchUpEngine → FitWriter
    ▼
FitWriter ──► GoogleFitWriter（HistoryClient.insertData）
```

寫入路徑透過 `FitWriter` 介面隔離，以便在 Google Fit API 或整合需求變更時替換底層實作。

---

## Getting Started

### Prerequisites

- Android 12 (API 31) or later
- Google Play Services
- Google Fit app installed on the device
- A Google account with Google Fit permissions granted

### Build

```bash
git clone https://github.com/Pixson-Lin/APBFit.git
cd APBFit
./gradlew assembleRelease
```

Debug builds: `./gradlew assembleDebug`

Install the generated APK via sideloading. Google Play distribution is planned for a future release.

Current release: **v1.3.20260629** (`versionCode` 26062901).

**繁體中文**

### 環境需求

- Android 12（API 31）或以上
- Google Play Services
- 裝置已安裝 Google Fit
- 已授權 Google Fit 權限的 Google 帳號

### 建置（原始碼就緒後）

```bash
git clone https://github.com/Pixson-Lin/APBFit.git
cd APBFit
./gradlew assembleDebug
```

請以 sideload 方式安裝產生的 APK。Google Play 上架規劃於未來版本。

目前版本：**v1.3.20260629**（`versionCode` 26062901）。

---

## Documentation

| Document | Description |
|----------|-------------|
| [SRS v1.2 (public)](docs/APBFit_SRS_v1.2_public.md) | v1.2 requirements (shipped) — pre-planned segments, scheduled write + catch-up, orphan resume (Issue #5) |
| [SDS v1.2 (public)](docs/APBFit_SDS_v1.2_public.md) | v1.2 design (shipped) — Scheme C scheduler, Room v3 |
| [SRS v1.1 (public)](docs/APBFit_SRS_v1.1_public.md) | v1.1 requirements (shipped) — multi-account sessions, simplified UI |
| [SDS v1.1 (public)](docs/APBFit_SDS_v1.1_public.md) | v1.1 design (shipped) |
| [SRS v1.0 (public)](docs/APBFit_SRS_v1.0_public.md) | v1.0 requirements |
| [Development Guide (public)](docs/APBFit_Cursor_Prompt_public.md) | Technical decisions, package structure, implementation order, and development constraints |
| [2hr Batch Power Estimate](docs/APBFit_2hr_Batch_Power_Estimate.md) | Wake frequency and rough battery impact for batch=3 vs batch=6 on a 2-hour run; background execution analysis (Issue #5) |

**繁體中文**

| 文件 | 說明 |
|------|------|
| [SRS v1.2（公開版）](docs/APBFit_SRS_v1.2_public.md) | v1.2 需求（已發布）— 步數預生成、排程寫入與 catch-up、orphan 續跑（Issue #5） |
| [SDS v1.2（公開版）](docs/APBFit_SDS_v1.2_public.md) | v1.2 設計（已發布）— Scheme C 排程、Room v3 |
| [SRS v1.1（公開版）](docs/APBFit_SRS_v1.1_public.md) | v1.1 需求（已發布）— 多帳號並發、簡化 UI |
| [SDS v1.1（公開版）](docs/APBFit_SDS_v1.1_public.md) | v1.1 設計（已發布） |
| [SRS v1.0（公開版）](docs/APBFit_SRS_v1.0_public.md) | v1.0 需求規格 |
| [開發指南（公開版）](docs/APBFit_Cursor_Prompt_public.md) | 技術決策、套件結構、實作順序與開發限制 |
| [2 小時 Run 耗電／喚醒估算](docs/APBFit_2hr_Batch_Power_Estimate.md) | batch=3 vs batch=6 喚醒頻率與粗略耗電；背景執行分析（Issue #5） |

---

## Roadmap

| Version | Scope |
|---------|-------|
| **v1.3** | Sideload release — formal applicationId `com.pixsonlin.apbfit` (Play prep), WARN indicator orange |
| **v1.2** | Sideload release — scheduled write engine, screen-off catch-up, orphan resume (Issue #5) |
| **v1.1** | Sideload release — concurrent multi-account runs, simplified UI, config persistence, zh-TW UI |
| **v1.0** | Sideload release — single-account runs, preset intensity levels, run history, result validation |
| v1.4 | Custom intensity parameters |
| v1.5 | Orphan recovery preference (abandon vs resume PLANNED) |
| v1.6 | Google Play Store release |
| v1.7 | Write path adaptability via `FitWriter` interface |
| v2.0+ | Cloud sync, export, dashboard |

**繁體中文**

| 版本 | 範圍 |
|------|------|
| **v1.3** | 側載發布 — 正式 applicationId `com.pixsonlin.apbfit`（上架準備）、WARN 指示改橘色 |
| **v1.2** | 側載發布 — 排程寫入引擎、關螢幕 catch-up、orphan 續跑（Issue #5） |
| **v1.1** | 側載發布 — 多帳號並發 Run、簡化 UI、設定記憶、繁體中文介面 |
| **v1.0** | 側載發布 — 單帳號 Run、預設強度、歷史紀錄、驗證結果紀錄 |
| v1.4 | 自訂強度參數 |
| v1.5 | Orphan 恢復偏好（放棄 vs 續寫 PLANNED） |
| v1.6 | Google Play 上架 |
| v1.7 | 透過 `FitWriter` 介面支援寫入路徑替換 |
| v2.0+ | 雲端同步、匯出、儀表板 |

---

## Disclaimer

This project interacts with third-party services (including Google Fit) that may change their APIs, data policies, or integration behavior at any time. Use at your own risk. The authors are not affiliated with Google or any third-party fitness application.

**繁體中文**

本專案與第三方服務（包含 Google Fit）互動，相關 API、資料政策或整合行為可能隨時變更。使用風險自負。作者與 Google 或任何第三方健身應用無關聯。

---

## License

This project is licensed under the [MIT License](LICENSE).

**繁體中文**

本專案採用 [MIT License](LICENSE) 授權。
