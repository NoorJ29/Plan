package com.borealis.planner.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.Readout
import kotlin.math.hypot

// ------------------------------------------------------------------ haptics

class Haptics(private val view: View, private val enabled: Boolean) {
    fun confirm() {
        if (!enabled) return
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun heavy() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

@Composable
fun rememberHaptics(): Haptics {
    val provided = LocalHaptics.current
    val view = LocalView.current
    return provided ?: remember(view) { Haptics(view, true) }
}

// ------------------------------------------------------------------ glass

/** Frosted panel: a dark veil for legibility, a faint top-lit fill, and a light-catching rim. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.White,
    fillAlpha: Float = 0.06f,
    strokeAlpha: Float = 0.14f,
    veil: Float = 0.38f,
): Modifier = this
    .clip(shape)
    .background(Aurora.Night.copy(alpha = veil), shape)
    .background(
        Brush.verticalGradient(listOf(tint.copy(alpha = fillAlpha * 1.6f), tint.copy(alpha = fillAlpha * 0.5f))),
        shape,
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = (strokeAlpha * 1.8f).coerceAtMost(1f)),
                Color.White.copy(alpha = strokeAlpha * 0.25f),
                tint.copy(alpha = strokeAlpha),
            ),
        ),
        shape,
    )

/** Click with a gentle press-down scale instead of a ripple. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

// ------------------------------------------------------------------ check orb

/** The round checkbox: fills with aurora light and draws its tick when completed. */
@Composable
fun CheckOrb(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    ring: Color = Aurora.Mist,
    colors: List<Color> = listOf(Aurora.Borealis, Aurora.Glacier),
) {
    val haptics = rememberHaptics()
    val p by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 320f),
        label = "orb",
    )
    val source = remember { MutableInteractionSource() }
    Canvas(
        modifier
            .size(size + 16.dp)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null) {
                if (!checked) haptics.confirm() else haptics.tick()
                onToggle()
            }
            .padding(8.dp),
    ) {
        val r = this.size.minDimension / 2f
        val c = center
        val fill = p.coerceIn(0f, 1.2f)
        drawCircle(
            color = ring.copy(alpha = 0.75f * (1f - p.coerceIn(0f, 1f))),
            radius = r - 1.dp.toPx(),
            style = Stroke(1.7.dp.toPx()),
        )
        if (fill > 0.01f) {
            drawCircle(
                brush = Brush.linearGradient(colors, start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)),
                radius = r * fill.coerceAtMost(1.08f),
            )
            val t = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
            if (t > 0f) {
                val a = Offset(c.x - r * 0.42f, c.y + r * 0.02f)
                val b = Offset(c.x - r * 0.12f, c.y + r * 0.32f)
                val e = Offset(c.x + r * 0.45f, c.y - r * 0.3f)
                val l1 = hypot(b.x - a.x, b.y - a.y)
                val l2 = hypot(e.x - b.x, e.y - b.y)
                val reach = t * (l1 + l2)
                val stroke = 2.2.dp.toPx()
                val ink = Aurora.Night
                if (reach <= l1) {
                    drawLine(ink, a, a + (b - a) * (reach / l1), stroke, StrokeCap.Round)
                } else {
                    drawLine(ink, a, b, stroke, StrokeCap.Round)
                    drawLine(ink, b, b + (e - b) * ((reach - l1) / l2), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ chips & buttons

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    accent: Color = Aurora.Borealis,
) {
    val haptics = rememberHaptics()
    val fg by animateColorAsState(if (selected) Aurora.Frost else Aurora.Mist, label = "chipFg")
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .pressable { haptics.tick(); onClick() }
            .then(
                if (selected) {
                    Modifier
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.10f))), shape)
                        .border(1.dp, accent.copy(alpha = 0.65f), shape)
                } else {
                    Modifier.glass(shape, fillAlpha = 0.05f, strokeAlpha = 0.12f, veil = 0.2f)
                },
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = if (selected) accent else fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

enum class Tone { Neutral, Accent, Warm, Danger }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    tone: Tone = Tone.Neutral,
) {
    val color = when (tone) {
        Tone.Neutral -> Aurora.Frost
        Tone.Accent -> Aurora.Borealis
        Tone.Warm -> Aurora.Ember
        Tone.Danger -> Aurora.Flare
    }
    val haptics = rememberHaptics()
    Row(
        modifier
            .pressable { haptics.tick(); onClick() }
            .glass(RoundedCornerShape(50), tint = color, fillAlpha = 0.07f, strokeAlpha = 0.22f, veil = 0.15f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** The one bright button on a screen. */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .height(54.dp)
            .pressable(enabled) { haptics.confirm(); onClick() }
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(listOf(Aurora.Borealis, Aurora.Glacier, Aurora.Ion))
                else Brush.linearGradient(listOf(Aurora.Haze, Aurora.Haze)),
                shape,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = Aurora.Night, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Aurora.Night)
    }
}

@Composable
fun IconOrb(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Aurora.Frost,
    size: Dp = 44.dp,
) {
    val haptics = rememberHaptics()
    Box(
        modifier
            .size(size)
            .pressable { haptics.tick(); onClick() }
            .glass(CircleShape, fillAlpha = 0.06f, strokeAlpha = 0.16f, veil = 0.25f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Aurora.Mist) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    color: Color = Aurora.Mist,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(PaddingValues(start = 4.dp, end = 2.dp, top = 18.dp, bottom = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Eyebrow(title, color = color)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = Readout, color = color.copy(alpha = 0.7f))
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Aurora.Glacier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.Frost)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Aurora.Mist,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun AuroraSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { haptics.tick(); onCheckedChange(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Aurora.Night,
            checkedTrackColor = Aurora.Borealis,
            checkedBorderColor = Aurora.Borealis,
            uncheckedThumbColor = Aurora.Mist,
            uncheckedTrackColor = Color(0x22FFFFFF),
            uncheckedBorderColor = Color(0x33FFFFFF),
        ),
    )
}
