package org.librepaw.app.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
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
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.ui.map.PetMapScreen
import org.librepaw.app.ui.petlist.PetListScreen

/** The two tabs the iOS app shows (a third, Settings, isn't ported yet). Map is the default, same as iOS. */
private enum class HomeTab {
  Map,
  Pets,
}

@Composable
fun HomeScreen(
  petRepository: PetRepository,
  onPetClick: (Pet) -> Unit,
  onLogOut: () -> Unit,
  modifier: Modifier = Modifier,
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
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      HomeTab.Map ->
        PetMapScreen(
          petRepository = petRepository,
          onPetClick = onPetClick,
          onLogOut = onLogOut,
          modifier = Modifier.padding(innerPadding),
        )
      HomeTab.Pets ->
        PetListScreen(
          petRepository = petRepository,
          onPetClick = onPetClick,
          onLogOut = onLogOut,
          modifier = Modifier.padding(innerPadding),
        )
    }
  }
}
