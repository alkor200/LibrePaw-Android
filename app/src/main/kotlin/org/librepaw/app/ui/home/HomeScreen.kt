package org.librepaw.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.pets.Pet
import org.librepaw.app.data.pets.PetRepository
import org.librepaw.app.data.settings.AppearancePreferences
import org.librepaw.app.ui.map.PetMapScreen
import org.librepaw.app.ui.petlist.PetListScreen
import org.librepaw.app.ui.settings.SettingsScreen

/** The three tabs the iOS app shows, same order: Map first (the default), then Pets, then Settings.
 * Owned by [org.librepaw.app.MainNavigation] rather than this screen itself, so the bottom bar
 * built from it stays visible on every logged-in screen, not just this one. */
enum class HomeTab {
  Map,
  Pets,
  Settings,
}

/** The content for whichever [HomeTab] is selected; the persistent bottom bar that switches
 * between them lives one level up, in [org.librepaw.app.MainNavigation]. */
@Composable
fun HomeScreen(
  selectedTab: HomeTab,
  session: StoredSession,
  petRepository: PetRepository,
  appearancePreferences: AppearancePreferences,
  onPetClick: (Pet) -> Unit,
  onLogOut: () -> Unit,
  modifier: Modifier = Modifier,
  /** Bumped after a successful pet edit, so the Pets grid and Map re-fetch instead of showing stale cached state. */
  dataVersion: Int = 0,
  /** Set by Pet Detail's "View on Map", so the Map tab centers on that one pet once; see
   * [org.librepaw.app.MainNavigation]'s own mapFocusPetId, matching iOS's AppTabNavigation.mapFocusPet. */
  focusPetId: Int? = null,
  onFocusConsumed: () -> Unit = {},
) {
  when (selectedTab) {
    HomeTab.Map ->
      PetMapScreen(
        petRepository = petRepository,
        onPetClick = onPetClick,
        modifier = modifier,
        dataVersion = dataVersion,
        focusPetId = focusPetId,
        onFocusConsumed = onFocusConsumed,
      )
    HomeTab.Pets ->
      PetListScreen(petRepository = petRepository, onPetClick = onPetClick, modifier = modifier, dataVersion = dataVersion)
    HomeTab.Settings ->
      SettingsScreen(session = session, appearancePreferences = appearancePreferences, onLogOut = onLogOut, modifier = modifier)
  }
}
