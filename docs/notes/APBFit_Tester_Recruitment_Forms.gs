/**
 * Creates APBFit tester recruitment assets in the current Google account:
 * 1. Recruitment application form
 * 2. Test completion report form
 * 3. Linked response/tracking spreadsheet
 *
 * Run createApbFitTesterForms() once from a standalone Apps Script project.
 */

const APBFIT_FORM_CONFIG = {
  supportEmail: "pixson.srv@gmail.com",
  responseDeadlineDays: 5,
  appVersion: "1.4.20260903",
};

function createApbFitTesterForms() {
  const properties = PropertiesService.getScriptProperties();
  if (properties.getProperty("APBFIT_RECRUITMENT_FORM_ID")) {
    throw new Error(
      "Forms were already created by this script project. Open the URLs in the execution log or the 設定資訊 sheet."
    );
  }

  const spreadsheet = createManagementSpreadsheet_();
  const recruitmentForm = createRecruitmentForm_();
  const reportForm = createTestReportForm_();

  recruitmentForm.setDestination(
    FormApp.DestinationType.SPREADSHEET,
    spreadsheet.getId()
  );
  reportForm.setDestination(
    FormApp.DestinationType.SPREADSHEET,
    spreadsheet.getId()
  );

  properties.setProperties({
    APBFIT_RECRUITMENT_FORM_ID: recruitmentForm.getId(),
    APBFIT_REPORT_FORM_ID: reportForm.getId(),
    APBFIT_SPREADSHEET_ID: spreadsheet.getId(),
  });

  writeSetupLinks_(spreadsheet, recruitmentForm, reportForm);

  const result = {
    recruitmentFormPublicUrl: recruitmentForm.getPublishedUrl(),
    recruitmentFormEditUrl: recruitmentForm.getEditUrl(),
    reportFormPublicUrl: reportForm.getPublishedUrl(),
    reportFormEditUrl: reportForm.getEditUrl(),
    managementSpreadsheetUrl: spreadsheet.getUrl(),
  };

  console.log(JSON.stringify(result, null, 2));
  return result;
}

function createRecruitmentForm_() {
  const form = FormApp.create("APBFit 內部測試人員招募");
  form
    .setDescription(
      [
        "APBFit 是一款將模擬步行／跑步資料寫入 Health Connect 的 Android App。",
        "",
        "本次招募限 Android 14 以上，以便直接驗證 APBFit → Health Connect → 支援 Health Connect 的第三方步數應用或遊戲。",
        "",
        `內部測試名額有限。獲邀後請於 ${APBFIT_FORM_CONFIG.responseDeadlineDays} 天內完成至少一次 Run 並提交測試回報；逾期未回報者可能會暫時移出名單。`,
        "",
        "本表單不要求健康數值或 Health Connect 內容。資料只用於安排 APBFit 內部測試及聯絡。",
        `聯絡信箱：${APBFIT_FORM_CONFIG.supportEmail}`,
      ].join("\n")
    )
    .setCollectEmail(true)
    .setProgressBar(true)
    .setConfirmationMessage(
      "申請已收到。由於名額有限，我們會分批加入測試；入選後將以你填寫的 Gmail 聯絡。"
    );

  form
    .addSectionHeaderItem()
    .setTitle("基本資料")
    .setHelpText("請填寫將用於 Google Play 內部測試的帳號與裝置。");

  form
    .addTextItem()
    .setTitle("Google Play 使用的 Gmail")
    .setHelpText(
      "必須是手機 Play 商店目前可使用的 Gmail；獲邀後會加入 Google Play 內部測試名單與 OAuth 測試使用者名單。"
    )
    .setValidation(
      FormApp.createTextValidation()
        .requireTextIsEmail()
        .setHelpText("請輸入有效的 Gmail 地址。")
        .build()
    )
    .setRequired(true);

  form
    .addTextItem()
    .setTitle("社群暱稱（選填）")
    .setHelpText("僅用來對照你在社群上的申請。")
    .setRequired(false);

  form
    .addMultipleChoiceItem()
    .setTitle("Android 版本")
    .setChoiceValues([
      "Android 16",
      "Android 15",
      "Android 14",
      "不確定",
    ])
    .setRequired(true);

  form
    .addTextItem()
    .setTitle("手機品牌與型號")
    .setHelpText("例如：Samsung Galaxy S23、Google Pixel 8。")
    .setRequired(true);

  form
    .addTextItem()
    .setTitle("預計使用的支援 Health Connect 的第三方步數應用或遊戲")
    .setHelpText("若尚未決定，請填寫「尚未決定」。")
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("Health Connect 狀態")
    .setChoiceValues([
      "已安裝／系統內建，且知道如何開啟",
      "尚未安裝，但願意依說明設定",
      "不確定是否支援",
    ])
    .setRequired(true);

  form
    .addSectionHeaderItem()
    .setTitle("測試承諾")
    .setHelpText("這些問題用來避免有限名額被閒置。");

  form
    .addMultipleChoiceItem()
    .setTitle(`獲邀後，你能否在 ${APBFIT_FORM_CONFIG.responseDeadlineDays} 天內完成測試？`)
    .setChoiceValues([
      "可以",
      "可能需要更多時間",
      "目前無法確定",
    ])
    .setRequired(true);

  form
    .addCheckboxItem()
    .setTitle("我了解內部測試規則")
    .setChoiceValues([
      `我會在獲邀後 ${APBFIT_FORM_CONFIG.responseDeadlineDays} 天內完成至少一次 Run 並提交回報；若未完成，名額可能轉給候補者。`,
    ])
    .setRequired(true);

  form
    .addCheckboxItem()
    .setTitle("資料使用同意")
    .setChoiceValues([
      "我同意提供上述帳號與裝置資訊，用於 APBFit 內部測試名單管理及必要聯絡。",
    ])
    .setRequired(true);

  form
    .addParagraphTextItem()
    .setTitle("其他想補充的資訊（選填）")
    .setHelpText("請勿填寫健康數值、密碼或其他敏感資料。")
    .setRequired(false);

  return form;
}

