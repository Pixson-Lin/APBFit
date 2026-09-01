# APBFit Privacy Policy

**Effective date:** 2026-09-01
**Last updated:** 2026-09-01

This Privacy Policy explains how the **APBFit** application ("APBFit", "the app", "we", "us") handles information when you use it. APBFit is distributed for internal / testing use via Google Play internal testing and may also be sideloaded for development.

**Contact:** pixson.srv@gmail.com

---

## 1. Summary

- APBFit runs **entirely on your device**. There is **no APBFit server** and **no APBFit-operated database**.
- We do **not** sell, rent, or share your data with third parties.
- APBFit writes simulated walking/running activity to **Health Connect** on your device (steps, distance, and exercise sessions).
- **Google Sign-In** is used only to identify your Google account (email) within the app. APBFit does **not** use the Google Fit / Fitness API.
- All app-generated run history is stored **locally** on your device and can be deleted by you at any time.

---

## 2. What APBFit does

APBFit lets you sign in with a Google account, configure a simulated run (intensity, duration, batching), and **write the resulting activity records into Health Connect** on the same device. Other apps you authorize (for example, games that read steps from Health Connect) may read that data according to their own permissions and policies.

Health Connect stores health data **on your device**. APBFit does not operate a cloud sync service and does not upload your Health Connect records to the developer.

---

## 3. Information the app accesses and processes

### 3.1 Google account information (via Google Sign-In)
- Your Google account **email address** and **account identifier**, and basic profile information returned by Google Sign-In.
- Purpose: to show which account is signed in and to associate local run history with that account on the device.

APBFit does **not** request Google Fit / Fitness OAuth scopes.

### 3.2 Health Connect data
- APBFit requests permission to **write** steps, distance, and exercise sessions, and to **read** steps (used to confirm writes during development/debug flows and to satisfy Health Connect permission requirements).
- The app **creates and writes** these health records into **Health Connect on your device**. This is **health and fitness data**.
- APBFit does **not** read your broader Health Connect history for analytics, advertising, or profiling.

### 3.3 Data stored locally on your device
- **Run history and segment records** (start/end times, generated steps/distance, write status) — stored in a local on-device database (Room).
- **App preferences** — signed-in account state, run configuration (intensity, duration, batch size) — stored in local app storage (SharedPreferences).
- This local data never leaves your device except as the Health Connect records you choose to write through the app.

### 3.4 What we do NOT collect
- No analytics, advertising identifiers, or tracking SDKs.
- No location data.
- No transmission of your data to the developer or to any third-party server operated by us.

---

## 4. How information is used

Information is used solely to:
- Authenticate you with Google Sign-In and show your signed-in account in the app.
- Generate and write the activity records you request into Health Connect.
- Display your run history and status within the app.

We do **not** use your data for advertising, profiling, or any purpose unrelated to the features above.

---

## 5. Google API Services — Limited Use disclosure

APBFit's use of information received from **Google Sign-In** adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the **Limited Use** requirements. Specifically:

- Google user data from Sign-In is used only to provide the app's account identity features.
- We do not transfer or sell Google user data to third parties, ad networks, data brokers, or for advertising.
- We do not use Google user data for purposes unrelated to the app's features.
- No humans read your Google user data except where required for security, to comply with law, or with your explicit consent.

Health Connect data is governed by Android / Health Connect platform policies and your device permissions, not by Google Fit API Limited Use terms.

---

## 6. Health Connect — storage, sharing, and deletion

- Records written by APBFit are stored in **Health Connect on your device**.
- Other apps may access Health Connect data only if **you** grant them permission in Health Connect or Android settings.
- APBFit does **not** control how third-party apps use data they read from Health Connect.
- To remove records written by APBFit, use the **Health Connect** app or revoke APBFit's Health Connect permissions.
- Uninstalling APBFit removes **local app history** but does not automatically delete records already stored in Health Connect.

---

## 7. Data storage, retention, and deletion (local app data)

- App history is stored **locally** and is automatically pruned: records older than **90 days** are deleted on app launch.
- You can clear history within the app, and **uninstalling APBFit removes all local app data**.
- You can sign out within the app to clear the active Google Sign-In session from APBFit's perspective.

---

## 8. Data sharing

We do **not** share your data with third parties. The app exchanges data only with:
- **Google Sign-In** (authentication), and
- **Health Connect** on your device (reading/writing health records you authorize).

---

## 9. Security

- APBFit relies on Android's app sandbox, Health Connect permission controls, and Google's Sign-In flow.
- No passwords are stored by the app; authentication is handled by Google Sign-In / Google Play Services.

---

## 10. Children's privacy

APBFit is not directed to children under 13 (or the equivalent minimum age in your jurisdiction) and does not knowingly collect data from them.

---

## 11. Access restrictions (testing)

APBFit currently uses a Google OAuth configuration in **Testing** mode. Only Google accounts added as **test users** can complete Google Sign-In during internal testing.

---

## 12. Changes to this policy

We may update this Privacy Policy from time to time. Material changes will be reflected by updating the "Last updated" date above.

---

## 13. Contact

For any questions about this Privacy Policy or your data, contact: pixson.srv@gmail.com.

---
---

# APBFit 隱私權政策（繁體中文）

**生效日期：** 2026-09-01
**最後更新：** 2026-09-01

本隱私權政策說明 **APBFit** 應用程式（以下稱「APBFit」「本 App」「我們」）在您使用時如何處理資訊。APBFit 目前透過 Google Play 內部測試發布，開發階段亦可能以 sideload 方式安裝。

**聯絡方式：** pixson.srv@gmail.com

---

## 1. 摘要

