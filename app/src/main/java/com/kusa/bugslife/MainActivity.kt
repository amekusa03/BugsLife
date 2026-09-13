package com.kusa.bugslife

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kusa.bugslife.data.AppPreferences
import com.kusa.bugslife.service.WatcherForegroundService
import com.kusa.bugslife.ui.MainScreen
import com.kusa.bugslife.ui.theme.BugsLifeTheme

class MainActivity : ComponentActivity() {
    private lateinit var prefs: AppPreferences

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startWatcherService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = AppPreferences(this)

        checkAndRequestPermissions()

        setContent {
            BugsLifeTheme {
                MainScreen(
                    prefs = prefs,
                    onRequireServiceReload = {
                        WatcherForegroundService.reloadSettings(this)
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startWatcherService()
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startWatcherService()
        }
    }

    private fun startWatcherService() {
        if (prefs.isServiceEnabled) {
            WatcherForegroundService.startService(this)
        }
    }
}
