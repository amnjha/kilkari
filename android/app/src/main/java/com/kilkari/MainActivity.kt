package com.kilkari

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kilkari.ui.nav.KilkariNavHost
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.KilkariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the system splash until the database has answered, so the app never shows an
        // empty frame or flashes onboarding at someone who has already set it up.
        val splash = installSplashScreen()
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        // Both bars fully transparent, with dark icons: the screen's colour runs all the way
        // to the top of the display and the status bar sits on it, rather than the system
        // laying its own scrim over the top and cutting the colour off in a line.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val repository = (application as KilkariApp).repository
        setContent {
            KilkariTheme {
                // Edge to edge, with no inset here: the navigation host paints the screen's
                // colour behind the status bar and insets its own content underneath it.
                Box(Modifier.fillMaxSize().background(KC.Screen)) {
                    KilkariNavHost(repository, onReady = { ready = true })
                }
            }
        }
    }

    /**
     * Re-arms on the way back into the app.
     *
     * Exact alarms are granted from a system settings page, and returning from it left the
     * permission allowed but nothing scheduled until the app was next restarted — the parent
     * would have granted it and seen no change.
     */
    override fun onResume() {
        super.onResume()
        val repository = (application as KilkariApp).repository
        lifecycleScope.launch { repository.rescheduleNotifications() }
    }
}
