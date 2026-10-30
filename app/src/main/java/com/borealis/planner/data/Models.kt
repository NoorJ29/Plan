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
    /** A [Category] id: either a top-level category or a subcategory. Null = uncategorised. */
    val categoryId: String? = null,
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
    val categoryId: String? = null,
)

/**
 * A folder for tasks, e.g. "Uni". When [parentId] is set it is a subcategory inside that
 * category, e.g. "Uni › BIO101". Subcategories show their parent's colour.
 */
data class Category(
    val id: String = newId(),
    val name: String,
    val color: Int = 0,
    val parentId: String? = null,
    val order: Int = 0,
)

object CategoryPalette {
    val colors: LongArray = longArrayOf(
        0xFF46F5B0, 0xFF3FD8FF, 0xFFA08BFF, 0xFFFFB65C,
        0xFFFF5E8E, 0xFFFFE066, 0xFF6FA8FF, 0xFFB8F26B,
    )
    val names = listOf("Aurora", "Glacier", "Ion", "Ember", "Flare", "Sun", "Sky", "Lime")
    fun argb(index: Int): Int = colors[Math.floorMod(index, colors.size)].toInt()
}

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
    /** Plan screen: group tasks under their categories (true) or one list by time (false). */
    val groupByCategory: Boolean = true,
)

data class PlannerState(
    val tasks: List<Task> = emptyList(),
    val series: List<Series> = emptyList(),
    val settings: Settings = Settings(),
    /** Epoch day of the last day rollover; also serves as "today" for the UI. */
    val lastRollover: Long = 0,
    /** App-widget ids of Today widgets currently flipped to tomorrow. */
    val widgetTomorrow: Set<Int> = emptySet(),
    val categories: List<Category> = emptyList(),
    /** App-widget id -> top-level category id the widget is filtered to. */
    val widgetCategory: Map<Int, String> = emptyMap(),
    /** Category ids folded shut on the Plan screen. */
    val collapsed: Set<String> = emptySet(),
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
    val categoryId: String? = null,
)

fun newId(): String = UUID.randomUUID().toString()
