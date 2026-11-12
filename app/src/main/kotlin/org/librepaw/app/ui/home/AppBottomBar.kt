package org.librepaw.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * How much bottom clearance a screen's own scrollable content needs to reserve so its last item
 * can still scroll fully clear of the floating bar: since [org.librepaw.app.MainNavigation] now
 * overlays the bar on top of full-size content rather than reserving space for it in a Scaffold,
 * each screen needs to add this itself (its own Scaffold's innerPadding knows nothing about a bar
 * living one level up). Matches the bar's own vertical padding (12dp) + content padding (10dp) +
 * icon/label height (roughly 42dp) + a little breathing room.
 */
val BottomBarClearance = 96.dp

/**
 * A floating, pill-shaped tab bar, always visible across every logged-in screen (not just
 * [HomeScreen] itself, see [org.librepaw.app.MainNavigation]) so switching tabs never loses its
 * place the way a plain full-width [androidx.compose.material3.NavigationBar] would while buried
 * in a pushed screen like Pet Detail.
 */
@Composable
fun AppBottomBar(selectedTab: HomeTab, onTabSelected: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TabItem(
        icon = Icons.Filled.Place,
        label = "Map",
        selected = selectedTab == HomeTab.Map,
        onClick = { onTabSelected(HomeTab.Map) },
        modifier = Modifier.weight(1f),
      )
      TabItem(
        icon = Icons.Filled.Pets,
        label = "Pets",
        selected = selectedTab == HomeTab.Pets,
        onClick = { onTabSelected(HomeTab.Pets) },
        modifier = Modifier.weight(1f),
      )
      TabItem(
        icon = Icons.Filled.Settings,
        label = "Settings",
        selected = selectedTab == HomeTab.Settings,
        onClick = { onTabSelected(HomeTab.Settings) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val containerColor by
    animateColorAsState(
      if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
      label = "tabItemContainerColor",
    )
  val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

  Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = containerColor, onClick = onClick) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(icon, contentDescription = label, tint = contentColor)
      Text(label, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
  }
}
