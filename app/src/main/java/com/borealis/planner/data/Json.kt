package com.borealis.planner.data

import org.json.JSONArray
import org.json.JSONObject

/** Hand-written JSON mapping (org.json ships with Android, so no extra library). */
object Json {
    private const val VERSION = 1

    fun encode(s: PlannerState): String = JSONObject().apply {
        put("v", VERSION)
        put("lastRollover", s.lastRollover)
        put("tasks", JSONArray().apply { s.tasks.forEach { put(task(it)) } })
        put("series", JSONArray().apply { s.series.forEach { put(series(it)) } })
        put("settings", settings(s.settings))
        put("widgetTomorrow", JSONArray().apply { s.widgetTomorrow.forEach { put(it) } })
        put("categories", JSONArray().apply { s.categories.forEach { put(category(it)) } })
        put("widgetCategory", JSONObject().apply { s.widgetCategory.forEach { (k, v) -> put(k.toString(), v) } })
        put("collapsed", JSONArray().apply { s.collapsed.forEach { put(it) } })
    }.toString()

    fun decode(text: String): PlannerState {
        val o = JSONObject(text)
        return PlannerState(
            tasks = o.optJSONArray("tasks").objects().mapNotNull { runCatching { task(it) }.getOrNull() },
            series = o.optJSONArray("series").objects().mapNotNull { runCatching { series(it) }.getOrNull() },
            settings = o.optJSONObject("settings")?.let { settings(it) } ?: Settings(),
            lastRollover = o.optLong("lastRollover", 0L),
            widgetTomorrow = o.optJSONArray("widgetTomorrow").ints().toSet(),
            categories = o.optJSONArray("categories").objects().mapNotNull { runCatching { category(it) }.getOrNull() },
            widgetCategory = o.optJSONObject("widgetCategory")?.let { m ->
                m.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to m.optString(k) } }.filter { it.second.isNotEmpty() }.toMap()
            } ?: emptyMap(),
            collapsed = o.optJSONArray("collapsed").strings().toSet(),
        )
    }

    // ------------------------------------------------------------ category

    private fun category(c: Category) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("color", c.color)
        c.parentId?.let { put("parentId", it) }
        put("order", c.order)
    }

    private fun category(o: JSONObject) = Category(
        id = o.getString("id"),
        name = o.optString("name"),
        color = o.optInt("color", 0),
        parentId = o.optStringOrNull("parentId"),
        order = o.optInt("order", 0),
    )

    // ------------------------------------------------------------ task

    private fun task(t: Task) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("notes", t.notes)
        put("day", t.day)
        t.time?.let { put("time", it) }
        put("priority", t.priority.name)
        put("alert", t.alert.name)
        put("alertLead", t.alertLead)
        put("subtasks", JSONArray().apply {
            t.subtasks.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("done", it.done)) }
        })
        put("done", t.done)
        t.doneAt?.let { put("doneAt", it) }
        put("letGo", t.letGo)
        t.seriesId?.let { put("seriesId", it) }
        put("slips", JSONArray().apply { t.slips.forEach { put(it) } })
        t.snoozeUntil?.let { put("snoozeUntil", it) }
        put("createdAt", t.createdAt)
        t.categoryId?.let { put("categoryId", it) }
    }

    private fun task(o: JSONObject) = Task(
        id = o.getString("id"),
        title = o.optString("title"),
        notes = o.optString("notes"),
        day = o.getLong("day"),
        time = o.optIntOrNull("time"),
        priority = enumOr(o.optString("priority"), Priority.NONE),
        alert = enumOr(o.optString("alert"), AlertType.NONE),
        alertLead = o.optInt("alertLead", 0),
        subtasks = o.optJSONArray("subtasks").objects().map {
            Subtask(id = it.optString("id").ifEmpty { newId() }, title = it.optString("title"), done = it.optBoolean("done"))
        },
        done = o.optBoolean("done"),
        doneAt = o.optLongOrNull("doneAt"),
        letGo = o.optBoolean("letGo"),
        seriesId = o.optStringOrNull("seriesId"),
        slips = o.optJSONArray("slips").longs(),
        snoozeUntil = o.optLongOrNull("snoozeUntil"),
        createdAt = o.optLong("createdAt", 0L),
        categoryId = o.optStringOrNull("categoryId"),
    )

    // ------------------------------------------------------------ series

    private fun series(s: Series) = JSONObject().apply {
        put("id", s.id)
        put("title", s.title)
        put("notes", s.notes)
        s.time?.let { put("time", it) }
        put("priority", s.priority.name)
        put("alert", s.alert.name)
        put("alertLead", s.alertLead)
        put("subtasks", JSONArray().apply { s.subtasks.forEach { put(it) } })
        put("repeat", s.repeat.name)
        put("weekdays", JSONArray().apply { s.weekdays.forEach { put(it) } })
        put("startDay", s.startDay)
        s.endDay?.let { put("endDay", it) }
        put("skipDays", JSONArray().apply { s.skipDays.forEach { put(it) } })
        s.categoryId?.let { put("categoryId", it) }
    }

    private fun series(o: JSONObject) = Series(
        id = o.getString("id"),
        title = o.optString("title"),
        notes = o.optString("notes"),
        time = o.optIntOrNull("time"),
        priority = enumOr(o.optString("priority"), Priority.NONE),
        alert = enumOr(o.optString("alert"), AlertType.NONE),
        alertLead = o.optInt("alertLead", 0),
        subtasks = o.optJSONArray("subtasks").strings(),
        repeat = enumOr(o.optString("repeat"), Repeat.DAILY),
        weekdays = o.optJSONArray("weekdays").ints().toSet(),
        startDay = o.getLong("startDay"),
        endDay = o.optLongOrNull("endDay"),
        skipDays = o.optJSONArray("skipDays").longs().toSet(),
        categoryId = o.optStringOrNull("categoryId"),
    )

    // ------------------------------------------------------------ settings

    private fun settings(s: Settings) = JSONObject().apply {
        put("autoCarry", s.autoCarry)
        put("lockCard", s.lockCard)
        put("snoozeMinutes", s.snoozeMinutes)
        put("haptics", s.haptics)
        put("calmSky", s.calmSky)
        put("widgetTheme", s.widget.theme.name)
        put("widgetOpacity", s.widget.opacity.toDouble())
        put("widgetShowDone", s.widget.showDone)
        put("groupByCategory", s.groupByCategory)
    }

    private fun settings(o: JSONObject): Settings {
        val d = Settings()
        return Settings(
            autoCarry = o.optBoolean("autoCarry", d.autoCarry),
            lockCard = o.optBoolean("lockCard", d.lockCard),
            snoozeMinutes = o.optInt("snoozeMinutes", d.snoozeMinutes),
            haptics = o.optBoolean("haptics", d.haptics),
            calmSky = o.optBoolean("calmSky", d.calmSky),
            widget = WidgetStyle(
                theme = enumOr(o.optString("widgetTheme"), d.widget.theme),
                opacity = o.optDouble("widgetOpacity", d.widget.opacity.toDouble()).toFloat().coerceIn(0.2f, 1f),
                showDone = o.optBoolean("widgetShowDone", d.widget.showDone),
            ),
            groupByCategory = o.optBoolean("groupByCategory", d.groupByCategory),
        )
    }

    // ------------------------------------------------------------ helpers

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).ifEmpty { null } else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.longs(): List<Long> =
        if (this == null) emptyList() else (0 until length()).map { optLong(it) }

    private fun JSONArray?.ints(): List<Int> =
        if (this == null) emptyList() else (0 until length()).map { optInt(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
