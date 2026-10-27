package com.borealis.planner.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.borealis.planner.MainActivity
import com.borealis.planner.data.AlertType
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.Task

/** Keeps AlarmManager in line with the planner: one exact alarm per upcoming alert. */
object AlarmScheduler {
    const val ACTION_ALERT = "com.borealis.planner.action.ALERT"
    const val ACTION_ROLLOVER = "com.borealis.planner.action.ROLLOVER"
    const val ACTION_TEST = "com.borealis.planner.action.TEST_ALARM"
    const val EXTRA_ID = "task_id"

    private const val PREFS = "alarm_scheduler"
    private const val KEY_IDS = "scheduled_ids"
    private const val HORIZON_MS = 8L * 24 * 60 * 60 * 1000

    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    @Synchronized
    fun sync(context: Context, state: PlannerState) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val wanted = state.tasks.mapNotNull { t ->
            val at = PlannerLogic.alertAt(t) ?: return@mapNotNull null
            if (at > now && at < now + HORIZON_MS) t to at else null
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val ids = wanted.map { it.first.id }.toSet()
        for (gone in previous - ids) am.cancel(alertIntent(context, gone))
        for ((task, at) in wanted) schedule(context, am, task, at)
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
        scheduleRollover(context, am)
    }

    private fun schedule(context: Context, am: AlarmManager, task: Task, at: Long) {
        val pi = alertIntent(context, task.id)
        try {
            when {
                task.alert == AlertType.ALARM && canExact(context) ->
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent(context)), pi)
                canExact(context) ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            Log.w("AlarmScheduler", "Exact alarm refused, using inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun scheduleRollover(context: Context, am: AlarmManager) {
        val at = Day.epochMillis(Day.today() + 1, 0) + 15_000L
        val pi = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_ROLLOVER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Rings a sample alarm a few seconds from now so you can check sound and lock screen. */
    fun scheduleTest(context: Context, delayMs: Long = 5_000L) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMs
        val pi = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canExact(context)) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppIntent(context)), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun alertIntent(context: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_ALERT)
                .setData(Uri.parse("borealis://alert/$id"))
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
