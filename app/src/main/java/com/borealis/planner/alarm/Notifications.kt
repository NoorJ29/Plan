package com.borealis.planner.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.borealis.planner.MainActivity
import com.borealis.planner.R
import com.borealis.planner.data.AlertType
import com.borealis.planner.data.Day
import com.borealis.planner.data.Task

object Notifications {
    const val CH_ALARMS = "task_alarms_v1"
    const val CH_REMINDERS = "task_reminders_v1"
    const val CH_TODAY = "today_card_v1"

    const val TODAY_ID = 7001
    const val TEST_ID = 7002
    const val TEST_TASK_ID = "__test__"

    private const val ACCENT = 0xFF46F5B0.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarms = NotificationChannel(
            CH_ALARMS,
            context.getString(R.string.channel_alarms),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarms_desc)
            setSound(alarmSound, alarmAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 1000)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val reminders = NotificationChannel(
            CH_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val today = NotificationChannel(
            CH_TODAY,
            context.getString(R.string.channel_today),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_today_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(alarms, reminders, today))
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun idFor(taskId: String): Int = (taskId.hashCode() and 0x3FFFFFFF) or 0x40000000

    fun subtitle(context: Context, t: Task): String = buildString {
        if (t.time != null) append(Day.formatTime(t.time, DateFormat.is24HourFormat(context)))
        if (t.subtasks.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("${t.subtasksDone}/${t.subtasks.size} steps")
        }
        val note = t.notes.lineSequence().firstOrNull()?.trim().orEmpty()
        if (note.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(note)
        }
    }

    @SuppressLint("MissingPermission")
    fun showAlert(context: Context, t: Task) {
        if (!canPost(context)) return
        val id = idFor(t.id)
        val isAlarm = t.alert == AlertType.ALARM
        val builder = NotificationCompat.Builder(context, if (isAlarm) CH_ALARMS else CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_borealis)
            .setColor(ACCENT)
            .setContentTitle(t.title)
            .setContentText(subtitle(context, t).ifEmpty { "Due now" })
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_check, "Done", actionIntent(context, ActionReceiver.DONE, t.id))
            .addAction(R.drawable.ic_snooze, "Snooze", actionIntent(context, ActionReceiver.SNOOZE, t.id))

        if (isAlarm) {
            val full = alarmScreenIntent(context, t.id, t.title)
            builder
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(5 * 60_000L)
                .addAction(R.drawable.ic_close, "Stop", actionIntent(context, ActionReceiver.DISMISS, t.id))
        } else {
            builder
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
        }
        val n = builder.build()
        if (isAlarm) n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(id, n)
    }

    @SuppressLint("MissingPermission")
    fun showTest(context: Context) {
        if (!canPost(context)) return
        val full = alarmScreenIntent(context, TEST_TASK_ID, "Test alarm")
        val n = NotificationCompat.Builder(context, CH_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_borealis)
            .setColor(ACCENT)
            .setContentTitle("Test alarm")
            .setContentText("This is how a task alarm rings.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setTimeoutAfter(2 * 60_000L)
            .addAction(R.drawable.ic_close, "Stop", actionIntent(context, ActionReceiver.DISMISS, TEST_TASK_ID))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(TEST_ID, n)
    }

    fun cancel(context: Context, taskId: String) {
        val nm = NotificationManagerCompat.from(context)
        if (taskId == TEST_TASK_ID) nm.cancel(TEST_ID) else nm.cancel(idFor(taskId))
    }

    fun actionIntent(context: Context, action: String, taskId: String?): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("borealis://action/$action/${taskId ?: "none"}"))
        if (taskId != null) intent.putExtra(AlarmScheduler.EXTRA_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmScreenIntent(context: Context, taskId: String, title: String): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .setData(Uri.parse("borealis://ring/$taskId"))
            .putExtra(AlarmScheduler.EXTRA_ID, taskId)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun openApp(context: Context, action: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (action != null) intent.action = action
        return PendingIntent.getActivity(
            context,
            if (action == null) 10 else 11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canFullScreen(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }
}
