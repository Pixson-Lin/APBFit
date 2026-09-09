# APBFit 內部測試快速使用說明

本說明適用於 Android 14 以上的 APBFit Google Play 內部測試版本。

開始前請準備：

- 申請測試時登記的 Gmail
- Android 14 或以上手機
- Health Connect
- 約 10 分鐘測試時間

---

## 步驟 1：確認 Google Play 測試資格

使用手機開啟內部測試連結：

https://play.google.com/apps/internaltest/4701168678750706823

確認頁面登入的是申請時登記的 Gmail，然後點選「成為測試人員」。

成功後，頁面會顯示你已加入測試，並提供前往 Google Play 下載的連結。

> 截圖 1：內部測試加入頁面
>
> 截圖 2：成功加入測試後的畫面

如果頁面顯示其他 Gmail，請先切換帳號再重新開啟連結。

---

## 步驟 2：安裝或更新 APBFit

從測試頁面前往 Google Play，點選「安裝」或「更新」。

APBFit 不會出現在一般商店搜尋結果中，請務必從內部測試連結進入。

> 截圖 3：Play 商店的 APBFit 安裝頁面

若看不到安裝按鈕：

1. 確認 Play 商店使用正確 Gmail
2. 等待 5～10 分鐘
3. 重新開啟內部測試連結

---

## 步驟 3：使用 Google 帳號登入

開啟 APBFit，選擇申請測試時登記的 Gmail。

> 截圖 4：Google 帳號選擇畫面

成功登入後，APBFit 首頁會顯示目前登入的 Gmail。

這也代表 OAuth 測試資格運作正常。Google 不會另外提供 OAuth 測試名單查詢頁面。

若出現拒絕存取或應用程式未通過驗證，請截圖並聯絡開發者。

---

## 步驟 4：授權 Health Connect

首次使用時，APBFit 會要求 Health Connect 權限。

請允許 APBFit 使用測試所需的：

- 步數讀取與寫入
- 距離寫入
- 運動紀錄寫入

> 截圖 5：Health Connect 權限畫面

APBFit 只會依你開始的 Run 寫入模擬紀錄，不會將 Health Connect 資料傳送給開發者。

---

## 步驟 5：確認環境狀態

回到 APBFit 首頁後，確認畫面下方的 Health Connect 狀態為綠色。

建議同時確認：

- 電池
- Health Connect
- 通知
- 鬧鐘

> 截圖 6：APBFit 首頁及綠色狀態圖示

若 Health Connect 顯示橘色，請點選該圖示並依提示完成設定。

---

## 步驟 6：設定並開始第一次 Run

第一次測試建議使用簡短設定：

- 時長：5 分鐘
- 強度：任一預設強度
- 批次量：維持預設值

確認設定後，點選畫面上方的「RUN」。

> 截圖 7：強度、時長、批次量與 RUN 按鈕

Run 開始後會顯示：

- 目前狀態
- 已進行時間
- 剩餘時間
- 已寫入步數
- 寫入進度

> 截圖 8：Run 進行中畫面

測試期間可將 APBFit 切到背景或短暫關閉螢幕，但請不要強制停止應用程式。

---

## 步驟 7：確認 Run 完成

Run 完成後，開啟「歷史紀錄」。

確認最新紀錄顯示：

- 狀態為「已完成」
- 已寫入步數大於 0

> 截圖 9：歷史紀錄完成畫面

---

## 步驟 8：確認 Health Connect 紀錄

可從 APBFit 的「設定」頁面點選「Health Connect」，開啟 Health Connect 相關頁面。

> 截圖 10：設定頁面的 Health Connect 按鈕

確認 Health Connect 中可以看到 APBFit 寫入的步數、距離或運動紀錄。

不同手機品牌的 Health Connect 畫面可能略有不同。

不需要將實際數值或 Health Connect 畫面提交給開發者，只需在完成回報表單勾選是否成功。

---

## 步驟 9：選擇性檢查第三方應用

若你有使用支援 Health Connect 的第三方步數應用或遊戲：

1. 確認該應用已設定為從 Health Connect 讀取步數
2. 等待其正常同步
3. 確認是否能讀到本次 Run 的步數

第三方應用的同步時間與讀取規則可能不同；測試結果不代表官方整合或持續相容性保證。

---

## 步驟 10：提交完成回報

完成測試後，請填寫：

`<完成回報表單連結>`

請回報：

- 安裝及登入是否成功
- Health Connect 授權是否成功
- 是否完成至少一次 Run
- Health Connect 是否看到紀錄
- 第三方應用是否讀到步數
- 遇到的錯誤或異常

請勿提交密碼、健康數值或未遮蔽個人資料的截圖。

需要協助請聯絡：

pixson.srv@gmail.com