- APBFit **完全在你的裝置上執行**，**沒有 APBFit 伺服器**、**沒有我們營運的資料庫**。
- 我們**不會**販售、出租或將你的資料分享給第三方。
- APBFit 會把模擬的步行／跑步活動寫入你裝置上的 **Health Connect**（步數、距離、運動紀錄）。
- **Google 登入**僅用於在 App 內辨識你的 Google 帳號（email）。APBFit **不使用** Google Fit／Fitness API。
- 所有由 App 產生的 Run 歷史都**儲存在你的裝置本機**，你可隨時刪除。

---

## 2. APBFit 的功能

APBFit 讓你以 Google 帳號登入，設定模擬 Run（強度、時長、批次量），並把產生的活動紀錄**寫入同一台裝置上的 Health Connect**。你授權的其他 App（例如從 Health Connect 讀取步數的遊戲）可能依其自身權限與政策讀取這些資料。

Health Connect 將健康資料儲存在**你的裝置上**。APBFit 不提供雲端同步服務，也不會把 Health Connect 紀錄上傳給開發者。

---

## 3. App 存取與處理的資訊

### 3.1 Google 帳號資訊（透過 Google 登入）
- 你的 Google 帳號 **email**、**帳號識別碼**，以及 Google 登入回傳的基本個人資料。
- 用途：顯示目前登入的帳號，並在裝置本機將 Run 歷史與該帳號關聯。

APBFit **不會**申請 Google Fit／Fitness OAuth 權限。

### 3.2 Health Connect 資料
- APBFit 申請**寫入**步數、距離、運動紀錄，以及**讀取**步數（用於開發／除錯時確認寫入，並符合 Health Connect 權限要求）。
- App 會**建立並寫入**這些健康紀錄到**你裝置上的 Health Connect**。此屬於**健康與健身資料**。
- APBFit **不會**為了分析、廣告或建檔而讀取你更廣泛的 Health Connect 歷史。

### 3.3 儲存在裝置本機的資料
- **Run 歷史與 segment 紀錄**（起訖時間、產生的步數／距離、寫入狀態）— 儲存於裝置本機資料庫（Room）。
- **App 偏好設定** — 登入帳號狀態、Run 設定（強度、時長、批次量）— 儲存於本機 App 儲存空間（SharedPreferences）。
- 這些本機資料不會離開你的裝置，除了你透過 App 授權寫入 Health Connect 的紀錄之外。

### 3.4 我們不會蒐集的資料
- 不含分析、廣告識別碼或追蹤 SDK。
- 不蒐集定位資料。
- 不會將你的資料傳送給開發者或我們營運的任何第三方伺服器。

---

## 4. 資訊的使用方式

資訊僅用於：
- 透過 Google 登入驗證身分，並在 App 內顯示登入帳號。
- 依你的要求產生並寫入活動紀錄到 Health Connect。
- 在 App 內顯示你的 Run 歷史與狀態。

我們**不會**將你的資料用於廣告、建檔，或與上述功能無關的任何用途。

---

## 5. Google API 服務 — Limited Use 聲明

APBFit 對於 **Google 登入**所取得資訊的使用，遵循
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy)，
包含其 **Limited Use（有限使用）** 要求：

- Google 登入資料僅用於提供 App 的帳號身分功能。
- 我們不會將 Google 使用者資料轉移或販售給第三方、廣告聯播網、資料仲介，或用於廣告。
- 我們不會將 Google 使用者資料用於與功能無關的用途。
- 除非為了安全、法律遵循或經你明確同意，否則不會有任何人讀取你的 Google 使用者資料。

Health Connect 資料受 Android／Health Connect 平台政策與你的裝置權限管理，不適用 Google Fit API 的 Limited Use 條款。

---

## 6. Health Connect — 儲存、分享與刪除

- APBFit 寫入的紀錄儲存在**你裝置上的 Health Connect**。
- 其他 App 僅在你於 Health Connect 或 Android 設定中**授權**後，才能讀取 Health Connect 資料。
- APBFit **無法控制**第三方 App 如何使用其從 Health Connect 讀取的資料。
- 若要刪除 APBFit 寫入的紀錄，請使用 **Health Connect** App，或撤銷 APBFit 的 Health Connect 權限。
- 解除安裝 APBFit 會刪除**本機 App 歷史**，但不會自動刪除已存在於 Health Connect 的紀錄。

---

## 7. 資料儲存、保留與刪除（本機 App 資料）

- App 歷史儲存於**本機**並自動清理：每次啟動時刪除超過 **90 天**的紀錄。
- 你可在 App 內清除歷史；**移除安裝 APBFit 會刪除所有本機 App 資料**。
- 你可在 App 內登出，以清除 APBFit 端的 Google 登入工作階段。

---

## 8. 資料分享

我們**不會**將你的資料分享給第三方。本 App 僅與以下項目交換資料：
- **Google 登入**（驗證身分），以及
- 你裝置上的 **Health Connect**（在你授權下讀寫健康紀錄）。

---

## 9. 安全性

- APBFit 依賴 Android 的 App 沙箱、Health Connect 權限控制，以及 Google 登入流程。
- App 不儲存密碼；驗證由 Google 登入／Google Play 服務處理。

---

## 10. 兒童隱私

APBFit 並非以未滿 13 歲（或你所在地區之等同最低年齡）之兒童為對象，亦不會在知情下蒐集其資料。

---

## 11. 存取限制（測試階段）

APBFit 目前使用處於 **Testing** 模式的 Google OAuth 設定。僅被加入為 **test users** 的 Google 帳號可在內部測試期間完成 Google 登入。

---

## 12. 政策變更

我們可能不時更新本隱私權政策。重大變更會以上方「最後更新」日期反映。

---

## 13. 聯絡

如對本隱私權政策或你的資料有任何疑問，請聯絡：pixson.srv@gmail.com。
