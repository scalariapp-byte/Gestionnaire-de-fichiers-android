package com.scalari.fichiers

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scalari.fichiers.ui.FichiersApp
import com.scalari.fichiers.ui.theme.FichiersTheme
import com.scalari.fichiers.ui.theme.isAppInDarkTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) com.scalari.fichiers.widget.OpenRequests.handle(intent)
        setContent {
            val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
            val dark = isAppInDarkTheme(settings)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            FichiersTheme(settings) {
                FichiersApp()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        com.scalari.fichiers.widget.OpenRequests.handle(intent)
    }
}
