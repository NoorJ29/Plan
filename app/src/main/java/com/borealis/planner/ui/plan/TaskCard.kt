package com.borealis.planner.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import com.borealis.planner.R
import com.borealis.planner.data.AlertType
import com.borealis.planner.data.Day
import com.borealis.planner.data.Priority
import com.borealis.planner.data.Task
import com.borealis.planner.ui.components.CheckOrb
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.components.rememberHaptics
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.Readout
import com.borealis.planner.ui.theme.priorityColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CardMode { Open, Done, Missed }

class CardCallbacks(
    val toggle: (Task) -> Unit,
    val subtask: (Task, String) -> Unit,
    val move: (Task, Long) -> Unit,
    val letGo: (Task) -> Unit,
    val delete: (Task) -> Unit,
    val edit: (Task) -> Unit,
)

@Composable
fun TaskCard(
    task: Task,
    mode: CardMode,
    today: Long,
    use24: Boolean,
    nowMinutes: Int,
    expanded: Boolean,
    onExpand: () -> Unit,
    cb: CardCallbacks,
    modifier: Modifier = Modifier,
    category: String? = null,
    categoryTint: Color? = null,
    indent: Dp = 0.dp,
) {
    val forward = if (task.day < today) today else task.day + 1
    val forwardLabel = when (forward) {
        today -> "Today"
        today + 1 -> "Tomorrow"
        else -> Day.short(forward)
    }
    val right = when (mode) {
        CardMode.Done -> SwipeAction("Undo", R.drawable.ic_undo, Aurora.Mist) { cb.toggle(task) }
        else -> SwipeAction("Done", R.drawable.ic_check, Aurora.Borealis) { cb.toggle(task) }
    }
    val left = when (mode) {
        CardMode.Done -> SwipeAction("Delete", R.drawable.ic_trash, Aurora.Flare) { cb.delete(task) }
        CardMode.Missed -> SwipeAction("Today", R.drawable.ic_sun, Aurora.Glacier) { cb.move(task, today) }
        CardMode.Open -> SwipeAction(forwardLabel, R.drawable.ic_moon, Aurora.Glacier) { cb.move(task, forward) }
    }

    SwipeBox(left = left, right = right, modifier = modifier.padding(start = indent, top = 5.dp, bottom = 5.dp)) {
        CardBody(task, mode, today, use24, nowMinutes, expanded, onExpand, cb, forward, forwardLabel, category, categoryTint)
    }
}

@Composable
private fun CardBody(
    task: Task,
    mode: CardMode,
    today: Long,
    use24: Boolean,
    nowMinutes: Int,
    expanded: Boolean,
    onExpand: () -> Unit,
    cb: CardCallbacks,
    forward: Long,
    forwardLabel: String,
    category: String?,
    categoryTint: Color?,
) {
    val tint = when {
        mode == CardMode.Missed -> Aurora.Ember
        task.done -> Aurora.Borealis
        task.priority != Priority.NONE -> priorityColor(task.priority)
        else -> Color.White
    }
    val overdue = mode == CardMode.Open && !task.done && task.time != null &&
        (task.day < today || (task.day == today && task.time < nowMinutes))
    val shape = RoundedCornerShape(22.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape, tint = tint, fillAlpha = if (task.done) 0.03f else 0.06f, strokeAlpha = if (task.done) 0.09f else 0.15f, veil = 0.44f)
            .clickable(onClick = onExpand)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckOrb(
                checked = task.done,
                onToggle = { cb.toggle(task) },
                colors = if (mode == CardMode.Missed) listOf(Aurora.Ember, Aurora.Borealis) else listOf(Aurora.Borealis, Aurora.Glacier),
                ring = if (mode == CardMode.Missed) Aurora.Ember else Aurora.Mist,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
            ) {
                if (mode == CardMode.Missed) {
                    Eyebrow("From " + Day.label(task.day, today), color = Aurora.Ember)
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.done) Aurora.Mist else Aurora.Frost,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = if (expanded) 5 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(task, use24, overdue, mode, category, categoryTint)
            }
            if (task.priority != Priority.NONE && !task.done) {
                Spacer(Modifier.width(10.dp))
                PriorityGlyph(task.priority)
            }
        }
        if (expanded) {
            Expanded(task, mode, today, cb, forward, forwardLabel)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetaRow(task: Task, use24: Boolean, overdue: Boolean, mode: CardMode, category: String?, categoryTint: Color?) {
    val items = ArrayList<@Composable () -> Unit>()
    if (category != null) {
        val c = categoryTint ?: Aurora.Mist
        items += {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(c, 7.dp)
                Spacer(Modifier.width(5.dp))
                Text(category, style = Readout, color = c.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            }
        }
    }
    task.time?.let { time ->
        val label = Day.formatTime(time, use24)
        items += { MetaBit(R.drawable.ic_clock, label, if (overdue) Aurora.Ember else Aurora.Mist) }
    }
    if (task.alert != AlertType.NONE && !task.done) {
        items += {
            Icon(
                painterResource(if (task.alert == AlertType.ALARM) R.drawable.ic_alarm else R.drawable.ic_bell),
                contentDescription = task.alert.label,
                tint = Aurora.Glacier,
                modifier = Modifier.size(14.dp),
            )
        }
    }
    if (task.isRecurring) {
        items += { Icon(painterResource(R.drawable.ic_repeat), "Repeats", tint = Aurora.Ion, modifier = Modifier.size(14.dp)) }
    }
    if (task.subtasks.isNotEmpty()) {
        items += {
            MetaBit(
                R.drawable.ic_subtask,
                "${task.subtasksDone}/${task.subtasks.size}",
                if (task.subtasksDone == task.subtasks.size) Aurora.Borealis else Aurora.Mist,
            )
        }
    }
    if (task.notes.isNotBlank()) {
        items += { Icon(painterResource(R.drawable.ic_notes), "Has notes", tint = Aurora.Haze, modifier = Modifier.size(14.dp)) }
    }
    if (task.slips.isNotEmpty() && mode != CardMode.Missed && !task.done) {
        items += {
            Text(
                if (task.slips.size == 1) "carried over" else "carried ×${task.slips.size}",
                style = Readout,
                color = Aurora.Ember,
            )
        }
    }
    if (items.isEmpty()) return
    Spacer(Modifier.height(5.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { Box(Modifier.align(Alignment.CenterVertically)) { it() } }
    }
}

@Composable
private fun MetaBit(@DrawableRes icon: Int, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = Readout, color = color)
    }
}

