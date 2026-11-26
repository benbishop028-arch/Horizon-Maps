package com.headunit.launcher

/**
 * Stage 3 localisation audit, batch 1.
 *
 * These whole-phrase corrections replace English fallback labels in the Dutch, German,
 * French, Spanish and Italian packs. Kept separate from the generated language tables
 * so later audit batches can be reviewed and tested without disturbing existing entries.
 */
object I18nAuditBatch1 {
    fun rows(code: String): List<Pair<String, String>> = when (code) {
        "nl" -> listOf(
            "Incident" to "Voorval",
            "via {0}" to "via {0}",
            "Start" to "Start",
            "Stop" to "Stoppen",
            "Via {0}" to "Via {0}",
            "Auto" to "Automatisch",
            "Stops" to "Haltes",
            "+{0} min" to "+{0} min",
            "Routes" to "Routes",
            "Later" to "Later",
            "+ Stop" to "+ Tussenstop",
            "Hatchback" to "Hatchback",
            "Media" to "Media",
            "Volume" to "Volume",
            "Diesel" to "Diesel",
            "Demo" to "Demo",
            "Feedback" to "Feedback",
            "Version" to "Versie",
            "Online" to "Online",
            "1 stop" to "1 tussenstop",
            "{0} stops" to "{0} tussenstops",
            "Compact" to "Compact",
            "Recent" to "Recent",
            "1 track" to "1 track",
            "{0} tracks" to "{0} tracks",
            "Test" to "Test",
            "School" to "School",
            "Website" to "Website"
        )
        "de" -> listOf(
            "Alternative" to "Alternative",
            "Start" to "Start",
            "Standard" to "Standard",
            "Auto" to "Automatisch",
            "Navigation" to "Navigation",
            "Diesel" to "Diesel",
            "Bioethanol (E85)" to "Bioethanol (E85)",
            "Demo" to "Demo",
            "Feedback" to "Rückmeldung",
            "Version" to "Version",
            "Name (optional)" to "Name (optional)",
            "Online" to "Online",
            "Normal" to "Normal",
            "in {0}" to "in {0}",
            "Pause" to "Pause",
            "Website" to "Webseite"
        )
        "fr" -> listOf(
            "Destination" to "Destination",
            "{0} miles" to "{0} miles",
            "Accident" to "Accident",
            "Police" to "Police",
            "Incident" to "Incident",
            "Alternative" to "Alternative",
            "Parking" to "Stationnement",
            "Standard" to "Standard",
            "Satellite" to "Satellite",
            "Auto" to "Automatique",
            "+{0} min" to "+{0} min",
            "Graphite" to "Graphite",
            "Olive" to "Olive",
            "Orange" to "Orange",
            "Champagne" to "Champagne",
            "France" to "France",
            "Portugal" to "Portugal",
            "Navigation" to "Navigation",
            "Volume" to "Volume",
            "Miles" to "Miles",
            "Version" to "Version",
            "Notifications" to "Notifications",
            "tonnes" to "tonnes",
            "{0} min" to "{0} min",
            "Normal" to "Normal",
            "Compact" to "Compact",
            "Microphone" to "Microphone",
            "Pause" to "Pause"
        )
        "es" -> listOf(
            "Auto" to "Automático",
            "+{0} min" to "+{0} min",
            "Portugal" to "Portugal",
            "Austria" to "Austria",
            "Demo" to "Demostración",
            "Normal" to "Normal",
            "{0} min" to "{0} min"
        )
        "it" -> listOf(
            "via {0}" to "via {0}",
            "Via {0}" to "Via {0}",
            "Standard" to "Standard",
            "Satellite" to "Satellite",
            "Auto" to "Automatico",
            "+{0} min" to "+{0} min",
            "Hatchback" to "Berlina a due volumi",
            "Champagne" to "Champagne",
            "Lime" to "Verde lime",
            "Austria" to "Austria",
            "Slovenia" to "Slovenia",
            "Volume" to "Volume",
            "Super" to "Super",
            "Diesel" to "Diesel",
            "Demo" to "Dimostrazione",
            "Feedback" to "Feedback",
            "Backup" to "Backup",
            "Online" to "Online",
            "{0} min" to "{0} min"
        )
        else -> emptyList()
    }
}
