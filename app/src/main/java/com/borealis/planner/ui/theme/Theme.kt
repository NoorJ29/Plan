package com.borealis.planner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.borealis.planner.R
import com.borealis.planner.data.Priority

/** Polar-night palette: the sky is near-black navy; aurora greens, glacier cyan and ion violet light it. */
object Aurora {
    val Night = Color(0xFF060912)
    val Deep = Color(0xFF0C1324)
    val Panel = Color(0xFF111A30)
    val Borealis = Color(0xFF46F5B0)
    val Glacier = Color(0xFF3FD8FF)
    val Ion = Color(0xFFA08BFF)
    val Ember = Color(0xFFFFB65C)
    val Flare = Color(0xFFFF5E8E)
    val Frost = Color(0xFFEAF2FF)
    val Mist = Color(0xFF93A1C0)
    val Haze = Color(0xFF5B6787)

    val spectrum = listOf(Borealis, Glacier, Ion)
}

fun priorityColor(p: Priority): Color = when (p) {
    Priority.HIGH -> Aurora.Flare
    Priority.MEDIUM -> Aurora.Ember
    Priority.LOW -> Aurora.Glacier
    Priority.NONE -> Color.Transparent
}

val Unbounded = FontFamily(
    Font(R.font.unbounded_medium, FontWeight.Medium),
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
)

val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
)

val Mono = FontFamily(Font(R.font.jetbrainsmono_medium, FontWeight.Medium))

private val type = Typography(
    displayLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
)

/** Mono readout style for times and counts. */
val Readout = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp)

private val scheme = darkColorScheme(
    primary = Aurora.Borealis,
    onPrimary = Aurora.Night,
    primaryContainer = Color(0xFF123A33),
    onPrimaryContainer = Aurora.Frost,
    secondary = Aurora.Glacier,
    onSecondary = Aurora.Night,
    secondaryContainer = Color(0xFF10324A),
    onSecondaryContainer = Aurora.Frost,
    tertiary = Aurora.Ion,
    onTertiary = Aurora.Night,
    background = Aurora.Night,
    onBackground = Aurora.Frost,
    surface = Aurora.Deep,
    onSurface = Aurora.Frost,
    surfaceVariant = Aurora.Panel,
    onSurfaceVariant = Aurora.Mist,
    surfaceContainerLowest = Aurora.Night,
    surfaceContainerLow = Color(0xFF0A1020),
    surfaceContainer = Aurora.Deep,
    surfaceContainerHigh = Aurora.Panel,
    surfaceContainerHighest = Color(0xFF17213A),
    outline = Color(0xFF34405E),
    outlineVariant = Color(0xFF222C46),
    error = Aurora.Flare,
    onError = Aurora.Night,
    scrim = Color(0xCC000000),
)

@Composable
fun BorealisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
