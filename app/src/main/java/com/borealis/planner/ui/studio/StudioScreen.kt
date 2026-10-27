package com.borealis.planner.ui.studio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borealis.planner.R
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.data.Task
import com.borealis.planner.data.WidgetStyle
import com.borealis.planner.data.WidgetTheme
import com.borealis.planner.ui.components.AuroraSwitch
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlowButton
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.SettingRow
import com.borealis.planner.ui.components.ToastData
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.components.rememberHaptics
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.widget.NextUpWidgetReceiver
import com.borealis.planner.widget.OrbWidgetReceiver
import com.borealis.planner.widget.TodayWidgetReceiver
import com.borealis.planner.widget.WidgetArt
import com.borealis.planner.widget.WidgetPalettes

@Composable
fun StudioScreen(
    state: PlannerState,
    today: Long,
    store: PlannerStore,
    showToast: (ToastData) -> Unit,
) {
    val context = LocalContext.current
    val saved = state.settings.widget
    var opacity by remember(saved.opacity) { mutableFloatStateOf(saved.opacity) }
    val style = saved.copy(opacity = opacity)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val sample = remember(state, today) {
        val real = PlannerLogic.openOn(state, today) + PlannerLogic.doneOn(state, today)
        real.ifEmpty {
            listOf(
                Task(title = "Morning run", day = today, time = 7 * 60, done = true),
                Task(title = "Send the proposal", day = today, time = 11 * 60 + 30),
                Task(title = "Call mum", day = today, time = 19 * 60),
            )
        }
    }
    val progress = PlannerLogic.Progress(sample.count { it.done }, sample.size)

    fun pin(receiver: Class<*>, name: String) {
        if (!pinWidget(context, receiver)) {
            showToast(ToastData("Long-press your home screen, tap Widgets, then find Borealis › $name."))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = top + 16.dp, bottom = bottom + 130.dp),
    ) {
        Eyebrow("Home & lock screen", color = Aurora.Ion)
        Spacer(Modifier.height(8.dp))
        Text("Widgets", style = MaterialTheme.typography.displayMedium, color = Aurora.Frost)
        Spacer(Modifier.height(6.dp))
        Text(
            "Style them here — every widget on your phone follows instantly.",
            style = MaterialTheme.typography.bodyLarge,
            color = Aurora.Mist,
        )

        Spacer(Modifier.height(20.dp))
        TodayPreview(style, sample, progress, today)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbPreview(style, progress, Modifier.size(132.dp))
            NextPreview(style, sample.firstOrNull { !it.done }, progress, Modifier.weight(1f))
        }

        Section("Theme")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WidgetTheme.entries.forEach { t ->
                ThemeSwatch(t, t == saved.theme) {
                    store.settings { it.copy(widget = it.widget.copy(theme = t)) }
                }
            }
        }

        Section("Glass")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clear", style = MaterialTheme.typography.labelMedium, color = Aurora.Mist)
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { store.settings { it.copy(widget = it.widget.copy(opacity = opacity)) } },
                valueRange = 0.25f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Aurora.Frost,
                    activeTrackColor = Aurora.Borealis,
                    inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text("Solid", style = MaterialTheme.typography.labelMedium, color = Aurora.Mist)
        }

        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), veil = 0.42f)) {
            SettingRow(
                "Show finished tasks",
                "Keep ticked-off tasks in the checklist, struck through.",
                icon = R.drawable.ic_check,
                iconTint = Aurora.Borealis,
            ) {
                AuroraSwitch(saved.showDone) { v -> store.settings { it.copy(widget = it.widget.copy(showDone = v)) } }
            }
        }

        Section("Add to home screen")
        GlowButton(
            "Add Today checklist",
            onClick = { pin(TodayWidgetReceiver::class.java, "Today checklist") },
            icon = R.drawable.ic_widgets,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Add Progress orb", { pin(OrbWidgetReceiver::class.java, "Progress orb") }, icon = R.drawable.ic_add)
            PillButton("Add Next up", { pin(NextUpWidgetReceiver::class.java, "Next up") }, icon = R.drawable.ic_add)
        }

        Section("Lock screen")
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(24.dp), tint = Aurora.Ion, veil = 0.42f),
        ) {
            SettingRow(
                "Today card on the lock screen",
                "A quiet pinned card with what's left today, your next task and a Done button. Works on any Galaxy without extra apps.",
                icon = R.drawable.ic_lock,
                iconTint = Aurora.Ion,
            ) {
                AuroraSwitch(state.settings.lockCard) { v -> store.settings { it.copy(lockCard = v) } }
            }
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Column(Modifier.padding(16.dp)) {
                Text("Widgets on the Galaxy lock screen", style = MaterialTheme.typography.titleSmall, color = Aurora.Frost)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Samsung only allows other apps' widgets on the lock screen through Good Lock:\n" +
                        "1. Install Good Lock from the Galaxy Store, then add its LockStar module.\n" +
                        "2. Open LockStar, switch it on and tap the lock-screen preview.\n" +
                        "3. Tap Add widget › Borealis › Next up (it's sized for the lock screen).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aurora.Mist,
                )
            }
        }
    }
}

