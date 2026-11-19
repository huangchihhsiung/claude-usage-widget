package io.github.claudewidget

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import io.github.claudewidget.data.UsageRefresher
import io.github.claudewidget.data.UsageStore
import io.github.claudewidget.work.RefreshWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 在 App 內開啟 claude.ai 登入頁。登入完成後讀取 claude.ai 的 cookie
 * （含 sessionKey），只存在本機，之後背景更新就用它查詢用量。
 *
 * 注意：Google 不允許在 WebView 內用 Google 帳號登入，請改用 Email 驗證碼登入。
 */
class LoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = checkLogin(url)
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) =
                checkLogin(url)
        }
        webView.loadUrl("https://claude.ai/login")

        // claude.ai 是單頁應用，登入後不一定觸發頁面事件，所以再定時檢查一次
        lifecycleScope.launch {
            while (isActive && !done) {
                delay(1500)
                checkLogin(webView.url)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun checkLogin(url: String?) {
        if (done) return
        if (url == null || url.contains("/login") || url.contains("/magic-link")) return
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai") ?: return
        if (!cookies.contains("sessionKey=")) return

        done = true
        CookieManager.getInstance().flush()
        val store = UsageStore(this)
        store.cookie = cookies
        store.userAgent = webView.settings.userAgentString
        store.orgId = null // 重新自動挑選組織

        lifecycleScope.launch {
            UsageRefresher.refresh(applicationContext)
            RefreshWorker.schedule(applicationContext)
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
