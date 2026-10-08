package com.savemymoney.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.savemymoney.app.ui.AppTheme
import com.savemymoney.app.ui.HomeScreen
import com.savemymoney.app.ui.LogScreen
import com.savemymoney.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the setup steps re-check what was switched on in Settings. */
    private val resumeCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                App(resumeCount.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
    }
}

private enum class Screen { Home, Settings, Log }

@Composable
private fun App(resumeCount: Int) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    when (screen) {
        Screen.Home -> HomeScreen(
            resumeCount = resumeCount,
            onOpenSettings = { screen = Screen.Settings },
            onOpenLog = { screen = Screen.Log },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
        Screen.Log -> LogScreen(onBack = { screen = Screen.Home })
    }
}
