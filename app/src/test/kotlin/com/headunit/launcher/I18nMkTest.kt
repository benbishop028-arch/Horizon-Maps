package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nMkTest {
    private val placeholder = Regex("\\{\\d\\}")

    @Test fun macedonianIsOffered() {
        assertTrue(L.supported.any { it.first == "mk" })
    }

    @Test fun everyMacedonianPhraseKeepsItsPlaceholdersAndSpaces() {
        for ((en, mk) in I18nMk.rows() + I18nMkExtra.rows()) {
            assertEquals("placeholders in '$en'", placeholder.findAll(en).map { it.value }.sorted().toList(), placeholder.findAll(mk).map { it.value }.sorted().toList())
            assertEquals("leading space in '$en'", en.startsWith(" "), mk.startsWith(" "))
            assertEquals("trailing space in '$en'", en.endsWith(" "), mk.endsWith(" "))
            assertTrue("empty translation for '$en'", mk.isNotBlank())
        }
    }

    @Test fun macedonianPackHasNoDuplicates() {
        val keys = I18nMk.rows().map { it.first }
        assertEquals(keys.size, keys.toSet().size)
    }
}
