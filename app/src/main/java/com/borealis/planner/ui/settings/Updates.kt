package com.borealis.planner.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.borealis.planner.R
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlowButton
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.theme.Aurora
import com.borealis.planner.ui.theme.Readout
import com.borealis.planner.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** Drives "check → download → install" for app updates. One instance lives at the app root. */
@Stable
class UpdateController(private val context: Context, private val scope: CoroutineScope, private val store: PlannerStore) {
    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data class UpToDate(val versionName: String) : Phase
        data class Available(val release: Updater.Release) : Phase
        data class Downloading(val release: Updater.Release, val progress: Float) : Phase
        data class NeedsPermission(val release: Updater.Release, val file: File) : Phase
        data class Installing(val release: Updater.Release, val file: File) : Phase
        data class WrongKey(val release: Updater.Release) : Phase
        data class Failed(val message: String, val release: Updater.Release?) : Phase
    }

    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    var sheetOpen by mutableStateOf(false)
    private var job: Job? = null

    val available: Updater.Release?
        get() = when (val p = phase) {
            is Phase.Available -> p.release
            is Phase.Downloading -> p.release
            is Phase.NeedsPermission -> p.release
            is Phase.Installing -> p.release
            is Phase.WrongKey -> p.release
            is Phase.Failed -> p.release
            else -> null
        }

    fun check(auto: Boolean = false) {
        if (phase is Phase.Checking || phase is Phase.Downloading) return
        if (!auto) sheetOpen = true
        phase = Phase.Checking
        job = scope.launch {
            phase = when (val r = Updater.check(context)) {
                is Updater.Check.Available -> Phase.Available(r.release)
                is Updater.Check.UpToDate -> if (auto) Phase.Idle else Phase.UpToDate(r.versionName)
                is Updater.Check.Failed -> if (auto) Phase.Idle else Phase.Failed(r.message, null)
            }
        }
    }

    fun download(release: Updater.Release) {
        job?.cancel()
        phase = Phase.Downloading(release, 0f)
        job = scope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.flush() }
                val file = Updater.download(context, release) { p -> scope.launch { if (phase is Phase.Downloading) phase = Phase.Downloading(release, p) } }
                if (Updater.compareSignature(context, file) == Updater.Signature.DIFFERENT) {
                    phase = Phase.WrongKey(release)
                    return@launch
                }
                install(release, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                phase = Phase.Available(release)
            } catch (e: Exception) {
                phase = Phase.Failed(e.message?.takeIf { it.startsWith("Download failed") } ?: "The download stopped. Check your connection and try again.", release)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun install(release: Updater.Release, file: File) {
        if (!Updater.canInstall(context)) {
            phase = Phase.NeedsPermission(release, file)
            return
        }
        phase = Phase.Installing(release, file)
        try {
            Updater.install(context, file)
        } catch (e: Exception) {
            phase = Phase.Failed("Android couldn't open the installer. Try again.", release)
        }
    }

    /** Called when the app comes back to the front (e.g. from the "install unknown apps" page). */
    fun onResume() {
        val p = phase
        if (p is Phase.NeedsPermission && Updater.canInstall(context)) install(p.release, p.file)
    }

    fun dismiss() {
        available?.let { Updater.dismiss(context, it.versionCode) }
        if (phase !is Phase.Downloading) phase = Phase.Idle
    }
}

@Composable
fun rememberUpdateController(store: PlannerStore): UpdateController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val c = remember { UpdateController(context, scope, store) }
    LifecycleResumeEffect(c) {
        c.onResume()
        onPauseOrDispose { }
    }
    return c
}

