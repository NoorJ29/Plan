package com.borealis.planner.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.borealis.planner.AppScope
import com.borealis.planner.MainActivity
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.data.Priority
import com.borealis.planner.data.Task
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =================================================================== plumbing

object WidgetKeys {
    val taskId = ActionParameters.Key<String>("task_id")
    val widgetId = ActionParameters.Key<Int>("widget_id")
    val tomorrow = ActionParameters.Key<Boolean>("tomorrow")
}

object WidgetUpdater {
    private var job: Job? = null

    @Synchronized
    fun request(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        job = AppScope.scope.launch {
            delay(200)
            updateNow(app)
        }
    }

    suspend fun updateNow(context: Context) {
        try {
            TodayWidget().updateAll(context)
            OrbWidget().updateAll(context)
            NextUpWidget().updateAll(context)
        } catch (e: Exception) {
            Log.w("WidgetUpdater", "Widget refresh failed", e)
        }
    }
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.taskId] ?: return
        PlannerStore.get(context).toggleDone(id)
        WidgetUpdater.updateNow(context)
    }
}

class SetWidgetDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val wid = parameters[WidgetKeys.widgetId] ?: return
        val tomorrow = parameters[WidgetKeys.tomorrow] ?: false
        PlannerStore.get(context).setWidgetDay(wid, tomorrow)
        WidgetUpdater.updateNow(context)
    }
}

private fun openApp(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

private fun addTask(context: Context, day: Long): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD)
            .putExtra(MainActivity.EXTRA_DAY, day)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

private fun color(argb: Int) = ColorProvider(Color(argb))

private fun meta(t: Task, use24: Boolean): String = buildString {
    if (t.time != null) append(Day.formatTime(t.time, use24))
    if (t.subtasks.isNotEmpty()) {
        if (isNotEmpty()) append("  ·  ")
        append("${t.subtasksDone}/${t.subtasks.size}")
    }
}

// =================================================================== Today checklist

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = PlannerStore.get(context)
        store.ensureFresh()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val state by store.state.collectAsState()
            TodayContent(state, appWidgetId)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        PlannerStore.get(context).forgetWidgets(appWidgetIds)
    }
}

