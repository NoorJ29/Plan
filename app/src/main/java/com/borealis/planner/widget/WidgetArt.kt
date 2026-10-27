package com.borealis.planner.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.LruCache
import com.borealis.planner.data.Priority
import com.borealis.planner.data.WidgetStyle
import com.borealis.planner.data.WidgetTheme
import kotlin.math.max

/** Colours for one widget theme, as ARGB ints. */
data class WidgetPalette(
    val baseTop: Int,
    val baseBottom: Int,
    val glowA: Int,
    val glowB: Int,
    val glowC: Int,
    val accent: Int,
    val accent2: Int,
    val text: Int,
    val muted: Int,
    val onAccent: Int,
)

private fun c(v: Long): Int = v.toInt()

object WidgetPalettes {
    fun of(theme: WidgetTheme): WidgetPalette = when (theme) {
        WidgetTheme.AURORA -> WidgetPalette(
            c(0xFF0F1A33), c(0xFF060912), c(0xFF46F5B0), c(0xFF3FD8FF), c(0xFFA08BFF),
            c(0xFF46F5B0), c(0xFF3FD8FF), c(0xFFEAF2FF), c(0xFF93A1C0), c(0xFF060912),
        )
        WidgetTheme.NEBULA -> WidgetPalette(
            c(0xFF1C1033), c(0xFF0A0614), c(0xFFB07CFF), c(0xFFFF6FD8), c(0xFF6E8BFF),
            c(0xFFFF7AD9), c(0xFFB07CFF), c(0xFFF6EDFF), c(0xFFA996C4), c(0xFF140A22),
        )
        WidgetTheme.GLACIER -> WidgetPalette(
            c(0xFF0C2436), c(0xFF04101A), c(0xFF5FE6FF), c(0xFFBFF4FF), c(0xFF2FBFA8),
            c(0xFF7CEBFF), c(0xFFBFF4FF), c(0xFFEFFBFF), c(0xFF8FB3C4), c(0xFF04101A),
        )
        WidgetTheme.EMBER -> WidgetPalette(
            c(0xFF2B1411), c(0xFF0E0607), c(0xFFFFB65C), c(0xFFFF5E8E), c(0xFFFFE08A),
            c(0xFFFFB65C), c(0xFFFF7A6B), c(0xFFFFF3EA), c(0xFFC4A193), c(0xFF1A0A07),
        )
        WidgetTheme.ECLIPSE -> WidgetPalette(
            c(0xFF161618), c(0xFF050505), c(0xFF8A8A8A), c(0xFF5A5A5A), c(0xFFB5B5B5),
            c(0xFFF5F5F5), c(0xFFBDBDBD), c(0xFFF7F7F7), c(0xFF9A9A9A), c(0xFF0A0A0A),
        )
    }

    fun priority(p: Priority): Int = when (p) {
        Priority.HIGH -> c(0xFFFF5E8E)
        Priority.MEDIUM -> c(0xFFFFB65C)
        Priority.LOW -> c(0xFF3FD8FF)
        Priority.NONE -> Color.TRANSPARENT
    }
}

