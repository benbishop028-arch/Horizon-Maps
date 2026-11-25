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
        "Sorry about that. Sending the report below lets it be fixed for this head unit. Nothing personal is in it."
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
