pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

// Mapbox's download token (a secret token with the DOWNLOADS:READ scope). The GitHub build passes it in from the repo's secret
// MAPBOX_DOWNLOADS_TOKEN; it is never kept in the project. Mapbox's current guide downloads the SDK without one, so when it isn't
// set the repository is used without signing in.
val mapboxDownloadsToken: String = (System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull ?: "").trim()

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repositories.tomtom.com/artifactory/maven") }
        // the Mapbox Maps SDK
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            if (mapboxDownloadsToken.isNotEmpty()) {
                credentials {
                    username = "mapbox"
                    password = mapboxDownloadsToken
                }
                authentication { create<org.gradle.authentication.http.BasicAuthentication>("basic") }
            }
        }
    }
}
rootProject.name = "HorizonNav"
include(":app")
