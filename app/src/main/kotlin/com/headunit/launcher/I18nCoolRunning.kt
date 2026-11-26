package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Android Auto's cool running keeps a gentler 3D view
 * instead of a flat map (#414). Order: the setting's explanation.
 */
internal object I18nCoolRunning {
    private val keys = listOf(
        "For phones that get hot: 30 frames a second, a flatter map, no rain or lamp effects. Automatic turns it on when the phone reports it's getting hot"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Voor telefoons die heet worden: 30 beelden per seconde, een vlakkere kaart, geen regen- of lantaarneffecten. Automatisch zet het aan zodra de telefoon meldt dat hij warm wordt"),
        "de" to listOf("Für Handys, die heiß werden: 30 Bilder pro Sekunde, eine flachere Karte, keine Regen- oder Laterneneffekte. Automatisch schaltet es ein, sobald das Handy meldet, dass es warm wird"),
        "fr" to listOf("Pour les téléphones qui chauffent : 30 images par seconde, une carte plus à plat, sans effets de pluie ni de lampadaires. Automatique l'active quand le téléphone signale qu'il chauffe"),
        "es" to listOf("Para teléfonos que se calientan: 30 fotogramas por segundo, un mapa más plano, sin efectos de lluvia ni farolas. Automático lo activa cuando el teléfono avisa de que se está calentando"),
        "esar" to listOf("Para teléfonos que se calientan: 30 cuadros por segundo, un mapa más plano, sin efectos de lluvia ni faroles. Automático lo activa cuando el teléfono avisa que se está calentando"),
        "esmx" to listOf("Para teléfonos que se calientan: 30 cuadros por segundo, un mapa más plano, sin efectos de lluvia ni postes de luz. Automático lo activa cuando el teléfono avisa que se está calentando"),
        "it" to listOf("Per i telefoni che si scaldano: 30 fotogrammi al secondo, una mappa più piatta, niente effetti di pioggia o lampioni. Automatico lo attiva quando il telefono segnala che si sta scaldando"),
        "pt" to listOf("Para telemóveis que aquecem: 30 imagens por segundo, um mapa mais plano, sem efeitos de chuva ou candeeiros. Automático liga-o quando o telemóvel indica que está a aquecer"),
        "ptbr" to listOf("Para celulares que esquentam: 30 quadros por segundo, um mapa mais plano, sem efeitos de chuva ou postes. Automático liga quando o celular avisa que está esquentando"),
        "pl" to listOf("Dla telefonów, które się nagrzewają: 30 klatek na sekundę, bardziej płaska mapa, bez efektów deszczu i latarni. Automatycznie włącza się, gdy telefon zgłasza, że się nagrzewa"),
        "th" to listOf("สำหรับโทรศัพท์ที่ร้อนง่าย: 30 เฟรมต่อวินาที แผนที่แบนลง ไม่มีเอฟเฟกต์ฝนหรือไฟถนน อัตโนมัติจะเปิดเมื่อโทรศัพท์แจ้งว่ากำลังร้อนขึ้น"),
        "ja" to listOf("熱くなりやすいスマートフォン向け：毎秒30フレーム、より平らな地図、雨や街灯の効果なし。自動では、スマートフォンが熱くなってきたと知らせたときにオンになります"),
        "id" to listOf("Untuk ponsel yang cepat panas: 30 bingkai per detik, peta lebih datar, tanpa efek hujan atau lampu jalan. Otomatis menyalakannya saat ponsel melaporkan mulai panas"),
        "bg" to listOf("За телефони, които загряват: 30 кадъра в секунда, по-плоска карта, без ефекти на дъжд и улични лампи. Автоматично го включва, когато телефонът съобщи, че загрява"),
        "sl" to listOf("Za telefone, ki se segrevajo: 30 sličic na sekundo, bolj ploščat zemljevid, brez učinkov dežja in svetilk. Samodejno se vklopi, ko telefon sporoči, da se segreva"),
        "ro" to listOf("Pentru telefoanele care se încălzesc: 30 de cadre pe secundă, o hartă mai plată, fără efecte de ploaie sau felinare. Automat îl pornește când telefonul anunță că se încălzește"),
        "eu" to listOf("Berotzen diren telefonoetarako: 30 irudi segundoko, mapa lauagoa, euri edo farolen efekturik gabe. Automatikoak aktibatzen du telefonoak berotzen ari dela esaten duenean"),
        "zh" to listOf("適用於容易發熱的手機：每秒 30 幀、較平的地圖、沒有雨水或路燈效果。「自動」會在手機回報正在變熱時開啟"),
        "zhhk" to listOf("適用於容易發熱的手機：每秒 30 幀、較平的地圖、沒有雨水或路燈效果。「自動」會在手機報告正在變熱時開啟"),
        "zhs" to listOf("适用于容易发热的手机：每秒 30 帧、更平的地图、没有雨水或路灯效果。“自动”会在手机报告正在变热时开启"),
        "el" to listOf("Για κινητά που ζεσταίνονται: 30 καρέ το δευτερόλεπτο, πιο επίπεδος χάρτης, χωρίς εφέ βροχής ή φανοστατών. Το Αυτόματο το ενεργοποιεί όταν το κινητό αναφέρει ότι ζεσταίνεται"),
        "cs" to listOf("Pro telefony, které se zahřívají: 30 snímků za sekundu, plošší mapa, bez efektů deště a lamp. Automaticky se zapne, když telefon hlásí, že se zahřívá"),
        "fi" to listOf("Kuumeneville puhelimille: 30 kuvaa sekunnissa, litteämpi kartta, ei sade- tai lyhtytehosteita. Automaattinen ottaa sen käyttöön, kun puhelin ilmoittaa lämpenevänsä"),
        "sk" to listOf("Pre telefóny, ktoré sa zahrievajú: 30 snímok za sekundu, plochšia mapa, bez efektov dažďa a lámp. Automaticky sa zapne, keď telefón hlási, že sa zahrieva"),
        "da" to listOf("Til telefoner, der bliver varme: 30 billeder i sekundet, et fladere kort, ingen regn- eller lygteeffekter. Automatisk slår det til, når telefonen melder, at den bliver varm"),
        "hr" to listOf("Za telefone koji se zagrijavaju: 30 sličica u sekundi, plosnatija karta, bez efekata kiše i svjetiljki. Automatski se uključuje kad telefon javi da se zagrijava"),
        "bs" to listOf("Za telefone koji se zagrijavaju: 30 sličica u sekundi, ravnija karta, bez efekata kiše i lampi. Automatski se uključuje kad telefon javi da se zagrijava"),
        "sr" to listOf("За телефоне који се загревају: 30 сличица у секунди, равнија мапа, без ефеката кише и лампи. Аутоматски се укључује када телефон јави да се загрева"),
        "ms" to listOf("Untuk telefon yang cepat panas: 30 bingkai sesaat, peta lebih rata, tiada kesan hujan atau lampu jalan. Automatik menghidupkannya apabila telefon melaporkan ia semakin panas"),
        "hu" to listOf("Melegedő telefonokhoz: 30 képkocka másodpercenként, laposabb térkép, eső- és lámpaeffektek nélkül. Az Automatikus akkor kapcsolja be, amikor a telefon jelzi, hogy melegszik"),
        "tr" to listOf("Isınan telefonlar için: saniyede 30 kare, daha düz bir harita, yağmur veya sokak lambası efekti yok. Otomatik, telefon ısındığını bildirdiğinde açar"),
        "ar" to listOf("للهواتف التي تسخن: 30 إطارًا في الثانية، وخريطة أكثر تسطحًا، بلا تأثيرات المطر أو أعمدة الإنارة. الوضع التلقائي يشغّله عندما يبلّغ الهاتف أنه يسخن"),
        "he" to listOf("לטלפונים שמתחממים: 30 פריימים בשנייה, מפה שטוחה יותר, בלי אפקטים של גשם או פנסים. אוטומטי מפעיל את זה כשהטלפון מדווח שהוא מתחמם"),
        "ur" to listOf("گرم ہونے والے فونز کے لیے: 30 فریم فی سیکنڈ، زیادہ ہموار نقشہ، بارش یا اسٹریٹ لائٹ کے اثرات نہیں۔ خودکار اسے تب آن کرتا ہے جب فون بتائے کہ وہ گرم ہو رہا ہے"),
        "sv" to listOf("För telefoner som blir varma: 30 bilder per sekund, en plattare karta, inga regn- eller lykteffekter. Automatiskt slår på det när telefonen meddelar att den blir varm"),
        "kn" to listOf("ಬಿಸಿಯಾಗುವ ಫೋನ್‌ಗಳಿಗೆ: ಸೆಕೆಂಡಿಗೆ 30 ಫ್ರೇಮ್‌ಗಳು, ಹೆಚ್ಚು ಚಪ್ಪಟೆಯಾದ ನಕ್ಷೆ, ಮಳೆ ಅಥವಾ ಬೀದಿದೀಪ ಪರಿಣಾಮಗಳಿಲ್ಲ. ಫೋನ್ ಬಿಸಿಯಾಗುತ್ತಿದೆ ಎಂದು ತಿಳಿಸಿದಾಗ ಸ್ವಯಂಚಾಲಿತ ಇದನ್ನು ಆನ್ ಮಾಡುತ್ತದೆ"),
        "mr" to listOf("गरम होणाऱ्या फोनसाठी: सेकंदाला 30 फ्रेम, अधिक सपाट नकाशा, पाऊस किंवा दिव्यांचे परिणाम नाहीत. फोन गरम होत असल्याचे कळवल्यावर स्वयंचलित हे चालू करते"),
        "nb" to listOf("For telefoner som blir varme: 30 bilder i sekundet, et flatere kart, ingen regn- eller lykteeffekter. Automatisk slår det på når telefonen melder at den blir varm"),
        "ru" to listOf("Для телефонов, которые нагреваются: 30 кадров в секунду, более плоская карта, без эффектов дождя и фонарей. «Автоматически» включает режим, когда телефон сообщает, что нагревается"),
        "uk" to listOf("Для телефонів, що нагріваються: 30 кадрів на секунду, пласкіша мапа, без ефектів дощу та ліхтарів. «Автоматично» вмикає режим, коли телефон повідомляє, що нагрівається"),
        "vi" to listOf("Cho điện thoại hay nóng: 30 khung hình mỗi giây, bản đồ phẳng hơn, không có hiệu ứng mưa hay đèn đường. Tự động sẽ bật khi điện thoại báo đang nóng lên"),
        "ml" to listOf("ചൂടാകുന്ന ഫോണുകൾക്കായി: സെക്കൻഡിൽ 30 ഫ്രെയിമുകൾ, കൂടുതൽ പരന്ന മാപ്പ്, മഴയോ തെരുവുവിളക്കോ ഇഫക്റ്റുകളില്ല. ഫോൺ ചൂടാകുന്നുവെന്ന് അറിയിക്കുമ്പോൾ ഓട്ടോമാറ്റിക് ഇത് ഓണാക്കും"),
        "ta" to listOf("சூடாகும் போன்களுக்கு: விநாடிக்கு 30 பிரேம்கள், மேலும் தட்டையான வரைபடம், மழை அல்லது தெருவிளக்கு விளைவுகள் இல்லை. போன் சூடாகிறது என்று தெரிவிக்கும்போது தானியங்கி இதை இயக்கும்"),
        "te" to listOf("వేడెక్కే ఫోన్‌ల కోసం: సెకనుకు 30 ఫ్రేమ్‌లు, మరింత చదునైన మ్యాప్, వర్షం లేదా వీధిదీపాల ఎఫెక్ట్‌లు లేవు. ఫోన్ వేడెక్కుతోందని తెలిపినప్పుడు ఆటోమేటిక్ దీన్ని ఆన్ చేస్తుంది"),
        "ko" to listOf("뜨거워지는 휴대전화용: 초당 30프레임, 더 평평한 지도, 비나 가로등 효과 없음. 자동은 휴대전화가 뜨거워지고 있다고 알리면 켜집니다"),
        "ca" to listOf("Per a telèfons que s'escalfen: 30 fotogrames per segon, un mapa més pla, sense efectes de pluja ni fanals. Automàtic l'activa quan el telèfon avisa que s'està escalfant")
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
