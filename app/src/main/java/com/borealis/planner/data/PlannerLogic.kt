package com.borealis.planner.data

/**
 * Pure planner rules. Every function takes a state and returns a new one (or the same
 * instance when nothing changed), so it can be unit-tested without Android.
 */
object PlannerLogic {

    // ---------------------------------------------------------------- recurrence

    fun matches(s: Series, day: Long): Boolean {
        if (day < s.startDay) return false
        val end = s.endDay
        if (end != null && day > end) return false
        if (day in s.skipDays) return false
        val d = Day.date(day)
        return when (s.repeat) {
            Repeat.NONE -> day == s.startDay
            Repeat.DAILY -> true
            Repeat.WEEKDAYS -> d.dayOfWeek.value <= 5
            Repeat.WEEKLY -> d.dayOfWeek.value in effectiveWeekdays(s)
            Repeat.MONTHLY -> {
                val startDom = Day.date(s.startDay).dayOfMonth
                d.dayOfMonth == minOf(startDom, d.lengthOfMonth())
            }
        }
    }

    fun effectiveWeekdays(s: Series): Set<Int> =
        s.weekdays.ifEmpty { setOf(Day.dayOfWeek(s.startDay)) }

    fun firstOccurrence(s: Series, from: Long, limit: Int = 400): Long? =
        (from until from + limit).firstOrNull { matches(s, it) }

    private fun instance(s: Series, day: Long) = Task(
        title = s.title,
        notes = s.notes,
        day = day,
        time = s.time,
        priority = s.priority,
        alert = s.alert,
        alertLead = s.alertLead,
        subtasks = s.subtasks.map { Subtask(title = it) },
        seriesId = s.id,
        categoryId = s.categoryId,
    )

    /** Creates missing instances of every series for [days]. */
    fun generate(state: PlannerState, days: Iterable<Long>): PlannerState {
        if (state.series.isEmpty()) return state
        val existing = HashSet<String>()
        for (t in state.tasks) {
            val sid = t.seriesId ?: continue
            existing += "$sid@${t.day}"
            for (slip in t.slips) existing += "$sid@$slip"
        }
        val added = ArrayList<Task>()
        for (s in state.series) {
            for (d in days) {
                if (matches(s, d) && existing.add("${s.id}@$d")) added += instance(s, d)
            }
        }
        return if (added.isEmpty()) state else state.copy(tasks = state.tasks + added)
    }

    /** Runs once per new day: optional carry-over, then today's and tomorrow's repeats. */
    fun rollover(state: PlannerState, today: Long): PlannerState {
        var s = state
        if (s.settings.autoCarry) {
            var changed = false
            val tasks = s.tasks.map { t ->
                if (t.day < today && t.isOpen && !t.isRecurring) {
                    changed = true
                    t.copy(day = today, slips = (t.slips + t.day).distinct(), snoozeUntil = null)
                } else t
            }
            if (changed) s = s.copy(tasks = tasks)
        }
        s = generate(s, listOf(today, today + 1))
        if (s.lastRollover != today) s = s.copy(lastRollover = today)
        return s
    }

    // ---------------------------------------------------------------- queries

    private val openOrder = compareBy<Task>(
        { it.time == null },
        { it.time ?: 0 },
        { -it.priority.weight },
        { it.createdAt },
    )

    fun dayTasks(state: PlannerState, day: Long): List<Task> =
        state.tasks.filter { it.day == day && !it.letGo }

    fun openOn(state: PlannerState, day: Long): List<Task> =
        dayTasks(state, day).filter { !it.done }.sortedWith(openOrder)

    fun doneOn(state: PlannerState, day: Long): List<Task> =
        dayTasks(state, day).filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

    /** One-off tasks from earlier days that were never finished or let go. */
    fun missed(state: PlannerState, today: Long): List<Task> =
        state.tasks.filter { it.day < today && it.isOpen && !it.isRecurring }
            .sortedWith(compareByDescending<Task> { it.day }.thenBy { it.time ?: Int.MAX_VALUE })

    fun nextUp(state: PlannerState, today: Long, nowMinutes: Int): Task? {
        val open = openOn(state, today)
        return open.firstOrNull { it.time != null && it.time >= nowMinutes - 30 }
            ?: open.firstOrNull { it.time == null }
            ?: open.firstOrNull()
    }

