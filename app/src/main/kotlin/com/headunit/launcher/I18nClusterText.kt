package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the driver's display text size (#451).
 * Order: the setting, its largest choice.
 */
internal object I18nClusterText {
    private val keys = listOf(
        "Driver's display text size",
        "Largest"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Tekstgrootte op het display van de bestuurder", "Grootst"),
        "de" to listOf("Textgröße im Fahrerdisplay", "Am größten"),
        "fr" to listOf("Taille du texte sur l'écran conducteur", "Le plus grand"),
        "es" to listOf("Tamaño del texto en la pantalla del conductor", "El más grande"),
        "esar" to listOf("Tamaño del texto en la pantalla del conductor", "El más grande"),
        "esmx" to listOf("Tamaño del texto en la pantalla del conductor", "El más grande"),
        "it" to listOf("Dimensione del testo sul display del guidatore", "Massima"),
        "pt" to listOf("Tamanho do texto no ecrã do condutor", "Maior"),
        "ptbr" to listOf("Tamanho do texto na tela do motorista", "Maior"),
        "pl" to listOf("Rozmiar tekstu na wyświetlaczu kierowcy", "Największy"),
        "th" to listOf("ขนาดตัวอักษรบนหน้าจอคนขับ", "ใหญ่ที่สุด"),
        "ja" to listOf("ドライバーディスプレイの文字サイズ", "最大"),
        "id" to listOf("Ukuran teks di layar pengemudi", "Terbesar"),
        "bg" to listOf("Размер на текста на дисплея на водача", "Най-голям"),
        "sl" to listOf("Velikost besedila na zaslonu voznika", "Največja"),
        "ro" to listOf("Dimensiunea textului pe afișajul șoferului", "Cea mai mare"),
        "eu" to listOf("Gidariaren pantailako testu-tamaina", "Handiena"),
        "zh" to listOf("駕駛顯示器文字大小", "最大"),
        "zhhk" to listOf("駕駛顯示屏文字大小", "最大"),
        "zhs" to listOf("驾驶员显示屏文字大小", "最大"),
        "el" to listOf("Μέγεθος κειμένου στην οθόνη του οδηγού", "Μέγιστο"),
        "cs" to listOf("Velikost textu na displeji řidiče", "Největší"),
        "fi" to listOf("Kuljettajan näytön tekstin koko", "Suurin"),
        "sk" to listOf("Veľkosť textu na displeji vodiča", "Najväčšia"),
        "da" to listOf("Tekststørrelse på førerens display", "Størst"),
        "hr" to listOf("Veličina teksta na zaslonu vozača", "Najveća"),
        "bs" to listOf("Veličina teksta na ekranu vozača", "Najveća"),
        "sr" to listOf("Величина текста на екрану возача", "Највећа"),
        "ms" to listOf("Saiz teks pada paparan pemandu", "Terbesar"),
        "hu" to listOf("Szövegméret a sofőr kijelzőjén", "Legnagyobb"),
        "tr" to listOf("Sürücü ekranında yazı boyutu", "En büyük"),
        "ar" to listOf("حجم النص على شاشة السائق", "الأكبر"),
        "he" to listOf("גודל הטקסט במסך הנהג", "הגדול ביותר"),
        "ur" to listOf("ڈرائیور ڈسپلے پر متن کا سائز", "سب سے بڑا"),
        "sv" to listOf("Textstorlek på förarens display", "Störst"),
        "kn" to listOf("ಚಾಲಕರ ಪ್ರದರ್ಶನದಲ್ಲಿ ಪಠ್ಯದ ಗಾತ್ರ", "ಅತಿ ದೊಡ್ಡದು"),
        "mr" to listOf("ड्रायव्हर डिस्प्लेवरील मजकुराचा आकार", "सर्वात मोठा"),
        "nb" to listOf("Tekststørrelse på førerens display", "Størst"),
        "ru" to listOf("Размер текста на дисплее водителя", "Самый большой"),
        "uk" to listOf("Розмір тексту на дисплеї водія", "Найбільший"),
        "vi" to listOf("Cỡ chữ trên màn hình người lái", "Lớn nhất"),
        "ml" to listOf("ഡ്രൈവർ ഡിസ്പ്ലേയിലെ വാചകത്തിന്റെ വലിപ്പം", "ഏറ്റവും വലുത്"),
        "ta" to listOf("ஓட்டுநர் காட்சியில் எழுத்தின் அளவு", "மிகப் பெரியது"),
        "te" to listOf("డ్రైవర్ డిస్‌ప్లేలో అక్షరాల పరిమాణం", "అతి పెద్దది"),
        "ko" to listOf("운전자 디스플레이 글자 크기", "가장 크게"),
        "ca" to listOf("Mida del text a la pantalla del conductor", "La més gran"),
        "mk" to listOf("Големина на текстот на екранот на возачот", "Најголемо"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
