package com.borealis.planner

import android.app.Application
import com.borealis.planner.alarm.Notifications
import com.borealis.planner.data.PlannerStore

class BorealisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        val store = PlannerStore.get(this)
        Effects.onChanged(this, store.current)
    }
}
