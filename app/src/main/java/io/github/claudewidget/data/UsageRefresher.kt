package io.github.claudewidget.data

import android.content.Context
import io.github.claudewidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object UsageRefresher {
    private val lock = Mutex()

    /** 抓取最新用量、存檔並刷新所有桌面小工具。回傳 true 表示成功。 */
    suspend fun refresh(context: Context): Boolean = lock.withLock {
        val ok = withContext(Dispatchers.IO) { fetch(context) }
        WidgetUpdater.pushAll(context)
        ok
    }

    private fun fetch(context: Context): Boolean {
        val store = UsageStore(context)
        val cookie = store.cookie
        if (cookie.isNullOrBlank()) {
            store.saveError("尚未登入，請開啟 App 登入")
            return false
        }
        val ua = store.userAgent ?: FALLBACK_UA
        return try {
            var orgId = store.orgId
            if (orgId.isNullOrBlank()) {
                val org = ClaudeApi.pickDefault(ClaudeApi.organizations(cookie, ua))
                    ?: throw IllegalStateException("找不到組織")
                store.orgId = org.uuid
                store.orgName = org.name
                orgId = org.uuid
            }
            val json = ClaudeApi.usage(orgId, cookie, ua)
            if (UsageParser.parse(json).isEmpty()) {
                throw IllegalStateException("回傳格式無法辨識")
            }
            store.saveSuccess(json)
            true
        } catch (e: AuthException) {
            store.saveError("登入已失效，請開啟 App 重新登入")
            false
        } catch (e: Exception) {
            store.saveError("更新失敗：${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    const val FALLBACK_UA =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/141.0.0.0 Mobile Safari/537.36"
}
