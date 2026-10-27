package com.borealis.planner.ui.recap

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borealis.planner.R
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.data.Task
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.SectionHeader
import com.borealis.planner.ui.components.ToastData
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.Readout
import com.borealis.planner.ui.theme.Unbounded

@Composable
fun RecapScreen(
    state: PlannerState,
    today: Long,
    store: PlannerStore,
    onOpenDay: (Long) -> Unit,
    showToast: (ToastData) -> Unit,
) {
    val days = remember(today) { (1..14).map { today - it } }
    val recaps = remember(state, today) { days.map { PlannerLogic.recap(state, it, today) } }
    val week = recaps.take(7)
    val weekDone = week.sumOf { it.done.size }
    val weekMissed = week.sumOf { it.missed.size }
    val weekPlanned = week.sumOf { it.planned }
    val stillOpen = remember(state, today) { PlannerLogic.missed(state, today) }
    val history = recaps.filter { it.planned > 0 }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 16.dp, bottom = bottom + 130.dp),
    ) {
        item(key = "title") {
            Column {
                Eyebrow("Last 7 days", color = Aurora.Glacier)
                Spacer(Modifier.height(8.dp))
                Text("Recap", style = MaterialTheme.typography.displayMedium, color = Aurora.Frost)
                Spacer(Modifier.height(6.dp))
                Text(
                    "What got done, and what slipped — so tomorrow's plan is honest.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Aurora.Mist,
                )
            }
        }
        item(key = "week") {
            WeekCard(week.reversed(), weekDone, weekMissed, weekPlanned, Modifier.padding(top = 20.dp))
        }
        if (stillOpen.isNotEmpty()) {
            item(key = "open-h") { SectionHeader("Still waiting on you", count = stillOpen.size, color = Aurora.Ember) }
            items(stillOpen, key = { "open-" + it.id }) { t ->
                StillOpenRow(
                    t, today,
                    onToday = {
                        store.move(t.id, today)
                        showToast(ToastData("Moved to today", "Undo") { store.replace(t) })
                    },
                    onTomorrow = {
                        store.move(t.id, today + 1)
                        showToast(ToastData("Moved to tomorrow", "Undo") { store.replace(t) })
                    },
                    onLetGo = {
                        store.letGo(t.id)
                        showToast(ToastData("Let go of “${t.title}”", "Undo") { store.replace(t) })
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "days-h") { SectionHeader("Day by day", color = Aurora.Glacier) }
        if (history.isEmpty()) {
            item(key = "none") {
                Text(
                    "Your recap fills in as the days go by. Plan something today and check back tomorrow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Mist,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(history, key = { "day-" + it.day }) { r ->
            DayRow(
                r, today,
                expanded = expanded == r.day,
                onClick = { expanded = if (expanded == r.day) null else r.day },
                onOpen = { onOpenDay(r.day) },
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun WeekCard(
    days: List<PlannerLogic.DayRecap>,
    done: Int,
    missed: Int,
    planned: Int,
    modifier: Modifier = Modifier,
) {
    val pct = if (planned == 0) 0 else (done * 100f / planned).toInt()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val maxPlanned = (days.maxOfOrNull { it.planned } ?: 0).coerceAtLeast(1)

    Column(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), tint = Aurora.Glacier, veil = 0.45f)
            .padding(22.dp),
    ) {
        if (planned == 0) {
            Text("No history yet", style = MaterialTheme.typography.headlineSmall, color = Aurora.Frost)
            Spacer(Modifier.height(4.dp))
            Text(
                "Each finished day adds a bar here: green for done, amber for what slipped.",
                style = MaterialTheme.typography.bodySmall,
                color = Aurora.Mist,
            )
        } else Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$pct%",
                style = TextStyle(
                    fontFamily = Unbounded,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 44.sp,
                    letterSpacing = (-1).sp,
                    brush = Brush.linearGradient(listOf(Aurora.Borealis, Aurora.Glacier, Aurora.Ion)),
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("done", style = MaterialTheme.typography.titleSmall, color = Aurora.Frost)
                Text("of what you planned", style = MaterialTheme.typography.bodySmall, color = Aurora.Mist)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("$done", "done", Aurora.Borealis)
            Stat("$missed", "couldn't finish", Aurora.Ember)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.width(16.dp).weight(1f)) {
                        val full = size.height
                        val w = size.width
                        val r = CornerRadius(w / 2f, w / 2f)
                        drawRoundRect(Color.White.copy(alpha = 0.07f), size = Size(w, full), cornerRadius = r)
                        if (d.planned > 0) {
                            val total = full * (d.planned.toFloat() / maxPlanned) * grow.value
                            val doneH = total * (d.done.size.toFloat() / d.planned)
                            val missH = total - doneH
                            if (missH > 0.5f) {
                                drawRoundRect(
                                    Aurora.Ember.copy(alpha = 0.75f),
                                    topLeft = Offset(0f, full - total),
                                    size = Size(w, missH),
                                    cornerRadius = r,
                                )
                            }
                            if (doneH > 0.5f) {
                                drawRoundRect(
                                    Brush.verticalGradient(listOf(Aurora.Glacier, Aurora.Borealis), startY = full - doneH, endY = full),
                                    topLeft = Offset(0f, full - doneH),
                                    size = Size(w, doneH),
                                    cornerRadius = r,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(Day.weekdayLetter(Day.dayOfWeek(d.day)), style = Readout, color = Aurora.Mist)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).glass(RoundedCornerShape(4.dp), tint = color, fillAlpha = 0.9f, strokeAlpha = 0f, veil = 0f))
        Spacer(Modifier.width(6.dp))
        Text(value, style = Readout.copy(fontSize = 14.sp), color = Aurora.Frost)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Aurora.Mist)
    }
}

@Composable
private fun StillOpenRow(
    t: Task,
    today: Long,
    onToday: () -> Unit,
    onTomorrow: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), tint = Aurora.Ember, veil = 0.44f)
            .padding(16.dp),
    ) {
        Eyebrow("Planned for " + Day.label(t.day, today), color = Aurora.Ember)
        Spacer(Modifier.height(4.dp))
        Text(t.title, style = MaterialTheme.typography.titleMedium, color = Aurora.Frost, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Do today", onToday, icon = R.drawable.ic_sun, tone = Tone.Accent)
            PillButton("Tomorrow", onTomorrow, icon = R.drawable.ic_moon)
            PillButton("Let go", onLetGo, icon = R.drawable.ic_close, tone = Tone.Warm)
        }
    }
}

@Composable
private fun DayRow(
    r: PlannerLogic.DayRecap,
    today: Long,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), veil = 0.42f)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Day.label(r.day, today), style = MaterialTheme.typography.titleMedium, color = Aurora.Frost)
                Text(
                    buildString {
                        append("${r.done.size} of ${r.planned} done")
                        if (r.missed.isNotEmpty()) append(" · ${r.missed.size} couldn't finish")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Aurora.Mist,
                )
            }
            Canvas(Modifier.size(width = 64.dp, height = 8.dp)) {
                val w = size.width
                val h = size.height
                val cr = CornerRadius(h / 2f, h / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.08f), size = Size(w, h), cornerRadius = cr)
                val f = r.fraction
                if (f > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Aurora.Borealis, Aurora.Glacier)),
                        size = Size(w * f, h),
                        cornerRadius = cr,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right),
                null,
                tint = Aurora.Haze,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            r.missed.forEach { t -> RecapLine(t.title, Aurora.Ember, if (t.isRecurring) "repeat" else if (r.day in t.slips) "moved on" else if (t.letGo) "let go" else null) }
            r.done.forEach { t -> RecapLine(t.title, Aurora.Borealis, null, done = true) }
            Spacer(Modifier.height(8.dp))
            PillButton("Open this day", onOpen, icon = R.drawable.ic_arrow_right)
        }
    }
}

@Composable
private fun RecapLine(title: String, color: Color, tag: String?, done: Boolean = false) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(if (done) R.drawable.ic_check else R.drawable.ic_close),
            null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) Aurora.Mist else Aurora.Frost,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tag != null) {
            Text(tag, style = Readout, color = color.copy(alpha = 0.8f))
        }
    }
}
