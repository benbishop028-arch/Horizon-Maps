package com.headunit.launcher

/**
 * Stage 3 localisation audit, batch 2.
 *
 * Targeted whole-phrase corrections for Portuguese, Polish, Romanian, Basque and Slovenian.
 * Kept separate from generated language tables so each audit batch can be reviewed independently.
 */
object I18nAuditBatch2 {
    fun rows(code: String): List<Pair<String, String>> = when (code) {
        "pt" -> listOf(
            "Auto" to "Automático",
            "Hatchback" to "Carro de dois volumes"
        )
        "pl" -> listOf(
            "Auto" to "Automatycznie"
        )
        "ro" -> listOf(
            "Auto" to "Automat"
        )
        "eu" -> listOf(
            "Auto" to "Automatikoa"
        )
        "sl" -> listOf(
            "Pickup" to "Poltovornjak"
        )
        else -> emptyList()
    }
}
