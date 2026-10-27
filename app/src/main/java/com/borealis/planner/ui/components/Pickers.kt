package com.borealis.planner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.borealis.planner.data.Day
import com.borealis.planner.ui.theme.Aurora
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Minutes since midnight, updated on each minute boundary. */
@Composable
fun rememberNowMinutes(): State<Int> = produceState(initialValue = Day.minutesNow()) {
    while (true) {
        val ms = System.currentTimeMillis()
        delay(60_000L - ms % 60_000L + 50L)
        value = Day.minutesNow()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = Day.toUtcMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Day.fromUtcMillis(it)) }
                onDismiss()
            }) { Text("Choose", color = Aurora.Borealis) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Aurora.Mist) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: Int?, use24: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = initial ?: ((LocalTime.now().hour + 1) % 24) * 60
    val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = use24)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .glass(RoundedCornerShape(30.dp), veil = 0.94f, fillAlpha = 0.05f)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow("Set a time", Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            TimePicker(state = state)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Aurora.Mist) }
                Spacer(Modifier.width(8.dp))
                GlowButton("Set time", onClick = {
                    onPick(state.hour * 60 + state.minute)
                    onDismiss()
                })
            }
        }
    }
}

@Composable
fun ConfirmRepeatDelete(onJustThis: () -> Unit, onWhole: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aurora.Deep,
        title = { Text("Delete a repeating task", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Remove only this day, or stop it repeating from today on? Finished days stay in your recap.",
                color = Aurora.Mist,
            )
        },
        confirmButton = {
            TextButton(onClick = onWhole) { Text("Stop repeating", color = Aurora.Flare) }
        },
        dismissButton = {
            TextButton(onClick = onJustThis) { Text("Only this day", color = Aurora.Frost) }
        },
    )
}

// ------------------------------------------------------------------ toast

data class ToastData(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val id: Long = System.nanoTime(),
)

@Composable
fun ToastHost(data: ToastData?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf<ToastData?>(null) }
    LaunchedEffect(data?.id) {
        if (data != null) {
            last = data
            delay(4_200)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = data != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val d = data ?: last
        if (d != null) {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(20.dp), veil = 0.88f, fillAlpha = 0.07f, strokeAlpha = 0.2f)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d.message, style = MaterialTheme.typography.bodyMedium, color = Aurora.Frost, modifier = Modifier.weight(1f))
                if (d.actionLabel != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        d.actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Aurora.Borealis,
                        modifier = Modifier
                            .clickable {
                                d.action?.invoke()
                                onDismiss()
                            }
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ celebration

private class Spark(val angle: Float, val speed: Float, val color: Color, val radius: Float)

/** A short burst of aurora sparks when the last task of the day is done. */
@Composable
fun Celebration(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    val anim = remember(trigger) { Animatable(0f) }
    val sparks = remember(trigger) {
        val r = Random(trigger)
        val palette = listOf(Aurora.Borealis, Aurora.Glacier, Aurora.Ion, Aurora.Frost)
        List(70) {
            Spark(
                angle = r.nextFloat() * 2f * PI.toFloat(),
                speed = 0.35f + r.nextFloat() * 0.75f,
                color = palette[r.nextInt(palette.size)],
                radius = 2f + r.nextFloat() * 5f,
            )
        }
    }
    val haptics = rememberHaptics()
    LaunchedEffect(trigger) {
        haptics.heavy()
        anim.animateTo(1f, tween(1700, easing = LinearOutSlowInEasing))
    }
    Canvas(modifier) {
        val t = anim.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.2f)
        val reach = size.minDimension * 0.75f
        for (s in sparks) {
            val d = s.speed * t * reach
            val pos = origin + Offset(cos(s.angle) * d, sin(s.angle) * d + 420f * t * t)
            drawCircle(s.color.copy(alpha = (1f - t).coerceIn(0f, 1f)), radius = s.radius * (1f - 0.5f * t), center = pos)
        }
    }
}