/** Three ascending bars, lit by priority — readable even without colour. */
@Composable
private fun PriorityGlyph(p: Priority) {
    val color = priorityColor(p)
    Canvas(Modifier.size(width = 14.dp, height = 14.dp)) {
        val bw = size.width / 5f
        for (i in 0..2) {
            val h = size.height * (0.4f + 0.3f * i)
            val lit = i < p.weight
            drawRoundRect(
                color = if (lit) color else Color.White.copy(alpha = 0.14f),
                topLeft = Offset(i * bw * 2f, size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}

@Composable
private fun Expanded(task: Task, mode: CardMode, today: Long, cb: CardCallbacks, forward: Long, forwardLabel: String) {
    Column(Modifier.padding(start = 10.dp, end = 14.dp, bottom = 14.dp)) {
        task.subtasks.forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckOrb(
                    checked = st.done,
                    onToggle = { cb.subtask(task, st.id) },
                    size = 18.dp,
                )
                Text(
                    st.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (st.done) Aurora.Mist else Aurora.Frost,
                    textDecoration = if (st.done) TextDecoration.LineThrough else null,
                )
            }
        }
        if (task.notes.isNotBlank()) {
            Text(
                task.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = Aurora.Mist,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (mode) {
                CardMode.Missed -> {
                    PillButton("Do today", { cb.move(task, today) }, icon = R.drawable.ic_sun, tone = Tone.Accent)
                    PillButton("Tomorrow", { cb.move(task, today + 1) }, icon = R.drawable.ic_moon)
                    PillButton("Let go", { cb.letGo(task) }, icon = R.drawable.ic_close, tone = Tone.Warm)
                    PillButton("Edit", { cb.edit(task) }, icon = R.drawable.ic_edit)
                }
                CardMode.Open -> {
                    PillButton("Edit", { cb.edit(task) }, icon = R.drawable.ic_edit, tone = Tone.Accent)
                    PillButton(forwardLabel, { cb.move(task, forward) }, icon = R.drawable.ic_moon)
                    if (task.day > today) PillButton("Do today", { cb.move(task, today) }, icon = R.drawable.ic_sun)
                    PillButton("Delete", { cb.delete(task) }, icon = R.drawable.ic_trash, tone = Tone.Danger)
                }
                CardMode.Done -> {
                    PillButton("Not done", { cb.toggle(task) }, icon = R.drawable.ic_undo)
                    PillButton("Edit", { cb.edit(task) }, icon = R.drawable.ic_edit)
                    PillButton("Delete", { cb.delete(task) }, icon = R.drawable.ic_trash, tone = Tone.Danger)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ swipe

class SwipeAction(
    val label: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val run: () -> Unit,
)

/** Horizontal swipe with a reveal behind the card; past 30% of the width the action fires. */
@Composable
fun SwipeBox(
    left: SwipeAction?,
    right: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dragX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val threshold = 0.3f

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        val frac = dragX.value / width
        val action = when {
            frac > 0.01f -> right
            frac < -0.01f -> left
            else -> null
        }
        if (action != null) {
            val strength = (abs(frac) / threshold).coerceIn(0f, 1f)
            val fromStart = frac > 0
            Row(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (fromStart) listOf(action.color.copy(alpha = 0.32f * strength), Color.Transparent)
                            else listOf(Color.Transparent, action.color.copy(alpha = 0.32f * strength)),
                        ),
                    )
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    painterResource(action.icon),
                    null,
                    tint = action.color.copy(alpha = 0.35f + 0.65f * strength),
                    modifier = Modifier.size(20.dp).scale(0.8f + 0.35f * strength),
                )
                Spacer(Modifier.width(8.dp))
                Text(action.label, style = MaterialTheme.typography.labelLarge, color = action.color.copy(alpha = 0.35f + 0.65f * strength))
            }
        }
        Box(
            Modifier
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val min = if (left != null) -width * 0.5f else 0f
                        val max = if (right != null) width * 0.5f else 0f
                        val next = (dragX.value + delta).coerceIn(min, max)
                        scope.launch { dragX.snapTo(next) }
                        val nowArmed = abs(next / width) >= threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (nowArmed) haptics.tick()
                        }
                    },
                    onDragStopped = {
                        val f = dragX.value / width
                        armed = false
                        when {
                            f >= threshold -> {
                                dragX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
                                right?.run?.invoke()
                            }
                            f <= -threshold -> {
                                left?.run?.invoke()
                                dragX.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
                            }
                            else -> dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