    data class Progress(val done: Int, val total: Int) {
        val left: Int get() = total - done
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    fun progress(state: PlannerState, day: Long): Progress {
        val list = dayTasks(state, day)
        return Progress(list.count { it.done }, list.size)
    }

    data class DayRecap(
        val day: Long,
        val done: List<Task>,
        val missed: List<Task>,
        val open: List<Task>,
    ) {
        val planned: Int get() = done.size + missed.size + open.size
        val fraction: Float get() = if (planned == 0) 0f else done.size.toFloat() / planned
    }

    fun recap(state: PlannerState, day: Long, today: Long): DayRecap {
        val done = ArrayList<Task>()
        val missed = ArrayList<Task>()
        val open = ArrayList<Task>()
        for (t in state.tasks) {
            when {
                t.day == day && t.done -> done += t
                t.day == day && (day < today || t.letGo) -> missed += t
                t.day == day -> open += t
                day in t.slips -> missed += t
            }
        }
        return DayRecap(day, done, missed, open)
    }

    fun alertAt(t: Task): Long? {
        if (t.alert == AlertType.NONE || t.time == null || !t.isOpen) return null
        val base = Day.epochMillis(t.day, t.time) - t.alertLead * 60_000L
        val snooze = t.snoozeUntil
        return if (snooze != null && snooze > base) snooze else base
    }

    // ---------------------------------------------------------------- edits

    private inline fun PlannerState.mapTask(id: String, f: (Task) -> Task): PlannerState {
        var hit = false
        val tasks = tasks.map { if (it.id == id) { hit = true; f(it) } else it }
        return if (hit) copy(tasks = tasks) else this
    }

    fun add(state: PlannerState, draft: TaskDraft, today: Long): PlannerState {
        val title = draft.title.trim()
        if (title.isEmpty()) return state
        if (draft.repeat == Repeat.NONE) {
            val task = Task(
                title = title,
                notes = draft.notes.trim(),
                day = draft.day,
                time = draft.time,
                priority = draft.priority,
                alert = if (draft.time == null) AlertType.NONE else draft.alert,
                alertLead = draft.alertLead,
                subtasks = draft.subtasks.filter { it.title.isNotBlank() },
                categoryId = validCategory(state, draft.categoryId),
            )
            return state.copy(tasks = state.tasks + task)
        }
        val series = seriesFrom(draft, title, newId(), draft.day).copy(categoryId = validCategory(state, draft.categoryId))
        val withSeries = state.copy(series = state.series + series)
        val days = buildSet {
            if (today >= draft.day) add(today)
            if (today + 1 >= draft.day) add(today + 1)
            firstOccurrence(series, draft.day)?.let { add(it) }
        }
        return generate(withSeries, days.sorted())
    }

    private fun seriesFrom(draft: TaskDraft, title: String, id: String, start: Long) = Series(
        id = id,
        title = title,
        notes = draft.notes.trim(),
        time = draft.time,
        priority = draft.priority,
        alert = if (draft.time == null) AlertType.NONE else draft.alert,
        alertLead = draft.alertLead,
        subtasks = draft.subtasks.map { it.title }.filter { it.isNotBlank() },
        repeat = draft.repeat,
        weekdays = if (draft.repeat == Repeat.WEEKLY) {
            draft.weekdays.ifEmpty { setOf(Day.dayOfWeek(draft.day)) }
        } else emptySet(),
        startDay = start,
    )

