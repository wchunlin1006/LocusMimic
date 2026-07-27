package com.locusmimic.app.manager

import android.content.Context
import android.os.Bundle
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.locusmimic.app.manager.localization.LocaleController
import com.locusmimic.app.manager.ui.navigation.AppNavGraph
import com.locusmimic.app.manager.ui.theme.LocusMimicTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasRequiredFrameworkApis()) {
            showUnsupportedAndroidDialog()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setContent {
            // The map SDK is intentionally light-only. Keep the app in the same light scheme so
            // switching the system to dark mode never creates a mismatched, high-contrast shell.
            LocusMimicTheme(darkTheme = false) {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

    private fun showUnsupportedAndroidDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsupported Android version")
            .setMessage("LocusMimic需要 Android 11 或更高版本。")
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun hasRequiredFrameworkApis(): Boolean =
        runCatching {
            android.view.View::class.java.getMethod(
                "setForceDarkAllowed",
                java.lang.Boolean.TYPE
            )
            android.view.Window::class.java.getMethod(
                "setDecorFitsSystemWindows",
                java.lang.Boolean.TYPE
            )
        }.isSuccess
}
