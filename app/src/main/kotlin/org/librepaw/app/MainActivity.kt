package org.librepaw.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.librepaw.app.data.settings.AppearanceMode
import org.librepaw.app.ui.theme.LibrePawTheme
import org.librepaw.app.ui.theme.LocalDarkTheme

/** An AppCompatActivity (rather than a plain ComponentActivity) for per-app languages, see AppLanguage. */
class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val appContainer = AppContainer(applicationContext)

    enableEdgeToEdge()
    setContent {
      val appearanceMode by appContainer.appearancePreferences.mode.collectAsStateWithLifecycle()
      val isDarkTheme =
        when (appearanceMode) {
          AppearanceMode.AUTOMATIC -> isSystemInDarkTheme()
          AppearanceMode.LIGHT -> false
          AppearanceMode.DARK -> true
        }

      CompositionLocalProvider(LocalDarkTheme provides isDarkTheme) {
        LibrePawTheme(darkTheme = isDarkTheme) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainNavigation(
            authenticationService = appContainer.authenticationService,
            appearancePreferences = appContainer.appearancePreferences,
          )
          }
        }
      }
    }
  }
}
