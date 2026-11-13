package org.librepaw.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.librepaw.app.BuildConfig
import org.librepaw.app.R
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.settings.AppLanguage
import org.librepaw.app.data.settings.AppearanceMode
import org.librepaw.app.data.settings.AppearancePreferences
import org.librepaw.app.ui.home.BottomBarClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  session: StoredSession,
  appearancePreferences: AppearancePreferences,
  onLogOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showLogOutConfirmation by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
          .padding(bottom = BottomBarClearance),
    ) {
      SectionHeader(stringResource(R.string.settings_account))
      InfoCard {
        SettingsRow(label = stringResource(R.string.settings_server), value = session.serverUrl.toHttpUrlOrNull()?.host ?: session.serverUrl)
        SettingsRow(label = stringResource(R.string.settings_email), value = session.email)
      }

      SectionHeader(stringResource(R.string.settings_appearance))
      AppearancePicker(appearancePreferences = appearancePreferences)

      SectionHeader(stringResource(R.string.settings_language))
      LanguagePicker()

      SectionHeader("")
      Button(
        onClick = { showLogOutConfirmation = true },
        modifier = Modifier.fillMaxWidth(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
          ),
      ) {
        Text(stringResource(R.string.settings_log_out))
      }

      VersionFooter()
    }
  }

  if (showLogOutConfirmation) {
    AlertDialog(
      onDismissRequest = { showLogOutConfirmation = false },
      title = { Text(stringResource(R.string.settings_log_out_confirmation)) },
      confirmButton = {
        TextButton(
          onClick = {
            showLogOutConfirmation = false
            onLogOut()
          }
        ) {
          Text(stringResource(R.string.settings_log_out))
        }
      },
      dismissButton = { TextButton(onClick = { showLogOutConfirmation = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    title,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
  Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { content() }
  }
}

@Composable
private fun SettingsRow(label: String, value: String) {
  Column(modifier = Modifier.padding(vertical = 6.dp)) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun AppearancePicker(appearancePreferences: AppearancePreferences) {
  val selectedMode by appearancePreferences.mode.collectAsStateWithLifecycle()

  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    val options =
      listOf(
        AppearanceMode.AUTOMATIC to R.string.settings_appearance_automatic,
        AppearanceMode.LIGHT to R.string.settings_appearance_light,
        AppearanceMode.DARK to R.string.settings_appearance_dark,
      )
    options.forEachIndexed { index, (mode, label) ->
      SegmentedButton(
        selected = selectedMode == mode,
        onClick = { appearancePreferences.setMode(mode) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
      ) {
        Text(stringResource(label))
      }
    }
  }
  Text(
    stringResource(R.string.settings_appearance_footer),
    modifier = Modifier.padding(top = 8.dp),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Changing it recreates the activity in the new language, see [AppLanguage]. */
@Composable
private fun LanguagePicker() {
  var selectedLanguage by remember { mutableStateOf(AppLanguage.current()) }

  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    AppLanguage.entries.forEachIndexed { index, language ->
      SegmentedButton(
        selected = selectedLanguage == language,
        onClick = {
          selectedLanguage = language
          language.apply()
        },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = AppLanguage.entries.size),
      ) {
        Text(stringResource(language.label))
      }
    }
  }
  Text(
    stringResource(R.string.settings_language_footer),
    modifier = Modifier.padding(top = 8.dp),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun VersionFooter() {
  Column(modifier = Modifier.padding(top = 32.dp)) {
    Text(
      stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      stringResource(R.string.settings_disclaimer),
      modifier = Modifier.padding(top = 4.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
