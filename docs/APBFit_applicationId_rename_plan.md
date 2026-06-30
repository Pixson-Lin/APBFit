# APBFit — applicationId / package 改名與上架前置計畫

> 內部規劃文件。目的：在上架 Google Play 前，將 applicationId / package name 由 `com.pixson.apbfit` 改為正式名稱 `com.pixsonlin.apbfit`，並釐清相關前置條件。

---

## 1. 專案定位（已確認）

| # | 定位 | 說明 |
|---|------|------|
| 1 | **APBFit v1.x 持續使用舊的 Google Fit API** | 本輪不遷移 Health Connect；Fit API 遷移屬未來版本範圍。 |
| 2 | **OAuth 永遠停留在 Testing 模式** | test users 上限 100 人；不走公開上架（Production）驗證流程。 |
| 3 | **服務壽命預計到 2026 年底** | 依 Google 官方對 Fit API 的關閉時程；屆滿後 App 預期失效。 |

### 因上述定位「可排除」的顧慮
- OAuth 公開驗證（Production / 敏感權限審查）→ **不需要**（停在 Testing 模式）。
- Fit API 是否趁改名遷移 Health Connect → **本輪不處理**，已劃為 v1.x 範圍、用到 2026 底為止。

---

## 2. 改名決策（已確認）

| 項目 | 決定 |
|------|------|
| 改名範圍 | **全面**：`applicationId` + `namespace` + 所有 Kotlin `package`/import + 資料夾 `com/pixson` → `com/pixsonlin` |
| 新名稱 | `com.pixson.apbfit` → `com.pixsonlin.apbfit`（保留 `.apbfit` 後綴） |
| OAuth | 在**同一個新 Cloud 專案**新增 Android OAuth client；測試用戶照舊管理 |
| 版本 | `versionName = "1.3.20260629"`、`versionCode = 26062901`（遞增） |
| 文件 | docs + `google-services.json.example` 一併更新 |
| 既有 release/tag | **完全不動** |

---

## 3. 仍需注意的前提（精簡後）

### 3.1 「測試者」有兩份獨立名單，缺一不可
- **OAuth test users**（Google Cloud Console）：決定誰能通過 Google 登入 / 授權 Fitness 權限。
- **Play Console testers**（Internal / Closed testing track）：決定誰能從 Play 下載 App。
- 一位試用者要能完整使用，**必須同時出現在兩份名單**，否則會「下載得到但登入失敗」或反之。
- 兩邊各自獨立管理（各自的人數 / 名單）。

### 3.2 applicationId 一旦上 Play 永久鎖定
- `com.pixsonlin.apbfit` 在某 Play 帳號發布後，**永遠**綁定該帳號，不可改名、不可重用。
- → 這次改名即視為**最終定案名稱**。

### 3.3 簽章：Play App Signing 會產生第二把 SHA-1
- 上 Play 後 Google 以 **Play App Signing** 重新簽 App，正式版的憑證 SHA-1 是 **Google 的 app signing 金鑰**，非本機 key。
- OAuth client 需登錄**兩把 SHA-1**：
  - 本機測試用的 **upload key（或目前的 debug key）SHA-1**
  - Play Console 產生的 **app signing key SHA-1**（上架後於 Play Console 查得）
- 上 Play 前應**改用正式 upload keystore**，release 不再以 debug keystore 簽（詳見第 5 節）。
- 即使 OAuth 停在 Testing 模式，只要透過 Play 派送，雙 SHA-1 要求依然成立。

---

## 4. 改名執行計畫（分階段）

### 第 1 階段：local 改 + 驗證
1. `app/build.gradle.kts`：`applicationId`、`namespace` → `com.pixsonlin.apbfit`；版本號更新為 `1.3.20260629` / `26062901`。
2. 移動資料夾：`app/src/main/java/com/pixson/apbfit/` → `.../com/pixsonlin/apbfit/`；`app/src/test/java/com/pixson/...` 同步。
3. 全面取代所有 `.kt` 內 `com.pixson.apbfit` → `com.pixsonlin.apbfit`（package 宣告 + import + `BuildConfig` / 字串參照）。
4. 更新 `app/google-services.json.example` 的 `package_name`。
5. 更新 docs 內 `com.pixson.apbfit` 字串。
6. 驗證：`./gradlew testDebugUnitTest assembleDebug`（預期全綠）。

### 第 2 階段：Console 設定（人工）
7. 在 Cloud Console 新增 Android OAuth client：package = `com.pixsonlin.apbfit`、SHA-1 = 目前測試用 keystore 的 SHA-1。
8. 實機 / 模擬器 sideload 安裝，測試：登入、加帳號、寫入 Fit、關螢幕 catch-up。

### 第 3 階段：發布（既有 release 不動）
9. 第 1、2 階段皆 OK → commit（訊息標明 applicationId 變更）、打 tag `v1.3.20260629`、`assembleRelease`、`gh release create` 上傳新 APK（sideload 用）。

> 註：改 applicationId 等於**全新 App**，舊 `com.pixson.apbfit` 會並存、舊資料不沿用（sideload 重裝即可，無需資料遷移）。

---

## 5. 上 Play 前改用正式 upload keystore