fun pinWidget(context: Context, receiver: Class<*>): Boolean {
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) return false
    return mgr.requestPinAppWidget(ComponentName(context, receiver), null, null)
}

@Composable
private fun Section(title: String) {
    Eyebrow(title, Modifier.padding(top = 26.dp, bottom = 12.dp))
}

@Composable
private fun ThemeSwatch(theme: WidgetTheme, selected: Boolean, onClick: () -> Unit) {
    val p = WidgetPalettes.of(theme)
    val haptics = rememberHaptics()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(if (selected) 2.dp else 1.dp, if (selected) Color(p.accent) else Color.White.copy(alpha = 0.15f), CircleShape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(p.glowA), Color(p.glowB), Color(p.glowC))))
                .clickable {
                    haptics.tick()
                    onClick()
                },
        )
        Spacer(Modifier.height(6.dp))
        Text(theme.label, style = MaterialTheme.typography.labelMedium, color = if (selected) Aurora.Frost else Aurora.Mist)
    }
}

// ------------------------------------------------------------------ previews (mirror the real widgets)

private val WidgetFont = FontFamily.SansSerif

@Composable
private fun WidgetCanvas(style: WidgetStyle, fraction: Float, width: Dp, height: Dp, radius: Float = 26f, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(style, fraction, width, height, radius) {
        WidgetArt.background(context, width.value, height.value, style, fraction, radius).asImageBitmap()
    }
    Box(Modifier.size(width, height)) {
        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        content()
    }
}

@Composable
private fun TodayPreview(style: WidgetStyle, tasks: List<Task>, progress: PlannerLogic.Progress, today: Long) {
    val p = WidgetPalettes.of(style.theme)
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val shown = if (style.showDone) tasks.take(4) else tasks.filter { !it.done }.take(4)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        WidgetCanvas(style, progress.fraction, maxWidth, 230.dp) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Day.eyebrow(today), fontFamily = WidgetFont, fontSize = 10.sp, color = Color(p.muted), fontWeight = FontWeight.Medium)
                        Text(
                            if (progress.left == 0) "All clear" else "${progress.left} left",
                            fontFamily = WidgetFont,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(p.text),
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        val ring = remember(style.theme, progress.fraction) {
                            WidgetArt.ring(context, 44f, progress.fraction, style.theme, 4f).asImageBitmap()
                        }
                        Image(ring, null, Modifier.size(44.dp))
                        Text("${progress.done}/${progress.total}", fontFamily = WidgetFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(p.text))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PreviewPill("Today", true, p.accent, p.text, p.muted)
                    Spacer(Modifier.width(6.dp))
                    PreviewPill("Tomorrow", false, p.accent, p.text, p.muted)
                    Spacer(Modifier.weight(1f))
                    val plus = remember(style.theme) { WidgetArt.plus(context, style.theme, 28f).asImageBitmap() }
                    Image(plus, null, Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                shown.forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val check = remember(style.theme, t.done) { WidgetArt.check(context, t.done, style.theme, 22f).asImageBitmap() }
                        Image(check, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                fontFamily = WidgetFont,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(if (t.done) p.muted else p.text),
                                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            t.time?.let {
                                Text(Day.formatTime(it, use24), fontFamily = WidgetFont, fontSize = 11.sp, color = Color(p.muted))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPill(label: String, selected: Boolean, accent: Int, text: Int, muted: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(accent).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            fontFamily = WidgetFont,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color(if (selected) text else muted),
        )
    }
}

@Composable
private fun OrbPreview(style: WidgetStyle, progress: PlannerLogic.Progress, modifier: Modifier = Modifier) {
    val p = WidgetPalettes.of(style.theme)
    val context = LocalContext.current
    Box(modifier) {
        WidgetCanvas(style, progress.fraction, 132.dp, 132.dp, 30f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ring = remember(style.theme, progress.fraction) {
                    WidgetArt.ring(context, 84f, progress.fraction, style.theme, 6f).asImageBitmap()
                }
                Image(ring, null, Modifier.size(84.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (progress.left == 0) "✓" else "${progress.left}",
                        fontFamily = WidgetFont,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(p.text),
                    )
                    Text(if (progress.left == 0) "all done" else "left", fontFamily = WidgetFont, fontSize = 11.sp, color = Color(p.muted))
                }
            }
        }
    }
}

@Composable
private fun NextPreview(style: WidgetStyle, next: Task?, progress: PlannerLogic.Progress, modifier: Modifier = Modifier) {
    val p = WidgetPalettes.of(style.theme)
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    BoxWithConstraints(modifier) {
        WidgetCanvas(style, progress.fraction, maxWidth, 64.dp, 26f) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val check = remember(style.theme, next == null) { WidgetArt.check(context, next == null, style.theme, 24f).asImageBitmap() }
                Image(check, null, Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            next == null -> "ALL CLEAR"
                            next.time != null -> "NEXT · " + Day.formatTime(next.time, use24)
                            else -> "NEXT"
                        },
                        fontFamily = WidgetFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(p.accent),
                        maxLines = 1,
                    )
                    if (next != null) {
                        Text(next.title, fontFamily = WidgetFont, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
