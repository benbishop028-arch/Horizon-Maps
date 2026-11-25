package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nNavigationPromptsTest {
    @Test fun everySupportedLanguageHasALocalisedArrivalPrompt() {
        for ((code, name) in L.supported) {
            if (code == "en") continue
            L.set(code)
            val translated = L.t("Arrive at {0}", "Central Station")
            assertNotEquals("$name still falls back to English", "Arrive at Central Station", translated)
            assertTrue("$name lost the place name", translated.contains("Central Station"))
        }
        L.set("en")
    }

    @Test fun missingIncidentPromptsAreTranslatedWithTheirArguments() {
        val distanceTemplates = mapOf(
            "ko" to "{1}{2} 앞에 {0}",
            "ta" to "{1}{2} தூரத்தில் {0}",
            "te" to "{1}{2} దూరంలో {0}"
        )
        for ((code, template) in distanceTemplates) {
            val rows = I18nNavigationPrompts.rows(code).toMap()
            assertEquals(template, rows["{0} ahead in {1}{2}"])
            assertTrue(rows[", about {0} minutes delay"]?.contains("{0}") == true)
            assertTrue(rows[" onto {0}"]?.contains("{0}") == true)
            assertTrue(rows["Road closed ahead. Finding a way round."]?.isNotBlank() == true)
            assertTrue(rows["Looking for a way round…"]?.isNotBlank() == true)
        }
    }
}
