package com.borealis.planner.data

import android.content.Context
import android.util.Log
import com.borealis.planner.Effects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The single source of truth. Holds the planner in memory as a StateFlow, saves it to
 * a JSON file in the background (atomic rename), and runs side effects (alarms, widgets,
 * lock-screen card) after every change. Shared by the app, widgets and receivers.
 */
class PlannerStore private constructor(private val app: Context) {

    private val file = File(app.filesDir, "planner.json")
    private val backup = File(app.filesDir, "planner.json.bak")
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "planner-writer").apply { isDaemon = true } }
    private val pending = AtomicReference<PlannerState?>(null)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    val current: PlannerState get() = _state.value

    private fun load(): PlannerState {
        for (f in listOf(file, backup)) {
            if (!f.exists()) continue
            try {
                return Json.decode(f.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Could not read ${f.name}", e)
            }
        }
        return PlannerState()
    }

    /** Applies [transform]; if it returns a different state, saves it and runs side effects. */
    fun update(transform: (PlannerState) -> PlannerState): PlannerState {
        val next: PlannerState = synchronized(lock) {
            val before = _state.value
            val candidate = transform(before)
            if (candidate === before || candidate == before) null else candidate.also { _state.value = it }
        } ?: return _state.value
        persist(next)
        Effects.onChanged(app, next)
        return next
    }

    private fun persist(s: PlannerState) {
        if (pending.getAndSet(s) != null) return // a write is already queued; it will pick up s
        writer.execute {
            val snapshot = pending.getAndSet(null) ?: return@execute
            try {
                val tmp = File(app.filesDir, "planner.json.tmp")
                tmp.writeText(Json.encode(snapshot))
                if (file.exists()) file.copyTo(backup, overwrite = true)
                if (!tmp.renameTo(file)) {
                    file.writeText(Json.encode(snapshot))
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
            }
        }
    }

    /** Blocks until queued saves are on disk. Use from receivers before they return. */
    fun flush() {
        try {
            writer.submit {}.get(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }

    /** Day rollover + repeat generation; cheap no-op when already current. */
    fun ensureFresh() {
        val today = Day.today()
        update { PlannerLogic.rollover(it, today) }
    }

    /** Makes sure repeating tasks exist on a day you are looking at. */
    fun ensureDay(day: Long) {
        val today = Day.today()
        if (day < today) return
        update { PlannerLogic.generate(it, listOf(day)) }
    }

    // -------------------------------------------------------------- actions

    private val today get() = Day.today()
    private val now get() = System.currentTimeMillis()

    fun add(draft: TaskDraft) = update { PlannerLogic.add(it, draft, today) }

    fun edit(id: String, draft: TaskDraft, allUpcoming: Boolean) =
        update { PlannerLogic.update(it, id, draft, allUpcoming, today) }

    fun toggleDone(id: String) = update { PlannerLogic.toggleDone(it, id, now) }

    fun setDone(id: String, done: Boolean) = update { PlannerLogic.setDone(it, id, done, now) }

    fun toggleSubtask(taskId: String, subId: String) =
        update { PlannerLogic.toggleSubtask(it, taskId, subId, now) }

    fun move(id: String, day: Long) = update { PlannerLogic.move(it, id, day, today) }

    fun moveAllMissed(toDay: Long) = update { PlannerLogic.moveAllMissed(it, today, toDay) }

    fun letGo(id: String) = update { PlannerLogic.letGo(it, id) }

    fun delete(id: String, wholeSeries: Boolean) =
        update { PlannerLogic.delete(it, id, wholeSeries, today) }

    /** Puts back an earlier copy of a task (used by Undo). */
    fun replace(task: Task) = update { s ->
        if (s.tasks.none { it.id == task.id }) s.copy(tasks = s.tasks + task)
        else s.copy(tasks = s.tasks.map { if (it.id == task.id) task else it })
    }

    fun restore(task: Task) = update { s ->
        if (s.tasks.any { it.id == task.id }) s else s.copy(tasks = s.tasks + task)
    }

    fun snooze(id: String, minutes: Int) = update { PlannerLogic.snooze(it, id, minutes, now) }

    fun completeNext() = update { PlannerLogic.completeNext(it, today, Day.minutesNow(), now) }

    fun settings(transform: (Settings) -> Settings) = update { it.copy(settings = transform(it.settings)) }

    fun toggleWidgetDay(appWidgetId: Int) = update { PlannerLogic.toggleWidgetDay(it, appWidgetId) }

    fun setWidgetDay(appWidgetId: Int, tomorrow: Boolean) = update { s ->
        val next = if (tomorrow) s.widgetTomorrow + appWidgetId else s.widgetTomorrow - appWidgetId
        if (next == s.widgetTomorrow) s else s.copy(widgetTomorrow = next)
    }

    fun forgetWidgets(ids: IntArray) = update { s ->
        val next = s.widgetTomorrow - ids.toSet()
        if (next == s.widgetTomorrow) s else s.copy(widgetTomorrow = next)
    }

    companion object {
        private const val TAG = "PlannerStore"

        @Volatile
        private var instance: PlannerStore? = null

        fun get(context: Context): PlannerStore =
            instance ?: synchronized(this) {
                instance ?: PlannerStore(context.applicationContext).also {
                    instance = it
                    it.ensureFresh()
                }
            }
    }
}
