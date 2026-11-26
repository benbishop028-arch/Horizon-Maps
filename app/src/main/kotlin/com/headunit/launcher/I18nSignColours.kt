package com.headunit.launcher

/** Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Turn card colour › Match road signs (#431). */
internal object I18nSignColours {
    private val keys = listOf("Match road signs", "The card takes the colour of the road signs where you're driving: motorway colour on motorways, the usual colour elsewhere")

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Zoals de verkeersborden", "De kaart krijgt de kleur van de borden waar je rijdt: snelwegkleur op de snelweg, elders de gewone kleur"),
        "de" to listOf("Wie die Verkehrsschilder", "Die Karte nimmt die Farbe der Schilder an, wo du fährst: Autobahnfarbe auf der Autobahn, sonst die übliche Farbe"),
        "fr" to listOf("Comme les panneaux", "La carte prend la couleur des panneaux là où vous roulez : couleur autoroute sur autoroute, la couleur habituelle ailleurs"),
        "es" to listOf("Como las señales", "La tarjeta toma el color de las señales de donde conduces: color de autopista en autopista, el habitual en el resto"),
        "esar" to listOf("Como los carteles", "La tarjeta toma el color de los carteles de donde manejás: color de autopista en autopista, el habitual en el resto"),
        "esmx" to listOf("Como los letreros", "La tarjeta toma el color de los letreros de donde manejas: color de autopista en autopista, el habitual en el resto"),
        "it" to listOf("Come i cartelli", "La scheda prende il colore dei cartelli dove guidi: colore autostrada in autostrada, quello solito altrove"),
        "pt" to listOf("Como os sinais", "O cartão fica com a cor dos sinais onde conduz: cor de autoestrada na autoestrada, a habitual nos outros sítios"),
        "ptbr" to listOf("Como as placas", "O cartão fica com a cor das placas onde você dirige: cor de rodovia na rodovia, a de sempre nos outros lugares"),
        "pl" to listOf("Jak znaki drogowe", "Karta przyjmuje kolor znaków tam, gdzie jedziesz: kolor autostrady na autostradzie, zwykły gdzie indziej"),
        "th" to listOf("ตามป้ายจราจร", "การ์ดใช้สีของป้ายจราจรตรงที่คุณขับ: สีทางด่วนบนทางด่วน สีปกติที่อื่น"),
        "ja" to listOf("道路標識に合わせる", "走っている道路の標識の色になります。高速道路では高速道路の色、それ以外はいつもの色"),
        "id" to listOf("Ikuti rambu jalan", "Kartu mengikuti warna rambu jalan tempat Anda berkendara: warna jalan tol di jalan tol, warna biasa di tempat lain"),
        "bg" to listOf("Като пътните знаци", "Картата приема цвета на пътните знаци, където карате: цвета на магистралата по магистрала, обичайния другаде"),
        "sl" to listOf("Kot prometni znaki", "Kartica prevzame barvo znakov, kjer vozite: barvo avtoceste na avtocesti, običajno drugje"),
        "ro" to listOf("Ca indicatoarele", "Cardul ia culoarea indicatoarelor de unde conduci: culoarea autostrăzii pe autostradă, cea obișnuită în rest"),
        "eu" to listOf("Errepide-seinaleen arabera", "Txartelak gidatzen ari zaren lekuko seinaleen kolorea hartzen du: autobidearen kolorea autobidean, ohikoa beste lekuetan"),
        "zh" to listOf("配合道路標誌", "卡片會配合你行駛道路的標誌顏色：高速公路上用高速公路顏色，其他地方用一般顏色"),
        "zhhk" to listOf("配合道路標誌", "卡片會配合你行駛道路的標誌顏色：高速公路上用高速公路顏色，其他地方用一般顏色"),
        "zhs" to listOf("配合道路标志", "卡片会配合你行驶道路的标志颜色：高速公路上用高速公路颜色，其他地方用一般颜色"),
        "el" to listOf("Όπως οι πινακίδες", "Η κάρτα παίρνει το χρώμα των πινακίδων εκεί που οδηγείτε: χρώμα αυτοκινητόδρομου στον αυτοκινητόδρομο, το συνηθισμένο αλλού"),
        "cs" to listOf("Podle dopravních značek", "Karta má barvu značek tam, kde jedete: barvu dálnice na dálnici, jinde obvyklou"),
        "fi" to listOf("Liikennemerkkien mukaan", "Kortti saa ajotien merkkien värin: moottoritiellä moottoritien värin, muualla tavallisen"),
        "sk" to listOf("Podľa dopravných značiek", "Karta má farbu značiek tam, kde jazdíte: farbu diaľnice na diaľnici, inde obvyklú"),
        "da" to listOf("Som vejskiltene", "Kortet får farven fra skiltene, hvor du kører: motorvejsfarve på motorvejen, den sædvanlige ellers"),
        "hr" to listOf("Kao prometni znakovi", "Kartica poprima boju znakova gdje vozite: boju autoceste na autocesti, uobičajenu drugdje"),
        "bs" to listOf("Kao saobraćajni znakovi", "Kartica poprima boju znakova gdje vozite: boju autoputa na autoputu, uobičajenu drugdje"),
        "sr" to listOf("Као саобраћајни знакови", "Картица добија боју знакова где возите: боју аутопута на аутопуту, уобичајену другде"),
        "ms" to listOf("Ikut papan tanda jalan", "Kad mengikut warna papan tanda di tempat anda memandu: warna lebuh raya di lebuh raya, warna biasa di tempat lain"),
        "hu" to listOf("Mint a közúti táblák", "A kártya felveszi az út tábláinak színét: autópályán az autópálya színét, máshol a szokásosat"),
        "tr" to listOf("Yol tabelalarına göre", "Kart, sürdüğünüz yerdeki tabelaların rengini alır: otoyolda otoyol rengi, diğer yerlerde olağan renk"),
        "ar" to listOf("مطابقة لافتات الطريق", "تأخذ البطاقة لون لافتات الطريق حيث تقود: لون الطريق السريع عليه، واللون المعتاد في غيره"),
        "he" to listOf("לפי שלטי הדרך", "הכרטיס מקבל את צבע השלטים היכן שאתם נוהגים: צבע כביש מהיר בכביש מהיר, הצבע הרגיל בשאר המקומות"),
        "ur" to listOf("سڑک کے نشانوں جیسا", "کارڈ وہاں کے سڑک کے نشانوں کا رنگ لیتا ہے جہاں آپ چلا رہے ہیں: موٹر وے پر موٹر وے کا رنگ، باقی جگہ معمول کا رنگ"),
        "sv" to listOf("Som vägskyltarna", "Kortet får skyltarnas färg där du kör: motorvägsfärg på motorvägen, den vanliga annars"),
        "kn" to listOf("ರಸ್ತೆ ಫಲಕಗಳಂತೆ", "ನೀವು ಚಲಿಸುವ ರಸ್ತೆಯ ಫಲಕಗಳ ಬಣ್ಣವನ್ನು ಕಾರ್ಡ್ ಪಡೆಯುತ್ತದೆ: ಮೋಟಾರ್‌ವೇಯಲ್ಲಿ ಮೋಟಾರ್‌ವೇ ಬಣ್ಣ, ಬೇರೆಡೆ ಸಾಮಾನ್ಯ ಬಣ್ಣ"),
        "mr" to listOf("रस्त्याच्या चिन्हांप्रमाणे", "तुम्ही चालवत असलेल्या रस्त्याच्या चिन्हांचा रंग कार्डला मिळतो: मोटरवेवर मोटरवेचा रंग, इतरत्र नेहमीचा"),
        "nb" to listOf("Som veiskiltene", "Kortet får fargen til skiltene der du kjører: motorveifarge på motorveien, den vanlige ellers"),
        "ru" to listOf("Как дорожные знаки", "Карточка принимает цвет знаков там, где вы едете: цвет автомагистрали на магистрали, обычный в остальных местах"),
        "uk" to listOf("Як дорожні знаки", "Картка набуває кольору знаків там, де ви їдете: колір автомагістралі на магістралі, звичайний деінде"),
        "vi" to listOf("Theo biển báo", "Thẻ lấy màu biển báo nơi bạn đang lái: màu đường cao tốc trên đường cao tốc, màu thường ở nơi khác"),
        "ml" to listOf("റോഡ് ബോർഡുകൾ പോലെ", "നിങ്ങൾ ഓടിക്കുന്നിടത്തെ ബോർഡുകളുടെ നിറം കാർഡ് എടുക്കും: മോട്ടോർവേയിൽ മോട്ടോർവേ നിറം, മറ്റിടങ്ങളിൽ സാധാരണ നിറം"),
        "ta" to listOf("சாலை அடையாளங்களைப் போல", "நீங்கள் ஓட்டும் இடத்தின் அடையாளங்களின் நிறத்தை அட்டை எடுக்கும்: மோட்டார்வேயில் மோட்டார்வே நிறம், மற்ற இடங்களில் வழக்கமான நிறம்"),
        "te" to listOf("రహదారి సూచికల ప్రకారం", "మీరు నడుపుతున్న చోటి సూచికల రంగును కార్డు తీసుకుంటుంది: మోటార్‌వేలో మోటార్‌వే రంగు, ఇతర చోట్ల సాధారణ రంగు"),
        "ko" to listOf("도로 표지판에 맞추기", "주행 중인 도로의 표지판 색을 따릅니다: 고속도로에서는 고속도로 색, 그 밖에는 평소 색"),
        "ca" to listOf("Com els senyals", "La targeta pren el color dels senyals d'on condueixes: color d'autopista a l'autopista, l'habitual a la resta"),
        "mk" to listOf("Според патоказите", "Картичката ја зема бојата на патоказите каде што возите: бојата на автопатот на автопат, вообичаената на други места"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
