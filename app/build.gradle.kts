plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val tomtomApiKey: String = ((findProperty("tomtomApiKey") as String?) ?: "")
    .takeUnless { it.isBlank() || it == "YOUR_TOMTOM_API_KEY" } ?: ""
val escapedTomtomApiKey = tomtomApiKey.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.headunit.launcher"
    compileSdk = 36

    defaultConfig {
        // Keep the established application ID so releases install as updates to existing Horizon Nav installations.
        applicationId = "com.headunit.nav"
        minSdk = 26
        targetSdk = 36
        // each GitHub build gets a higher version number, so Android always treats a new APK as an update
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + run
        versionName = "1.0.$run"
        resValue("string", "app_name", "Horizon Nav")
        // only the chips head units and phones use (the Mapbox map comes with a copy for each, so this keeps the APK much smaller)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Sign every build with the same key: the signing.keystore the build puts next to the project from GitHub Secrets (found by
    // looking up from the project folder). Android only installs a new APK over the old one when both are signed with the same key.
    // The passwords and alias come from GitHub Secrets too (the build passes them in); they're no longer written here. After
    // Gradle, the build signs again with the new key and the key-rotation record (see .github/workflows/build.yml), so phones on
    // Android 9 and newer move to the new key while older ones keep updating.
    val repoKeystore = generateSequence(rootDir) { it.parentFile }.take(6).map { File(it, "signing.keystore") }.firstOrNull { it.isFile }
    fun secret(name: String, fallback: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback
    signingConfigs {
        if (repoKeystore != null) create("stable") {
            storeFile = repoKeystore
            // (the fallback is the old key's well-known debug password, for a build made without the secrets)
            storePassword = secret("SIGNING_STORE_PASSWORD", "android")
            keyAlias = secret("SIGNING_KEY_ALIAS", "androiddebugkey")
            keyPassword = secret("SIGNING_KEY_PASSWORD", "android")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    // A release build runs noticeably faster than the debug one (no debug overhead, and the libraries' built-in speed-up profiles are applied).
    // It is signed with the debug key so it installs like before; code shrinking stays off to avoid breaking the map library.
    buildTypes {
        // Local test builds install beside the released app (com.headunit.nav.public.dev) under their own name, so the
        // real one needn't be uninstalled to try a change on a phone or the Desktop Head Unit.
        debug {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Horizon Nav Dev")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("stable") ?: signingConfigs.getByName("debug")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
    // The public flavor suffix gives the package ID com.headunit.nav.public, preserving update compatibility with existing releases.
    flavorDimensions += "key"
    productFlavors {
        create("public") {
            dimension = "key"
            applicationIdSuffix = ".public"
            // No TomTom key is ever built into the app: anyone can pull it out of a shared APK and run up the bill on it.
            // People paste their own in Settings > Data (or the app uses Mapbox).
            buildConfigField("String", "TOMTOM_API_KEY", "\"\"")
            buildConfigField("String", "DEFAULT_MAP", "\"mapbox\"")
            // where the build's "publish" option puts new versions (the "app" release); the app checks it for updates
            buildConfigField("String", "UPDATE_REPO", "\"benbishop028-arch/Horizon-Maps\"")
            // the APK file the updater looks for in that release
            buildConfigField("String", "UPDATE_APK", "\"HorizonNav-public.apk\"")
            // the GitHub edition updates itself from GitHub
            buildConfigField("Boolean", "SELF_UPDATE", "true")
            buildConfigField("String", "STORE", "\"github\"")
            // (the GitHub edition has no subscription: people use their own keys)
            buildConfigField("String", "KEY_SERVER", "\"\"")
        }
        // The Google Play edition: the same app as its own package (com.headunit.nav.play), so it installs beside the GitHub one.
        // Play only allows apps to update through Play, so it has no updater (and no permission to install apps), no PayPal
        // donations (Play's payment rules), and no "how to get it into Android Auto" help (Android Auto lists Play apps by itself).
        // Only built when asked for (-PwithPlay, the build's publish run), so ordinary builds take no longer.
        create("play") {
            dimension = "key"
            applicationIdSuffix = ".play"
            // called Horizon Maps on the home screen, in Android Auto and in its own messages, so it's easy to tell from the GitHub one
            resValue("string", "app_name", "Horizon Maps")
            buildConfigField("String", "TOMTOM_API_KEY", "\"\"")
            buildConfigField("String", "DEFAULT_MAP", "\"mapbox\"")
            // (kept for the feedback and issue links, which still go to GitHub)
            buildConfigField("String", "UPDATE_REPO", "\"benbishop028-arch/Horizon-Maps\"")
            buildConfigField("String", "UPDATE_APK", "\"\"")
            buildConfigField("Boolean", "SELF_UPDATE", "false")
            buildConfigField("String", "STORE", "\"play\"")
            // The Horizon Maps Plus key server (see server/README.md). Until its address is filled in, the Play edition works as it
            // does now (your own keys), so the subscription stays switched off while it's being set up.
            buildConfigField("String", "KEY_SERVER", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // the Mapbox map and the offline router (Valhalla) both bring the C++ runtime: one copy is kept
        jniLibs.pickFirsts += "**/libc++_shared.so"
    }
}

// The Play edition is only built when asked for: -PwithPlay (otherwise "assembleRelease" would build it too and the GitHub build would
// take twice as long)
androidComponents {
    beforeVariants { v -> if (v.flavorName == "play" && !project.hasProperty("withPlay")) v.enable = false }
}

kotlin { jvmToolchain(17) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // QR codes (the donate card's code, scanned with a phone)
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // The map: Mapbox Maps SDK (v11, Standard style). Needs OpenGL ES 3.0; the app checks for it at start-up.
    // (the "-ndk27" build, which Mapbox makes for 16 KB memory pages: Google Play now requires apps to support them, and Play Console
    // showed this app as not supporting them because the standard build's map libraries are made for 4 KB pages)
    implementation("com.mapbox.maps:android-ndk27:11.32.1")
    // Android Auto: Google's car app library (the navigation layout, search and list screens, and the map surface)
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    // Offline routing engine (Valhalla) that runs on downloaded road data, with the libraries it needs at run time
    implementation("io.github.rallista:valhalla-mobile:0.6.4")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Google Play edition only: Google's billing library, for the Horizon Maps subscription. (It also tells Play Console the app
    // sells something, which Play needs to see in an uploaded build before a subscription can be set up there.) The GitHub edition
    // doesn't include it.
    "playImplementation"("com.android.billingclient:billing:9.1.0")

    // Small, fast JVM tests for camera and GPS decision policies; these don't require a device.
    testImplementation("junit:junit:4.13.2")
}

// After a release build, put the APK in one folder with a clear name (HorizonNav-public.apk), where the GitHub build picks it up.
val collectApks by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/public/release")) { include("*.apk"); rename { "HorizonNav-public.apk" } }
    into(layout.buildDirectory.dir("outputs/apk/release"))
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(collectApks) }