    /**
     * Saves editor changes. For a repeating task, [allUpcoming] also rewrites the series
     * and its not-yet-done future instances. Changing or switching off the repeat always
     * applies to all upcoming.
     */
    fun update(
        state: PlannerState,
        id: String,
        draft: TaskDraft,
        allUpcoming: Boolean,
        today: Long,
    ): PlannerState {
        val old = state.tasks.firstOrNull { it.id == id } ?: return state
        val title = draft.title.trim().ifEmpty { old.title }
        val alert = if (draft.time == null) AlertType.NONE else draft.alert
        val timingChanged = old.day != draft.day || old.time != draft.time ||
            old.alertLead != draft.alertLead || old.alert != alert
        val slip = !old.done && old.day <= today && draft.day > old.day

        var s = state.mapTask(id) {
            it.copy(
                title = title,
                notes = draft.notes.trim(),
                day = draft.day,
                time = draft.time,
                priority = draft.priority,
                alert = alert,
                alertLead = draft.alertLead,
                subtasks = draft.subtasks.filter { st -> st.title.isNotBlank() },
                categoryId = validCategory(state, draft.categoryId),
                snoozeUntil = if (timingChanged) null else it.snoozeUntil,
                slips = if (slip) (it.slips + old.day).distinct() else it.slips,
            )
        }

        val sid = old.seriesId
        if (sid == null) {
            if (draft.repeat != Repeat.NONE) {
                // Turn this one-off into the first instance of a new series.
                val series = seriesFrom(draft, title, newId(), draft.day).copy(categoryId = validCategory(state, draft.categoryId))
                s = s.copy(series = s.series + series).mapTask(id) { it.copy(seriesId = series.id) }
                s = generate(s, listOf(today, today + 1).filter { it >= draft.day })
            }
            return s
        }

        val series = s.series.firstOrNull { it.id == sid } ?: return s
        if (old.day != draft.day) {
            s = s.copy(series = s.series.map { if (it.id == sid) it.copy(skipDays = it.skipDays + old.day) else it })
        }

        val repeatChanged = draft.repeat != series.repeat ||
            (draft.repeat == Repeat.WEEKLY && draft.weekdays.isNotEmpty() && draft.weekdays != series.weekdays)

        if (draft.repeat == Repeat.NONE) {
            // Stop repeating; this task becomes a normal one-off and nothing regenerates from here on.
            val cut = minOf(old.day, draft.day) - 1
            s = s.copy(
                series = s.series.map { if (it.id == sid) it.copy(endDay = cut) else it },
                tasks = s.tasks.filterNot { it.seriesId == sid && it.id != id && it.day > cut && !it.done }
                    .map { if (it.id == id) it.copy(seriesId = null) else it },
            )
            return s
        }

        if (allUpcoming || repeatChanged) {
            val updated = series.copy(
                title = title,
                notes = draft.notes.trim(),
                time = draft.time,
                priority = draft.priority,
                alert = alert,
                alertLead = draft.alertLead,
                subtasks = draft.subtasks.map { it.title }.filter { it.isNotBlank() },
                repeat = draft.repeat,
                weekdays = if (draft.repeat == Repeat.WEEKLY) {
                    draft.weekdays.ifEmpty { series.weekdays.ifEmpty { setOf(Day.dayOfWeek(draft.day)) } }
                } else emptySet(),
                categoryId = validCategory(state, draft.categoryId),
            )
            s = s.copy(series = s.series.map { if (it.id == sid) updated else it })
            s = s.copy(
                tasks = s.tasks.mapNotNull { t ->
                    when {
                        t.seriesId != sid || t.id == id || t.done || t.day < today -> t
                        !matches(updated, t.day) -> null
                        else -> t.copy(
                            title = updated.title,
                            notes = updated.notes,
                            time = updated.time,
                            priority = updated.priority,
                            alert = updated.alert,
                            alertLead = updated.alertLead,
                            subtasks = updated.subtasks.map { Subtask(title = it) },
                            categoryId = updated.categoryId,
                            snoozeUntil = null,
                        )
                    }
                },
            )
            s = generate(s, listOf(today, today + 1))
        }
        return s
    }

    fun setDone(state: PlannerState, id: String, done: Boolean, now: Long): PlannerState =
        state.mapTask(id) {
            if (it.done == done) it
            else it.copy(done = done, doneAt = if (done) now else null, letGo = false, snoozeUntil = null)
        }

    fun toggleDone(state: PlannerState, id: String, now: Long): PlannerState {
        val t = state.tasks.firstOrNull { it.id == id } ?: return state
        return setDone(state, id, !t.done, now)
    }

