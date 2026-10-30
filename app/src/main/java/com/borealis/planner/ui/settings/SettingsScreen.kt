package com.borealis.planner.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.borealis.planner.R
import com.borealis.planner.update.Updater
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import com.borealis.planner.alarm.AlarmScheduler
import com.borealis.planner.alarm.Notifications
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.ui.components.AuroraSwitch
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlassChip
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.SettingRow
import com.borealis.planner.ui.components.ToastData
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.theme.Aurora

@Composable
fun SettingsScreen(
    state: PlannerState,
    store: PlannerStore,
    showToast: (ToastData) -> Unit,
    updates: UpdateController,
    onManageCategories: () -> Unit,
) {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val backup = rememberBackupLauncher(store) { ok -> showToast(ToastData(if (ok) "Backup saved" else "Couldn't save the backup")) }
    val restore = rememberRestoreLauncher { text ->
        if (text == null || !text.trimStart().startsWith("{") || !text.contains("\"tasks\"")) showToast(ToastData("That file isn't a Borealis backup."))
        else pendingRestore = text
    }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val health = remember(resumes) { Health.read(context) }
    val s = state.settings
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = top + 16.dp, bottom = bottom + 130.dp),
    ) {
        Eyebrow("Preferences", color = Aurora.Glacier)
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.displayMedium, color = Aurora.Frost)

        Group("Organise") {
            val tops = com.borealis.planner.data.PlannerLogic.topCategories(state)
            SettingRow(
                "Categories",
                if (tops.isEmpty()) "Group tasks into categories and subcategories, like Uni › BIO101."
                else tops.joinToString(" · ") { it.name } + " · ${state.categories.size - tops.size} subcategories",
                icon = R.drawable.ic_folder,
                iconTint = Aurora.Borealis,
                onClick = onManageCategories,
            ) { PillButton(if (tops.isEmpty()) "Set up" else "Edit", onManageCategories, tone = Tone.Accent) }
        }

        Group("Planning") {
            SettingRow(
                "Carry unfinished tasks forward",
                if (s.autoCarry) "Each morning, yesterday's unfinished one-off tasks move to today automatically."
                else "Off: unfinished tasks wait under “Couldn't finish” so you decide what to do with each.",
                icon = R.drawable.ic_arrow_right,
                iconTint = Aurora.Ember,
            ) { AuroraSwitch(s.autoCarry) { v -> store.settings { it.copy(autoCarry = v) } } }
            Divider()
            SettingRow("Snooze length", "How long Snooze waits before alerting again.", icon = R.drawable.ic_snooze, iconTint = Aurora.Glacier)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 66.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 10, 15, 30).forEach { m ->
                    GlassChip("$m min", s.snoozeMinutes == m, { store.settings { it.copy(snoozeMinutes = m) } }, accent = Aurora.Glacier)
                }
            }
            Divider()
            SettingRow("Haptics", "Gentle taps when you tick, swipe and switch.", icon = R.drawable.ic_sparkle, iconTint = Aurora.Ion) {
                AuroraSwitch(s.haptics) { v -> store.settings { it.copy(haptics = v) } }
            }
            Divider()
            SettingRow("Calm sky", "Hold the aurora still to save battery.", icon = R.drawable.ic_moon, iconTint = Aurora.Ion) {
                AuroraSwitch(s.calmSky) { v -> store.settings { it.copy(calmSky = v) } }
            }
        }

        Group("Alarms & reminders") {
            StatusRow(
                "Notifications",
                if (health.notifications) "Allowed" else "Off — reminders and alarms can't appear.",
                health.notifications,
                R.drawable.ic_bell,
            ) { open(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            Divider()
            StatusRow(
                "On-time alerts",
                if (health.exact) "Alerts fire at the exact minute." else "Off — alerts may arrive a few minutes late.",
                health.exact,
                R.drawable.ic_clock,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
            Divider()
            StatusRow(
                "Full-screen alarms",
                if (health.fullScreen) "Alarms take over the lock screen." else "Off — alarms show only as a notification.",
                health.fullScreen,
                R.drawable.ic_alarm,
            ) {
                if (Build.VERSION.SDK_INT >= 34) {
                    open(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }
            }
            Divider()
            StatusRow(
                "Never sleeping",
                if (health.battery) "Samsung won't put Borealis to sleep." else "Samsung may delay alerts to save battery. Tap Fix and choose Allow.",
                health.battery,
                R.drawable.ic_battery,
            ) {
                open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }
            Divider()
            SettingRow(
                "Ring a test alarm",
                "Rings in 5 seconds. Lock your phone to see the full-screen alarm.",
                icon = R.drawable.ic_alarm,
                iconTint = Aurora.Borealis,
            ) {
                PillButton("Test", {
                    AlarmScheduler.scheduleTest(context)
                    showToast(ToastData("Test alarm rings in 5 seconds"))
                }, tone = Tone.Accent)
            }
        }

        Group("App") {
            val p = updates.phase
            SettingRow(
                "Borealis " + Updater.installedVersionName(context),
                when (p) {
                    is UpdateController.Phase.Checking -> "Checking for updates…"
                    is UpdateController.Phase.UpToDate -> "You're on the newest version."
                    is UpdateController.Phase.Available -> "Version ${p.release.versionName} is ready to install."
                    is UpdateController.Phase.Downloading -> "Downloading ${p.release.versionName}… ${(p.progress * 100).toInt()}%"
                    is UpdateController.Phase.Failed -> p.message
                    else -> "Updates install over this copy and keep all your tasks."
                },
                icon = R.drawable.ic_update,
                iconTint = Aurora.Borealis,
            ) {
                PillButton(
                    if (updates.available != null) "Update" else "Check",
                    { if (updates.available != null) updates.sheetOpen = true else updates.check() },
                    tone = Tone.Accent,
                )
            }
            Divider()
            SettingRow("Back up tasks", "Save everything to a file, e.g. in Downloads or Google Drive.", icon = R.drawable.ic_save, iconTint = Aurora.Glacier) {
                PillButton("Save", backup)
            }
            Divider()
            SettingRow("Restore from backup", "Replace what's here with a saved backup file.", icon = R.drawable.ic_restore, iconTint = Aurora.Glacier) {
                PillButton("Open", restore)
            }
            pendingRestore?.let { text ->
                val preview = remember(text) { runCatching { com.borealis.planner.data.Json.decode(text) }.getOrNull() }
                Column(Modifier.padding(16.dp).fillMaxWidth().background(Aurora.Flare.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text("Replace everything with this backup?", style = MaterialTheme.typography.titleSmall, color = Aurora.Frost)
                    Text(
                        if (preview != null) "It has ${preview.tasks.size} tasks and ${preview.categories.size} categories. What's in Borealis now will be replaced." else "This file couldn't be read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Aurora.Mist,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (preview != null) {
                            PillButton("Replace", {
                                val ok = store.importJson(text)
                                pendingRestore = null
                                showToast(ToastData(if (ok) "Backup restored" else "That file isn't a Borealis backup."))
                            }, tone = Tone.Danger)
                            Spacer(Modifier.width(8.dp))
                        }
                        PillButton("Cancel", { pendingRestore = null })
                    }
                }
            }
        }

        Group("About") {
            SettingRow("Borealis", "A day planner for today, tomorrow and everything that slipped.", icon = R.drawable.ic_sparkle, iconTint = Aurora.Borealis)
            Divider()
            SettingRow(
                "Fonts",
                "Unbounded, Onest and JetBrains Mono, used under the SIL Open Font License 1.1.",
                icon = R.drawable.ic_notes,
                iconTint = Aurora.Mist,
            )
        }
    }
}

private data class Health(val notifications: Boolean, val exact: Boolean, val fullScreen: Boolean, val battery: Boolean) {
    companion object {
        fun read(context: Context): Health {
            val pm = context.getSystemService(PowerManager::class.java)
            return Health(
                notifications = Notifications.canPost(context),
                exact = AlarmScheduler.canExact(context),
                fullScreen = Notifications.canFullScreen(context),
                battery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
            )
        }
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Eyebrow(title, Modifier.padding(top = 26.dp, bottom = 12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), veil = 0.44f),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f)),
    )
}

@Composable
private fun StatusRow(title: String, subtitle: String, ok: Boolean, icon: Int, onFix: () -> Unit) {
    SettingRow(
        title,
        subtitle,
        icon = icon,
        iconTint = if (ok) Aurora.Borealis else Aurora.Ember,
        onClick = if (ok) null else onFix,
    ) {
        if (ok) {
            Text("On", style = MaterialTheme.typography.labelLarge, color = Aurora.Borealis)
        } else {
            PillButton("Fix", onFix, tone = Tone.Warm)
        }
    }
}