/** Draws widget backgrounds, progress rings and check marks as bitmaps (shared with the app's previews). */
object WidgetArt {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun background(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        style: WidgetStyle,
        intensity: Float,
        radiusDp: Float = 26f,
    ): Bitmap {
        val scale = (context.resources.displayMetrics.density * 0.75f).coerceIn(1f, 2.4f)
        val w = (widthDp * scale).toInt().coerceIn(8, 1400)
        val h = (heightDp * scale).toInt().coerceIn(8, 1400)
        val level = (intensity.coerceIn(0f, 1f) * 8).toInt()
        val key = "bg|${style.theme}|${(style.opacity * 100).toInt()}|$w|$h|$level|$radiusDp"
        cache.get(key)?.let { return it }

        val p = WidgetPalettes.of(style.theme)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val r = radiusDp * scale
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        cv.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })

        val op = style.opacity.coerceIn(0.2f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            withAlpha(p.baseTop, op), withAlpha(p.baseBottom, op), Shader.TileMode.CLAMP,
        )
        cv.drawRect(rect, paint)

        val k = (0.55f + 0.45f * level / 8f) * (0.45f + 0.55f * op)
        val span = max(w, h).toFloat()
        fun glow(cx: Float, cy: Float, radius: Float, color: Int, a: Float) {
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(withAlpha(color, a), withAlpha(color, a * 0.35f), withAlpha(color, 0f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            cv.drawRect(rect, paint)
        }
        glow(w * 0.10f, -h * 0.20f, span * 0.80f, p.glowA, 0.55f * k)
        glow(w * 1.00f, h * 0.10f, span * 0.55f, p.glowB, 0.38f * k)
        glow(w * 0.55f, h * 1.25f, span * 0.75f, p.glowC, 0.42f * k)

        paint.shader = LinearGradient(
            0f, 0f, w * 0.7f, h.toFloat(),
            Color.argb((32 * op).toInt() + 8, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        cv.drawRect(rect, paint)

        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.1f * scale
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.argb(110, 255, 255, 255), Color.argb(18, 255, 255, 255), Shader.TileMode.CLAMP,
            )
        }
        val inset = rim.strokeWidth / 2f
        cv.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r - inset, r - inset, rim)

        cache.put(key, bmp)
        return bmp
    }

    fun ring(context: Context, sizeDp: Float, fraction: Float, theme: WidgetTheme, strokeDp: Float = 5f): Bitmap {
        val d = context.resources.displayMetrics.density
        val s = (sizeDp * d).toInt().coerceIn(8, 900)
        val step = (fraction.coerceIn(0f, 1f) * 100).toInt()
        val key = "ring|$theme|$s|$step|$strokeDp"
        cache.get(key)?.let { return it }
        val p = WidgetPalettes.of(theme)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val sw = strokeDp * d
        val oval = RectF(sw / 2 + 1, sw / 2 + 1, s - sw / 2 - 1, s - sw / 2 - 1)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            color = Color.argb(44, 255, 255, 255)
        }
        cv.drawOval(oval, track)
        if (step > 0) {
            val sweep = SweepGradient(s / 2f, s / 2f, intArrayOf(p.accent, p.accent2, p.glowC, p.accent), null)
            sweep.setLocalMatrix(Matrix().apply { setRotate(-90f, s / 2f, s / 2f) })
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = sw
                strokeCap = Paint.Cap.ROUND
                shader = sweep
            }
            cv.drawArc(oval, -90f, 360f * step / 100f, false, arc)
        }
        cache.put(key, bmp)
        return bmp
    }

    fun check(context: Context, done: Boolean, theme: WidgetTheme, sizeDp: Float = 22f): Bitmap {
        val d = context.resources.displayMetrics.density
        val s = (sizeDp * d).toInt().coerceIn(8, 300)
        val key = "check|$theme|$s|$done"
        cache.get(key)?.let { return it }
        val p = WidgetPalettes.of(theme)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = s / 2f
        val r = s / 2f - 1.2f * d
        if (!done) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * d
                color = withAlpha(p.muted, 0.9f)
            }
            cv.drawCircle(cx, cx, r, ring)
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), p.accent, p.accent2, Shader.TileMode.CLAMP)
            }
            cv.drawCircle(cx, cx, r + 0.6f * d, fill)
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * d
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = p.onAccent
            }
            val path = Path().apply {
                moveTo(cx - r * 0.42f, cx + r * 0.02f)
                lineTo(cx - r * 0.12f, cx + r * 0.32f)
                lineTo(cx + r * 0.45f, cx - r * 0.30f)
            }
            cv.drawPath(path, tick)
        }
        cache.put(key, bmp)
        return bmp
    }

    fun plus(context: Context, theme: WidgetTheme, sizeDp: Float = 30f): Bitmap {
        val d = context.resources.displayMetrics.density
        val s = (sizeDp * d).toInt().coerceIn(8, 300)
        val key = "plus|$theme|$s"
        cache.get(key)?.let { return it }
        val p = WidgetPalettes.of(theme)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), p.accent, p.accent2, Shader.TileMode.CLAMP)
        }
        cv.drawCircle(s / 2f, s / 2f, s / 2f, fill)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * d
            strokeCap = Paint.Cap.ROUND
            color = p.onAccent
        }
        val a = s * 0.28f
        cv.drawLine(s / 2f, a, s / 2f, s - a, line)
        cv.drawLine(a, s / 2f, s - a, s / 2f, line)
        cache.put(key, bmp)
        return bmp
    }
}
