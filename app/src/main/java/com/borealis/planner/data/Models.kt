package com.borealis.planner.data

import java.util.UUID

enum class Priority(val weight: Int, val label: String) {
    NONE(0, "None"),
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High"),
}

enum class AlertType(val label: String) {
    NONE("Off"),
    REMINDER("Reminder"),
    ALARM("Alarm"),
}

enum class Repeat(val label: String) {
    NONE("Never"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

data class Subtask(
    val id: String = newId(),
    val title: String,
    val done: Boolean = false,
)

/**
 * One planned item on one day.
 *
 * [day] is a LocalDate epoch day. [time] is minutes after midnight, or null for "anytime".
 * [slips] records every earlier day this task was planned for but not finished there,
 * so the recap can still show it as "couldn't do" on those days after it was moved.
 */
data class Task(
    val id: String = newId(),
    val title: String,
    val notes: String = "",
    val day: Long,
    val time: Int? = null,
    val priority: Priority = Priority.NONE,
    val alert: AlertType = AlertType.NONE,
    val alertLead: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
    val done: Boolean = false,
    val doneAt: Long? = null,
    val letGo: Boolean = false,
    val seriesId: String? = null,
    val slips: List<Long> = emptyList(),
    val snoozeUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isRecurring: Boolean get() = seriesId != null
    val subtasksDone: Int get() = subtasks.count { it.done }
    val isOpen: Boolean get() = !done && !letGo
}

/** A repeating template. Instances are generated as ordinary [Task]s for each matching day. */
data class Series(
    val id: String = newId(),
    val title: String,
    val notes: String = "",
    val time: Int? = null,
    val priority: Priority = Priority.NONE,
    val alert: AlertType = AlertType.NONE,
    val alertLead: Int = 0,
    val subtasks: List<String> = emptyList(),
    val repeat: Repeat,
    /** ISO day-of-week numbers 1 (Mon) .. 7 (Sun); used by [Repeat.WEEKLY]. */
    val weekdays: Set<Int> = emptySet(),
    val startDay: Long,
    val endDay: Long? = null,
    val skipDays: Set<Long> = emptySet(),
)

enum class WidgetTheme(val label: String) {
    AURORA("Aurora"),
    NEBULA("Nebula"),
    GLACIER("Glacier"),
    EMBER("Ember"),
    ECLIPSE("Eclipse"),
}

data class WidgetStyle(
    val theme: WidgetTheme = WidgetTheme.AURORA,
    /** 0.25 = see-through glass, 1.0 = solid. */
    val opacity: Float = 0.86f,
    val showDone: Boolean = true,
)

data class Settings(
    val autoCarry: Boolean = false,
    val lockCard: Boolean = false,
    val snoozeMinutes: Int = 10,
    val haptics: Boolean = true,
    val calmSky: Boolean = false,
    val widget: WidgetStyle = WidgetStyle(),
)

data class PlannerState(
    val tasks: List<Task> = emptyList(),
    val series: List<Series> = emptyList(),
    val settings: Settings = Settings(),
    /** Epoch day of the last day rollover; also serves as "today" for the UI. */
    val lastRollover: Long = 0,
    /** App-widget ids of Today widgets currently flipped to tomorrow. */
    val widgetTomorrow: Set<Int> = emptySet(),
)

/** Everything the editor collects; turned into a [Task] and maybe a [Series]. */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val day: Long,
    val time: Int? = null,
    val priority: Priority = Priority.NONE,
    val alert: AlertType = AlertType.NONE,
    val alertLead: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
    val repeat: Repeat = Repeat.NONE,
    val weekdays: Set<Int> = emptySet(),
)

fun newId(): String = UUID.randomUUID().toString()
