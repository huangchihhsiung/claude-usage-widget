package io.github.claudewidget.data

import org.json.JSONObject
import java.time.Duration
import java.time.OffsetDateTime

/** 一個用量視窗，例如「5 小時」或「本週」 */
data class UsageWindow(
    val key: String,
    /** 已使用百分比 0–100 */
    val utilization: Double,
    val resetsAt: OffsetDateTime?,
) {
    val remainingPercent: Double get() = (100.0 - utilization).coerceIn(0.0, 100.0)

    val label: String
        get() = when (key) {
            "five_hour" -> "5 小時"
            "seven_day" -> "本週"
            "seven_day_sonnet" -> "本週 Sonnet"
            "seven_day_opus" -> "本週 Opus"
            else -> key
        }

    fun resetText(now: OffsetDateTime = OffsetDateTime.now()): String {
        val r = resetsAt ?: return "尚未開始計算"
        val d = Duration.between(now, r)
        if (d.isNegative || d.isZero) return "已重置，待更新"
        val days = d.toDays()
        val hours = d.toHours() % 24
        val mins = d.toMinutes() % 60
        return when {
            days > 0 -> "${days} 天 ${hours} 小時後重置"
            hours > 0 -> "${hours} 小時 ${mins} 分後重置"
            else -> "${mins} 分後重置"
        }
    }
}

object UsageParser {
    private val ORDER = listOf("five_hour", "seven_day", "seven_day_sonnet", "seven_day_opus")

    fun parse(json: String?): List<UsageWindow> {
        if (json.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return ORDER.mapNotNull { key ->
            val w = root.optJSONObject(key) ?: return@mapNotNull null
            if (w.isNull("utilization")) return@mapNotNull null
            val u = w.optDouble("utilization", Double.NaN)
            if (u.isNaN()) return@mapNotNull null
            val reset = w.optString("resets_at", "")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            UsageWindow(key, u.coerceIn(0.0, 100.0), reset)
        }
    }
}
