package app.plandsw.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.plandsw.MainActivity
import app.plandsw.R
import app.plandsw.data.Lesson
import app.plandsw.data.Repository
import app.plandsw.data.WARSAW
import app.plandsw.ui.DarkColors
import app.plandsw.ui.LightColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The app's own palette, so the widget matches the app instead of the wallpaper. */
private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

class PlanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = Repository.get(context)
        // The first frame already has data instead of being empty.
        val initial = loadWidgetData(repo, context)
        provideContent {
            // Glance keeps the session alive, and updateAll() only redraws it with the old values.
            // So the data is re-read inside the composition on every widgetVersion change.
            val version by repo.widgetVersion.collectAsState()
            val data by produceState(initialValue = initial, key1 = version) { value = loadWidgetData(repo, context) }
            GlanceTheme(colors = WidgetColors) { WidgetBody(data.ctx, hasSchedule = data.hasSchedule, day = data.day, now = data.now) }
        }
    }
}

private class WidgetData(val ctx: Context, val hasSchedule: Boolean, val day: WidgetDay?, val now: LocalDateTime)

private suspend fun loadWidgetData(repo: Repository, context: Context): WidgetData {
    val lessons = repo.primaryLessons()
    val now = LocalDateTime.now(WARSAW)
    return WidgetData(repo.localizedContext(context), lessons != null, lessons?.let { pickWidgetDay(it, now) }, now)
}

class PlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlanWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("H:mm")

@Composable
private fun WidgetBody(ctx: Context, hasSchedule: Boolean, day: WidgetDay?, now: LocalDateTime) {
    val colors = GlanceTheme.colors
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            title(ctx, day, now),
            style = TextStyle(color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        )
        Spacer(GlanceModifier.height(6.dp))
        val empty = when {
            !hasSchedule -> ctx.getString(R.string.widget_no_schedule)
            day == null -> ctx.getString(R.string.widget_no_classes)
            else -> null
        }
        if (empty != null || day == null) {
            Text(empty.orEmpty(), style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
        } else {
            LazyColumn {
                items(day.lessons) { lesson -> LessonRow(ctx, lesson, now) }
            }
        }
    }
}

private fun title(ctx: Context, day: WidgetDay?, now: LocalDateTime): String {
    val date = day?.date ?: return ctx.getString(R.string.widget_today)
    val today = now.toLocalDate()
    return when (date) {
        today -> ctx.getString(R.string.widget_today)
        today.plusDays(1) -> ctx.getString(R.string.widget_tomorrow)
        else -> DateTimeFormatter.ofPattern("EEEE, d MMMM", ctx.resources.configuration.locales[0])
            .format(date).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun LessonRow(ctx: Context, lesson: Lesson, now: LocalDateTime) {
    val colors = GlanceTheme.colors
    val running = !now.isBefore(lesson.startAt) && now.isBefore(lesson.endAt)
    Row(
        GlanceModifier
            .fillMaxWidth()
            .cornerRadius(8.dp)
            .background(if (running) colors.primaryContainer else colors.background)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            // List rows intercept touches — a click on the root column never gets here.
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            lesson.startAt.format(timeFmt),
            modifier = GlanceModifier.width(44.dp),
            style = TextStyle(color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        )
        Column(GlanceModifier.defaultWeight()) {
            Text(lesson.subject, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 13.sp))
            val place = if (lesson.isOnline) ctx.getString(R.string.lesson_online) else lesson.room
            if (place.isNotBlank()) {
                Text(place, maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
            }
        }
    }
}
