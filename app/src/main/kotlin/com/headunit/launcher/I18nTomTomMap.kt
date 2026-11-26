package com.headunit.launcher

/** Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): why TomTom's map can turn down a working key. */
internal object I18nTomTomMap {
    private const val KEY = "TomTom's map is a separate product from its routes and search: check it's switched on for your key at developer.tomtom.com"

    private val t: Map<String, String> = mapOf(
        "nl" to "De kaart van TomTom is een apart product naast routes en zoeken: controleer op developer.tomtom.com of het voor je sleutel aanstaat",
        "de" to "Die Karte von TomTom ist ein eigenes Produkt neben Routen und Suche: Prüfe auf developer.tomtom.com, ob sie für deinen Schlüssel aktiviert ist",
        "fr" to "La carte TomTom est un produit distinct des itinéraires et de la recherche : vérifiez sur developer.tomtom.com qu'elle est activée pour votre clé",
        "es" to "El mapa de TomTom es un producto aparte de sus rutas y su búsqueda: comprueba en developer.tomtom.com que esté activado para tu clave",
        "esar" to "El mapa de TomTom es un producto aparte de sus rutas y su búsqueda: fijate en developer.tomtom.com que esté activado para tu clave",
        "esmx" to "El mapa de TomTom es un producto aparte de sus rutas y su búsqueda: revisa en developer.tomtom.com que esté activado para tu clave",
        "it" to "La mappa di TomTom è un prodotto separato da percorsi e ricerca: controlla su developer.tomtom.com che sia attiva per la tua chiave",
        "pt" to "O mapa da TomTom é um produto separado das rotas e da pesquisa: verifique em developer.tomtom.com se está ativo para a sua chave",
        "ptbr" to "O mapa da TomTom é um produto separado das rotas e da busca: confira em developer.tomtom.com se ele está ativado para a sua chave",
        "pl" to "Mapa TomTom to osobny produkt niż trasy i wyszukiwanie: sprawdź na developer.tomtom.com, czy jest włączona dla twojego klucza",
        "th" to "แผนที่ของ TomTom เป็นผลิตภัณฑ์แยกจากเส้นทางและการค้นหา: ตรวจสอบที่ developer.tomtom.com ว่าเปิดใช้สำหรับคีย์ของคุณแล้ว",
        "ja" to "TomTom の地図はルートや検索とは別の製品です。developer.tomtom.com でキーに対して有効になっているか確認してください",
        "id" to "Peta TomTom adalah produk terpisah dari rute dan pencarian: periksa di developer.tomtom.com bahwa peta aktif untuk kunci Anda",
        "bg" to "Картата на TomTom е отделен продукт от маршрутите и търсенето: проверете в developer.tomtom.com дали е включена за вашия ключ",
        "sl" to "Zemljevid TomTom je ločen izdelek od poti in iskanja: na developer.tomtom.com preverite, ali je vklopljen za vaš ključ",
        "ro" to "Harta TomTom este un produs separat de trasee și căutare: verifică pe developer.tomtom.com dacă este activată pentru cheia ta",
        "eu" to "TomTom-en mapa ibilbideetatik eta bilaketatik bereizitako produktua da: egiaztatu developer.tomtom.com-en zure gakorako aktibatuta dagoela",
        "zh" to "TomTom 的地圖是與路線和搜尋分開的產品：請到 developer.tomtom.com 確認你的金鑰已開啟此功能",
        "zhhk" to "TomTom 的地圖是與路線和搜尋分開的產品：請到 developer.tomtom.com 確認你的金鑰已開啟此功能",
        "zhs" to "TomTom 的地图是与路线和搜索分开的产品：请到 developer.tomtom.com 确认你的密钥已开启此功能",
        "el" to "Ο χάρτης της TomTom είναι ξεχωριστό προϊόν από τις διαδρομές και την αναζήτηση: ελέγξτε στο developer.tomtom.com ότι είναι ενεργός για το κλειδί σας",
        "cs" to "Mapa TomTom je samostatný produkt vedle tras a vyhledávání: na developer.tomtom.com ověřte, že je pro váš klíč zapnutá",
        "fi" to "TomTomin kartta on reiteistä ja hausta erillinen tuote: tarkista developer.tomtom.com-sivulta, että se on käytössä avaimellesi",
        "sk" to "Mapa TomTom je samostatný produkt popri trasách a vyhľadávaní: na developer.tomtom.com overte, že je pre váš kľúč zapnutá",
        "da" to "TomToms kort er et andet produkt end ruter og søgning: tjek på developer.tomtom.com, at det er slået til for din nøgle",
        "hr" to "TomTomova karta zaseban je proizvod od ruta i pretraživanja: na developer.tomtom.com provjerite je li uključena za vaš ključ",
        "bs" to "TomTomova karta je zaseban proizvod od ruta i pretrage: na developer.tomtom.com provjerite je li uključena za vaš ključ",
        "sr" to "TomTom мапа је засебан производ од рута и претраге: на developer.tomtom.com проверите да ли је укључена за ваш кључ",
        "ms" to "Peta TomTom ialah produk berasingan daripada laluan dan carian: semak di developer.tomtom.com bahawa ia dihidupkan untuk kunci anda",
        "hu" to "A TomTom térképe az útvonalaktól és a kereséstől külön termék: a developer.tomtom.com oldalon ellenőrizd, hogy be van-e kapcsolva a kulcsodhoz",
        "tr" to "TomTom haritası rotalar ve aramadan ayrı bir üründür: developer.tomtom.com adresinde anahtarınız için açık olduğunu kontrol edin",
        "ar" to "خريطة TomTom منتج منفصل عن المسارات والبحث: تحقق في developer.tomtom.com من تفعيلها لمفتاحك",
        "he" to "המפה של TomTom היא מוצר נפרד מהמסלולים והחיפוש: בדקו ב-developer.tomtom.com שהיא מופעלת עבור המפתח שלכם",
        "ur" to "TomTom کا نقشہ اس کے راستوں اور تلاش سے الگ پروڈکٹ ہے: developer.tomtom.com پر دیکھیں کہ یہ آپ کی کلید کے لیے آن ہے",
        "sv" to "TomToms karta är en separat produkt från rutter och sökning: kontrollera på developer.tomtom.com att den är påslagen för din nyckel",
        "kn" to "TomTom ನಕ್ಷೆ ಅದರ ಮಾರ್ಗಗಳು ಮತ್ತು ಹುಡುಕಾಟದಿಂದ ಪ್ರತ್ಯೇಕ ಉತ್ಪನ್ನ: developer.tomtom.com ನಲ್ಲಿ ನಿಮ್ಮ ಕೀಗೆ ಅದು ಆನ್ ಆಗಿದೆಯೇ ಎಂದು ಪರಿಶೀಲಿಸಿ",
        "mr" to "TomTom चा नकाशा त्याच्या मार्ग आणि शोधापेक्षा वेगळे उत्पादन आहे: developer.tomtom.com वर तुमच्या कीसाठी तो चालू आहे का ते तपासा",
        "nb" to "TomToms kart er et eget produkt ved siden av ruter og søk: sjekk på developer.tomtom.com at det er slått på for nøkkelen din",
        "ru" to "Карта TomTom — отдельный продукт, не связанный с маршрутами и поиском: проверьте на developer.tomtom.com, что она включена для вашего ключа",
        "uk" to "Мапа TomTom — окремий продукт, не пов'язаний із маршрутами та пошуком: перевірте на developer.tomtom.com, що її ввімкнено для вашого ключа",
        "vi" to "Bản đồ TomTom là sản phẩm riêng, tách với tuyến đường và tìm kiếm: hãy kiểm tra tại developer.tomtom.com rằng nó đã được bật cho khóa của bạn",
        "ml" to "TomTom മാപ്പ് അതിന്റെ റൂട്ടുകളിൽ നിന്നും തിരയലിൽ നിന്നും വേറിട്ട ഉൽപ്പന്നമാണ്: developer.tomtom.com ൽ നിങ്ങളുടെ കീയ്ക്ക് അത് ഓണാണോ എന്ന് പരിശോധിക്കുക",
        "ta" to "TomTom வரைபடம் அதன் வழிகள் மற்றும் தேடலிலிருந்து தனி தயாரிப்பு: developer.tomtom.com இல் உங்கள் சாவிக்கு அது இயக்கப்பட்டுள்ளதா என்று சரிபார்க்கவும்",
        "te" to "TomTom మ్యాప్ దాని మార్గాలు మరియు శోధన నుండి వేరే ఉత్పత్తి: developer.tomtom.com లో మీ కీకి అది ఆన్ చేసి ఉందో లేదో తనిఖీ చేయండి",
        "ko" to "TomTom 지도는 경로·검색과 별개의 제품입니다. developer.tomtom.com에서 키에 지도가 켜져 있는지 확인하세요",
        "ca" to "El mapa de TomTom és un producte a part de les rutes i la cerca: comprova a developer.tomtom.com que estigui activat per a la teva clau",
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { listOf(KEY to it) } ?: emptyList()
}
