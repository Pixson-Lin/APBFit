# APBFit — Auto Personal Boost Fit

> **Status:** v1.1 sideload release — concurrent multi-account runs, simplified Home UI, Traditional Chinese UI.

[Software Requirements Specification (v1.1)](docs/APBFit_SRS_v1.1_public.md) · [SRS v1.0](docs/APBFit_SRS_v1.0_public.md) · [Development Guide](docs/APBFit_Cursor_Prompt_public.md)

---

## Overview

**APBFit** is an Android application that writes simulated walking and running activity data into **Google Fit** on behalf of a signed-in Google account. It generates step count records designed to be compatible with fitness-integrated applications that read from Google Fit.

The app provides a structured run-based workflow with full observability: configurable intensity and duration, foreground background execution, per-run history with segment-level detail, and optional logging of whether downstream apps accepted the written data.

**繁體中文**

**APBFit** 是一款 Android 應用程式，代表已登入的 Google 帳號，將模擬的步行與跑步活動資料寫入 **Google Fit**。其目標是產生與讀取 Google Fit 資料的健身整合應用相容的步數紀錄。

本 App 提供結構化的 Run 流程與完整可觀察性：可設定強度與時長、前景服務背景執行、含 segment 層級細節的 Run 歷史紀錄，以及可選填的下游應用驗證結果紀錄。

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
    │  segment generator + batch queue
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
    │  segment 生成器 + batch 佇列
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

Current release: **v1.1.20260621** (`versionCode` 26062101).

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

---

## Documentation

| Document | Description |
|----------|-------------|
| [SRS v1.0 (public)](docs/APBFit_SRS_v1.0_public.md) | Software Requirements Specification — functional and non-functional requirements, data model, UI inventory |
| [Development Guide (public)](docs/APBFit_Cursor_Prompt_public.md) | Technical decisions, package structure, implementation order, and development constraints |

**繁體中文**

| 文件 | 說明 |
|------|------|
| [SRS v1.0（公開版）](docs/APBFit_SRS_v1.0_public.md) | 軟體需求規格書 — 功能/非功能需求、資料模型、畫面清單 |
| [開發指南（公開版）](docs/APBFit_Cursor_Prompt_public.md) | 技術決策、套件結構、實作順序與開發限制 |

---

## Roadmap

| Version | Scope |
|---------|-------|
| **v1.1** | Sideload release — concurrent multi-account runs, simplified UI, config persistence, zh-TW UI |
| **v1.0** | Sideload release — single-account runs, preset intensity levels, run history, result validation |
| v1.2 | Custom intensity parameters |
| v1.3 | Google Play Store release |
| v1.4 | Write path adaptability via `FitWriter` interface |
| v2.0+ | Cloud sync, export, dashboard |

**繁體中文**

| 版本 | 範圍 |
|------|------|
| **v1.1** | 側載發布 — 多帳號並發 Run、簡化 UI、設定記憶、繁體中文介面 |
| **v1.0** | 側載發布 — 單帳號 Run、預設強度、歷史紀錄、驗證結果紀錄 |
| v1.2 | 自訂強度參數 |
| v1.3 | Google Play 上架 |
| v1.4 | 透過 `FitWriter` 介面支援寫入路徑替換 |
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
