package io.github.claudewidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.claudewidget.MainActivity
import io.github.claudewidget.data.UsageParser
import io.github.claudewidget.data.UsageRefresher
import io.github.claudewidget.data.UsageStore
import io.github.claudewidget.data.UsageWindow
import io.github.claudewidget.work.RefreshWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val K_JSON = stringPreferencesKey("usage_json")
private val K_UPDATED = longPreferencesKey("updated_at")
private val K_ERROR = stringPreferencesKey("error")

private val BG = Color(0xFF1F1E1D)
private val TRACK = Color(0xFF3A3836)
private val TEXT = Color(0xFFF5F0E8)
private val SUBTLE = Color(0xFFA8A29A)
private val GOOD = Color(0xFF6FBF73)
private val WARN = Color(0xFFE0A84F)
private val BAD = Color(0xFFE06A4F)

class UsageWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = UsageStore(context)
        val fbJson = store.usageJson
        val fbUpdated = store.updatedAt
        val fbError = store.error
        val loggedIn = store.isLoggedIn

        provideContent {
            val prefs = currentState<Preferences>()
            val json = prefs[K_JSON]?.takeIf { it.isNotEmpty() } ?: fbJson
            val updated = prefs[K_UPDATED]?.takeIf { it > 0 } ?: fbUpdated
            val error = (prefs[K_ERROR] ?: fbError)?.takeIf { it.isNotEmpty() }
            WidgetContent(UsageParser.parse(json), updated, error, loggedIn)
        }
    }
}

@Composable
private fun WidgetContent(
    windows: List<UsageWindow>,
    updatedAt: Long,
    error: String?,
    loggedIn: Boolean,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BG)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Claude 剩餘用量",
                style = TextStyle(
                    color = ColorProvider(TEXT),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "↻",
                style = TextStyle(color = ColorProvider(SUBTLE), fontSize = 18.sp),
                modifier = GlanceModifier
                    .padding(start = 8.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        if (windows.isEmpty()) {
            Text(
                text = when {
                    !loggedIn -> "點此開啟 App 登入 claude.ai"
                    error != null -> error
                    else -> "尚無資料，點 ↻ 更新"
                },
                style = TextStyle(color = ColorProvider(SUBTLE), fontSize = 12.sp),
            )
        } else {
            windows.take(3).forEach { w ->
                WindowRow(w)
                Spacer(GlanceModifier.height(6.dp))
            }
        }

        val footer = buildString {
            if (updatedAt > 0) {
                append("更新於 ")
                append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAt)))
            }
            if (error != null && windows.isNotEmpty()) append(" · ⚠ 更新失敗")
        }
        if (footer.isNotEmpty()) {
            Text(
                text = footer,
                style = TextStyle(color = ColorProvider(SUBTLE), fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun WindowRow(w: UsageWindow) {
    val remain = w.remainingPercent
    val color = when {
        remain > 50 -> GOOD
        remain > 20 -> WARN
        else -> BAD
    }
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = w.label,
            style = TextStyle(color = ColorProvider(TEXT), fontSize = 12.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = "剩 ${remain.roundToInt()}%",
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
    Spacer(GlanceModifier.height(3.dp))
    LinearProgressIndicator(
        progress = (remain / 100.0).toFloat(),
        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        color = ColorProvider(color),
        backgroundColor = ColorProvider(TRACK),
    )
    Spacer(GlanceModifier.height(2.dp))
    Text(
        text = w.resetText(),
        style = TextStyle(color = ColorProvider(SUBTLE), fontSize = 10.sp),
    )
}

/** 點小工具上的 ↻ 立即更新 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        UsageRefresher.refresh(context)
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }
}

object WidgetUpdater {
    /** 把本機最新資料寫進每個小工具的狀態並重繪 */
    suspend fun pushAll(context: Context) {
        val store = UsageStore(context)
        val widget = UsageWidget()
        val ids = GlanceAppWidgetManager(context).getGlanceIds(UsageWidget::class.java)
        ids.forEach { id ->
            updateAppWidgetState(context, id) { p ->
                p[K_JSON] = store.usageJson ?: ""
                p[K_UPDATED] = store.updatedAt
                p[K_ERROR] = store.error ?: ""
            }
            widget.update(context, id)
        }
    }
}
