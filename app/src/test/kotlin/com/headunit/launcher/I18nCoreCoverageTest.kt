package com.headunit.launcher

import org.junit.Assert.assertEquals
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
        "No signal: showing saved places and offline towns",
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
        "{0} saved: {1}",
        "Switched: about {0} min quicker",
        "Switched: about {0} min longer",
        "Switched route",
        "Leave now to arrive by {0}",
        "Leave by {0} to arrive by {1}. I'll remind you.",
        "Free space: {0}", "arrive {0}",
        "Weather check failed: {0}. Using Open-Meteo instead.", "OK {0}: {1} (Met Office code {2}), {3}°C, rain {4} mm/h, for {5}{6}", "near {0}", "Trace rain", "Clear night", "Sunny", "Partly cloudy", "Mist", "Fog", "Cloudy", "Overcast", "Light rain shower", "Drizzle", "Light rain", "Heavy rain shower", "Heavy rain", "Sleet shower", "Sleet", "Hail shower", "Hail", "Light snow shower", "Light snow", "Heavy snow shower", "Heavy snow", "Thunder shower", "Thunder", "Unknown", "Prices updated {0} · {1} stations", "Fuel prices failed: {0}", "just now", "{0} h ago", "{0} days ago", "Super unleaded", "Diesel", "Bioethanol (E85)", "Electric", "Unleaded", "No zones found in the map data", "{0} zones: {1}", "Zone download failed: {0}", "Low emission zone", "Google Play billing isn't available: {0}", "Plus isn't on sale yet",
        "{0} report saved here", "Entering {0}", "Emergency: {0} · {1} {2}",
        "Typical car limits (urban / rural / motorway): {0}. Emergency: {1}. Follow local signs; limits can vary by road, conditions and vehicle.",
    )

    @Test fun incompleteLanguagePacksTranslateCoreInterfaceActions() {
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                for (key in coreKeys) {
                    val value = L.t(key)
                    assertTrue("$code is missing '$key'", value.isNotBlank())
                    // Catalan legitimately spells the negative response exactly like English.
                    // These two source strings are valid unchanged Catalan wording.
                    if (!(code == "ca" && key in setOf("No", "{0} zones: {1}"))) {
                        assertNotEquals("$code still falls back for '$key'", key, value)
                    }
                }
            }
        } finally {
            L.set("en")
        }
    }


    @Test fun firstStageThreeAuditBatchHasTranslatedLabelsAndPreservesPlaceholders() {
        val expected = mapOf(
            "nl" to listOf("Stops", "+ Stop", "Version"),
            "de" to listOf("Auto", "Feedback"),
            "fr" to listOf("Parking"),
            "es" to listOf("Auto", "Demo"),
            "it" to listOf("Auto", "Hatchback", "Demo")
        )
        val placeholder = Regex("""\{\d+}""")
        try {
            for ((code, keys) in expected) {
                L.set(code)
                for (key in keys) {
                    assertNotEquals("$code still falls back for '$key'", key, L.t(key))
                }
                for ((source, translated) in I18nAuditBatch1.rows(code)) {
                    assertTrue("$code changed placeholders in '$source'",
                        placeholder.findAll(source).map { it.value }.toSet() ==
                            placeholder.findAll(translated).map { it.value }.toSet())
                }
            }
        } finally {
            L.set("en")
        }
    }


    @Test fun secondStageThreeAuditBatchHasTranslatedLabelsAndPreservesPlaceholders() {
        val expected = mapOf(
            "pt" to listOf("Auto", "Hatchback"),
            "pl" to listOf("Auto"),
            "ro" to listOf("Auto"),
            "eu" to listOf("Auto"),
            "sl" to listOf("Pickup")
        )
        val placeholder = Regex("""\{\d+}""")
        try {
            for ((code, keys) in expected) {
                L.set(code)
                for (key in keys) {
                    assertNotEquals("$code still falls back for '$key'", key, L.t(key))
                }
                for ((source, translated) in I18nAuditBatch2.rows(code)) {
                    assertTrue("$code changed placeholders in '$source'",
                        placeholder.findAll(source).map { it.value }.toSet() ==
                            placeholder.findAll(translated).map { it.value }.toSet())
                }
            }
        } finally {
            L.set("en")
        }
    }

    @Test fun navigationNotificationLabelsAreTranslatedInEveryShortPack() {
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                for (key in listOf("Navigating", "Shown while a route is running, with the next turn",
                    "At the roundabout", "Continue", "Head off", "Exit left", "Exit right", "Arrive {0}")) {
                    assertNotEquals("$code missing translation: $key", key, L.t(key, 2))
                }
                assertTrue(L.t("Take exit {0}", 2).contains("2"))
                assertTrue(L.t("Arrive {0}", "18:30").contains("18:30"))
            }
        } finally {
            L.set("en")
        }
    }

    @Test fun androidAutoReportsAndBorderNotificationsAreLocalisedInEveryShortPack() {
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                val report = L.t("{0} report saved here", "GPS")
                assertTrue("$code lost the report name", report.contains("GPS"))
                assertNotEquals("$code report confirmation fell back", "GPS report saved here", report)
                val border = BorderDrivingInfo.forCountry("GB")!!.notificationText()
                assertTrue("$code border notice lost speed limits", border.contains("30 / 60 / 70 mph"))
                assertTrue("$code border notice lost emergency number", border.contains("999 or 112"))
                assertTrue("$code border notice fell back to English", !border.startsWith("Typical car limits"))
            }
        } finally {
            L.set("en")
        }
    }

    @Test fun weatherFuelZonesAndBillingLabelsExistInEveryShortPack() {
        val packs = listOf("ca" to I18nCa.rows(), "ko" to I18nKo.rows(), "ta" to I18nTa.rows(), "te" to I18nTe.rows())
        for ((code, rows) in packs) {
            val translations = rows.toMap()
            for (key in listOf("Weather check failed: {0}. Using Open-Meteo instead.", "OK {0}: {1} (Met Office code {2}), {3}°C, rain {4} mm/h, for {5}{6}",
                "Fuel prices failed: {0}", "Prices updated {0} · {1} stations", "{0} h ago", "{0} days ago",
                "No zones found in the map data", "{0} zones: {1}", "Zone download failed: {0}",
                "Google Play billing isn't available: {0}", "Plus isn't on sale yet")) {
                assertTrue("$code is missing '$key'", !translations[key].isNullOrBlank())
            }
            for (key in listOf("Sunny", "Heavy rain", "Fog", "Heavy snow", "Diesel", "Electric", "Super unleaded", "LPG")) {
                assertTrue("$code is missing '$key'", !translations[key].isNullOrBlank())
            }
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

    @Test fun offlinePickerGroupsExistInEveryShortPack() {
        val packs = listOf("ca" to I18nCa.rows(), "ko" to I18nKo.rows(), "ta" to I18nTa.rows(), "te" to I18nTe.rows())
        val keys = listOf("UK & Ireland", "Europe", "United States", "Canada",
            "Mexico & Central America", "South America", "Asia", "Africa", "Oceania")
        for ((code, rows) in packs) {
            val translations = rows.toMap()
            for (key in keys) {
                assertTrue("$code is missing offline picker label '$key'", !translations[key].isNullOrBlank())
                // "Oceania" is correctly spelt the same way in Catalan.
                if (!(code == "ca" && key == "Oceania")) {
                    assertNotEquals("$code offline picker label '$key' is still English", key, translations[key])
                }
            }
            val free = translations["Free space: {0}"].orEmpty()
            val arrival = translations["arrive {0}"].orEmpty()
            assertTrue("$code free-space translation lost its placeholder", free.contains("{0}"))
            assertTrue("$code car-arrival translation lost its placeholder", arrival.contains("{0}"))
        }
        try {
            for (code in listOf("ca", "ko", "ta", "te")) {
                L.set(code)
                val free = L.t("Free space: {0}", "2 GB")
                assertTrue("$code lost the free-space value", free.contains("2 GB"))
                assertNotEquals("$code free-space label falls back", "Free space: 2 GB", free)
                val arrival = L.t("arrive {0}", "18:30")
                assertTrue("$code lost the arrival time", arrival.contains("18:30"))
                assertNotEquals("$code car arrival label falls back", "arrive 18:30", arrival)
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

    @Test fun thirdStageThreeAuditBatchHasCorrectAutomaticLabelsAndPreservesPlaceholders() {
        val expected = mapOf(
            "da" to "Automatisk",
            "fi" to "Automaattinen",
            "nb" to "Automatisk",
            "sv" to "Automatiskt",
            "cs" to "Automaticky",
            "hr" to "Automatski",
            "hu" to "Automatikus",
            "sk" to "Automaticky",
            "sr" to "Automatski"
        )
        val placeholder = Regex("""\{\d+}""")
        for ((code, value) in expected) {
            val rows = I18nAuditBatch3.rows(code)
            val translations = rows.toMap()
            assertEquals("$code has the wrong automatic label", value, translations["Auto"])
            assertTrue("$code batch has duplicate keys", rows.map { it.first }.distinct().size == rows.size)
            for ((source, translated) in rows) {
                assertTrue("$code has an empty translation for '$source'", translated.isNotBlank())
                assertEquals("$code changed placeholders in '$source'",
                    placeholder.findAll(source).map { it.value }.toSet(),
                    placeholder.findAll(translated).map { it.value }.toSet())
            }
        }
        assertEquals("Škola", I18nAuditBatch3.rows("sr").toMap()["School"])
        assertEquals("Veb-sajt", I18nAuditBatch3.rows("sr").toMap()["Website"])
    }
}
