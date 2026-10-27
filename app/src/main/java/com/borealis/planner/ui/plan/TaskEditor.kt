package com.borealis.planner.ui.plan

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borealis.planner.R
import com.borealis.planner.alarm.Notifications
import com.borealis.planner.data.AlertType
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.data.Priority
import com.borealis.planner.data.QuickParse
import com.borealis.planner.data.Repeat
import com.borealis.planner.data.Subtask
import com.borealis.planner.data.TaskDraft
import com.borealis.planner.ui.components.CheckOrb
import com.borealis.planner.ui.components.ConfirmRepeatDelete
import com.borealis.planner.ui.components.DateDialog
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlassChip
import com.borealis.planner.ui.components.GlowButton
import com.borealis.planner.ui.components.IconOrb
import com.borealis.planner.ui.components.TimeDialog
import com.borealis.planner.ui.components.ToastData
import com.borealis.planner.ui.components.rememberHaptics
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.Onest
import com.borealis.planner.ui.theme.priorityColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface EditorTarget {
    data class New(val day: Long) : EditorTarget
    data class Existing(val id: String) : EditorTarget
}

private enum class Picker { DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    target: EditorTarget,
    state: PlannerState,
    today: Long,
    store: PlannerStore,
    onDismiss: () -> Unit,
    showToast: (ToastData) -> Unit,
) {
    val existing = (target as? EditorTarget.Existing)?.let { e -> state.tasks.firstOrNull { it.id == e.id } }
    if (target is EditorTarget.Existing && existing == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val series = existing?.seriesId?.let { sid -> state.series.firstOrNull { it.id == sid } }
    val isNew = existing == null
    val baseDay = (target as? EditorTarget.New)?.day ?: existing?.day ?: today
    val initial = remember(target) {
        existing?.let { PlannerLogic.draftOf(it, series) } ?: TaskDraft(title = "", day = baseDay)
    }

    var titleField by remember(target) {
        mutableStateOf(TextFieldValue(initial.title, TextRange(initial.title.length)))
    }
    val title = titleField.text
    var notes by remember(target) { mutableStateOf(initial.notes) }
    var dayOverride by remember(target) { mutableStateOf(if (isNew) null else initial.day) }
    var timeSet by remember(target) { mutableStateOf(!isNew) }
    var time by remember(target) { mutableStateOf(initial.time) }
    var prioritySet by remember(target) { mutableStateOf(!isNew) }
    var priority by remember(target) { mutableStateOf(initial.priority) }
    var alertSet by remember(target) { mutableStateOf(!isNew && initial.time != null) }
    var alert by remember(target) { mutableStateOf(initial.alert) }
    var lead by remember(target) { mutableIntStateOf(initial.alertLead) }
    var repeat by remember(target) { mutableStateOf(initial.repeat) }
    var weekdays by remember(target) { mutableStateOf(initial.weekdays) }
    val steps = remember(target) { mutableStateListOf<Subtask>().apply { addAll(initial.subtasks) } }
    var newStep by remember(target) { mutableStateOf("") }
    var allUpcoming by remember(target) { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saveRequested by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val haptics = rememberHaptics()

    val parsed = remember(title, isNew) { if (isNew) QuickParse.parse(title) else QuickParse.Result(title) }
    val effDay = dayOverride ?: parsed.dayOffset?.let { today + it } ?: baseDay
    val effTime = if (timeSet) time else parsed.time
    val effPriority = if (prioritySet) priority else parsed.priority ?: Priority.NONE
    val effAlert = when {
        effTime == null -> AlertType.NONE
        alertSet -> alert
        else -> AlertType.REMINDER
    }
    val cleanTitle = if (isNew) parsed.title else title.trim()
    val canSave = cleanTitle.isNotBlank()
    val repeatChanged = series != null && repeat != series.repeat

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(target) {
        if (isNew) {
            delay(280)
            runCatching { focus.requestFocus() }
        }
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun save() {
        if (!canSave) return
        val pendingStep = newStep.trim()
        val draft = TaskDraft(
            title = cleanTitle,
            notes = notes,
            day = effDay,
            time = effTime,
            priority = effPriority,
            alert = effAlert,
            alertLead = if (effAlert == AlertType.NONE) 0 else lead,
            subtasks = steps.toList() + if (pendingStep.isNotEmpty()) listOf(Subtask(title = pendingStep)) else emptyList(),
            repeat = repeat,
            weekdays = weekdays,
        )
        if (existing == null) {
            store.add(draft)
            val whenText = Day.label(effDay, today)
            val alertText = when {
                effTime == null || effAlert == AlertType.NONE -> ""
                effAlert == AlertType.ALARM -> " · alarm at ${Day.formatTime(effTime, use24)}"
                else -> " · reminder at ${Day.formatTime(effTime, use24)}"
            }
            showToast(ToastData("Added to $whenText$alertText"))
        } else {
            store.edit(existing.id, draft, allUpcoming)
            showToast(ToastData("Saved"))
        }
        if (effAlert != AlertType.NONE && !Notifications.canPost(context)) {
            showToast(ToastData("Notifications are off, so alerts can't ring. Turn them on in Settings."))
        }
        close()
    }

    LaunchedEffect(saveRequested) {
        if (saveRequested) {
            saveRequested = false
            save()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xF50B1222),
        contentColor = Aurora.Frost,
        scrimColor = Color(0xA6000000),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    when {
                        isNew -> "New task"
                        existing?.isRecurring == true -> "Edit repeating task"
                        else -> "Edit task"
                    },
                    color = Aurora.Borealis,
                )
                Spacer(Modifier.weight(1f))
                if (existing != null) {
                    IconOrb(R.drawable.ic_trash, "Delete task", onClick = {
                        if (existing.isRecurring) {
                            confirmDelete = true
                        } else {
                            store.delete(existing.id, false)
                            showToast(ToastData("Deleted “${existing.title}”", "Undo") { store.restore(existing) })
                            close()
                        }
                    }, tint = Aurora.Flare, size = 38.dp)
                }
            }
            Spacer(Modifier.height(12.dp))

            val titleStyle = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, color = Aurora.Frost)
            BasicTextField(
                value = titleField,
                onValueChange = { v ->
                    // Enter on the keyboard saves instead of adding a new line.
                    if ('\n' in v.text) {
                        val t = v.text.replace("\n", " ").trim()
                        titleField = TextFieldValue(t, TextRange(t.length))
                        saveRequested = true
                    } else {
                        titleField = v
                    }
                },
                maxLines = 4,
                textStyle = titleStyle,
                cursorBrush = SolidColor(Aurora.Borealis),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) Text("What needs doing?", style = titleStyle.copy(color = Aurora.Haze))
                        inner()
                    }
                },
            )
            if (isNew && parsed.found) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_sparkle), null, tint = Aurora.Ion, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        buildList {
                            parsed.dayOffset?.let { add(Day.label(today + it, today)) }
                            parsed.time?.let { add(Day.formatTime(it, use24)) }
                            parsed.priority?.let { add("${it.label.lowercase()} priority") }
                        }.joinToString(" · ", prefix = "Got it: "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Aurora.Ion,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val notesStyle = MaterialTheme.typography.bodyLarge.copy(color = Aurora.Mist)
            BasicTextField(
                value = notes,
                onValueChange = { notes = it },
                textStyle = notesStyle,
                cursorBrush = SolidColor(Aurora.Borealis),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (notes.isEmpty()) Text("Add notes", style = notesStyle.copy(color = Aurora.Haze))
                        inner()
                    }
                },
            )
            Hairline()

            Label("When")
            ChipRow {
                GlassChip("Today", effDay == today, { dayOverride = today }, icon = R.drawable.ic_sun)
                GlassChip("Tomorrow", effDay == today + 1, { dayOverride = today + 1 }, icon = R.drawable.ic_moon)
                val other = effDay != today && effDay != today + 1
                GlassChip(if (other) Day.label(effDay, today) else "Pick date", other, { picker = Picker.DATE }, icon = R.drawable.ic_calendar)
            }
            Spacer(Modifier.height(8.dp))
            ChipRow {
                GlassChip(
                    effTime?.let { Day.formatTime(it, use24) } ?: "Add time",
                    effTime != null,
                    { picker = Picker.TIME },
                    icon = R.drawable.ic_clock,
                    accent = Aurora.Glacier,
                )
                if (effTime != null) {
                    GlassChip("Anytime", false, { timeSet = true; time = null }, icon = R.drawable.ic_close)
                } else {
                    listOf(9 * 60, 12 * 60, 18 * 60, 21 * 60).forEach { m ->
                        GlassChip(Day.formatTime(m, use24), false, { timeSet = true; time = m })
                    }
                }
            }

            Label("Alert")
            if (effTime == null) {
                Text(
                    "Set a time to get a reminder or an alarm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aurora.Haze,
                )
            } else {
                ChipRow {
                    GlassChip("Off", effAlert == AlertType.NONE, { alertSet = true; alert = AlertType.NONE })
                    GlassChip("Reminder", effAlert == AlertType.REMINDER, { alertSet = true; alert = AlertType.REMINDER }, icon = R.drawable.ic_bell, accent = Aurora.Glacier)
                    GlassChip("Alarm", effAlert == AlertType.ALARM, { alertSet = true; alert = AlertType.ALARM }, icon = R.drawable.ic_alarm, accent = Aurora.Ion)
                }
                if (effAlert != AlertType.NONE) {
                    Spacer(Modifier.height(8.dp))
                    ChipRow {
                        listOf(0, 5, 15, 30, 60).forEach { m ->
                            GlassChip(
                                when {
                                    m == 0 -> "On time"
                                    m < 60 -> "$m min early"
                                    else -> "1 hr early"
                                },
                                lead == m,
                                { lead = m },
                                accent = Aurora.Glacier,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (effAlert == AlertType.ALARM) "Rings like a clock alarm, even on the lock screen, until you stop or snooze it."
                        else "A single notification with Done and Snooze buttons.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aurora.Haze,
                    )
                }
            }

            Label("Priority")
            ChipRow {
                Priority.entries.forEach { p ->
                    GlassChip(
                        p.label,
                        effPriority == p,
                        { prioritySet = true; priority = p },
                        accent = if (p == Priority.NONE) Aurora.Mist else priorityColor(p),
                        icon = if (p == Priority.NONE) null else R.drawable.ic_flag,
                    )
                }
            }

            Label("Repeat")
            ChipRow {
                Repeat.entries.forEach { r ->
                    GlassChip(
                        r.label,
                        repeat == r,
                        {
                            repeat = r
                            if (r == Repeat.WEEKLY && weekdays.isEmpty()) weekdays = setOf(Day.dayOfWeek(effDay))
                        },
                        icon = if (r == Repeat.NONE) null else R.drawable.ic_repeat,
                        accent = Aurora.Ion,
                    )
                }
            }
            if (repeat == Repeat.WEEKLY) {
                Spacer(Modifier.height(10.dp))
                WeekdayPicker(weekdays) { weekdays = it }
            }
            if (repeat != Repeat.NONE && (isNew || series == null)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Each repeat appears on its day, ready to tick off. Unfinished repeats show up in your recap, not as leftovers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aurora.Haze,
                )
            }
            if (existing?.isRecurring == true) {
                Label("Apply changes to")
                ChipRow {
                    GlassChip("This day only", !allUpcoming && !repeatChanged, { allUpcoming = false })
                    GlassChip("All upcoming", allUpcoming || repeatChanged, { allUpcoming = true }, accent = Aurora.Ion)
                }
                if (repeatChanged) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Changing how it repeats always updates all upcoming days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aurora.Haze,
                    )
                }
            }

            Label("Steps")
            steps.forEachIndexed { i, st ->
                key(st.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckOrb(st.done, { steps[i] = st.copy(done = !st.done) }, size = 18.dp)
                        BasicTextField(
                            value = st.title,
                            onValueChange = { v -> steps[i] = st.copy(title = v) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if (st.done) Aurora.Mist else Aurora.Frost,
                                textDecoration = if (st.done) TextDecoration.LineThrough else null,
                            ),
                            cursorBrush = SolidColor(Aurora.Borealis),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painterResource(R.drawable.ic_close),
                            "Remove step",
                            tint = Aurora.Haze,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { haptics.tick(); steps.removeAt(i) }
                                .padding(9.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 4.dp)) {
                Icon(painterResource(R.drawable.ic_add), null, tint = Aurora.Haze, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(16.dp))
                val stepStyle = MaterialTheme.typography.bodyLarge.copy(color = Aurora.Frost)
                BasicTextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    textStyle = stepStyle,
                    cursorBrush = SolidColor(Aurora.Borealis),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        if (newStep.isNotBlank()) {
                            steps.add(Subtask(title = newStep.trim()))
                            newStep = ""
                        }
                    }),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (newStep.isEmpty()) Text("Add a step", style = stepStyle.copy(color = Aurora.Haze))
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            GlowButton(
                text = if (isNew) {
                    val l = Day.label(effDay, today)
                    "Add to " + if (effDay in (today - 1)..(today + 1)) l.lowercase() else l
                } else "Save changes",
                onClick = { save() },
                enabled = canSave,
                icon = R.drawable.ic_check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (picker) {
        Picker.DATE -> DateDialog(initial = effDay, onDismiss = { picker = null }, onPick = { dayOverride = it })
        Picker.TIME -> TimeDialog(initial = effTime, use24 = use24, onDismiss = { picker = null }, onPick = {
            timeSet = true
            time = it
        })
        null -> {}
    }

    if (confirmDelete && existing != null) {
        ConfirmRepeatDelete(
            onJustThis = {
                store.delete(existing.id, false)
                confirmDelete = false
                showToast(ToastData("Removed this day only"))
                close()
            },
            onWhole = {
                store.delete(existing.id, true)
                confirmDelete = false
                showToast(ToastData("“${existing.title}” won't repeat any more"))
                close()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Eyebrow(text, Modifier.padding(top = 20.dp, bottom = 10.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent))),
    )
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 1..7) {
            val on = d in selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (on) Modifier.background(Brush.linearGradient(listOf(Aurora.Ion, Aurora.Glacier)))
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    )
                    .clickable {
                        haptics.tick()
                        val next = if (on) selected - d else selected + d
                        if (next.isNotEmpty()) onChange(next)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Day.weekdayLetter(d),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Aurora.Night else Aurora.Mist,
                )
            }
        }
    }
}
