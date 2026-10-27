package com.borealis.planner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.borealis.planner.data.Day
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.ui.BorealisRoot
import com.borealis.planner.ui.theme.BorealisTheme

class MainActivity : ComponentActivity() {

    /** Day for which a widget or notification asked to open the "new task" sheet. */
    private val addRequest = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        val store = PlannerStore.get(this)
        setContent {
            BorealisTheme {
                BorealisRoot(store = store, addRequest = addRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        PlannerStore.get(this).ensureFresh()
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ADD) {
            addRequest.value = intent.getLongExtra(EXTRA_DAY, Day.today())
            intent.action = Intent.ACTION_MAIN
        }
    }

    companion object {
        const val ACTION_ADD = "com.borealis.planner.action.ADD"
        const val EXTRA_DAY = "day"
    }
}
