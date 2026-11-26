package com.headunit.launcher

/** Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the size of the speed limit signs on the route. */
internal object I18nRouteLimit {
    private val keys = listOf("Limit sign size on the route", "The speed limit signs drawn on your route where the limit changes")

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Grootte limietborden op de route", "De snelheidsborden op je route waar de limiet verandert"),
        "de" to listOf("Größe der Tempolimit-Schilder auf der Route", "Die Tempolimit-Schilder auf deiner Route, wo sich das Limit ändert"),
        "fr" to listOf("Taille des panneaux de limite sur l'itinéraire", "Les panneaux de limitation dessinés sur votre itinéraire là où la limite change"),
        "es" to listOf("Tamaño de las señales de límite en la ruta", "Las señales de límite de velocidad en tu ruta donde cambia el límite"),
        "esar" to listOf("Tamaño de las señales de límite en la ruta", "Las señales de límite de velocidad en tu ruta donde cambia el límite"),
        "esmx" to listOf("Tamaño de los letreros de límite en la ruta", "Los letreros de límite de velocidad en tu ruta donde cambia el límite"),
        "it" to listOf("Dimensione cartelli limite sul percorso", "I cartelli del limite di velocità sul percorso dove il limite cambia"),
        "pt" to listOf("Tamanho dos sinais de limite no percurso", "Os sinais de limite de velocidade no seu percurso onde o limite muda"),
        "ptbr" to listOf("Tamanho das placas de limite na rota", "As placas de limite de velocidade na sua rota onde o limite muda"),
        "pl" to listOf("Rozmiar znaków ograniczeń na trasie", "Znaki ograniczenia prędkości na trasie, gdzie zmienia się limit"),
        "th" to listOf("ขนาดป้ายจำกัดความเร็วบนเส้นทาง", "ป้ายจำกัดความเร็วบนเส้นทางตรงที่ขีดจำกัดเปลี่ยน"),
        "ja" to listOf("ルート上の制限標識のサイズ", "制限速度が変わる地点でルート上に表示される標識"),
        "id" to listOf("Ukuran rambu batas di rute", "Rambu batas kecepatan di rute Anda tempat batas berubah"),
        "bg" to listOf("Размер на знаците за ограничение по маршрута", "Знаците за ограничение на скоростта по маршрута, където ограничението се сменя"),
        "sl" to listOf("Velikost znakov za omejitev na poti", "Znaki za omejitev hitrosti na poti, kjer se omejitev spremeni"),
        "ro" to listOf("Mărimea indicatoarelor de limită pe traseu", "Indicatoarele de limită de viteză de pe traseu, unde se schimbă limita"),
        "eu" to listOf("Ibilbideko muga-seinaleen tamaina", "Abiadura-mugaren seinaleak zure ibilbidean, muga aldatzen den lekuan"),
        "zh" to listOf("路線上速限標誌大小", "在路線上速限改變處顯示的速限標誌"),
        "zhhk" to listOf("路線上車速限制標誌大小", "在路線上車速限制改變處顯示的標誌"),
        "zhs" to listOf("路线上限速标志大小", "在路线上限速改变处显示的限速标志"),
        "el" to listOf("Μέγεθος πινακίδων ορίου στη διαδρομή", "Οι πινακίδες ορίου ταχύτητας στη διαδρομή σας όπου αλλάζει το όριο"),
        "cs" to listOf("Velikost značek rychlosti na trase", "Značky omezení rychlosti na trase, kde se limit mění"),
        "fi" to listOf("Rajoitusmerkkien koko reitillä", "Nopeusrajoitusmerkit reitilläsi kohdissa, joissa rajoitus muuttuu"),
        "sk" to listOf("Veľkosť značiek rýchlosti na trase", "Značky obmedzenia rýchlosti na trase, kde sa limit mení"),
        "da" to listOf("Størrelse på fartgrænseskilte på ruten", "Fartgrænseskiltene på din rute, hvor grænsen skifter"),
        "hr" to listOf("Veličina znakova ograničenja na ruti", "Znakovi ograničenja brzine na ruti, gdje se ograničenje mijenja"),
        "bs" to listOf("Veličina znakova ograničenja na ruti", "Znakovi ograničenja brzine na ruti, gdje se ograničenje mijenja"),
        "sr" to listOf("Величина знакова ограничења на рути", "Знакови ограничења брзине на рути, где се ограничење мења"),
        "ms" to listOf("Saiz papan had di laluan", "Papan had laju di laluan anda di tempat had berubah"),
        "hu" to listOf("Korlátozó táblák mérete az útvonalon", "A sebességkorlátozó táblák az útvonaladon, ahol a korlátozás változik"),
        "tr" to listOf("Rotadaki hız limiti tabelası boyutu", "Rotanızda limitin değiştiği yerlerdeki hız limiti tabelaları"),
        "ar" to listOf("حجم لافتات الحد على المسار", "لافتات حد السرعة على مسارك حيث يتغير الحد"),
        "he" to listOf("גודל תמרורי המהירות במסלול", "תמרורי המהירות במסלול שלך היכן שהמגבלה משתנה"),
        "ur" to listOf("راستے پر حد کے نشانات کا سائز", "آپ کے راستے پر رفتار کی حد کے نشانات جہاں حد بدلتی ہے"),
        "sv" to listOf("Storlek på hastighetsskyltar längs rutten", "Hastighetsskyltarna på din rutt där gränsen ändras"),
        "kn" to listOf("ಮಾರ್ಗದಲ್ಲಿ ಮಿತಿ ಫಲಕದ ಗಾತ್ರ", "ಮಿತಿ ಬದಲಾಗುವಲ್ಲಿ ನಿಮ್ಮ ಮಾರ್ಗದಲ್ಲಿನ ವೇಗ ಮಿತಿ ಫಲಕಗಳು"),
        "mr" to listOf("मार्गावरील मर्यादा चिन्हांचा आकार", "मर्यादा बदलते तिथे तुमच्या मार्गावरील वेग मर्यादा चिन्हे"),
        "nb" to listOf("Størrelse på fartsgrenseskilt langs ruten", "Fartsgrenseskiltene på ruten din der grensen endres"),
        "ru" to listOf("Размер знаков ограничения на маршруте", "Знаки ограничения скорости на маршруте там, где ограничение меняется"),
        "uk" to listOf("Розмір знаків обмеження на маршруті", "Знаки обмеження швидкості на маршруті там, де обмеження змінюється"),
        "vi" to listOf("Kích thước biển giới hạn trên tuyến", "Biển giới hạn tốc độ trên tuyến đường ở nơi giới hạn thay đổi"),
        "ml" to listOf("റൂട്ടിലെ പരിധി ചിഹ്ന വലുപ്പം", "പരിധി മാറുന്നിടത്ത് റൂട്ടിൽ കാണിക്കുന്ന വേഗപരിധി ചിഹ്നങ്ങൾ"),
        "ta" to listOf("வழியில் வரம்பு அடையாள அளவு", "வரம்பு மாறும் இடங்களில் உங்கள் வழியில் உள்ள வேக வரம்பு அடையாளங்கள்"),
        "te" to listOf("మార్గంలో పరిమితి సూచిక పరిమాణం", "పరిమితి మారే చోట మీ మార్గంలో ఉన్న వేగ పరిమితి సూచికలు"),
        "ko" to listOf("경로의 제한 표지 크기", "제한 속도가 바뀌는 곳에서 경로에 표시되는 제한 속도 표지"),
        "ca" to listOf("Mida dels senyals de límit a la ruta", "Els senyals de límit de velocitat a la ruta on canvia el límit"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
