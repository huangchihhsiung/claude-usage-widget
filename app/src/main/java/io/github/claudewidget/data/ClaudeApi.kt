package io.github.claudewidget.data

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AuthException(message: String) : Exception(message)

/**
 * 呼叫 claude.ai 網頁版自己在「設定 → 用量」頁使用的非公開端點。
 * 這不是官方公開 API，Anthropic 隨時可能變更，屆時需要更新本檔。
 */
object ClaudeApi {
    private const val BASE = "https://claude.ai"

    data class Org(val uuid: String, val name: String, val capabilities: List<String>)

    fun organizations(cookie: String, userAgent: String): List<Org> {
        val arr = JSONArray(get("/api/organizations", cookie, userAgent))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val caps = o.optJSONArray("capabilities")
            Org(
                uuid = o.getString("uuid"),
                name = o.optString("name", "(未命名)"),
                capabilities = if (caps == null) emptyList()
                else (0 until caps.length()).map { caps.optString(it) },
            )
        }
    }

    /** 回傳原始 JSON，內含 five_hour / seven_day / seven_day_sonnet ... */
    fun usage(orgId: String, cookie: String, userAgent: String): String =
        get("/api/organizations/$orgId/usage", cookie, userAgent)

    /** 個人訂閱優先（Max / Pro），其次可聊天的組織 */
    fun pickDefault(orgs: List<Org>): Org? =
        orgs.firstOrNull { o -> o.capabilities.any { it == "claude_max" || it == "claude_pro" } }
            ?: orgs.firstOrNull { "chat" in it.capabilities }
            ?: orgs.firstOrNull()

    private fun get(path: String, cookie: String, userAgent: String): String {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Cookie", cookie)
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Referer", "$BASE/settings/usage")
            setRequestProperty("anthropic-client-platform", "web_claude_ai")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            when {
                code == 401 || code == 403 ->
                    throw AuthException("HTTP $code（登入已失效或被網站防護擋下）")
                code !in 200..299 ->
                    throw IOException("HTTP $code ${body.take(120)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
