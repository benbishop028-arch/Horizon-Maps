package com.headunit.launcher

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nCoreCoverageTest {
    private val coreKeys = listOf(
        "Offline maps", "Download", "Delete", "Edit", "Done", "Yes", "No",
        "Route", "Routes", "left", "Send on GitHub", "Copy", "Try again",
        "Couldn't open that file", "That isn't a GPX file", "That file has no track in it",
        "Low bridge", "Weight limit", "Width limit", "Length limit"
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