    /** Ticking the last subtask completes the task; unticking one reopens it. */
    fun toggleSubtask(state: PlannerState, taskId: String, subId: String, now: Long): PlannerState =
        state.mapTask(taskId) { t ->
            val subs = t.subtasks.map { if (it.id == subId) it.copy(done = !it.done) else it }
            val allDone = subs.isNotEmpty() && subs.all { it.done }
            when {
                allDone && !t.done -> t.copy(subtasks = subs, done = true, doneAt = now, snoozeUntil = null)
                !allDone && t.done -> t.copy(subtasks = subs, done = false, doneAt = null)
                else -> t.copy(subtasks = subs)
            }
        }

    fun move(state: PlannerState, id: String, newDay: Long, today: Long): PlannerState {
        val t = state.tasks.firstOrNull { it.id == id } ?: return state
        if (t.day == newDay) return state
        val slip = !t.done && t.day <= today && newDay > t.day
        var s = state.mapTask(id) {
            it.copy(
                day = newDay,
                letGo = false,
                snoozeUntil = null,
                slips = if (slip) (it.slips + t.day).distinct() else it.slips,
            )
        }
        val sid = t.seriesId
        if (sid != null) {
            s = s.copy(series = s.series.map { if (it.id == sid) it.copy(skipDays = it.skipDays + t.day) else it })
        }
        return s
    }

    fun moveAllMissed(state: PlannerState, today: Long, toDay: Long): PlannerState {
        val ids = missed(state, today).map { it.id }.toSet()
        if (ids.isEmpty()) return state
        return state.copy(
            tasks = state.tasks.map {
                if (it.id in ids) it.copy(day = toDay, slips = (it.slips + it.day).distinct(), snoozeUntil = null) else it
            },
        )
    }

    fun letGo(state: PlannerState, id: String): PlannerState =
        state.mapTask(id) { it.copy(letGo = true, snoozeUntil = null) }

    /**
     * Deletes a task. For a repeating one, [wholeSeries] stops the repeat from today on
     * (finished and past instances stay in the recap); otherwise only this day is skipped.
     */
    fun delete(state: PlannerState, id: String, wholeSeries: Boolean, today: Long): PlannerState {
        val t = state.tasks.firstOrNull { it.id == id } ?: return state
        val sid = t.seriesId
        if (sid == null) return state.copy(tasks = state.tasks.filterNot { it.id == id })
        return if (wholeSeries) {
            state.copy(
                series = state.series.map { if (it.id == sid) it.copy(endDay = today - 1) else it },
                tasks = state.tasks.filterNot { it.id == id || (it.seriesId == sid && it.day >= today && !it.done) },
            )
        } else {
            state.copy(
                series = state.series.map { if (it.id == sid) it.copy(skipDays = it.skipDays + t.day) else it },
                tasks = state.tasks.filterNot { it.id == id },
            )
        }
    }

    fun snooze(state: PlannerState, id: String, minutes: Int, now: Long): PlannerState =
        state.mapTask(id) { it.copy(snoozeUntil = now + minutes * 60_000L) }

    fun completeNext(state: PlannerState, today: Long, nowMinutes: Int, now: Long): PlannerState {
        val next = nextUp(state, today, nowMinutes) ?: return state
        return setDone(state, next.id, true, now)
    }

    fun toggleWidgetDay(state: PlannerState, appWidgetId: Int): PlannerState =
        state.copy(
            widgetTomorrow = if (appWidgetId in state.widgetTomorrow) {
                state.widgetTomorrow - appWidgetId
            } else {
                state.widgetTomorrow + appWidgetId
            },
        )

    fun draftOf(t: Task, series: Series?): TaskDraft = TaskDraft(
        title = t.title,
        notes = t.notes,
        day = t.day,
        time = t.time,
        priority = t.priority,
        alert = t.alert,
        alertLead = t.alertLead,
        subtasks = t.subtasks,
        repeat = series?.repeat ?: Repeat.NONE,
        weekdays = series?.let { if (it.repeat == Repeat.WEEKLY) effectiveWeekdays(it) else emptySet() } ?: emptySet(),
        categoryId = t.categoryId,
    )

    // ---------------------------------------------------------------- categories

    private val categoryOrder = compareBy<Category>({ it.order }, { it.name.lowercase() })

    fun topCategories(state: PlannerState): List<Category> =
        state.categories.filter { it.parentId == null }.sortedWith(categoryOrder)

