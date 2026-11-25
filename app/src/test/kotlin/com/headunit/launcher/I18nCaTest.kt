package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class I18nCaTest {
    private val translations = I18nCa.rows().toMap()

    @Test fun navigationConnectorsAreTranslated() {
        assertEquals(" per Ronda del Litoral", translations[" onto {0}"]?.replace("{0}", "Ronda del Litoral"))
        assertEquals("Arribada a Avinguda dels Banús 105", translations["Arrive at {0}"]?.replace("{0}", "Avinguda dels Banús 105"))
    }

    @Test fun incidentDistanceAndRoadClosurePromptsAreTranslated() {
        assertEquals("Carretera tallada en 1,8 km", translations["{0} ahead in {1}{2}"]?.replace("{0}", "Carretera tallada")?.replace("{1}", "1,8")?.replace("{2}", " km"))
        assertEquals("Carretera tallada més endavant. Cercant una ruta alternativa.", translations["Road closed ahead. Finding a way round."])
        assertEquals(", uns 5 minuts de retard", translations[", about {0} minutes delay"]?.replace("{0}", "5"))
    }

    @Test fun translatedTemplatesKeepTheirPlaceholders() {
        val rows = translations
        assertEquals(true, rows[" onto {0}"]?.contains("{0}"))
        assertEquals(true, rows["Arrive at {0}"]?.contains("{0}"))
        assertEquals(true, rows["{0} ahead in {1}{2}"]?.contains("{1}{2}"))
    }
}
