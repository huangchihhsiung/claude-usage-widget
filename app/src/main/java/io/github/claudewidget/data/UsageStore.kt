package io.github.claudewidget.data

import android.content.Context

/**
 * 所有資料只存在手機本機（App 私有空間），不會上傳到 claude.ai 以外的地方。
 */
class UsageStore(context: Context) {
    val appContext: Context = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("claude_usage_store", Context.MODE_PRIVATE)

    var cookie: String?
        get() = prefs.getString(K_COOKIE, null)
        set(v) = prefs.edit().putString(K_COOKIE, v).apply()

    var userAgent: String?
        get() = prefs.getString(K_UA, null)
        set(v) = prefs.edit().putString(K_UA, v).apply()

    var orgId: String?
        get() = prefs.getString(K_ORG_ID, null)
        set(v) = prefs.edit().putString(K_ORG_ID, v).apply()

    var orgName: String?
        get() = prefs.getString(K_ORG_NAME, null)
        set(v) = prefs.edit().putString(K_ORG_NAME, v).apply()

    /** 最近一次成功取得的原始 usage JSON */
    val usageJson: String? get() = prefs.getString(K_JSON, null)

    /** 最近一次成功更新的時間（epoch ms） */
    val updatedAt: Long get() = prefs.getLong(K_UPDATED, 0L)

    /** 最近一次更新失敗的訊息；成功後會清空 */
    val error: String? get() = prefs.getString(K_ERROR, null)

    val isLoggedIn: Boolean get() = !cookie.isNullOrBlank()

    fun saveSuccess(json: String) {
        prefs.edit()
            .putString(K_JSON, json)
            .putLong(K_UPDATED, System.currentTimeMillis())
            .remove(K_ERROR)
            .apply()
    }

    fun saveError(message: String) {
        prefs.edit().putString(K_ERROR, message).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val K_COOKIE = "cookie"
        const val K_UA = "ua"
        const val K_ORG_ID = "org_id"
        const val K_ORG_NAME = "org_name"
        const val K_JSON = "usage_json"
        const val K_UPDATED = "updated_at"
        const val K_ERROR = "error"
    }
}
