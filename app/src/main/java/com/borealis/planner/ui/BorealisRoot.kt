package com.borealis.planner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.borealis.planner.MainActivity
import com.borealis.planner.R
import com.borealis.planner.update.Updater
import com.borealis.planner.ui.settings.rememberUpdateController
import com.borealis.planner.ui.settings.UpdateSheet
import com.borealis.planner.ui.settings.UpdateBanner
import com.borealis.planner.ui.plan.CategoryManagerSheet
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.ui.components.AuroraSky
import com.borealis.planner.ui.components.Haptics
import com.borealis.planner.ui.components.LocalHaptics
import com.borealis.planner.ui.components.ToastData
import com.borealis.planner.ui.components.ToastHost
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.components.pressable
import com.borealis.planner.ui.plan.EditorTarget
import com.borealis.planner.ui.plan.PlanScreen
import com.borealis.planner.ui.plan.TaskEditorSheet
import com.borealis.planner.ui.recap.RecapScreen
import com.borealis.planner.ui.settings.SettingsScreen
import com.borealis.planner.ui.studio.StudioScreen
import com.borealis.planner.ui.theme.Aurora

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    PLAN("Plan", R.drawable.ic_plan),
    RECAP("Recap", R.drawable.ic_recap),
    WIDGETS("Widgets", R.drawable.ic_widgets),
    SETTINGS("Settings", R.drawable.ic_settings),
}

@Composable
fun BorealisRoot(store: PlannerStore, addRequest: MutableState<Long?>) {
    val state by store.state.collectAsStateWithLifecycle()
    val today = if (state.lastRollover > 0) state.lastRollover else Day.today()

    var tab by rememberSaveable { mutableStateOf(Tab.PLAN) }
    var day by rememberSaveable { mutableLongStateOf(today) }
    var seenToday by rememberSaveable { mutableLongStateOf(today) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var managingCategories by remember { mutableStateOf(false) }
    val updates = rememberUpdateController(store)
    var toast by remember { mutableStateOf<ToastData?>(null) }
    val showToast: (ToastData) -> Unit = { toast = it }

    // When the date rolls over while you were looking at "today", follow it.
    LaunchedEffect(today) {
        if (day == seenToday) day = today
        seenToday = today
    }
    val requested = addRequest.value
    LaunchedEffect(requested) {
        if (requested != null) {
            tab = Tab.PLAN
            day = requested
            editor = EditorTarget.New(requested, MainActivity.pendingCategory.also { MainActivity.pendingCategory = null })
            addRequest.value = null
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) { if (Updater.shouldAutoCheck(context)) updates.check(auto = true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val view = LocalView.current
    val haptics = remember(view, state.settings.haptics) { Haptics(view, state.settings.haptics) }
    val progress = PlannerLogic.progress(state, today)
    val sky = if (progress.total == 0) 0.3f else progress.fraction
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalHaptics provides haptics) {
        Box(Modifier.fillMaxSize().background(Aurora.Night)) {
            AuroraSky(sky, Modifier.fillMaxSize(), calm = state.settings.calmSky)

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 30 }) togetherWith fadeOut(tween(150))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    Tab.PLAN -> PlanScreen(
                        state = state,
                        today = today,
                        day = day,
                        onSelectDay = { day = it },
                        onAdd = { cat -> editor = EditorTarget.New(day, cat) },
                        onEdit = { editor = EditorTarget.Existing(it) },
                        onManageCategories = { managingCategories = true },
                        topBanner = { UpdateBanner(updates, Modifier.padding(bottom = 14.dp)) },
                        store = store,
                        showToast = showToast,
                    )
                    Tab.RECAP -> RecapScreen(
                        state = state,
                        today = today,
                        store = store,
                        onOpenDay = {
                            day = it
                            tab = Tab.PLAN
                        },
                        showToast = showToast,
                    )
                    Tab.WIDGETS -> StudioScreen(state = state, today = today, store = store, showToast = showToast)
                    Tab.SETTINGS -> SettingsScreen(
                        state = state,
                        store = store,
                        showToast = showToast,
                        updates = updates,
                        onManageCategories = { managingCategories = true },
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomInset + 120.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Aurora.Night.copy(alpha = 0.92f)))),
            )
            ToastHost(
                toast,
                onDismiss = { toast = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 96.dp),
            )
            GlassNavBar(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        }

        if (managingCategories) {
            CategoryManagerSheet(state, store) { managingCategories = false }
        }
        UpdateSheet(updates, store)

        editor?.let { target ->
            TaskEditorSheet(
                target = target,
                state = state,
                today = today,
                store = store,
                onDismiss = { editor = null },
                showToast = showToast,
            )
        }
    }
}

@Composable
private fun GlassNavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .glass(RoundedCornerShape(26.dp), veil = 0.9f, fillAlpha = 0.07f, strokeAlpha = 0.18f)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { t ->
            val isSel = t == selected
            val tint by animateColorAsState(if (isSel) Aurora.Borealis else Aurora.Mist, label = "navTint")
            val text by animateColorAsState(if (isSel) Aurora.Frost else Aurora.Haze, label = "navText")
            val haptics = com.borealis.planner.ui.components.rememberHaptics()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSel) Brush.verticalGradient(listOf(Aurora.Borealis.copy(alpha = 0.20f), Aurora.Glacier.copy(alpha = 0.06f)))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                    )
                    .pressable {
                        if (!isSel) haptics.tick()
                        onSelect(t)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(t.icon), t.label, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = text)
            }
        }
    }
}