function createTestReportForm_() {
  const form = FormApp.create("APBFit 內部測試完成回報");
  form
    .setDescription(
      [
        `測試版本：${APBFIT_FORM_CONFIG.appVersion}`,
        "完成至少一次 Run 後，請用 1–2 分鐘提交結果。",
        "請勿上傳實際健康數值；只需回報成功／失敗與必要的錯誤訊息。",
      ].join("\n")
    )
    .setCollectEmail(true)
    .setProgressBar(true)
    .setConfirmationMessage(
      "謝謝，測試結果已收到！若需要更多資訊，我們會透過 Gmail 聯絡。"
    );

  form
    .addTextItem()
    .setTitle("加入內部測試時使用的 Gmail")
    .setValidation(
      FormApp.createTextValidation()
        .requireTextIsEmail()
        .setHelpText("請輸入有效的 Gmail 地址。")
        .build()
    )
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("Android 版本")
    .setChoiceValues([
      "Android 16",
      "Android 15",
      "Android 14",
    ])
    .setRequired(true);

  form
    .addTextItem()
    .setTitle("手機品牌與型號")
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("APBFit 安裝／更新")
    .setChoiceValues([
      "成功",
      "失敗",
      "Play 商店尚未顯示測試版本",
    ])
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("Google 登入")
    .setChoiceValues(["成功", "失敗"])
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("Health Connect 權限授權")
    .setChoiceValues(["成功", "失敗", "裝置上無法使用 Health Connect"])
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("完成至少一次 APBFit Run")
    .setChoiceValues(["成功", "失敗"])
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("Health Connect 是否看到 APBFit 寫入的紀錄？")
    .setChoiceValues(["是", "否", "尚未確認"])
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("支援 Health Connect 的第三方步數應用或遊戲是否讀到步數？")
    .setChoiceValues([
      "是，透過 Health Connect",
      "否",
      "未測試／尚未決定",
    ])
    .setRequired(true);

  form
    .addMultipleChoiceItem()
    .setTitle("關螢幕／背景執行測試（選填）")
    .setChoiceValues([
      "成功，背景仍持續寫入",
      "失敗或疑似中斷",
      "未測試",
    ])
    .setRequired(false);

  form
    .addParagraphTextItem()
    .setTitle("問題描述或錯誤訊息（選填）")
    .setHelpText(
      "請描述操作步驟、看到的錯誤及大約時間；不要貼健康數值或密碼。"
    )
    .setRequired(false);

  form
    .addTextItem()
    .setTitle("錯誤截圖／錄影連結（選填）")
    .setHelpText(
      "請使用只有知道連結者可查看的分享設定，並遮蔽 email、通知或其他個資。"
    )
    .setRequired(false);

  return form;
}

