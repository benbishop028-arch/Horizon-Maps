package com.headunit.launcher

/** Localised label used by the favourites A–Z / Nearest selector. */
internal object I18nFavouriteSort {
    fun rows(code: String): List<Pair<String, String>> {
        val nearest = mapOf(
            "nl" to "Dichtstbij",
            "de" to "Am nächsten",
            "fr" to "Les plus proches",
            "es" to "Más cercanos",
            "it" to "Più vicini",
            "pt" to "Mais próximos",
            "pl" to "Najbliższe",
            "th" to "ใกล้ที่สุด",
            "ja" to "近い順",
            "id" to "Terdekat",
            "bg" to "Най-близки",
            "sl" to "Najbližje",
            "ro" to "Cele mai apropiate",
            "eu" to "Hurbilenak",
            "zh" to "最近",
            "zhhk" to "最近",
            "el" to "Πλησιέστερα",
            "cs" to "Nejbližší",
            "fi" to "Lähin",
            "zhs" to "最近",
            "sk" to "Najbližšie",
            "da" to "Nærmeste",
            "hr" to "Najbliže",
            "ms" to "Terdekat",
            "hu" to "Legközelebbi",
            "tr" to "En yakın",
            "sr" to "Najbliže",
            "ar" to "الأقرب",
            "he" to "הקרובים ביותר",
            "ur" to "قریب ترین",
            "sv" to "Närmast",
            "bs" to "Najbliže",
            "kn" to "ಹತ್ತಿರದವು",
            "mr" to "सर्वात जवळचे",
            "nb" to "Nærmeste",
            "ru" to "Ближайшие",
            "uk" to "Найближчі",
            "vi" to "Gần nhất",
            "ml" to "ഏറ്റവും അടുത്തത്",
            "ta" to "அருகிலுள்ளது",
            "esar" to "Más cercanos",
            "esmx" to "Más cercanos"
        )
        val savedPlaceLabels = if (code == "pl") listOf(
            "Saved" to "Zapisane",
            "Recent" to "Ostatnie",
            "Name" to "Nazwa",
            "Distance" to "Odległość",
            "A–Z" to "A–Z"
        ) else emptyList()
        return savedPlaceLabels + listOf(
            "Sort favourites" to when (code) {
            "nl" -> "Favorieten sorteren"; "de" -> "Favoriten sortieren"; "fr" -> "Trier les favoris"; "es", "esar", "esmx" -> "Ordenar favoritos"
            "it" -> "Ordina preferiti"; "pt" -> "Ordenar favoritos"; "pl" -> "Sortuj ulubione"; "th" -> "เรียงรายการโปรด"; "ja" -> "お気に入りを並べ替え"
            "id" -> "Urutkan favorit"; "bg" -> "Сортиране на любимите"; "sl" -> "Razvrsti priljubljene"; "ro" -> "Sortează favoritele"
            "eu" -> "Ordenatu gogokoenak"; "zh", "zhhk", "zhs" -> "排序收藏地點"; "el" -> "Ταξινόμηση αγαπημένων"; "cs" -> "Seřadit oblíbené"
            "fi" -> "Järjestä suosikit"; "sk" -> "Zoradiť obľúbené"; "da" -> "Sortér favoritter"; "hr", "bs", "sr" -> "Poredaj omiljene"
            "ms" -> "Isih kegemaran"; "hu" -> "Kedvencek rendezése"; "tr" -> "Favorileri sırala"; "ar" -> "ترتيب المفضلة"
            "he" -> "מיון מועדפים"; "ur" -> "پسندیدہ ترتیب دیں"; "sv" -> "Sortera favoriter"; "kn" -> "ಮೆಚ್ಚಿನವುಗಳನ್ನು ವಿಂಗಡಿಸಿ"
            "mr" -> "आवडी क्रमाने लावा"; "nb" -> "Sorter favoritter"; "ru" -> "Сортировать избранное"; "uk" -> "Сортувати обране"
            "vi" -> "Sắp xếp mục yêu thích"; "ml" -> "പ്രിയപ്പെട്ടവ ക്രമീകരിക്കുക"; "ta" -> "பிடித்தவற்றை வரிசைப்படுத்து"
            else -> "Sort favourites"
        }) + listOf("Nearest" to (nearest[code] ?: "Nearest"))
    }
}
