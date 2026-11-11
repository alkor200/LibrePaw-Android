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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.librepaw.app.BuildConfig
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.settings.AppearanceMode
import org.librepaw.app.data.settings.AppearancePreferences

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
        title = { Text("Settings") },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
      SectionHeader("Account")
      InfoCard {
        SettingsRow(label = "Server", value = session.serverUrl.toHttpUrlOrNull()?.host ?: session.serverUrl)
        SettingsRow(label = "Email", value = session.email)
      }

      SectionHeader("Appearance")
      AppearancePicker(appearancePreferences = appearancePreferences)

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
        Text("Log Out")
      }

      VersionFooter()
    }
  }

  if (showLogOutConfirmation) {
    AlertDialog(
      onDismissRequest = { showLogOutConfirmation = false },
      title = { Text("Log Out?") },
      confirmButton = {
        TextButton(
          onClick = {
            showLogOutConfirmation = false
            onLogOut()
          }
        ) {
          Text("Log Out")
        }
      },
      dismissButton = { TextButton(onClick = { showLogOutConfirmation = false }) { Text("Cancel") } },
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
      listOf(AppearanceMode.AUTOMATIC to "Automatic", AppearanceMode.LIGHT to "Light", AppearanceMode.DARK to "Dark")
    options.forEachIndexed { index, (mode, label) ->
      SegmentedButton(
        selected = selectedMode == mode,
        onClick = { appearancePreferences.setMode(mode) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
      ) {
        Text(label)
      }
    }
  }
  Text(
    "Automatic follows your device's own appearance setting.",
    modifier = Modifier.padding(top = 8.dp),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun VersionFooter() {
  Column(modifier = Modifier.padding(top = 32.dp)) {
    Text(
      "Version ${BuildConfig.VERSION_NAME}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "LibrePaw is open source software and is not affiliated with Traccar.",
      modifier = Modifier.padding(top = 4.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
