package com.headunit.launcher

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nCoreCoverageTest {
    private val coreKeys = listOf(
        "Offline maps", "Download", "Delete", "Edit", "Done", "Yes", "No",
        "Route", "Routes", "left", "Send on GitHub", "Copy", "Try again",
        "Couldn't open that file", "That isn't a GPX file", "That file has no track in it",
        "Low bridge", "Weight limit", "Width limit", "Length limit",
        "Fuel prices", "Avoid toll roads", "Avoid motorways", "Avoid ferries",
        "Add stop", "Save place", "Search for a place", "Map style", "Satellite",
        "This head unit can't draw the Mapbox map", "Horizon Nav keeps closing while it starts",
        "Horizon Nav needs OpenGL ES 3.0 graphics, and this unit has version {0}. Versions of Horizon Nav from before the Mapbox map work on older graphics, if you still have one.",
        "Horizon Nav froze or closed unexpectedly. To be safe, the map now draws in a gentler way, at 30 frames a second, without the heaviest 3D extras. If it happens again, please send a feedback report from Settings > About & backup",
        "Horizon Nav froze or closed again, so the map is now on Lite with a lower sharpness. Please send a feedback report from Settings > About & backup so this unit can be fixed",
        "Horizon Nav keeps freezing or closing, so the map is now on its lightest setting. Please send a feedback report from Settings > About & backup so this unit can be fixed",
        "Horizon Nav had trouble starting, so the map is on Lightest. You can change it in Settings > Map style",
        "Horizon Nav had trouble starting, so the heaviest 3D map features are off. You can turn them back on in Settings > Light & 3D and On the map",
        "step", "steps",
        "Couldn't read a place from that", "{0} ahead",
        "Town / city", "Village / area", " · offline", "Postcode", "Address",
        "Show lane data diagnostics", "Only shown in Demo mode; does not affect lane guidance.",
        "Sorry about that. Sending the report below lets it be fixed for this head unit. Nothing personal is in it.",
        "Already saving {0}: wait for it to finish, or stop it",
        "Starting…",
        "Saving {0}% · {1} MB",
        "Saved: {0}",
        "Too big for Mapbox's offline limit: try a smaller distance, or delete a saved area",
        "Stopped",
        "Couldn't save the map",
        "Pack ready",
        "Download failed. Tap Download again to carry on.",
        "Checking the {0} pack…",
        "The {0} pack isn't available yet. It appears here once it has been built.",
        "Couldn't reach the download. Check the connection and try again.",
        "Already have {0}",
        "Downloading {0}…",
        "Downloading {0}… {1} MB",
        "Downloading {0}… {1} MB of {2} MB",
        "Joining {0}…",
        "Required offline map file {0} could not be downloaded",
        "Download finished",
        "No places.tsv: streets and shops will not be searchable offline",
        "No map.pmtiles in this pack",
        "No postcodes.tsv in this pack",
        "No addresses.tsv: house numbers will not be searchable offline",
        "Search index ready",
        "No lamps.tsv: night map will use estimated lamps",
        "No signals.tsv: traffic lights will be fetched online instead",
        "No {0} yet: {1} will be looked up online",
        "The phone ran out of memory planning this route offline",
        "Your starting location is outside the downloaded offline map areas. Download the area containing your current location, or turn off 'Without signal' to plan this route online.",
        "The starting location is outside the downloaded offline map areas. Download the area containing your current location, or turn off 'Without signal'.",
        "No offline route could be built from the downloaded areas",
        "No offline route from here towards the border of the downloaded area",
        "The starting location is outside the offline map area {0}. Download the area containing your current location, or turn off 'Without signal'.",
        "No offline pack installed",
        "No offline route",
        "No offline route found",
        "No offline route found. Check both places are inside your downloaded maps",
        "speed limits",
        "speed cameras",
        "Couldn't join your two offline map areas at their border. Try updating both",
        "This offline map area needs downloading again",
        "The destination is outside your offline maps",
        "The destination is outside your offline maps, and no road towards it was found in them",
        "No road found near the start or the destination in your offline maps",
        "Sound: all",
        "Sound: alerts only",
        "Sound: off",
        "TomTom turned down the key for its map, so Mapbox's map is showing",
        "Add a TomTom key in Settings > Keys & accounts for TomTom's map. Showing Mapbox's map",
        "Checking…",
        "Couldn't check: no connection",
        "You have the latest version",
        "Version {0} is available",
        "No signal, and nothing saved or downloaded matches",
        "No signal. Download the offline pack or place names in Settings > Offline maps to search with no signal",
        "Search failed: weak or no signal",
        "Mapbox returned no turn instructions for this route",
        "This key returned no turn instructions. Check it has TomTom Routing API access",
        "Alternative routes on",
        "Alternative routes off",
        "Rerouting: weak signal, still trying…",
        "Reroute failed ({0}), trying again…",
        "Nothing found along the route ahead",
        "Nothing found nearby",
    )

    @Test fun incompleteLanguagePacksTranslateCoreInterfaceActions() {
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                for (key in coreKeys) {
                    val value = L.t(key)
                    assertTrue("$code is missing '$key'", value.isNotBlank())
                    // Catalan legitimately spells the negative response exactly like English.
                    if (!(code == "ca" && key == "No")) {
                        assertNotEquals("$code still falls back for '$key'", key, value)
                    }
                }
            }
        } finally {
            L.set("en")
        }
    }

    @Test fun eachShortPackHasUniqueKeysAndPreservesTemplatePlaceholders() {
        val packs = listOf("ca" to I18nCa.rows(), "ko" to I18nKo.rows(), "ta" to I18nTa.rows(), "te" to I18nTe.rows())
        val placeholder = Regex("""\{\d+}""")
        for ((code, rows) in packs) {
            assertTrue("$code has duplicate translation keys", rows.map { it.first }.distinct().size == rows.size)
            for ((source, translated) in rows) {
                assertTrue("$code has an empty translation for '$source'", translated.isNotBlank())
                assertTrue("$code changed placeholders in '$source'", placeholder.findAll(source).map { it.value }.toSet() == placeholder.findAll(translated).map { it.value }.toSet())
            }
        }
    }

    @Test fun supplementalShortPackLabelsAreTranslated() {
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                for (key in listOf("Sort favourites", "Nearest", "Show lane data diagnostics",
                    "Only shown in Demo mode; does not affect lane guidance.")) {
                    assertNotEquals("$code still falls back for '$key'", key, L.t(key))
                }
                val diagnostics = I18nMapDiagnostics.rows(code).toMap()
                assertTrue("$code is missing lane diagnostics", diagnostics["Show lane data diagnostics"].orEmpty().isNotBlank())
                assertTrue("$code is missing lane diagnostics explanation", diagnostics["Only shown in Demo mode; does not affect lane guidance."].orEmpty().isNotBlank())
                val favourites = I18nFavouriteSort.rows(code).toMap()
                assertNotEquals("$code favourite sort label falls back", "Sort favourites", favourites["Sort favourites"])
                assertNotEquals("$code nearest label falls back", "Nearest", favourites["Nearest"])
            }
        } finally {
            L.set("en")
        }
    }

    @Test fun arrivalAndDistanceTemplatesKeepTheirDynamicValues() {
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                val arrival = L.t("Arrive at {0}", "Central Station")
                assertTrue("$code lost the destination placeholder", arrival.contains("Central Station"))
                assertNotEquals("$code arrival prompt fell back to English", "Arrive at Central Station", arrival)
                assertNotEquals("$code remaining label fell back to English", "left", L.t("left"))
            }
        } finally {
            L.set("en")
        }
    }
}
