package org.librepaw.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.librepaw.app.data.auth.AuthenticationService
import org.librepaw.app.data.auth.StoredSession
import org.librepaw.app.data.settings.AppearancePreferences
import org.librepaw.app.ui.auth.LoginScreen
import org.librepaw.app.ui.editpet.EditPetScreen
import org.librepaw.app.ui.home.HomeScreen
import org.librepaw.app.ui.petdetail.PetDetailScreen

@Composable
fun MainNavigation(authenticationService: AuthenticationService, appearancePreferences: AppearancePreferences) {
  var currentSession by remember { mutableStateOf(authenticationService.restoreSession()) }
  val backStack = rememberNavBackStack(if (currentSession != null) Main else Login)
  val petRepository = currentSession?.let { session -> remember(session) { authenticationService.makePetRepository(session) } }
  var petDataVersion by remember { mutableStateOf(0) }

  fun goTo(key: NavKey) {
    backStack.clear()
    backStack.add(key)
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          LoginScreen(
            authenticationService = authenticationService,
            onLoggedIn = { session: StoredSession ->
              currentSession = session
              goTo(Main)
            },
          )
        }
        entry<Main> {
          val session = currentSession
          val repository = petRepository
          if (session != null && repository != null) {
            HomeScreen(
              session = session,
              petRepository = repository,
              appearancePreferences = appearancePreferences,
              onPetClick = { pet -> backStack.add(PetDetail(pet.id)) },
              onLogOut = {
                authenticationService.logOut()
                currentSession = null
                goTo(Login)
              },
              dataVersion = petDataVersion,
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
              dataVersion = petDataVersion,
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
      },
  )
}