### 5.1 概念
- Play 採 **Play App Signing**：你用 **upload key** 簽署上傳檔，Google 保管 **app signing key** 並對外重新簽署。
- keystore **不綁定 applicationId**，同一把 keystore 可簽任何 package；因此此事與改名彼此獨立。

### 5.2 前置條件
- 產生 keystore 本身**不需要**開發者帳號，純本機操作（`keytool`）。
- 但「啟用 Play App Signing / 取得 app signing SHA-1」需要 **Play Console 開發者帳號**（一次性 USD 25，且需身分驗證，作業可能數日，需預留時間）。

### 5.3 步驟
1. 產生 upload keystore：
   ```bash
   keytool -genkeypair -v -keystore apbfit-upload.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias apbfit-upload
   ```
2. 將 keystore 路徑與密碼放入**不進版控**的 `keystore.properties`（或 `local.properties`），於 `app/build.gradle.kts` 新增 `signingConfigs { create("release") { ... } }` 並讓 `buildTypes.release` 使用它（取代目前的 `signingConfig = signingConfigs.getByName("debug")`）。
3. 產生 release **AAB**（Play 需要 `.aab`，非 `.apk`）：`./gradlew bundleRelease`。
4. 於 Play Console 建立 App、上傳 AAB、啟用 Play App Signing（現為預設）。
5. 上傳後於 Play Console 取得 **app signing key SHA-1**，連同 **upload key SHA-1** 一併加入 OAuth client。

### 5.4 最佳時機
- keystore 產生與簽章設定**與 applicationId 改名無技術相依**，可獨立進行。
- 建議順序：**先完成 applicationId 改名（第 1～3 階段）**，之後在準備 Play Console 上架時再切換 upload keystore，理由：
  - 改名期間仍用 debug keystore 做 sideload + OAuth(Testing) 測試最省事，不必提早動簽章。
  - keystore 不綁 package，提前做不會省到工，反而增加同時變動的變數。
- 開發者帳號註冊有作業時間，**可平行先去申請**，不必等改名完成。

### 5.5 注意
- upload keystore 一旦用於 Play，**遺失將無法上傳更新**（雖可向 Google 申請重設 upload key，但麻煩）。請妥善備份、勿進版控。
- 若日後也會「直接安裝自己簽的 release APK（非經 Play）」做測試，那份 APK 是用 upload key 簽的，OAuth 也要有 upload key SHA-1 才能登入。

### 5.6 已完成：upload keystore（2026-06-29）
- **檔案**：`keystore/apbfit-upload.jks`（gitignored）
- **設定檔**：`keystore.properties`（repo 根，gitignored；含密碼，**不入版控**）
- **金鑰**：alias `apbfit-upload`、RSA 2048、效期 10000 天、`CN=APBFit, O=Pixson Lin, C=TW`
- **密碼保管**：Bitwarden Secure Note（**不寫在本文件**）；本機僅留於 gitignored 的 `keystore.properties`
- **build 接線**：`app/build.gradle.kts` 於 `keystore.properties` 存在時用 upload key 簽 release；不存在則回退 debug（fresh clone / CI 仍可 build）
- **驗證**：`assembleRelease` 成功，apksigner 確認 release APK 憑證 = upload key

### 5.7 SHA-1 對照表
| 用途 | SHA-1 | 何時用 | OAuth 狀態 |
|------|-------|--------|-----------|
| debug（本機 debug 版） | `EE:7B:CE:F1:9C:01:29:AD:3A:DA:C4:0D:DD:AB:3D:98:35:F8:BA:3E` | 平常 debug sideload 測試 | 已加入 |
| upload（本機 release 版） | `E2:D3:66:45:6A:8B:FA:CE:B1:3E:D6:FD:88:82:40:62:54:86:12:AD` | 測 release 版 sideload | 需測 release 才加 |
| Play app signing | 上傳 AAB 後於 Play Console 取得 | 經 Play 派送的正式版 | 上架後補登 |

---

## 6. 待辦快照

- [x] 第 1 階段：local 改名 + build/測試通過
- [x] 第 2 階段：新增 OAuth client（新 package + debug SHA-1）＋ sideload 實測
- [x] 第 3 階段：commit / tag `v1.3.20260629` / 發 release
- [x] 產生 upload keystore、切換 release 簽章（已驗證）
- [ ] （平行）等待 Play Console 開發者帳號審核
- [ ] 帳號通過後：建立 App、產 AAB（`./gradlew bundleRelease`）、上傳、啟用 Play App Signing
- [ ] Play 上傳後：取得 app signing SHA-1，補登 OAuth client
- [ ] （視需要）測 release 版 sideload 前，補登 upload SHA-1 到 OAuth client
- [ ] Play 內測：testers 名單與 OAuth test users 名單對齊

- [ ] 第 1 階段：local 改名 + build/測試通過
- [ ] 第 2 階段：新增 OAuth client（新 package + 測試 SHA-1）＋ sideload 實測
- [ ] 第 3 階段：commit / tag `v1.3.20260629` / 發 release
- [ ] （平行）申請 Play Console 開發者帳號
- [ ] 上架前：產生 upload keystore、切換 release 簽章、產 AAB
- [ ] Play 上傳後：取得 app signing SHA-1，補登 OAuth client（雙 SHA-1）
- [ ] Play 內測：testers 名單與 OAuth test users 名單對齊