    fun subcategories(state: PlannerState, parentId: String): List<Category> =
        state.categories.filter { it.parentId == parentId }.sortedWith(categoryOrder)

    fun category(state: PlannerState, id: String?): Category? =
        if (id == null) null else state.categories.firstOrNull { it.id == id }

    private fun validCategory(state: PlannerState, id: String?): String? = category(state, id)?.id

    /** The top-level category for [categoryId] (itself, or its parent when it is a subcategory). */
    fun rootOf(state: PlannerState, categoryId: String?): Category? {
        val c = category(state, categoryId) ?: return null
        return if (c.parentId == null) c else category(state, c.parentId) ?: c
    }

    /** Colour index shown for a category: subcategories use their parent's. */
    fun colorOf(state: PlannerState, categoryId: String?): Int? = rootOf(state, categoryId)?.color

    /** "Uni › BIO101" for a subcategory, "Uni" for a category, null when uncategorised. */
    fun pathLabel(state: PlannerState, categoryId: String?): String? {
        val c = category(state, categoryId) ?: return null
        val p = c.parentId?.let { category(state, it) }
        return if (p != null) "${p.name} › ${c.name}" else c.name
    }

    /** True when [task] sits in [categoryId] or in one of its subcategories. */
    fun inCategory(state: PlannerState, task: Task, categoryId: String): Boolean {
        val c = category(state, task.categoryId) ?: return false
        return c.id == categoryId || c.parentId == categoryId
    }

    /** Adds a category (or a subcategory when [parentId] is a top-level category). Returns the new or existing one. */
    fun addCategory(state: PlannerState, name: String, parentId: String? = null, color: Int? = null): Pair<PlannerState, Category?> {
        val n = name.trim().replace(Regex("\\s+"), " ").take(40)
        if (n.isEmpty()) return state to null
        if (parentId != null && state.categories.none { it.id == parentId && it.parentId == null }) return state to null
        val siblings = state.categories.filter { it.parentId == parentId }
        siblings.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { return state to it }
        val c = Category(
            name = n,
            parentId = parentId,
            color = color ?: if (parentId == null) topCategories(state).size % CategoryPalette.colors.size else 0,
            order = (siblings.maxOfOrNull { it.order } ?: -1) + 1,
        )
        return state.copy(categories = state.categories + c) to c
    }

    fun renameCategory(state: PlannerState, id: String, name: String): PlannerState {
        val n = name.trim().replace(Regex("\\s+"), " ").take(40)
        if (n.isEmpty()) return state
        return state.copy(categories = state.categories.map { if (it.id == id) it.copy(name = n) else it })
    }

    fun recolorCategory(state: PlannerState, id: String, color: Int): PlannerState =
        state.copy(categories = state.categories.map { if (it.id == id) it.copy(color = Math.floorMod(color, CategoryPalette.colors.size)) else it })

    /** Moves a category up (-1) or down (+1) among its siblings. */
    fun moveCategory(state: PlannerState, id: String, delta: Int): PlannerState {
        val c = category(state, id) ?: return state
        val siblings = state.categories.filter { it.parentId == c.parentId }.sortedWith(categoryOrder).toMutableList()
        val i = siblings.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in siblings.indices) return state
        siblings.add(j, siblings.removeAt(i))
        val order = siblings.mapIndexed { k, cat -> cat.id to k }.toMap()
        return state.copy(categories = state.categories.map { cat -> order[cat.id]?.let { cat.copy(order = it) } ?: cat })
    }

    /**
     * Deletes a category. Deleting a top-level category also removes its subcategories and leaves
     * their tasks uncategorised; deleting a subcategory moves its tasks up into the parent category.
     */
    fun deleteCategory(state: PlannerState, id: String): PlannerState {
        val c = category(state, id) ?: return state
        val removed = if (c.parentId == null) state.categories.filter { it.id == id || it.parentId == id }.map { it.id }.toSet() else setOf(id)
        val moveTo = c.parentId
        fun fix(cat: String?): String? = if (cat != null && cat in removed) moveTo else cat
        return state.copy(
            categories = state.categories.filterNot { it.id in removed },
            tasks = state.tasks.map { t -> if (t.categoryId != null && t.categoryId in removed) t.copy(categoryId = fix(t.categoryId)) else t },
            series = state.series.map { s -> if (s.categoryId != null && s.categoryId in removed) s.copy(categoryId = fix(s.categoryId)) else s },
            widgetCategory = state.widgetCategory.filterValues { it !in removed },
            collapsed = state.collapsed - removed,
        )
    }

