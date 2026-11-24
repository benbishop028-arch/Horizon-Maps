package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the Clean view button on the map (#358).
 * Order: Full view, Minimal view, Map only.
 */
internal object I18nCleanView {
    private val keys = listOf("Full view", "Minimal view", "Map only")

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Volledig beeld", "Minimaal beeld", "Alleen kaart"),
        "de" to listOf("Volle Ansicht", "Minimale Ansicht", "Nur Karte"),
        "fr" to listOf("Vue complète", "Vue minimale", "Carte seule"),
        "es" to listOf("Vista completa", "Vista mínima", "Solo el mapa"),
        "esar" to listOf("Vista completa", "Vista mínima", "Solo el mapa"),
        "esmx" to listOf("Vista completa", "Vista mínima", "Solo el mapa"),
        "it" to listOf("Vista completa", "Vista minima", "Solo mappa"),
        "pt" to listOf("Vista completa", "Vista mínima", "Só o mapa"),
        "pl" to listOf("Pełny widok", "Widok minimalny", "Tylko mapa"),
        "th" to listOf("มุมมองเต็ม", "มุมมองน้อยที่สุด", "แผนที่อย่างเดียว"),
        "ja" to listOf("通常表示", "最小表示", "地図のみ"),
        "id" to listOf("Tampilan penuh", "Tampilan minimal", "Peta saja"),
        "bg" to listOf("Пълен изглед", "Минимален изглед", "Само картата"),
        "sl" to listOf("Poln pogled", "Minimalni pogled", "Samo zemljevid"),
        "ro" to listOf("Vedere completă", "Vedere minimă", "Doar harta"),
        "eu" to listOf("Ikuspegi osoa", "Ikuspegi minimoa", "Mapa bakarrik"),
        "zh" to listOf("完整檢視", "精簡檢視", "僅顯示地圖"),
        "zhhk" to listOf("完整檢視", "精簡檢視", "僅顯示地圖"),
        "zhs" to listOf("完整视图", "精简视图", "仅显示地图"),
        "el" to listOf("Πλήρης προβολή", "Ελάχιστη προβολή", "Μόνο ο χάρτης"),
        "cs" to listOf("Plný pohled", "Minimální pohled", "Jen mapa"),
        "fi" to listOf("Koko näkymä", "Minimaalinen näkymä", "Vain kartta"),
        "sk" to listOf("Plný pohľad", "Minimálny pohľad", "Iba mapa"),
        "da" to listOf("Fuld visning", "Minimal visning", "Kun kort"),
        "hr" to listOf("Puni prikaz", "Minimalni prikaz", "Samo karta"),
        "bs" to listOf("Puni prikaz", "Minimalni prikaz", "Samo karta"),
        "sr" to listOf("Пун приказ", "Минимални приказ", "Само мапа"),
        "ms" to listOf("Paparan penuh", "Paparan minimum", "Peta sahaja"),
        "hu" to listOf("Teljes nézet", "Minimális nézet", "Csak a térkép"),
        "tr" to listOf("Tam görünüm", "Asgari görünüm", "Yalnızca harita"),
        "ar" to listOf("عرض كامل", "عرض مبسّط", "الخريطة فقط"),
        "he" to listOf("תצוגה מלאה", "תצוגה מינימלית", "מפה בלבד"),
        "ur" to listOf("مکمل منظر", "کم سے کم منظر", "صرف نقشہ"),
        "sv" to listOf("Full vy", "Minimal vy", "Bara kartan"),
        "kn" to listOf("ಪೂರ್ಣ ನೋಟ", "ಕನಿಷ್ಠ ನೋಟ", "ನಕ್ಷೆ ಮಾತ್ರ"),
        "mr" to listOf("पूर्ण दृश्य", "किमान दृश्य", "फक्त नकाशा"),
        "nb" to listOf("Full visning", "Minimal visning", "Bare kartet"),
        "ru" to listOf("Полный вид", "Минимальный вид", "Только карта"),
        "uk" to listOf("Повний вигляд", "Мінімальний вигляд", "Лише карта"),
        "vi" to listOf("Xem đầy đủ", "Xem tối giản", "Chỉ bản đồ"),
        "ml" to listOf("പൂർണ്ണ കാഴ്ച", "കുറഞ്ഞ കാഴ്ച", "മാപ്പ് മാത്രം"),
        "ta" to listOf("முழு காட்சி", "குறைந்த காட்சி", "வரைபடம் மட்டும்"),
        "te" to listOf("పూర్తి వీక్షణ", "కనిష్ట వీక్షణ", "మ్యాప్ మాత్రమే"),
        "ko" to listOf("전체 보기", "최소 보기", "지도만"),
        "ca" to listOf("Vista completa", "Vista mínima", "Només el mapa"),
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