/** The small card at the top of the Plan screen when a new version is out. */
@Composable
fun UpdateBanner(controller: UpdateController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val release = controller.available ?: return
    if (controller.phase is UpdateController.Phase.Available && Updater.dismissedVersion(context) >= release.versionCode) return
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), tint = Aurora.Borealis, fillAlpha = 0.08f, strokeAlpha = 0.3f, veil = 0.5f)
            .clickable { controller.sheetOpen = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Aurora.Borealis, Aurora.Glacier))),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_update), null, tint = Aurora.Night, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Update ready · ${release.versionName}", style = MaterialTheme.typography.titleSmall, color = Aurora.Frost)
            val p = controller.phase
            Text(
                if (p is UpdateController.Phase.Downloading) "Downloading… ${(p.progress * 100).toInt()}%" else "Tap to update. Your tasks stay as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = Aurora.Mist,
            )
        }
        Icon(
            painterResource(R.drawable.ic_close),
            "Hide until the next update",
            tint = Aurora.Haze,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { controller.dismiss() }.padding(9.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(controller: UpdateController, store: PlannerStore) {
    if (!controller.sheetOpen) return
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val backup = rememberBackupLauncher(store)
    ModalBottomSheet(
        onDismissRequest = { controller.sheetOpen = false },
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xF50B1222),
        contentColor = Aurora.Frost,
        scrimColor = Color(0xA6000000),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Eyebrow("App update", color = Aurora.Borealis)
            Spacer(Modifier.height(6.dp))
            when (val p = controller.phase) {
                UpdateController.Phase.Idle, UpdateController.Phase.Checking -> {
                    Title("Checking for updates…")
                    Body("Looking for a newer Borealis on GitHub.")
                }
                is UpdateController.Phase.UpToDate -> {
                    Title("You're up to date")
                    Body("Borealis ${p.versionName} is the newest version.")
                    Spacer(Modifier.height(16.dp))
                    PillButton("Check again", { controller.check() }, icon = R.drawable.ic_update)
                }
                is UpdateController.Phase.Available -> {
                    Title("Borealis ${p.release.versionName} is ready")
                    Notes(p.release)
                    Body("It installs over this copy. Your tasks, categories, alarms and widgets stay as they are.")
                    Spacer(Modifier.height(18.dp))
                    GlowButton("Update now" + Updater.formatSize(p.release.sizeBytes).let { if (it.isEmpty()) "" else " · $it" }, { controller.download(p.release) }, icon = R.drawable.ic_update, modifier = Modifier.fillMaxWidth())
                }
                is UpdateController.Phase.Downloading -> {
                    Title("Downloading ${p.release.versionName}…")
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(p.progress.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Aurora.Borealis, Aurora.Glacier))))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${(p.progress * 100).toInt()}%", style = Readout, color = Aurora.Mist)
                    Spacer(Modifier.height(14.dp))
                    PillButton("Cancel", { controller.cancel() })
                }
                is UpdateController.Phase.NeedsPermission -> {
                    Title("One-time permission")
                    Body("Android needs your OK before Borealis can install its own updates. On the next screen, switch on \"Allow from this source\", then come back. The update continues by itself.")
                    Spacer(Modifier.height(18.dp))
                    GlowButton("Open settings", { Updater.openInstallPermission(context) }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateController.Phase.Installing -> {
                    Title("Confirm the update")
                    Body("Android is asking you to confirm. Tap Update (or Install) and Borealis reopens on the new version. If nothing appeared, tap below.")
                    Spacer(Modifier.height(16.dp))
                    PillButton("Show the installer again", { controller.install(p.release, p.file) }, icon = R.drawable.ic_update, tone = Tone.Accent)
                }
                is UpdateController.Phase.WrongKey -> {
                    Title("This update needs a reinstall")
                    Body("This version was signed differently, so Android won't install it over your current copy. Save a backup of your tasks first, then uninstall Borealis, install ${p.release.versionName} and restore the backup in Settings.")
                    Spacer(Modifier.height(16.dp))
                    GlowButton("Back up my tasks", { backup() }, icon = R.drawable.ic_save, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(Updater.RELEASES_PAGE, style = Readout, color = Aurora.Glacier)
                }
                is UpdateController.Phase.Failed -> {
                    Title("Update didn't finish")
                    Body(p.message)
                    Spacer(Modifier.height(16.dp))
                    PillButton("Try again", { val r = p.release; if (r != null) controller.download(r) else controller.check() }, icon = R.drawable.ic_update, tone = Tone.Accent)
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, color = Aurora.Frost)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Aurora.Mist)
}

@Composable
private fun Notes(release: Updater.Release) {
    val lines = release.notes.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return
    Column(
        Modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(14.dp),
    ) {
        Text("What's new", style = MaterialTheme.typography.labelLarge, color = Aurora.Borealis)
        Spacer(Modifier.height(6.dp))
        lines.forEach { l ->
            Text("• " + l.removePrefix("- ").removePrefix("* "), style = MaterialTheme.typography.bodyMedium, color = Aurora.Frost)
        }
    }
}

/** Opens the system "save file" screen and writes a JSON backup of everything in Borealis. */
@Composable
fun rememberBackupLauncher(store: PlannerStore, onDone: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray()) } != null
        } catch (e: Exception) { false }
        onDone(ok)
    }
    return {
        val stamp = java.time.LocalDate.now().toString()
        launcher.launch("borealis-backup-$stamp.json")
    }
}

/** Opens the system file picker and returns the chosen file's text. */
@Composable
fun rememberRestoreLauncher(onText: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) { null }
        onText(text)
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}
