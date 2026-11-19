# Claude 用量小工具（Android 桌面 Widget）

在 Android 手機桌面（例如三星 Galaxy S26 Ultra / One UI）上顯示 **Claude 剩餘用量**：

- **5 小時**滾動視窗：剩餘百分比、多久後重置
- **本週**用量：剩餘百分比、多久後重置
- 若帳號有提供，另顯示 **本週 Sonnet / Opus** 個別額度
- 依剩餘量變色（綠 > 50%、橘 > 20%、紅 ≤ 20%）
- 每 15 分鐘背景自動更新，點小工具上的 **↻** 可立即更新

**不需要 root**，一般 APK 安裝即可。

> ⚠️ 本專案為非官方開源專案，與 Anthropic 無關。資料來源是 claude.ai 網頁版
> 「設定 → 用量」頁面使用的非公開端點，Anthropic 可能隨時變更，屆時程式需要更新。

---

## 運作方式

```
App 內 WebView 登入 claude.ai
        │ 取得 claude.ai cookie（sessionKey），只存在手機本機
        ▼
GET https://claude.ai/api/organizations           → 自動挑選個人方案組織
GET https://claude.ai/api/organizations/{id}/usage → five_hour / seven_day ...
        ▼
WorkManager 每 15 分鐘更新 → Jetpack Glance 重繪桌面小工具
```

回傳格式範例：

```json
{
  "five_hour":  { "utilization": 35.0, "resets_at": "2026-11-19T05:00:00+00:00" },
  "seven_day":  { "utilization": 12.0, "resets_at": "2026-11-24T01:00:00+00:00" },
  "seven_day_sonnet": { "utilization": 3.0, "resets_at": "..." }
}
```

`utilization` 為已使用百分比，小工具顯示 `100 − utilization` 作為剩餘量。

## 隱私

- 登入 cookie 只存在 App 私有儲存空間（`SharedPreferences`），`allowBackup=false`，不會被雲端備份。
- 只會連線到 `claude.ai`，沒有任何第三方伺服器、分析或追蹤。
- App 內「登出並清除資料」會刪除 cookie 與所有快取。

## 安裝

### 方法 A：用 GitHub Actions 自動編譯（不用裝 Android Studio）

1. 把本專案推到你的 GitHub repo。
2. 到 **Actions → Build APK**，等編譯完成，下載 `claude-usage-widget-debug` 裡的 `app-debug.apk`。
   （發佈 Release 時也會自動把 APK 附在 Release 上。）
3. 把 APK 傳到手機，點開安裝，系統詢問時允許「安裝未知應用程式」。

### 方法 B：Android Studio

1. 用 Android Studio（Ladybug 以上）開啟專案資料夾，等 Gradle 同步完成。
2. 手機開啟「開發人員選項 → USB 偵錯」，接上電腦，按 ▶ Run。

## 使用

1. 開啟 App，按 **登入 claude.ai**。
   - **WebView 內無法使用「以 Google 帳號登入」**（Google 的限制），請選擇用 Email 收驗證碼登入。
   - 若你的帳號只能用 Google 登入，可用「進階：手動貼上 sessionKey」：
     在電腦瀏覽器登入 claude.ai → 開發者工具 → Application → Cookies → 複製 `sessionKey` 的值。
2. 登入成功後 App 會自動抓一次用量。
3. 回到桌面 → 長按空白處 → **小工具** → 找到「Claude 用量小工具」→ 拖到桌面，可調整大小。
4. 若同時有個人與團隊方案，可在 App 內按 **切換組織**。

### 三星 One UI 必做設定

One UI 對背景程式很積極地省電，否則小工具可能好幾小時不更新：

- **設定 → 應用程式 → Claude 用量小工具 → 電池 → 不受限制**
- **設定 → 電池 → 背景使用限制**：確認本 App 不在「休眠」或「深度休眠」清單中

## 已知限制

- Android 規定背景週期工作最短 15 分鐘，想更即時請點 ↻。
- claude.ai 有 Cloudflare 防護，偶爾會擋下背景請求（顯示 HTTP 403）。重新開啟 App 登入通常即可恢復。
- 手動貼 sessionKey 的方式沒有 Cloudflare cookie，被擋的機率比 WebView 登入高。
- sessionKey 過期後需要重新登入。

## 專案結構

```
app/src/main/java/io/github/claudewidget/
├── MainActivity.kt          # 設定畫面（Compose）
├── LoginActivity.kt         # WebView 登入 claude.ai
├── data/
│   ├── ClaudeApi.kt         # HTTP 請求（端點變更時改這裡）
│   ├── Usage.kt             # JSON 解析、重置時間文字
│   ├── UsageRefresher.kt    # 抓取 → 存檔 → 刷新小工具
│   └── UsageStore.kt        # 本機儲存
├── widget/UsageWidget.kt    # Glance 桌面小工具 UI、↻ 按鈕
└── work/RefreshWorker.kt    # 每 15 分鐘背景更新
```

## 想貢獻的方向

- 鎖定畫面 / 圓形小尺寸版面（Glance `SizeMode.Responsive`）
- 自訂更新頻率、主題色
- 低於門檻時推播通知
- Claude Code 使用者：改用 OAuth token 呼叫 `api.anthropic.com/api/oauth/usage`

## 授權

MIT License
