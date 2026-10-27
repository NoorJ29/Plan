package com.borealis.planner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.borealis.planner.AppScope
import com.borealis.planner.Effects
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Lets an open alarm screen close itself when the alarm is handled from the notification. */
object AlarmSignals {
    val handled = MutableStateFlow<String?>(null)
}

private fun BroadcastReceiver.work(context: Context, block: suspend (Context) -> Unit) {
    val pending = goAsync()
    val app = context.applicationContext
    AppScope.scope.launch {
        try {
            block(app)
        } catch (e: Exception) {
            Log.e("Receiver", "Failed", e)
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        work(context) { ctx ->
            val store = PlannerStore.get(ctx)
            when (intent.action) {
                AlarmScheduler.ACTION_ALERT -> {
                    val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return@work
                    val task = store.current.tasks.firstOrNull { it.id == id }
                    val at = task?.let { PlannerLogic.alertAt(it) }
                    if (task != null && at != null && System.currentTimeMillis() >= at - 90_000L) {
                        Notifications.showAlert(ctx, task)
                    }
                    Effects.onChanged(ctx, store.current)
                }
                AlarmScheduler.ACTION_ROLLOVER -> {
                    store.ensureFresh()
                    Effects.onChanged(ctx, store.current)
                    store.flush()
                }
                AlarmScheduler.ACTION_TEST -> Notifications.showTest(ctx)
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        work(context) { ctx ->
            val store = PlannerStore.get(ctx)
            val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID)
            when (intent.action) {
                DONE -> if (id != null) {
                    Notifications.cancel(ctx, id)
                    store.setDone(id, true)
                    AlarmSignals.handled.value = id
                }
                SNOOZE -> if (id != null) {
                    Notifications.cancel(ctx, id)
                    store.snooze(id, store.current.settings.snoozeMinutes)
                    AlarmSignals.handled.value = id
                }
                DISMISS -> if (id != null) {
                    Notifications.cancel(ctx, id)
                    AlarmSignals.handled.value = id
                }
                DONE_NEXT -> store.completeNext()
            }
            store.flush()
        }
    }

    companion object {
        const val DONE = "com.borealis.planner.action.DONE"
        const val SNOOZE = "com.borealis.planner.action.SNOOZE"
        const val DISMISS = "com.borealis.planner.action.DISMISS"
        const val DONE_NEXT = "com.borealis.planner.action.DONE_NEXT"
    }
}

/** Re-arms alarms after a reboot, app update, or a clock / time-zone change. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        work(context) { ctx ->
            Notifications.createChannels(ctx)
            val store = PlannerStore.get(ctx)
            store.ensureFresh()
            Effects.onChanged(ctx, store.current)
            store.flush()
        }
    }
}