@Composable
private fun TodayContent(state: PlannerState, appWidgetId: Int) {
    val context = LocalContext.current
    val size = LocalSize.current
    val style = state.settings.widget
    val p = WidgetPalettes.of(style.theme)
    val today = Day.today()
    val tomorrow = appWidgetId in state.widgetTomorrow
    val day = if (tomorrow) today + 1 else today
    val open = PlannerLogic.openOn(state, day)
    val done = if (style.showDone) PlannerLogic.doneOn(state, day) else emptyList()
    val progress = PlannerLogic.progress(state, day)
    val use24 = DateFormat.is24HourFormat(context)
    val bg = WidgetArt.background(context, size.width.value, size.height.value, style, progress.fraction)
    val roomy = size.height >= 170.dp

    val headline = when {
        progress.total == 0 -> if (tomorrow) "Nothing yet" else "Clear sky"
        progress.left == 0 -> "All clear"
        tomorrow -> "${progress.total} planned"
        else -> "${progress.left} left"
    }

    Box(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bg), ContentScale.FillBounds)
            .appWidgetBackground(),
    ) {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(openApp(context))) {
                    Text(
                        Day.eyebrow(day),
                        style = TextStyle(color = color(p.muted), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Text(
                        headline,
                        style = TextStyle(color = color(p.text), fontSize = if (roomy) 22.sp else 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
                if (progress.total > 0) {
                    val ringDp = if (roomy) 44f else 36f
                    Box(
                        GlanceModifier.size(ringDp.dp).clickable(openApp(context)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            ImageProvider(WidgetArt.ring(context, ringDp, progress.fraction, style.theme, 4f)),
                            contentDescription = "${progress.done} of ${progress.total} done",
                            modifier = GlanceModifier.fillMaxSize(),
                        )
                        Text(
                            "${progress.done}/${progress.total}",
                            style = TextStyle(color = color(p.text), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DayPill("Today", !tomorrow, appWidgetId, false, p)
                Spacer(GlanceModifier.width(6.dp))
                DayPill("Tomorrow", tomorrow, appWidgetId, true, p)
                Spacer(GlanceModifier.defaultWeight())
                Image(
                    ImageProvider(WidgetArt.plus(context, style.theme, 28f)),
                    contentDescription = "Add task",
                    modifier = GlanceModifier.size(28.dp).clickable(addTask(context, day)),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            val list = open + done
            if (list.isEmpty()) {
                Box(
                    GlanceModifier.fillMaxWidth().defaultWeight().clickable(addTask(context, day)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (tomorrow) "Plan tomorrow — tap +" else "Nothing left. Tap + to add.",
                        style = TextStyle(color = color(p.muted), fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(list, itemId = { it.id.hashCode().toLong() }) { t ->
                        TaskRow(t, p, style.theme, use24, context)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPill(label: String, selected: Boolean, appWidgetId: Int, tomorrow: Boolean, p: WidgetPalette) {
    val bg = if (selected) (p.accent and 0x00FFFFFF) or (0x40 shl 24) else (0x1AFFFFFF)
    Box(
        GlanceModifier
            .cornerRadius(14.dp)
            .background(color(bg))
            .clickable(
                actionRunCallback<SetWidgetDayAction>(
                    actionParametersOf(WidgetKeys.widgetId to appWidgetId, WidgetKeys.tomorrow to tomorrow),
                ),
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                color = color(if (selected) p.text else p.muted),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun TaskRow(t: Task, p: WidgetPalette, theme: com.borealis.planner.data.WidgetTheme, use24: Boolean, context: Context) {
    val sub = meta(t, use24)
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(WidgetArt.check(context, t.done, theme, 22f)),
            contentDescription = if (t.done) "Mark ${t.title} not done" else "Mark ${t.title} done",
            modifier = GlanceModifier
                .size(30.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(WidgetKeys.taskId to t.id))),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight().clickable(openApp(context))) {
            Text(
                t.title,
                style = TextStyle(
                    color = color(if (t.done) p.muted else p.text),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
            if (sub.isNotEmpty()) {
                Text(sub, style = TextStyle(color = color(p.muted), fontSize = 11.sp), maxLines = 1)
            }
        }
        if (t.priority != Priority.NONE && !t.done) {
            Spacer(GlanceModifier.width(8.dp))
            Box(
                GlanceModifier
                    .size(4.dp, 18.dp)
                    .cornerRadius(2.dp)
                    .background(color(WidgetPalettes.priority(t.priority))),
            ) {}
        }
    }
}

// =================================================================== Progress orb

class OrbWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = PlannerStore.get(context)
        store.ensureFresh()
        provideContent {
            val state by store.state.collectAsState()
            OrbContent(state)
        }
    }
}

class OrbWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OrbWidget()
}

@Composable
private fun OrbContent(state: PlannerState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val style = state.settings.widget
    val p = WidgetPalettes.of(style.theme)
    val today = Day.today()
    val progress = PlannerLogic.progress(state, today)
    val next = PlannerLogic.nextUp(state, today, Day.minutesNow())
    val use24 = DateFormat.is24HourFormat(context)
    val bg = WidgetArt.background(context, size.width.value, size.height.value, style, progress.fraction, 30f)
    val ringDp: Dp = (minOf(size.width.value, size.height.value) * 0.58f).coerceIn(48f, 220f).dp

    Box(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bg), ContentScale.FillBounds)
            .appWidgetBackground()
            .clickable(openApp(context)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            GlanceModifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(ringDp), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(WidgetArt.ring(context, ringDp.value, progress.fraction, style.theme, 6f)),
                    contentDescription = "${progress.done} of ${progress.total} done today",
                    modifier = GlanceModifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (progress.total > 0 && progress.left == 0) "✓" else "${progress.left}",
                        style = TextStyle(color = color(p.text), fontSize = (ringDp.value * 0.36f).coerceIn(18f, 56f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    )
                    Text(
                        when {
                            progress.total == 0 -> "planned"
                            progress.left == 0 -> "all done"
                            else -> "left"
                        },
                        style = TextStyle(color = color(p.muted), fontSize = 11.sp, textAlign = TextAlign.Center),
                    )
                }
            }
            if (size.height >= 120.dp) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    next?.let { n -> (n.time?.let { Day.formatTime(it, use24) + " · " } ?: "") + n.title } ?: "Nothing pending",
                    style = TextStyle(color = color(p.text), fontSize = 11.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

// =================================================================== Next up (lock-screen friendly)

class NextUpWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = PlannerStore.get(context)
        store.ensureFresh()
        provideContent {
            val state by store.state.collectAsState()
            NextUpContent(state)
        }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

@Composable
private fun NextUpContent(state: PlannerState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val style = state.settings.widget
    val p = WidgetPalettes.of(style.theme)
    val today = Day.today()
    val progress = PlannerLogic.progress(state, today)
    val next = PlannerLogic.nextUp(state, today, Day.minutesNow())
    val use24 = DateFormat.is24HourFormat(context)
    val radius = (size.height.value / 2f).coerceAtMost(26f)
    val bg = WidgetArt.background(context, size.width.value, size.height.value, style, progress.fraction, radius)
    val tall = size.height >= 64.dp

    Row(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bg), ContentScale.FillBounds)
            .appWidgetBackground()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (next != null) {
            Image(
                ImageProvider(WidgetArt.check(context, false, style.theme, 24f)),
                contentDescription = "Mark ${next.title} done",
                modifier = GlanceModifier
                    .size(32.dp)
                    .padding(4.dp)
                    .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(WidgetKeys.taskId to next.id))),
            )
        } else {
            Image(
                ImageProvider(WidgetArt.check(context, true, style.theme, 24f)),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp).padding(4.dp),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight().clickable(openApp(context))) {
            val label = when {
                next == null && progress.total == 0 -> "NOTHING PLANNED"
                next == null -> "ALL CLEAR"
                next.time != null -> "NEXT · " + Day.formatTime(next.time, use24)
                else -> "NEXT"
            }
            if (tall || next == null) {
                Text(label, style = TextStyle(color = color(p.accent), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            if (next != null) {
                Text(
                    if (tall) next.title else "${label.removePrefix("NEXT · ").removePrefix("NEXT")} ${next.title}".trim(),
                    style = TextStyle(color = color(p.text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
        if (progress.total > 0 && size.width >= 180.dp) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                if (progress.left == 0) "${progress.done}/${progress.total}" else "${progress.left} left",
                style = TextStyle(color = color(p.muted), fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}
