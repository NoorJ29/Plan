package com.borealis.planner.ui.plan

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borealis.planner.R
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.data.Priority
import com.borealis.planner.data.Task
import com.borealis.planner.ui.components.Celebration
import com.borealis.planner.ui.components.ConfirmRepeatDelete
import com.borealis.planner.ui.components.DateDialog
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlowButton
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.SectionHeader
import com.borealis.planner.ui.components.ToastData
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.components.pressable
import com.borealis.planner.ui.components.rememberHaptics
import com.borealis.planner.ui.components.rememberNowMinutes
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.Readout
import com.borealis.planner.ui.theme.Unbounded

@Composable
fun PlanScreen(
    state: PlannerState,
    today: Long,
    day: Long,
    onSelectDay: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    store: PlannerStore,
    showToast: (ToastData) -> Unit,
) {
    LaunchedEffect(day) { store.ensureDay(day) }
    val open = remember(state, day) { PlannerLogic.openOn(state, day) }
    val done = remember(state, day) { PlannerLogic.doneOn(state, day) }
    val missed = remember(state, day, today) { if (day == today) PlannerLogic.missed(state, today) else emptyList() }
    val dayTasks = remember(state, day) { PlannerLogic.dayTasks(state, day) }
    val progress = PlannerLogic.progress(state, day)

    var showDone by rememberSaveable { mutableStateOf(true) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Task?>(null) }
    var celebrate by remember { mutableIntStateOf(0) }
    var lastLeft by remember(day) { mutableIntStateOf(progress.left) }
    LaunchedEffect(progress.left, progress.total, day) {
        if (day == today && progress.total > 0 && progress.left == 0 && lastLeft > 0) celebrate++
        lastLeft = progress.left
    }

    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val now by rememberNowMinutes()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val cb = remember(store, today) {
        CardCallbacks(
            toggle = { t -> store.toggleDone(t.id) },
            subtask = { t, sid -> store.toggleSubtask(t.id, sid) },
            move = { t, to ->
                store.move(t.id, to)
                showToast(ToastData("Moved to ${Day.label(to, today)}", "Undo") { store.replace(t) })
            },
            letGo = { t ->
                store.letGo(t.id)
                showToast(ToastData("Let go of “${t.title}”", "Undo") { store.replace(t) })
            },
            delete = { t ->
                if (t.isRecurring) {
                    confirmDelete = t
                } else {
                    store.delete(t.id, false)
                    showToast(ToastData("Deleted “${t.title}”", "Undo") { store.restore(t) })
                }
            },
            edit = { t -> onEdit(t.id) },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 16.dp, bottom = bottom + 180.dp),
        ) {
            item(key = "header") {
                PlanHeader(state, today, day, progress, dayTasks, missed.size, now, use24)
            }
            item(key = "switcher") {
                DaySwitcher(today, day, onSelectDay, Modifier.padding(top = 20.dp, bottom = 4.dp))
            }
            if (missed.isNotEmpty()) {
                item(key = "missed-header") {
                    SectionHeader("Couldn't finish", count = missed.size, color = Aurora.Ember) {
                        PillButton(
                            "Move all here",
                            {
                                store.moveAllMissed(today)
                                showToast(ToastData("Moved ${missed.size} to today"))
                            },
                            icon = R.drawable.ic_arrow_right,
                            tone = Tone.Warm,
                        )
                    }
                }
                items(missed, key = { it.id }) { t ->
                    TaskCard(
                        t, CardMode.Missed, today, use24, now,
                        expanded = expandedId == t.id,
                        onExpand = { expandedId = if (expandedId == t.id) null else t.id },
                        cb = cb,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (open.isEmpty() && done.isEmpty() && missed.isEmpty()) {
                item(key = "empty") { EmptyDay(day, today, onAdd) }
            }
            if (open.isNotEmpty()) {
                item(key = "open-header") {
                    SectionHeader(
                        when {
                            day == today -> "Left to do"
                            day < today -> "Not done"
                            else -> "Planned"
                        },
                        count = open.size,
                        color = Aurora.Glacier,
                    )
                }
                items(open, key = { it.id }) { t ->
                    TaskCard(
                        t, CardMode.Open, today, use24, now,
                        expanded = expandedId == t.id,
                        onExpand = { expandedId = if (expandedId == t.id) null else t.id },
                        cb = cb,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    SectionHeader("Done", count = done.size, color = Aurora.Borealis) {
                        Text(
                            if (showDone) "Hide" else "Show",
                            style = MaterialTheme.typography.labelLarge,
                            color = Aurora.Mist,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showDone = !showDone }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (showDone) {
                    items(done, key = { it.id }) { t ->
                        TaskCard(
                            t, CardMode.Done, today, use24, now,
                            expanded = expandedId == t.id,
                            onExpand = { expandedId = if (expandedId == t.id) null else t.id },
                            cb = cb,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        AddOrb(onAdd, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = bottom + 92.dp))
        Celebration(celebrate, Modifier.fillMaxSize())
    }

    confirmDelete?.let { t ->
        ConfirmRepeatDelete(
            onJustThis = {
                store.delete(t.id, false)
                confirmDelete = null
                showToast(ToastData("Removed this day only"))
            },
            onWhole = {
                store.delete(t.id, true)
                confirmDelete = null
                showToast(ToastData("“${t.title}” won't repeat any more"))
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

// ================================================================== header

@OptIn(ExperimentalTextApi::class)
@Composable
private fun PlanHeader(
    state: PlannerState,
    today: Long,
    day: Long,
    progress: PlannerLogic.Progress,
    dayTasks: List<Task>,
    missedCount: Int,
    now: Int,
    use24: Boolean,
) {
    val next = if (day == today) PlannerLogic.nextUp(state, today, now) else null
    val label = Day.label(day, today)
    val phrase = if (day == today || day == today + 1 || day == today - 1) label.lowercase() else label
    val firstTimed = dayTasks.filter { !it.done && it.time != null }.minByOrNull { it.time ?: 0 }

    val headline: String
    val sub: String
    when {
        day == today -> when {
            progress.total == 0 -> {
                headline = "Clear sky"
                sub = if (missedCount > 0) "Nothing planned yet. $missedCount left over from before — see below."
                else "Nothing planned yet. Add what matters today."
            }
            progress.left == 0 -> {
                headline = "All clear"
                sub = "All ${progress.total} done. The sky is yours."
            }
            else -> {
                headline = "${progress.left} left"
                sub = buildString {
                    append("${progress.done} of ${progress.total} done")
                    if (next != null) {
                        append(" · Next: ")
                        append(next.title)
                        next.time?.let { append(" at ").append(Day.formatTime(it, use24)) }
                    }
                }
            }
        }
        day > today -> if (progress.total == 0) {
            headline = "Open day"
            sub = "Nothing planned for $phrase yet."
        } else {
            headline = "${progress.total} planned"
            sub = buildString {
                append("for $phrase")
                firstTimed?.time?.let { append(" · first at ").append(Day.formatTime(it, use24)) }
            }
        }
        else -> if (progress.total == 0) {
            headline = "Quiet day"
            sub = "Nothing was planned for $phrase."
        } else {
            headline = "${progress.done}/${progress.total} done"
            sub = if (progress.left == 0) "Everything got done." else "${progress.left} didn't get done."
        }
    }

    val fontSize = when {
        headline.length <= 6 -> 46.sp
        headline.length <= 9 -> 38.sp
        else -> 31.sp
    }

    Column(Modifier.fillMaxWidth()) {
        Eyebrow(Day.eyebrow(day), color = if (day == today) Aurora.Borealis else Aurora.Mist)
        Spacer(Modifier.height(10.dp))
        Text(
            headline,
            style = TextStyle(
                fontFamily = Unbounded,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                letterSpacing = (-1).sp,
                brush = Brush.linearGradient(listOf(Aurora.Frost, Aurora.Glacier, Aurora.Borealis)),
            ),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            sub,
            style = MaterialTheme.typography.bodyLarge,
            color = Aurora.Mist,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (day == today || dayTasks.any { it.time != null }) {
            DayHorizon(dayTasks, isToday = day == today, isPast = day < today, now = now, use24 = use24, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

/**
 * The day as a horizon line: every timed task is a point of light on it,
 * green when done, amber when its time passed undone, white when still ahead.
 */
@Composable
private fun DayHorizon(tasks: List<Task>, isToday: Boolean, isPast: Boolean, now: Int, use24: Boolean, modifier: Modifier = Modifier) {
    val timed = tasks.filter { it.time != null }
    val startH = minOf(6, (timed.minOfOrNull { it.time ?: 0 } ?: 6 * 60) / 60)
    val endH = maxOf(23, ((timed.maxOfOrNull { it.time ?: 0 } ?: 0) / 60) + 1).coerceAtMost(24)
    val span = ((endH - startH) * 60).coerceAtLeast(60).toFloat()
    val pulse by rememberInfiniteTransition(label = "now").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "nowPulse",
    )

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp),
        ) {
            val y = size.height * 0.5f
            fun xOf(min: Int) = ((min - startH * 60) / span).coerceIn(0f, 1f) * size.width
            drawLine(
                Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))),
                Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round,
            )
            for (h in startH..endH) {
                if (h % 3 != 0) continue
                val x = xOf(h * 60)
                drawLine(Color.White.copy(alpha = 0.18f), Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 1.dp.toPx())
            }
            if (isToday) {
                val nx = xOf(now)
                drawLine(
                    Brush.horizontalGradient(listOf(Aurora.Borealis.copy(alpha = 0.0f), Aurora.Borealis.copy(alpha = 0.7f)), startX = 0f, endX = nx),
                    Offset(0f, y), Offset(nx, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
            }
            for (t in timed) {
                val tm = t.time ?: continue
                val x = xOf(tm)
                val late = !t.done && (isPast || (isToday && tm < now))
                val c = when {
                    t.done -> Aurora.Borealis
                    late -> Aurora.Ember
                    t.priority == Priority.HIGH -> Aurora.Flare
                    else -> Aurora.Frost
                }
                val r = (3.5f + t.priority.weight * 0.7f).dp.toPx()
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), center = Offset(x, y), radius = r * 3.2f), radius = r * 3.2f, center = Offset(x, y))
                drawCircle(c, radius = r, center = Offset(x, y))
            }
            if (isToday) {
                val nx = xOf(now)
                drawLine(
                    Brush.verticalGradient(listOf(Color.Transparent, Aurora.Borealis, Color.Transparent)),
                    Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 2.dp.toPx(),
                )
                drawCircle(Aurora.Borealis.copy(alpha = 0.35f * pulse), radius = 9.dp.toPx(), center = Offset(nx, y))
                drawCircle(Aurora.Borealis, radius = 3.dp.toPx(), center = Offset(nx, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Day.formatTime(startH * 60, use24), style = Readout, color = Aurora.Haze)
            Spacer(Modifier.weight(1f))
            if (isToday) Text("now ${Day.formatTime(now, use24)}", style = Readout, color = Aurora.Borealis)
            Spacer(Modifier.weight(1f))
            Text(Day.formatTime((endH * 60).coerceAtMost(23 * 60 + 59), use24), style = Readout, color = Aurora.Haze)
        }
    }
}

// ================================================================== day switcher

@Composable
private fun DaySwitcher(today: Long, day: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    val index = when (day) {
        today -> 0
        today + 1 -> 1
        else -> 2
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(RoundedCornerShape(18.dp), veil = 0.45f)
            .padding(4.dp),
    ) {
        val seg = maxWidth / 3
        val x by animateDpAsState(seg * index, spring(dampingRatio = 0.72f, stiffness = 480f), label = "seg")
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .offset(x = x)
                .width(seg)
                .fillMaxHeight()
                .clip(shape)
                .background(Brush.linearGradient(listOf(Aurora.Borealis.copy(alpha = 0.26f), Aurora.Glacier.copy(alpha = 0.14f))))
                .border(1.dp, Aurora.Borealis.copy(alpha = 0.5f), shape),
        )
        Row(Modifier.fillMaxSize()) {
            Segment("Today", R.drawable.ic_sun, index == 0) { onSelect(today) }
            Segment("Tomorrow", R.drawable.ic_moon, index == 1) { onSelect(today + 1) }
            Segment(if (index == 2) Day.short(day) else "Pick day", R.drawable.ic_calendar, index == 2) { picking = true }
        }
    }
    if (picking) {
        DateDialog(initial = day, onDismiss = { picking = false }, onPick = onSelect)
    }
}

@Composable
private fun RowScope.Segment(label: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val color = if (selected) Aurora.Frost else Aurora.Mist
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.tick()
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(painterResource(icon), null, tint = if (selected) Aurora.Borealis else Aurora.Haze, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

// ================================================================== empty + add

@Composable
private fun EmptyDay(day: Long, today: Long, onAdd: () -> Unit) {
    val label = Day.label(day, today)
    val phrase = if (day in (today - 1)..(today + 1)) label.lowercase() else label
    Column(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), tint = Aurora.Ion, veil = 0.4f)
            .padding(24.dp),
    ) {
        Icon(painterResource(R.drawable.ic_sparkle), null, tint = Aurora.Ion, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (day >= today) "Nothing planned for $phrase" else "Nothing was planned for $phrase",
            style = MaterialTheme.typography.titleLarge,
            color = Aurora.Frost,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Add what you want to get done. Try typing “Gym 6pm !!” — the time and priority fill themselves in.",
            style = MaterialTheme.typography.bodyMedium,
            color = Aurora.Mist,
        )
        if (day >= today) {
            Spacer(Modifier.height(18.dp))
            GlowButton("Add a task", onAdd, icon = R.drawable.ic_add, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddOrb(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val glow by rememberInfiniteTransition(label = "add").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "addGlow",
    )
    Box(modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Aurora.Borealis.copy(alpha = 0.30f + 0.18f * glow), Color.Transparent)),
                radius = size.minDimension / 2f,
            )
        }
        Box(
            Modifier
                .size(62.dp)
                .pressable {
                    haptics.confirm()
                    onClick()
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Aurora.Borealis, Aurora.Glacier, Aurora.Ion))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_add), "Add task", tint = Aurora.Night, modifier = Modifier.size(28.dp))
        }
    }
}
