package org.librepaw.app.data.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.librepaw.app.R

/**
 * The in-app language override, same choices as the iOS app's AppLanguage (ADR-0022 there):
 * [AUTOMATIC] follows the device's own language (the default), the others force one.
 *
 * Stored and applied by AppCompat's per-app locales rather than by hand: from Android 13 on that
 * is the system's own per-app language setting (also reachable from the system settings, and kept
 * in sync with this picker), below it AppCompat persists the choice itself and applies it to the
 * activity. Either way the activity is recreated in the new language, ViewModels included as they
 * are, which is why they only ever hold string resources, never resolved text (see UiText).
 */
enum class AppLanguage(val languageTag: String?, @StringRes val label: Int) {
  AUTOMATIC(null, R.string.settings_language_automatic),
  ENGLISH("en", R.string.language_english),
  GERMAN("de", R.string.language_german);

  fun apply() {
    val locales = languageTag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
  }

  companion object {
    /** Whatever was last chosen, [AUTOMATIC] when nothing (or an unsupported language) was. */
    fun current(): AppLanguage {
      val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return AUTOMATIC
      return entries.firstOrNull { it.languageTag == tag } ?: AUTOMATIC
    }
  }
}
