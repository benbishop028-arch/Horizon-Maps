package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): how speed cameras are warned about (#436), and the
 * motorway services filter (#437). Order: Screen only, Camera pop-up, its explanation, Motorway services.
 */
internal object I18nCamWarn {
    private val keys = listOf(
        "Screen only", "Camera pop-up", "The banner on screen as you come up to one. The camera badge on the map shows either way",
        "Motorway services"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Alleen scherm", "Flitspaal-pop-up", "De melding op het scherm als je er een nadert. Het flitspaalsymbool op de kaart blijft hoe dan ook zichtbaar", "Snelwegservicestations"),
        "de" to listOf("Nur Bildschirm", "Blitzer-Einblendung", "Die Einblendung auf dem Bildschirm, wenn du dich einem näherst. Das Blitzersymbol auf der Karte bleibt so oder so sichtbar", "Autobahnraststätten"),
        "fr" to listOf("Écran seulement", "Fenêtre radar", "Le bandeau à l'écran quand vous en approchez un. L'icône du radar sur la carte reste affichée dans tous les cas", "Aires de service d'autoroute"),
        "es" to listOf("Solo pantalla", "Aviso de radar", "El aviso en pantalla al acercarte a uno. El icono del radar en el mapa se muestra igualmente", "Áreas de servicio de autopista"),
        "esar" to listOf("Solo pantalla", "Aviso de radar", "El aviso en pantalla al acercarte a uno. El ícono del radar en el mapa se muestra igual", "Estaciones de servicio de autopista"),
        "esmx" to listOf("Solo pantalla", "Aviso de radar", "El aviso en pantalla al acercarte a uno. El ícono del radar en el mapa se muestra de todos modos", "Áreas de servicio de autopista"),
        "it" to listOf("Solo schermo", "Avviso autovelox", "Il banner sullo schermo quando ti avvicini a uno. L'icona dell'autovelox sulla mappa si vede comunque", "Aree di servizio autostradali"),
        "pt" to listOf("Só no ecrã", "Aviso de radar", "O aviso no ecrã ao aproximar-se de um. O ícone do radar no mapa aparece na mesma", "Áreas de serviço de autoestrada"),
        "ptbr" to listOf("Só na tela", "Aviso de radar", "O aviso na tela ao se aproximar de um. O ícone do radar no mapa aparece de qualquer forma", "Postos de serviço de rodovia"),
        "pl" to listOf("Tylko ekran", "Okienko fotoradaru", "Baner na ekranie, gdy zbliżasz się do fotoradaru. Ikona fotoradaru na mapie jest widoczna tak czy inaczej", "Miejsca obsługi podróżnych przy autostradzie"),
        "th" to listOf("บนหน้าจอเท่านั้น", "ป๊อปอัปกล้องจับความเร็ว", "แบนเนอร์บนหน้าจอเมื่อคุณเข้าใกล้กล้อง ไอคอนกล้องบนแผนที่จะแสดงอยู่เสมอ", "จุดบริการบนทางด่วน"),
        "ja" to listOf("画面のみ", "オービスのポップアップ", "オービスに近づいたときの画面上のバナー。地図上のオービスのアイコンはどちらでも表示されます", "高速道路のサービスエリア"),
        "id" to listOf("Layar saja", "Pop-up kamera", "Banner di layar saat Anda mendekati kamera. Ikon kamera di peta tetap tampil", "Rest area jalan tol"),
        "bg" to listOf("Само на екрана", "Изскачащо за камера", "Банерът на екрана, когато наближавате камера. Значката на камерата на картата се показва и в двата случая", "Крайпътни комплекси на магистрала"),
        "sl" to listOf("Samo zaslon", "Pojavno okno za radar", "Pasica na zaslonu, ko se približujete radarju. Ikona radarja na zemljevidu je vidna v vsakem primeru", "Avtocestna počivališča s storitvami"),
        "ro" to listOf("Doar pe ecran", "Fereastră radar", "Bannerul de pe ecran când te apropii de un radar. Pictograma radarului de pe hartă apare oricum", "Stații de servicii de pe autostradă"),
        "eu" to listOf("Pantailan soilik", "Radarraren leiho gainerakorra", "Pantailako iragarkia radar batera hurbiltzean. Mapako radarraren ikurra beti ikusten da", "Autobideko zerbitzuguneak"),
        "zh" to listOf("僅螢幕", "測速照相彈出提示", "接近測速照相時螢幕上的橫幅。地圖上的測速照相圖示無論如何都會顯示", "高速公路服務區"),
        "zhhk" to listOf("只限螢幕", "快相機彈出提示", "接近快相機時螢幕上的橫幅。地圖上的快相機圖示無論如何都會顯示", "高速公路服務區"),
        "zhs" to listOf("仅屏幕", "测速摄像头弹出提示", "接近测速摄像头时屏幕上的横幅。地图上的摄像头图标无论如何都会显示", "高速公路服务区"),
        "el" to listOf("Μόνο οθόνη", "Αναδυόμενο κάμερας", "Το πλαίσιο στην οθόνη καθώς πλησιάζετε μια κάμερα. Το σήμα της κάμερας στον χάρτη εμφανίζεται έτσι κι αλλιώς", "Σταθμοί εξυπηρέτησης αυτοκινητόδρομου"),
        "cs" to listOf("Jen obrazovka", "Vyskakovací okno radaru", "Banner na obrazovce, když se k radaru blížíte. Ikona radaru na mapě se zobrazí tak jako tak", "Dálniční odpočívky se službami"),
        "fi" to listOf("Vain näyttö", "Kameran ponnahdusikkuna", "Näytön ilmoitus, kun lähestyt kameraa. Kameran merkki näkyy kartalla joka tapauksessa", "Moottoritien huoltoasemat"),
        "sk" to listOf("Len obrazovka", "Vyskakovacie okno radaru", "Banner na obrazovke, keď sa k radaru blížite. Ikona radaru na mape sa zobrazí tak či tak", "Diaľničné odpočívadlá so službami"),
        "da" to listOf("Kun skærm", "Fartkamera-pop op", "Banneret på skærmen, når du nærmer dig et. Fartkameraets mærke på kortet vises under alle omstændigheder", "Motorvejsrastepladser med service"),
        "hr" to listOf("Samo zaslon", "Skočni prozor za kameru", "Natpis na zaslonu kad se približavate kameri. Oznaka kamere na karti prikazuje se u svakom slučaju", "Autocestni uslužni objekti"),
        "bs" to listOf("Samo ekran", "Iskačući prozor za kameru", "Natpis na ekranu kad se približavate kameri. Oznaka kamere na mapi prikazuje se u svakom slučaju", "Uslužni objekti na autoputu"),
        "sr" to listOf("Само екран", "Искачући прозор за камеру", "Натпис на екрану када се приближавате камери. Ознака камере на мапи приказује се у сваком случају", "Услужни објекти на аутопуту"),
        "ms" to listOf("Skrin sahaja", "Timbulan kamera", "Sepanduk pada skrin semasa anda menghampiri kamera. Lencana kamera pada peta tetap dipaparkan", "Kawasan rehat lebuh raya"),
        "hu" to listOf("Csak képernyő", "Traffipax felugró ablak", "A képernyőn megjelenő sáv, amikor egy traffipaxhoz közeledsz. A traffipax jele a térképen mindenképp látszik", "Autópálya-pihenők szolgáltatásokkal"),
        "tr" to listOf("Yalnızca ekran", "Radar açılır penceresi", "Bir radara yaklaşırken ekrandaki bildirim. Haritadaki radar simgesi her durumda görünür", "Otoyol dinlenme tesisleri"),
        "ar" to listOf("على الشاشة فقط", "نافذة الكاميرا المنبثقة", "اللافتة على الشاشة عند الاقتراب من كاميرا. تظهر علامة الكاميرا على الخريطة في كل الأحوال", "محطات خدمة الطرق السريعة"),
        "he" to listOf("מסך בלבד", "חלונית מצלמה", "הבאנר במסך כשמתקרבים למצלמה. סמל המצלמה על המפה מוצג בכל מקרה", "תחנות שירות בכביש מהיר"),
        "ur" to listOf("صرف اسکرین", "کیمرے کا پاپ اپ", "کیمرے کے قریب پہنچنے پر اسکرین پر بینر۔ نقشے پر کیمرے کا نشان ہر صورت میں دکھتا ہے", "موٹروے سروس ایریاز"),
        "sv" to listOf("Bara skärmen", "Fartkamera-popup", "Banderollen på skärmen när du närmar dig en. Fartkamerans märke på kartan visas oavsett", "Motorvägsrastplatser med service"),
        "kn" to listOf("ಪರದೆ ಮಾತ್ರ", "ಕ್ಯಾಮೆರಾ ಪಾಪ್-ಅಪ್", "ಕ್ಯಾಮೆರಾ ಹತ್ತಿರ ಬರುವಾಗ ಪರದೆಯ ಮೇಲಿನ ಬ್ಯಾನರ್. ನಕ್ಷೆಯಲ್ಲಿನ ಕ್ಯಾಮೆರಾ ಗುರುತು ಹೇಗಿದ್ದರೂ ಕಾಣಿಸುತ್ತದೆ", "ಹೆದ್ದಾರಿ ಸೇವಾ ಕೇಂದ್ರಗಳು"),
        "mr" to listOf("फक्त स्क्रीन", "कॅमेरा पॉप-अप", "कॅमेऱ्याजवळ आल्यावर स्क्रीनवरील बॅनर. नकाशावरील कॅमेऱ्याचे चिन्ह तरीही दिसते", "महामार्ग सेवा केंद्रे"),
        "nb" to listOf("Bare skjerm", "Fotoboks-popup", "Banneret på skjermen når du nærmer deg en. Fotoboksmerket på kartet vises uansett", "Motorveiservicesteder"),
        "ru" to listOf("Только на экране", "Окно камеры", "Плашка на экране при приближении к камере. Значок камеры на карте виден в любом случае", "Придорожные сервисы на автомагистрали"),
        "uk" to listOf("Лише на екрані", "Вікно камери", "Плашка на екрані під час наближення до камери. Значок камери на мапі видно в будь-якому разі", "Придорожні сервіси на автомагістралі"),
        "vi" to listOf("Chỉ màn hình", "Cửa sổ camera", "Biểu ngữ trên màn hình khi bạn đến gần camera. Biểu tượng camera trên bản đồ vẫn hiển thị", "Trạm dừng nghỉ đường cao tốc"),
        "ml" to listOf("സ്ക്രീൻ മാത്രം", "ക്യാമറ പോപ്പ്-അപ്പ്", "ക്യാമറയ്ക്ക് അടുത്തെത്തുമ്പോൾ സ്ക്രീനിലെ ബാനർ. മാപ്പിലെ ക്യാമറ അടയാളം എന്തായാലും കാണിക്കും", "മോട്ടോർവേ സർവീസ് കേന്ദ്രങ്ങൾ"),
        "ta" to listOf("திரை மட்டும்", "கேமரா பாப்-அப்", "கேமராவை நெருங்கும்போது திரையில் வரும் பேனர். வரைபடத்தில் கேமரா குறி எப்படியும் தெரியும்", "நெடுஞ்சாலை சேவை மையங்கள்"),
        "te" to listOf("స్క్రీన్ మాత్రమే", "కెమెరా పాప్-అప్", "కెమెరా దగ్గరికి వస్తున్నప్పుడు స్క్రీన్‌పై బ్యానర్. మ్యాప్‌లో కెమెరా గుర్తు ఎలాగైనా కనిపిస్తుంది", "హైవే సేవా కేంద్రాలు"),
        "ko" to listOf("화면만", "카메라 팝업", "단속 카메라에 다가갈 때 화면에 뜨는 배너. 지도의 카메라 표시는 어느 쪽이든 보입니다", "고속도로 휴게소"),
        "ca" to listOf("Només pantalla", "Avís de radar", "El bàner a la pantalla quan t'hi acostes. La icona del radar al mapa es mostra igualment", "Àrees de servei d'autopista"),
        "mk" to listOf("Само на екранот", "Скокачки прозорец за камера", "Натписот на екранот кога се приближувате до камера. Ознаката на камерата на картата се гледа во секој случај", "Услужни објекти на автопат"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
