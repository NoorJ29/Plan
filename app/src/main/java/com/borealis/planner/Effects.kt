package com.borealis.planner

import android.content.Context
import android.util.Log
import com.borealis.planner.alarm.AlarmScheduler
import com.borealis.planner.alarm.TodayCard
import com.borealis.planner.data.PlannerState
import com.borealis.planner.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide coroutine scope for small background jobs (widget refreshes, receivers). */
object AppScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** Everything outside the app window that must follow the planner: alarms, lock card, widgets. */
object Effects {
    fun onChanged(context: Context, state: PlannerState) {
        try {
            AlarmScheduler.sync(context, state)
        } catch (e: Exception) {
            Log.e("Effects", "Alarm sync failed", e)
        }
        try {
            TodayCard.refresh(context, state)
        } catch (e: Exception) {
            Log.e("Effects", "Lock card failed", e)
        }
        WidgetUpdater.request(context)
    }
}
