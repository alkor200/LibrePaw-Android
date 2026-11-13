# R8 rules for the release build.
#
# Most libraries here ship their own consumer rules (OkHttp, MapLibre, Tink via
# security-crypto, kotlinx.serialization for classes with a generated
# serializer), so this file only covers what they can't know about.

# Navigation back stack keys are @Serializable NavKeys, saved and restored
# through their generated serializers (rememberNavBackStack), and so are the
# session, cache and excursion models persisted as JSON. kotlinx.serialization's
# own rules keep the generated `serializer()` of every @Serializable class;
# keeping the classes' names as well keeps saved instance state and the JSON
# already on disk readable across releases.
-keepnames @kotlinx.serialization.Serializable class org.librepaw.app.** { *; }

# Keep line numbers for readable crash reports, hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tink (pulled in by security-crypto, only used to migrate legacy sessions)
# references Error Prone's compile-time-only annotations, which aren't on the
# runtime classpath and don't need to be.
-dontwarn com.google.errorprone.annotations.**
