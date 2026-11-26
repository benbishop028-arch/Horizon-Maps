package com.headunit.launcher

/**
 * Stage 3 localisation audit, batch 3.
 *
 * Corrects the ambiguous automatic-mode label in Nordic and selected Eastern European
 * languages, and keeps Serbian labels consistently in Latin script.
 */
object I18nAuditBatch3 {
    fun rows(code: String): List<Pair<String, String>> = when (code) {
        "da" -> listOf("Auto" to "Automatisk")
        "fi" -> listOf("Auto" to "Automaattinen")
        "nb" -> listOf("Auto" to "Automatisk")
        "sv" -> listOf("Auto" to "Automatiskt")
        "cs" -> listOf("Auto" to "Automaticky")
        "hr" -> listOf("Auto" to "Automatski")
        "hu" -> listOf("Auto" to "Automatikus")
        "sk" -> listOf("Auto" to "Automaticky")
        "sr" -> listOf(
            "Auto" to "Automatski",
            "School" to "Škola",
            "Website" to "Veb-sajt"
        )
        else -> emptyList()
    }
}
