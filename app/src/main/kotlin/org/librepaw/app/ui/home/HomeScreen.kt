package org.librepaw.app.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.settings.AppearancePreferences
import org.librepaw.app.ui.map.PetMapScreen
import org.librepaw.app.ui.petlist.PetListScreen
import org.librepaw.app.ui.settings.SettingsScreen

/** The three tabs the iOS app shows, same order: Map first (the default), then Pets, then Settings. */
private enum class HomeTab {
  Map,
  Pets,
  Settings,
}

@Composable
fun HomeScreen(
  session: StoredSession,
  petRepository: PetRepository,
  appearancePreferences: AppearancePreferences,
  onPetClick: (Pet) -> Unit,
  onLogOut: () -> Unit,
  modifier: Modifier = Modifier,
  /** Bumped after a successful pet edit, so the Pets grid and Map re-fetch instead of showing stale cached state. */
  dataVersion: Int = 0,
) {
  var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Map) }

  Scaffold(
    modifier = modifier,
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = selectedTab == HomeTab.Map,
          onClick = { selectedTab = HomeTab.Map },
          icon = { Icon(Icons.Filled.Place, contentDescription = null) },
          label = { Text("Map") },
        )
        NavigationBarItem(
          selected = selectedTab == HomeTab.Pets,
          onClick = { selectedTab = HomeTab.Pets },
          icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
          label = { Text("Pets") },
        )
        NavigationBarItem(
          selected = selectedTab == HomeTab.Settings,
          onClick = { selectedTab = HomeTab.Settings },
          icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
          label = { Text("Settings") },
        )
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      HomeTab.Map ->
        PetMapScreen(
          petRepository = petRepository,
          onPetClick = onPetClick,
          modifier = Modifier.padding(innerPadding),
          dataVersion = dataVersion,
        )
      HomeTab.Pets ->
        PetListScreen(
          petRepository = petRepository,
          onPetClick = onPetClick,
          modifier = Modifier.padding(innerPadding),
          dataVersion = dataVersion,
        )
      HomeTab.Settings ->
        SettingsScreen(
          session = session,
          appearancePreferences = appearancePreferences,
          onLogOut = onLogOut,
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}
