package org.librepaw.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Launches turn-by-turn directions to a coordinate, matching iOS's `NavigationApp.openDirections`
 * in spirit: iOS tries Apple Maps/Google Maps/Waze in turn and lets the user choose when more than
 * one is installed. Android's `google.navigation:` URI is the equivalent "start navigating now"
 * intent most map apps (Google Maps included) register for; if nothing handles it (or handles it
 * but not navigation specifically), falling back to the more universally supported `geo:` URI at
 * least centers a map app on the pet's location. Android's own system disambiguation sheet stands
 * in for iOS's manual confirmationDialog when more than one app can handle either intent.
 */
object NavigationLauncher {
  fun navigateTo(context: Context, latitude: Double, longitude: Double, label: String) {
    val navigationIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$latitude,$longitude"))
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$latitude,$longitude(${Uri.encode(label)})"))
    try {
      context.startActivity(navigationIntent)
    } catch (e: ActivityNotFoundException) {
      try {
        context.startActivity(geoIntent)
      } catch (e: ActivityNotFoundException) {
        // No app on the device can handle either intent; nothing reasonable to fall back to.
      }
    }
  }
}