function createManagementSpreadsheet_() {
  const spreadsheet = SpreadsheetApp.create("APBFit 內部測試招募與追蹤");
  const trackingSheet = spreadsheet.getSheets()[0];
  trackingSheet.setName("測試者管理");

  const headers = [
    "申請 Gmail",
    "社群暱稱",
    "Android／機型",
    "狀態",
    "Play testers 已加入",
    "OAuth test users 已加入",
    "邀請日期",
    "回報期限",
    "已提交完成回報",
    "HC 結果",
    "第三方應用或遊戲結果",
    "最後聯絡日期",
    "備註",
  ];
  trackingSheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  trackingSheet.setFrozenRows(1);
  trackingSheet
    .getRange(1, 1, 1, headers.length)
    .setBackground("#5E35B1")
    .setFontColor("#FFFFFF")
    .setFontWeight("bold");
  trackingSheet.autoResizeColumns(1, headers.length);
  trackingSheet.setColumnWidth(1, 220);
  trackingSheet.setColumnWidth(13, 280);

  const statusValidation = SpreadsheetApp.newDataValidation()
    .requireValueInList(
      ["候補", "已邀請", "測試中", "已完成", "逾期移除", "退出"],
      true
    )
    .setAllowInvalid(false)
    .build();
  trackingSheet.getRange("D2:D1000").setDataValidation(statusValidation);
  trackingSheet.getRange("E2:F1000").insertCheckboxes();
  trackingSheet.getRange("I2:I1000").insertCheckboxes();
  trackingSheet.getRange("G2:H1000").setNumberFormat("yyyy-mm-dd");
  trackingSheet.getRange("L2:L1000").setNumberFormat("yyyy-mm-dd");

  const guideSheet = spreadsheet.insertSheet("管理說明");
  guideSheet.getRange("A1:B8").setValues([
    ["項目", "說明"],
    ["候補", "已申請，但尚未占用 Play／OAuth 名額。"],
    ["已邀請", "已加入兩份名單並寄出邀請。"],
    ["測試中", `應於邀請後 ${APBFIT_FORM_CONFIG.responseDeadlineDays} 天內提交完成回報。`],
    ["已完成", "收到完成回報；可視需求保留作回歸測試。"],
    ["逾期移除", "期限內未回報；從 Play 與 OAuth 兩份名單移除。"],
    ["隱私", "不要把健康數值、密碼或不必要的個資複製到管理表。"],
    ["名額", "分批加入 10–20 人，避免一次填滿 100 人。"],
  ]);
  guideSheet.getRange("A1:B1").setFontWeight("bold");
  guideSheet.setColumnWidth(1, 140);
  guideSheet.setColumnWidth(2, 520);

  return spreadsheet;
}

function writeSetupLinks_(spreadsheet, recruitmentForm, reportForm) {
  const sheet = spreadsheet.insertSheet("設定資訊");
  sheet.getRange("A1:B6").setValues([
    ["項目", "URL"],
    ["招募表單（公開填寫）", recruitmentForm.getPublishedUrl()],
    ["招募表單（編輯）", recruitmentForm.getEditUrl()],
    ["完成回報表單（傳給受邀者）", reportForm.getPublishedUrl()],
    ["完成回報表單（編輯）", reportForm.getEditUrl()],
    ["管理試算表", spreadsheet.getUrl()],
  ]);
  sheet.getRange("A1:B1").setFontWeight("bold");
  sheet.setColumnWidth(1, 240);
  sheet.setColumnWidth(2, 600);
}

/**
 * Clears only the saved IDs in this Apps Script project.
 * It does NOT delete forms or spreadsheets already created in Google Drive.
 * Use only when intentionally creating a fresh set.
 */
function resetApbFitFormSetupState() {
  PropertiesService.getScriptProperties().deleteAllProperties();
  console.log(
    "Saved IDs cleared. Existing Forms and Sheets were not deleted."
  );
}
