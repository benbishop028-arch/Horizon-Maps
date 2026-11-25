package com.headunit.launcher

/**
 * Navigation prompts which were missing from a number of otherwise supported language packs.
 * Existing translations always take priority; these entries only fill gaps.
 */
object I18nNavigationPrompts {
    private val arrivals = mapOf(
        "nl" to "Aankomst bij {0}",
        "de" to "Ankunft bei {0}",
        "fr" to "Arrivée à {0}",
        "es" to "Llegada a {0}",
        "it" to "Arrivo a {0}",
        "pt" to "Chegada a {0}",
        "pl" to "Przyjazd do {0}",
        "th" to "ถึง {0}",
        "ja" to "{0}に到着",
        "id" to "Tiba di {0}",
        "bg" to "Пристигане на {0}",
        "sl" to "Prihod na {0}",
        "ro" to "Sosire la {0}",
        "eu" to "{0} helmugara iristea",
        "zh" to "抵達 {0}",
        "zhhk" to "抵達 {0}",
        "el" to "Άφιξη στο {0}",
        "cs" to "Příjezd do {0}",
        "fi" to "Saapuminen kohteeseen {0}",
        "zhs" to "到达 {0}",
        "sk" to "Príchod do {0}",
        "da" to "Ankomst ved {0}",
        "hr" to "Dolazak na {0}",
        "ms" to "Tiba di {0}",
        "hu" to "Érkezés ide: {0}",
        "tr" to "{0} noktasına varış",
        "sr" to "Dolazak na {0}",
        "ar" to "الوصول إلى {0}",
        "he" to "הגעה אל {0}",
        "ur" to "{0} پر آمد",
        "sv" to "Ankomst till {0}",
        "bs" to "Dolazak na {0}",
        "kn" to "{0} ತಲುಪುವಿಕೆ",
        "mr" to "{0} येथे आगमन",
        "nb" to "Ankomst til {0}",
        "ru" to "Прибытие в {0}",
        "uk" to "Прибуття до {0}",
        "vi" to "Đến {0}",
        "ml" to "{0}-ൽ എത്തിച്ചേരുക",
        "ta" to "{0} வந்து சேருதல்",
        "ko" to "{0} 도착",
        "ca" to "Arribada a {0}",
        "te" to "{0} వద్దకు చేరుకోవడం",
        "esar" to "Llegada a {0}",
        "esmx" to "Llegada a {0}",
        "ptbr" to "Chegada a {0}"
    )

    private val incidentPrompts = listOf(
        "{0} ahead in {1}{2}" to "{1}{2} 앞에 {0}",
        ", about {0} minutes delay" to ", 약 {0}분 지연",
        " onto {0}" to " {0} 방향으로",
        "Road closed ahead. Finding a way round" to "전방 도로가 통제되었습니다. 우회 경로를 찾는 중입니다",
        "Road closed ahead. Finding a way round." to "전방 도로가 통제되었습니다. 우회 경로를 찾는 중입니다.",
        "Looking for a way round" to "우회 경로를 찾는 중",
        "Looking for a way round…" to "우회 경로를 찾는 중…"
    )

    private val tamilIncidentPrompts = listOf(
        "{0} ahead in {1}{2}" to "{1}{2} தூரத்தில் {0}",
        ", about {0} minutes delay" to ", சுமார் {0} நிமிடங்கள் தாமதம்",
        " onto {0}" to " {0} வழியாக",
        "Road closed ahead. Finding a way round" to "முன்னால் சாலை மூடப்பட்டுள்ளது. மாற்றுப் பாதையைத் தேடுகிறது",
        "Road closed ahead. Finding a way round." to "முன்னால் சாலை மூடப்பட்டுள்ளது. மாற்றுப் பாதையைத் தேடுகிறது.",
        "Looking for a way round" to "மாற்றுப் பாதையைத் தேடுகிறது",
        "Looking for a way round…" to "மாற்றுப் பாதையைத் தேடுகிறது…"
    )

    private val teluguIncidentPrompts = listOf(
        "{0} ahead in {1}{2}" to "{1}{2} దూరంలో {0}",
        ", about {0} minutes delay" to ", సుమారు {0} నిమిషాల ఆలస్యం",
        " onto {0}" to " {0} వైపు",
        "Road closed ahead. Finding a way round" to "ముందు రహదారి మూసివేయబడింది. ప్రత్యామ్నాయ మార్గం కోసం చూస్తోంది",
        "Road closed ahead. Finding a way round." to "ముందు రహదారి మూసివేయబడింది. ప్రత్యామ్నాయ మార్గం కోసం చూస్తోంది.",
        "Looking for a way round" to "ప్రత్యామ్నాయ మార్గం కోసం చూస్తోంది",
        "Looking for a way round…" to "ప్రత్యామ్నాయ మార్గం కోసం చూస్తోంది…"
    )

    fun rows(code: String): List<Pair<String, String>> = buildList {
        arrivals[code]?.let { add("Arrive at {0}" to it) }
        when (code) {
            "ko" -> addAll(incidentPrompts)
            "ta" -> addAll(tamilIncidentPrompts)
            "te" -> addAll(teluguIncidentPrompts)
        }
    }
}