    /** Files a task (and, for a repeat, its series and future copies) under [categoryId]. */
    fun setTaskCategory(state: PlannerState, taskId: String, categoryId: String?, today: Long): PlannerState {
        val t = state.tasks.firstOrNull { it.id == taskId } ?: return state
        val cat = validCategory(state, categoryId)
        val sid = t.seriesId
        return state.copy(
            tasks = state.tasks.map {
                if (it.id == taskId || (sid != null && it.seriesId == sid && it.day >= today && !it.done)) it.copy(categoryId = cat) else it
            },
            series = if (sid == null) state.series else state.series.map { if (it.id == sid) it.copy(categoryId = cat) else it },
        )
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Finds the category a "#tag" refers to: an exact name first (subcategories before categories), then a unique prefix. */
    fun matchTag(state: PlannerState, tag: String): Category? {
        val t = norm(tag)
        if (t.isEmpty()) return null
        val subs = state.categories.filter { it.parentId != null }
        val tops = state.categories.filter { it.parentId == null }
        subs.firstOrNull { norm(it.name) == t }?.let { return it }
        tops.firstOrNull { norm(it.name) == t }?.let { return it }
        val pre = (subs + tops).filter { norm(it.name).startsWith(t) }
        return pre.singleOrNull()
    }

    data class SubGroup(val sub: Category?, val tasks: List<Task>)

    /** One top-level category's tasks: those filed directly under it, then each subcategory's. */
    data class CategoryGroup(val category: Category?, val subs: List<SubGroup>) {
        val tasks: List<Task> get() = subs.flatMap { it.tasks }
        val done: Int get() = tasks.count { it.done }
    }

    /** Groups [tasks] (kept in their given order) by category and subcategory; uncategorised last. */
    fun groupByCategory(state: PlannerState, tasks: List<Task>): List<CategoryGroup> {
        val byId = state.categories.associateBy { it.id }
        val buckets = LinkedHashMap<String?, MutableList<Task>>()
        for (t in tasks) {
            val key = t.categoryId?.takeIf { it in byId }
            buckets.getOrPut(key) { ArrayList() }.add(t)
        }
        val out = ArrayList<CategoryGroup>()
        for (top in topCategories(state)) {
            val subs = ArrayList<SubGroup>()
            buckets[top.id]?.let { subs += SubGroup(null, it) }
            for (sub in subcategories(state, top.id)) buckets[sub.id]?.let { subs += SubGroup(sub, it) }
            if (subs.isNotEmpty()) out += CategoryGroup(top, subs)
        }
        // Subcategories whose parent vanished are shown as their own groups.
        for ((key, list) in buckets) {
            if (key == null) continue
            val c = byId[key] ?: continue
            if (c.parentId != null && byId[c.parentId] == null) out += CategoryGroup(c, listOf(SubGroup(null, list)))
        }
        buckets[null]?.let { out += CategoryGroup(null, listOf(SubGroup(null, it))) }
        return out
    }

    /** Steps the Today widget's category filter: All -> each category in order -> All. */
    fun cycleWidgetCategory(state: PlannerState, appWidgetId: Int): PlannerState {
        val tops = topCategories(state)
        if (tops.isEmpty()) return if (appWidgetId in state.widgetCategory) state.copy(widgetCategory = state.widgetCategory - appWidgetId) else state
        val cur = state.widgetCategory[appWidgetId]
        val i = tops.indexOfFirst { it.id == cur }
        val next = when {
            cur == null || i < 0 -> tops.first().id
            i + 1 < tops.size -> tops[i + 1].id
            else -> null
        }
        return state.copy(widgetCategory = if (next == null) state.widgetCategory - appWidgetId else state.widgetCategory + (appWidgetId to next))
    }

    fun toggleCollapsed(state: PlannerState, categoryKey: String): PlannerState =
        state.copy(collapsed = if (categoryKey in state.collapsed) state.collapsed - categoryKey else state.collapsed + categoryKey)
}
