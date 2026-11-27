package com.headunit.launcher

/**
 * The turn card in the colours of the country's road signs (Settings › Turn card colour › Match road signs, desko1973 #431):
 * motorway signs on a motorway, the main road signs elsewhere. Only countries whose sign colours are known, and only dark sign
 * colours (the card's writing is white): where ordinary signs are white or yellow, and in countries not listed, it's null and
 * the card keeps its usual green.
 */
internal object SignColours {
    private val blue = 0xFF0065B3 to 0xFF004E8C
    private val ukGreen = 0xFF00703C to 0xFF00552E
    private val green = 0xFF007A3D to 0xFF005C2E
    private val roadBlue = 0xFF0B4EA2 to 0xFF083A7A

    /** Blue motorways; [first] the main roads' colour, null where those signs are white or yellow. */
    private val blueMotorways: Map<String, Pair<Long, Long>?> = mapOf(
        "GB" to ukGreen, "IE" to ukGreen, "FR" to ukGreen,
        // (Norway, Poland, Austria, Hungary and the Baltic states: blue motorways, green main roads, desko1973 #431)
        "NO" to ukGreen, "PL" to ukGreen, "AT" to ukGreen, "HU" to ukGreen, "BY" to ukGreen, "LV" to ukGreen, "EE" to ukGreen,
        "DE" to null, "NL" to null, "BE" to null, "LU" to null, "ES" to null, "PT" to null,
    )

    /** Green motorways, blue main roads. */
    private val greenMotorways = setOf("SE", "FI", "RU", "LT", "UA", "RO", "CZ", "SK", "CH", "IT", "SI", "HR", "BA", "AL", "GR", "BG", "TR")

    /** The card's two colours (top, deeper) as ARGB, or null to keep the usual colour. */
    fun card(country: String, motorway: Boolean): Pair<Long, Long>? {
        val cc = country.uppercase()
        if (cc in greenMotorways) return if (motorway) green else roadBlue
        if (cc in blueMotorways) return if (motorway) blue else blueMotorways[cc]
        return null
    }
}
