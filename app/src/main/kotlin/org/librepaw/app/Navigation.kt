package org.librepaw.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.settings.AppearancePreferences
import org.librepaw.app.ui.auth.LoginScreen
import org.librepaw.app.ui.editpet.EditPetScreen
import org.librepaw.app.ui.excursions.ExcursionDetailScreen
import org.librepaw.app.ui.excursions.ExcursionsScreen
import org.librepaw.app.ui.find.FindPetScreen
import org.librepaw.app.ui.home.AppBottomBar
import org.librepaw.app.ui.home.HomeScreen
import org.librepaw.app.ui.home.HomeTab
import org.librepaw.app.ui.homes.HomesScreen
import org.librepaw.app.ui.homes.SetHomeScreen
import org.librepaw.app.ui.petdetail.PetDetailScreen
import org.librepaw.app.ui.sharepet.SharePetScreen
import org.librepaw.app.ui.trail.TrailScreen

@Composable
fun MainNavigation(authenticationService: AuthenticationService, appearancePreferences: AppearancePreferences) {
  var currentSession by remember { mutableStateOf(authenticationService.restoreSession()) }
  val backStack = rememberNavBackStack(if (currentSession != null) Main else Login)
  val petRepository = currentSession?.let { session -> remember(session) { authenticationService.makePetRepository(session) } }
  var petDataVersion by remember { mutableStateOf(0) }
  val viewModelStoreOwner = LocalViewModelStoreOwner.current

  var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Map) }
  // A one-shot signal for "View on Map": set alongside switching to the Map tab, consumed (and
  // cleared) by PetMapScreen once it's centered the camera on that one pet, matching iOS's own
  // AppTabNavigation.mapFocusPet.
  var mapFocusPetId by remember { mutableStateOf<Int?>(null) }

  fun goTo(key: NavKey) {
    backStack.clear()
    backStack.add(key)
  }

  /**
   * Every screen's `viewModel(key = ...)` is keyed by petId/dataVersion, never by session
   * identity, so switching accounts within the same process (log out, then log back in as a
   * different user) would otherwise leave a screen like PetDetail showing a ViewModel instance
   * still holding the previous session's permissions (e.g. an admin-only action staying visible
   * for a non-admin account that happens to view the same pet). Clearing the whole store on every
   * session transition guarantees every screen rebuilds fresh against the new session.
   */
  fun switchSession(newSession: StoredSession?) {
    viewModelStoreOwner?.viewModelStore?.clear()
    currentSession = newSession
  }

  // A plain Box, not a Scaffold: a Scaffold's bottomBar slot reserves its own full-width,
  // solidly-colored strip for the bar, which would hide whatever content (the map, a scrolled
  // list) sits directly behind it. Overlaying the bar on top of full-size content instead means
  // only the pill itself is opaque, everything else around it stays genuinely transparent.
  Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
      modifier = Modifier.fillMaxSize(),
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryProvider =
        entryProvider {
          entry<Login> {
            LoginScreen(
              authenticationService = authenticationService,
              onLoggedIn = { session: StoredSession ->
                switchSession(session)
                goTo(Main)
              },
            )
          }
          entry<Main> {
            val session = currentSession
            val repository = petRepository
            if (session != null && repository != null) {
              HomeScreen(
                selectedTab = selectedTab,
                session = session,
                petRepository = repository,
                appearancePreferences = appearancePreferences,
                onPetClick = { pet -> backStack.add(PetDetail(pet.id)) },
                onLogOut = {
                  authenticationService.logOut()
                  switchSession(null)
                  goTo(Login)
                },
                dataVersion = petDataVersion,
                focusPetId = mapFocusPetId,
                onFocusConsumed = { mapFocusPetId = null },
              )
            }
          }
          entry<PetDetail> { key ->
            petRepository?.let { repository ->
              PetDetailScreen(
                petId = key.petId,
                petRepository = repository,
                onBack = { backStack.removeLastOrNull() },
                onEditClick = { pet -> backStack.add(EditPet(pet.id)) },
                onHomesClick = { pet -> backStack.add(Homes(pet.id, pet.name)) },
                onExcursionsClick = { pet -> backStack.add(Excursions(pet.id, pet.name)) },
                onTrailClick = { pet -> backStack.add(Trail(pet.id, pet.name)) },
                onSharePetClick = { pet -> backStack.add(SharePet(pet.id, pet.name)) },
                onViewOnMapClick = { pet ->
                  mapFocusPetId = pet.id
                  selectedTab = HomeTab.Map
                  goTo(Main)
                },
                onFindPetClick = { pet -> backStack.add(Find(pet.id, pet.name)) },
                dataVersion = petDataVersion,
              )
            }
          }
          entry<Find> { key ->
            petRepository?.let { repository ->
              FindPetScreen(
                petId = key.petId,
                petName = key.petName,
                petRepository = repository,
                onBack = { backStack.removeLastOrNull() },
              )
            }
          }
          entry<EditPet> { key ->
            petRepository?.let { repository ->
              EditPetScreen(
                petId = key.petId,
                petRepository = repository,
                onSaved = {
                  petDataVersion++
                  backStack.removeLastOrNull()
                },
                onCancel = { backStack.removeLastOrNull() },
              )
            }
          }
          entry<Homes> { key ->
            petRepository?.let { repository ->
              HomesScreen(
                petId = key.petId,
                petName = key.petName,
                petRepository = repository,
                onBack = { backStack.removeLastOrNull() },
                onAddHome = { backStack.add(SetHome(key.petId, homeId = null)) },
                onEditHome = { home -> backStack.add(SetHome(key.petId, homeId = home.id)) },
                dataVersion = petDataVersion,
              )
            }
          }
          entry<SetHome> { key ->
            petRepository?.let { repository ->
              SetHomeScreen(
                petId = key.petId,
                homeId = key.homeId,
                petRepository = repository,
                onSaved = {
                  petDataVersion++
                  backStack.removeLastOrNull()
                },
                onDeleted = {
                  petDataVersion++
                  backStack.removeLastOrNull()
                },
                onCancel = { backStack.removeLastOrNull() },
              )
            }
          }
          entry<Excursions> { key ->
            petRepository?.let { repository ->
              ExcursionsScreen(
                petId = key.petId,
                petName = key.petName,
                petRepository = repository,
                onBack = { backStack.removeLastOrNull() },
                onExcursionClick = { excursion -> backStack.add(ExcursionDetail(key.petId, excursion)) },
              )
            }
          }
          entry<ExcursionDetail> { key ->
            petRepository?.let { repository ->
              ExcursionDetailScreen(
                petId = key.petId,
                excursion = key.excursion,
                petRepository = repository,
                onBack = { backStack.removeLastOrNull() },
              )
            }
          }
          entry<Trail> { key ->
            petRepository?.let { repository ->
              TrailScreen(
                petId = key.petId,
                petName = key.petName,
                petRepository = repository,
                onBack = { backStack.removeLastOrNull() },
                onExcursionClick = { excursion -> backStack.add(ExcursionDetail(key.petId, excursion)) },
              )
            }
          }
          entry<SharePet> { key ->
            petRepository?.let { repository ->
              SharePetScreen(
                petId = key.petId,
                petName = key.petName,
                petRepository = repository,
                onShared = { backStack.removeLastOrNull() },
                onCancel = { backStack.removeLastOrNull() },
              )
            }
          }
        },
    )

    // Hidden on Login: there's nothing to switch tabs between before signing in.
    if (currentSession != null) {
      AppBottomBar(
        selectedTab = selectedTab,
        onTabSelected = { tab ->
          selectedTab = tab
          goTo(Main)
        },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}
