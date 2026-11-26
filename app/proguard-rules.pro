# Rules for R8, which shrinks the release APK by removing code and resources the app never uses.
# (The libraries bring their own rules: Mapbox, Android Auto, Compose, Play billing. These cover what they don't.)

# Shrink only: don't rename classes. The problem report, background log and Android Auto diagnostics name the classes and
# lines that did something, and they must stay readable without a mapping file. Most of the size saving comes from removing
# unused code (above all the big icon library), not from shorter names.
-dontobfuscate
# No optimising either: it merges small functions into their callers, which changes the class and line numbers those logs
# show (e.g. "RouteSync:84 < NavEngine:3191" in the problem report's "Last routes ended").
-dontoptimize
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Offline routing (Valhalla): native code calls into these classes, and the engine turns its requests and answers into
# objects with Moshi by reflection, so nothing in them may be removed or changed.
-keep class com.valhalla.** { *; }
-keep class io.github.rallista.** { *; }
-dontwarn com.valhalla.**
-dontwarn io.github.rallista.**

# Moshi's Kotlin reflection (used by the engine above, and by the offline engine test in Settings, which finds it by name)
-keep class com.squareup.moshi.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn com.squareup.moshi.**
-dontwarn org.jetbrains.annotations.**
