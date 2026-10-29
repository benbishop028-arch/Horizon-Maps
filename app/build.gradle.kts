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
        targetSdk = 34
        // each GitHub build gets a higher version number, so Android always treats a new APK as an update
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + run
        versionName = "1.0.$run"
        resValue("string", "app_name", "Horizon Nav")
        // only the chips head units and phones use (the Mapbox map comes with a copy for each, so this keeps the APK much smaller)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Sign every build with the same key: the signing.keystore file in your GitHub repo (found by looking up from the project folder).
    // Android only installs a new APK over the old one when both are signed with the same key.
    val repoKeystore = generateSequence(rootDir) { it.parentFile }.take(6).map { File(it, "signing.keystore") }.firstOrNull { it.isFile }
    signingConfigs {
        if (repoKeystore != null) create("stable") {
            storeFile = repoKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    // A release build runs noticeably faster than the debug one (no debug overhead, and the libraries' built-in speed-up profiles are applied).
    // It is signed with the debug key so it installs like before; code shrinking stays off to avoid breaking the map library.
    buildTypes {
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
    implementation("com.mapbox.maps:android:11.32.1")
    // Android Auto: Google's car app library (the navigation layout, search and list screens, and the map surface)
    implementation("androidx.car.app:app:1.4.0")
    // Offline routing engine (Valhalla) that runs on downloaded road data, with the libraries it needs at run time
    implementation("io.github.rallista:valhalla-mobile:0.6.4")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Small, fast JVM tests for camera and GPS decision policies; these don't require a device.
    testImplementation("junit:junit:4.13.2")
}

// After a release build, put the APK in one folder with a clear name (HorizonNav-public.apk), where the GitHub build picks it up.
val collectApks by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/public/release")) { include("*.apk"); rename { "HorizonNav-public.apk" } }
    into(layout.buildDirectory.dir("outputs/apk/release"))
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(collectApks) }
