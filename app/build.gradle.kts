plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "org.librepaw.app"
  compileSdk = 36
  defaultConfig {
    applicationId = "org.librepaw.app"
    // Android 7.1.1. Not 7.0: it trusts neither of Let's Encrypt's ISRG roots and, since Let's
    // Encrypt dropped the old DST cross-sign in 2024, can't validate any Let's Encrypt certificate,
    // so it can't connect to almost any self-hosted Traccar server (confirmed on an emulator).
    minSdk = 25
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  // Signed only when a keystore is configured outside the repository, e.g. in
  // ~/.gradle/gradle.properties (see README, "Releasing"). Without one, release builds stay
  // unsigned, exactly as before; nothing secret ever goes into this repository.
  val releaseKeystore = providers.gradleProperty("librepaw.release.storeFile").orNull
  if (releaseKeystore != null) {
    signingConfigs {
      create("release") {
        storeFile = file(releaseKeystore)
        storePassword = providers.gradleProperty("librepaw.release.storePassword").get()
        keyAlias = providers.gradleProperty("librepaw.release.keyAlias").get()
        keyPassword = providers.gradleProperty("librepaw.release.keyPassword").get()
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
    }
  }

  // One APK per CPU architecture (plus a universal one) for direct downloads, instead of a single
  // 52 MB APK half of which is MapLibre's native libraries for the other architectures, x86 and
  // x86_64 alone being ~26 MB that only emulators use. Only for release APK builds: the Play Store
  // gets an App Bundle (bundleRelease), which it splits per device itself, and debug builds stay a
  // single APK for the emulator.
  splits {
    abi {
      // Never together with a bundle in the same Gradle invocation, AGP refuses that
      // (https://issuetracker.google.com/402800800).
      val tasks = gradle.startParameter.taskNames
      isEnable =
        tasks.any { it.contains("assemble", ignoreCase = true) && it.contains("Release", ignoreCase = true) } &&
          tasks.none { it.contains("bundle", ignoreCase = true) }
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
      isUniversalApk = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  // Per-app language (the in-app language picker), see AppLanguage
  implementation(libs.androidx.appcompat)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Local tests: jUnit, coroutines, a local HTTP server
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Networking (Traccar REST client)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)

  // Reading a picked photo's EXIF orientation, see ImageDecoding
  implementation(libs.androidx.exifinterface)

  // Secure credential storage
  implementation(libs.androidx.security.crypto)

  // Map (OpenFreeMap tiles, see ADR-0028 on the iOS side). The OpenGL ES build, not MapLibre's
  // default android-sdk artifact: that one renders with Vulkan only, no fallback, and crashes
  // with "No Vulkan compatible GPU found" on any device without usable Vulkan (common below
  // Android 10 and on budget hardware), on the map, the very first screen.
  implementation(libs.maplibre.android.sdk)
}
