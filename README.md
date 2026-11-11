# LibrePaw (Android)

The Android counterpart to [LibrePaw](https://github.com/alkor200/LibrePaw), an
open source pet GPS tracker client for self-hosted
[Traccar](https://www.traccar.org/) servers.

This is a fresh scaffold, not yet a port of the iOS app's features.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Navigation 3 (`androidx.navigation3`)
- MVVM: `ViewModel` + `StateFlow`, matching the iOS app's own MVVM approach
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

## Requirements

- **JDK 17, specifically** (not whatever is newest/default on the machine):
  confirmed live, building under JDK 27 (the Homebrew default `openjdk`
  formula pulled in as a `gradle` dependency) fails outright with
  `Unsupported class file major version 71`, the `android` CLI's own
  bundled Gradle distribution doesn't support anything newer than 17 yet.
  `brew install openjdk@17` (the keg-only formula, not the `temurin@17`
  cask, which needs `sudo` to install the pkg) gets a working one without
  admin rights. Export `JAVA_HOME` to it before building:
  ```sh
  export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home
  ```
- Android SDK: command-line tools are enough to build and test from the
  CLI (installed here via `brew install --cask android-commandlinetools`,
  plus Google's own agent-oriented `android` CLI, see
  `~/.claude/skills/android-cli`). Android Studio is the easier path if
  you want the IDE/emulator UI, Compose previews, and a debugger.
  Either way, `local.properties` (not committed, same reasoning as the
  iOS app's `Local.xcconfig`) needs an `sdk.dir` pointing at wherever the
  SDK lives:
  ```
  sdk.dir=/path/to/your/sdk
  ```

## Building and testing

```sh
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug   # build the debug APK
./gradlew test            # unit tests
./gradlew connectedAndroidTest  # instrumented tests, needs a device/emulator
```

## Running on an emulator

```sh
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"
android emulator start medium_phone   # the AVD created for this project
android run app --device=emulator-5554
android screen capture -o screenshot.png
```
