# APBFit 測試者招募 Google Forms 建立指南

此指南搭配 [`APBFit_Tester_Recruitment_Forms.gs`](APBFit_Tester_Recruitment_Forms.gs)，會在你的 Google 帳號內一次建立：

1. **測試者招募表單**：公開貼在社群收申請。
2. **測試完成回報表單**：只傳給已加入名單的測試者。
3. **管理試算表**：包含兩份表單回覆、名額追蹤表及管理說明。

腳本不會自動修改 Play Console 或 Google Cloud OAuth 名單；這兩份名單仍需由 Owner 人工管理。

---

## 1. 建立 Apps Script 專案

1. 使用管理 APBFit 測試的 Google 帳號登入。
2. 開啟 <https://script.google.com/>。
3. 點擊「**新增專案**」。
4. 將專案命名為 `APBFit Tester Recruitment Forms`。
5. 開啟預設的 `Code.gs`，刪除原有內容。
6. 將 [`APBFit_Tester_Recruitment_Forms.gs`](APBFit_Tester_Recruitment_Forms.gs) 全部複製到 `Code.gs`。
7. 按 `Ctrl+S` 儲存。

---

## 2. 執行腳本

1. 上方函式選單選擇 `createApbFitTesterForms`。
2. 點「**執行**」。
3. 第一次執行會要求授權：
   - 選擇你的 Google 帳號。
   - 若顯示「Google 尚未驗證這個應用程式」，點「進階」→「前往 APBFit Tester Recruitment Forms」。
   - 允許腳本建立及管理由它建立的 Google Forms／Sheets。
4. 等執行記錄顯示完成。
5. 打開「**執行記錄**」，取得：
   - 招募表單公開連結
   - 完成回報表單公開連結
   - 管理試算表連結

所有連結也會存放在管理試算表的「**設定資訊**」工作表。

> `createApbFitTesterForms` 在同一個 Apps Script 專案中預設只能成功執行一次，避免不小心產生重複表單。

---

## 3. 建立後必做檢查

分別打開兩份表單，確認：

- 表單語言與內容正確。
- 「回覆」頁籤顯示已連結至 `APBFit 內部測試招募與追蹤`。
- 招募表單正在接受回覆。
- 不要求填寫健康數值、密碼或不必要的個資。
- 用另一個 Google 帳號各送出一筆測試回覆，確認試算表收到資料。
- 測試完成後刪除測試回覆。

如果「收集電子郵件地址」設定會限制目標社群填寫，可在 Forms「設定 → 回覆」確認登入要求；但仍應保留「Google Play 使用的 Gmail」必填欄位。

---

## 4. 建議招募與名額輪替流程

### 公開招募階段

社群貼文只放「**招募表單公開連結**」，**不要直接公開內部測試安裝連結**。

建議每批挑選 **10–20 人**：

1. 優先 Android 14+、願意在 5 天內完成者。
2. 將入選者 Gmail 複製到「測試者管理」。
3. 同時加入：
   - Play Console → 內部測試 → Testers
   - Google Cloud → OAuth consent screen → Test users
4. 勾選管理表的兩個名單欄位。
5. 設定邀請日期與 5 天後的回報期限。
6. 寄送 [`Internal_test_notes.txt`](Internal_test_notes.txt) 與「完成回報表單」連結。

### 回報與回收名額

- 收到完成回報：狀態改為「已完成」。
- 期限內未回報：先提醒一次。
- 提醒後仍未回報：從 Play testers 與 OAuth test users **兩邊移除**，狀態改為「逾期移除」。
- 從候補名單補入下一位。

Play Console／Google Cloud 無法可靠顯示個別使用者是否真的完成 Run，因此以「完成回報表單」作為活躍依據。

---

## 5. 建議社群招募文案

```text
📱 APBFit Health Connect 內部測試招募

徵求 Android 使用者協助測試 APBFit 寫入 Health Connect，以及支援 Health Connect 的第三方步數應用或遊戲能否讀取步數。

• 本次招募限 Android 14 以上
• 需使用 Google Play 與 Health Connect
• 入選後請於 5 天內完成至少一次 Run 並提交簡短回報
• 名額有限，將分批邀請；未於期限內回報者會先釋出名額
• 不需要提供實際健康數值

申請表：<貼上招募表單公開連結>
```

---

## 6. 修改表單內容

腳本只負責首次建立。建立完成後，可直接在 Google Forms 編輯題目與說明，不必再次執行腳本。

若確實要建立全新一套表單：

1. 確認舊表單與試算表是否需要保留。
2. 在 Apps Script 執行 `resetApbFitFormSetupState()`。
3. 再執行 `createApbFitTesterForms()`。

`resetApbFitFormSetupState()` **只清除腳本記錄的 ID，不會刪除 Google Drive 中既有的表單或試算表**。

---

## 7. 隱私與安全注意事項

- 招募表單只收測試所需的 Gmail、裝置版本與聯絡識別。
- 不收 Health Connect 健康數值、密碼、生日、地址或付款資料。
- 錯誤截圖應遮蔽 email、通知和其他個資。
- 管理試算表不要設為公開，僅分享給需要管理測試的人。
- 測試結束後，依實際需求刪除不再需要的申請資料。


---

## 2026/09/09 09:41 執行結果
```
{
  "recruitmentFormPublicUrl": "https://docs.google.com/forms/d/e/1FAIpQLSdZU2jkmumuSnxHN9l0lvInseXR9gvG92-l6mfq9157aBiRHg/viewform",
  "recruitmentFormEditUrl": "https://docs.google.com/forms/d/1JrlMFOjNTAgJBh075ap-VrEGsUGbvMTxFP0B3ZvuODI/edit",
  "reportFormPublicUrl": "https://docs.google.com/forms/d/e/1FAIpQLSe7yHKps_-3ciQuhaaOTZ5ZoyGBrUG_W-hkvtrLh_s6F1GPFw/viewform",
  "reportFormEditUrl": "https://docs.google.com/forms/d/1QzJRaaVux56addEb9dU1VvTe6IgmTxcsVTNoa6NGqEM/edit",
  "managementSpreadsheetUrl": "https://docs.google.com/spreadsheets/d/1NTj1neN4iZoe0pTya_L_S_rZqyWlyzoD0sHeKPPpK_s/edit"
}
```