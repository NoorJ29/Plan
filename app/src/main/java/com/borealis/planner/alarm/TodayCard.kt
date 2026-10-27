package com.borealis.planner.alarm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.borealis.planner.R
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState

/**
 * Optional silent, pinned notification that shows today's progress on the lock screen
 * (and in Samsung's Now Bar on One UI versions that promote ongoing notifications).
 */
object TodayCard {

    @SuppressLint("MissingPermission")
    fun refresh(context: Context, state: PlannerState) {
        val nm = NotificationManagerCompat.from(context)
        if (!state.settings.lockCard || !Notifications.canPost(context)) {
            nm.cancel(Notifications.TODAY_ID)
            return
        }
        val today = Day.today()
        val progress = PlannerLogic.progress(state, today)
        val next = PlannerLogic.nextUp(state, today, Day.minutesNow())
        val use24 = DateFormat.is24HourFormat(context)

        val title = when {
            progress.total == 0 -> "Nothing planned today"
            progress.left == 0 -> "All clear today"
            else -> "${progress.left} left today"
        }
        val text = when {
            next != null -> buildString {
                append("Next: ")
                append(next.title)
                if (next.time != null) append(" · ").append(Day.formatTime(next.time, use24))
            }
            progress.total > 0 -> "${progress.done} of ${progress.total} done"
            else -> "Tap to plan your day"
        }

        val builder = NotificationCompat.Builder(context, Notifications.CH_TODAY)
            .setSmallIcon(R.drawable.ic_stat_borealis)
            .setColor(0xFF46F5B0.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(Notifications.openApp(context))
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })

        if (progress.total > 0) {
            builder.setProgress(progress.total, progress.done, false)
            builder.setSubText("${progress.done}/${progress.total}")
        }
        if (next != null) {
            builder.addAction(
                R.drawable.ic_check,
                "Done: ${next.title.take(18)}",
                Notifications.actionIntent(context, ActionReceiver.DONE_NEXT, null),
            )
        }
        builder.addAction(
            R.drawable.ic_add,
            "Add task",
            Notifications.openApp(context, com.borealis.planner.MainActivity.ACTION_ADD),
        )
        nm.notify(Notifications.TODAY_ID, builder.build())
    }
}
