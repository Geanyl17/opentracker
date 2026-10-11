package io.github.geanyl17.opentracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.geanyl17.opentracker.ui.App

class MainActivity : ComponentActivity() {
    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchAction = intent?.action
        val container = (application as OpenTrackerApp).container
        setContent {
            App(container, launchAction, onLaunchHandled = { launchAction = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchAction = intent.action
    }

    companion object {
        const val ACTION_VOICE = "io.github.geanyl17.opentracker.VOICE"
        const val ACTION_ADD = "io.github.geanyl17.opentracker.ADD"
    }
}
