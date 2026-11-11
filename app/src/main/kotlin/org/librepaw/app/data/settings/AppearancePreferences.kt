package org.librepaw.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the user's chosen [AppearanceMode] (plain, not secret, so no encryption needed). */
class AppearancePreferences(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

  private val _mode = MutableStateFlow(loadMode())
  val mode: StateFlow<AppearanceMode> = _mode.asStateFlow()

  fun setMode(mode: AppearanceMode) {
    prefs.edit().putString(MODE_KEY, mode.name).apply()
    _mode.value = mode
  }

  private fun loadMode(): AppearanceMode =
    prefs.getString(MODE_KEY, null)?.let { stored -> runCatching { AppearanceMode.valueOf(stored) }.getOrNull() }
      ?: AppearanceMode.AUTOMATIC

  companion object {
    private const val PREFS_FILE_NAME = "librepaw_settings"
    private const val MODE_KEY = "appearance_mode"
  }
}
