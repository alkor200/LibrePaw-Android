package org.librepaw.app

import android.app.Application
import org.maplibre.android.MapLibre

class LibrePawApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    MapLibre.getInstance(this)
  }
}
