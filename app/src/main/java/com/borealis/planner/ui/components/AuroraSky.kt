package com.borealis.planner.ui.components

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.borealis.planner.ui.theme.Aurora
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class Ribbon(
    val color: Color,
    val color2: Color,
    val base: Float,
    val amp: Float,
    val waves: Float,
    val speed: Float,
    val depth: Float,
    val offset: Float,
)

private val ribbons = listOf(
    Ribbon(Aurora.Borealis, Aurora.Glacier, base = 0.09f, amp = 0.045f, waves = 1.25f, speed = 1f, depth = 0.30f, offset = 0f),
    Ribbon(Aurora.Glacier, Aurora.Ion, base = 0.19f, amp = 0.055f, waves = 0.85f, speed = -0.7f, depth = 0.24f, offset = 1.7f),
    Ribbon(Aurora.Ion, Aurora.Borealis, base = 0.03f, amp = 0.035f, waves = 1.8f, speed = 0.45f, depth = 0.20f, offset = 3.1f),
)

private class Star(val x: Float, val y: Float, val r: Float, val twinkle: Float)

@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The living polar sky behind every screen. [intensity] (0..1, usually today's completion)
 * makes the aurora brighter and taller as you finish your day.
 */
@Composable
fun AuroraSky(intensity: Float, modifier: Modifier = Modifier, calm: Boolean = false) {
    val glow by animateFloatAsState(
        targetValue = 0.32f + 0.68f * intensity.coerceIn(0f, 1f),
        animationSpec = tween(1800),
        label = "glow",
    )
    val moving = !calm && !reducedMotion()
    val phase: State<Float> = if (moving) {
        rememberInfiniteTransition(label = "sky").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(32_000, easing = LinearEasing)),
            label = "phase",
        )
    } else {
        remember { mutableFloatStateOf(0.2f) }
    }
    val stars = remember {
        val r = Random(42)
        List(80) { Star(r.nextFloat(), r.nextFloat() * 0.8f, 0.5f + r.nextFloat() * 1.3f, r.nextFloat()) }
    }

    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0A1226), Aurora.Night, Color(0xFF04060C))))) {
        Canvas(Modifier.fillMaxSize()) {
            val p = phase.value * 2f * PI.toFloat()
            for (s in stars) {
                val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(p * 3f + s.twinkle * 12f))
                drawCircle(
                    color = Aurora.Frost.copy(alpha = 0.55f * tw),
                    radius = s.r,
                    center = Offset(s.x * size.width, s.y * size.height),
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(64f, 64f, TileMode.Decal)
                    }
                },
        ) {
            val p = phase.value * 2f * PI.toFloat()
            val w = size.width
            val h = size.height
            drawRect(
                Brush.radialGradient(
                    listOf(Aurora.Ion.copy(alpha = 0.20f * glow), Color.Transparent),
                    center = Offset(w * 0.85f, h * 1.02f),
                    radius = w * 0.95f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Aurora.Glacier.copy(alpha = 0.10f * glow), Color.Transparent),
                    center = Offset(w * 0.05f, h * 0.62f),
                    radius = w * 0.7f,
                ),
            )
            for (r in ribbons) drawRibbon(r, w, h, p, glow)
        }
    }
}

private fun DrawScope.drawRibbon(r: Ribbon, w: Float, h: Float, p: Float, glow: Float) {
    val steps = 40
    val top = FloatArray(steps + 1)
    val path = Path()
    val twoPi = 2f * PI.toFloat()
    for (i in 0..steps) {
        val fx = i.toFloat() / steps
        top[i] = h * (r.base +
            r.amp * sin(fx * r.waves * twoPi + p * r.speed + r.offset) +
            r.amp * 0.45f * sin(fx * r.waves * 5.1f + p * r.speed * 1.7f + r.offset * 2f))
        val x = w * fx
        if (i == 0) path.moveTo(x, top[i]) else path.lineTo(x, top[i])
    }
    val reach = 0.55f + 0.45f * glow
    for (i in steps downTo 0) {
        val fx = i.toFloat() / steps
        val y = top[i] + h * r.depth * reach * (0.62f + 0.38f * sin(fx * 7f + p * 1.3f + r.offset))
        path.lineTo(w * fx, y)
    }
    path.close()
    val minY = h * (r.base - r.amp * 1.5f)
    val maxY = h * (r.base + r.amp * 1.5f + r.depth * reach)
    drawPath(
        path,
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.10f to r.color.copy(alpha = 0.62f * glow),
            0.45f to r.color2.copy(alpha = 0.26f * glow),
            1f to Color.Transparent,
            startY = minY,
            endY = maxY,
        ),
    )
}
