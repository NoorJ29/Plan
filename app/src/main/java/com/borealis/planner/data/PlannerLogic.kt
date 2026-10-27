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
            )
            return state.copy(tasks = state.tasks + task)
        }
        val series = seriesFrom(draft, title, newId(), draft.day)
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
                snoozeUntil = if (timingChanged) null else it.snoozeUntil,
                slips = if (slip) (it.slips + old.day).distinct() else it.slips,
            )
        }

        val sid = old.seriesId
        if (sid == null) {
            if (draft.repeat != Repeat.NONE) {
                // Turn this one-off into the first instance of a new series.
                val series = seriesFrom(draft, title, newId(), draft.day)
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
    )
}
