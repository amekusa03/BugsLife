package com.kusa.bugslife

import android.app.Application
import com.kusa.bugslife.util.NotificationHelper

class BugsLifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
