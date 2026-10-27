package com.borealis.planner.alarm

import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.borealis.planner.R
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.ui.components.AuroraSky
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlowButton
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.BorealisTheme
import kotlinx.coroutines.delay

/** Full-screen alarm shown over the lock screen when a task alarm rings. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        val fallbackTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Task"
        val store = PlannerStore.get(this)
        val isTest = id == Notifications.TEST_TASK_ID

        setContent {
            BorealisTheme {
                AlarmScreen(
                    store = store,
                    id = id,
                    fallbackTitle = fallbackTitle,
                    isTest = isTest,
                    onDone = {
                        Notifications.cancel(this, id)
                        if (!isTest) store.setDone(id, true)
                        finish()
                    },
                    onSnooze = {
                        Notifications.cancel(this, id)
                        if (!isTest) store.snooze(id, store.current.settings.snoozeMinutes)
                        finish()
                    },
                    onStop = {
                        Notifications.cancel(this, id)
                        finish()
                    },
                    onHandledElsewhere = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
    }
}

@Composable
private fun AlarmScreen(
    store: PlannerStore,
    id: String,
    fallbackTitle: String,
    isTest: Boolean,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onHandledElsewhere: () -> Unit,
) {
    val state by store.state.collectAsState()
    val task = state.tasks.firstOrNull { it.id == id }
    val handled by AlarmSignals.handled.collectAsState()
    LaunchedEffect(handled) { if (handled == id) onHandledElsewhere() }
    BackHandler(onBack = onStop)

    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val now by produceState(Day.minutesNow()) {
        while (true) {
            delay(1_000)
            value = Day.minutesNow()
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )

    Box(Modifier.fillMaxSize()) {
        AuroraSky(intensity = 1f, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Eyebrow(if (isTest) "Test alarm" else "Task alarm", color = Aurora.Borealis)
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    val r = size.minDimension / 2f
                    for (i in 0..2) {
                        val t = (pulse + i / 3f) % 1f
                        drawCircle(
                            brush = Brush.sweepGradient(Aurora.spectrum + Aurora.Borealis),
                            radius = r * (0.55f + 0.45f * t),
                            alpha = (1f - t) * 0.55f,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                Text(
                    Day.formatTime(now, use24),
                    style = MaterialTheme.typography.displayLarge,
                    color = Aurora.Frost,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                task?.title ?: fallbackTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Aurora.Frost,
                textAlign = TextAlign.Center,
            )
            if (task != null && task.subtasks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${task.subtasksDone} of ${task.subtasks.size} steps done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Mist,
                )
            }
            if (task != null && task.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    task.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Aurora.Mist,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.weight(1.2f))
            GlowButton(
                text = if (isTest) "Looks good" else "Mark done",
                onClick = onDone,
                icon = R.drawable.ic_check,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PillButton(
                    "Snooze ${state.settings.snoozeMinutes} min",
                    onSnooze,
                    icon = R.drawable.ic_snooze,
                    tone = Tone.Accent,
                )
                Spacer(Modifier.width(12.dp))
                PillButton("Stop", onStop, icon = R.drawable.ic_close, tone = Tone.Neutral)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
