package io.github.claudewidget

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.claudewidget.data.ClaudeApi
import io.github.claudewidget.data.UsageParser
import io.github.claudewidget.data.UsageRefresher
import io.github.claudewidget.data.UsageStore
import io.github.claudewidget.widget.WidgetUpdater
import io.github.claudewidget.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = UsageStore(this)
        if (store.isLoggedIn) RefreshWorker.schedule(this)

        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(
                        store = store,
                        tick = tick,
                        onChanged = { tick++ },
                        onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                        onBattery = {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                        defaultUa = { WebSettings.getDefaultUserAgent(this) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 若 WebView 裡的 cookie 被網站更新過，同步回本機儲存
        val store = UsageStore(this)
        if (store.isLoggedIn) {
            runCatching {
                CookieManager.getInstance().getCookie("https://claude.ai")
                    ?.takeIf { it.contains("sessionKey=") }
                    ?.let { store.cookie = it }
            }
        }
        tick++
    }
}

@Composable
private fun MainScreen(
    store: UsageStore,
    tick: Int,
    onChanged: () -> Unit,
    onLogin: () -> Unit,
    onBattery: () -> Unit,
    defaultUa: () -> String,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var orgs by remember { mutableStateOf<List<ClaudeApi.Org>>(emptyList()) }
    var manualKey by remember { mutableStateOf("") }

    // tick 改變時重新讀取
    val loggedIn = remember(tick) { store.isLoggedIn }
    val windows = remember(tick) { UsageParser.parse(store.usageJson) }
    val updatedAt = remember(tick) { store.updatedAt }
    val error = remember(tick) { store.error }
    val orgName = remember(tick) { store.orgName }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Claude 用量小工具", style = MaterialTheme.typography.headlineSmall)

        if (!loggedIn) {
            Text("請先登入 claude.ai。登入資料只存在這支手機上，只會傳送給 claude.ai。")
            Text(
                "提示：WebView 內無法使用「以 Google 帳號登入」，請選擇以 Email 接收驗證碼登入。",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                Text("登入 claude.ai")
            }
            HorizontalDivider()
            Text("進階：手動貼上 sessionKey", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = manualKey,
                onValueChange = { manualKey = it.trim() },
                label = { Text("sk-ant-sid01-…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = manualKey.isNotBlank() && !busy,
                onClick = {
                    store.cookie = "sessionKey=$manualKey"
                    store.userAgent = defaultUa()
                    store.orgId = null
                    busy = true
                    scope.launch {
                        UsageRefresher.refresh(appContextOf(store))
                        RefreshWorker.schedule(appContextOf(store))
                        busy = false
                        onChanged()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存並測試") }
            return@Column
        }

        Text("組織：${orgName ?: "（自動選擇）"}", style = MaterialTheme.typography.bodyMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (windows.isEmpty()) {
                    Text(error ?: "尚無資料，請按「立即更新」")
                }
                windows.forEach { w ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(w.label, Modifier.weight(1f))
                        Text("剩 ${w.remainingPercent.roundToInt()}%", fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (w.remainingPercent / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(w.resetText(), style = MaterialTheme.typography.bodySmall)
                }
                if (updatedAt > 0) {
                    Text(
                        "更新於 " + SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            .format(Date(updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (error != null && windows.isNotEmpty()) {
                    Text("⚠ $error", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    UsageRefresher.refresh(appContextOf(store))
                    busy = false
                    onChanged()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "更新中…" else "立即更新") }

        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    orgs = withContext(Dispatchers.IO) {
                        runCatching {
                            ClaudeApi.organizations(
                                store.cookie ?: "",
                                store.userAgent ?: UsageRefresher.FALLBACK_UA,
                            )
                        }.getOrDefault(emptyList())
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("切換組織（個人／團隊方案）") }

        orgs.forEach { org ->
            TextButton(
                onClick = {
                    store.orgId = org.uuid
                    store.orgName = org.name
                    orgs = emptyList()
                    busy = true
                    scope.launch {
                        UsageRefresher.refresh(appContextOf(store))
                        busy = false
                        onChanged()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(org.name) }
        }

        HorizontalDivider()
        Text("三星手機建議", fontWeight = FontWeight.Bold)
        Text(
            "到「設定 → 應用程式 → Claude 用量小工具 → 電池」改成「不受限制」，" +
                "否則 One UI 可能暫停背景更新。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onBattery, modifier = Modifier.fillMaxWidth()) {
            Text("開啟電池最佳化設定")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                store.clear()
                runCatching { CookieManager.getInstance().removeAllCookies(null) }
                RefreshWorker.cancel(appContextOf(store))
                scope.launch {
                    WidgetUpdater.pushAll(appContextOf(store))
                    onChanged()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("登出並清除資料", color = MaterialTheme.colorScheme.error) }
    }
}

/** UsageStore 內部持有 applicationContext；這裡讓 UI 取得它 */
private fun appContextOf(store: UsageStore) = store.appContext
