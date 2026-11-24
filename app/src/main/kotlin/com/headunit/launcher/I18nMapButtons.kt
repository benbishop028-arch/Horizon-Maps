package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Settings > Map buttons (#335).
 * Order: the group name, its footer, then the six button names.
 */
internal object I18nMapButtons {
    private val keys = listOf(
        "Map buttons",
        "Map options is always there. Overview shows the whole route for a few seconds, then goes back to following you",
        "Compass",
        "Sound (while navigating)",
        "Other routes (while navigating)",
        "Clean view (while navigating)",
        "Overview (while navigating)"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Kaartknoppen", "Kaartopties is er altijd. Overzicht toont de hele route een paar seconden en gaat dan weer je positie volgen", "Kompas", "Geluid (tijdens navigeren)", "Andere routes (tijdens navigeren)", "Schone weergave (tijdens navigeren)", "Overzicht (tijdens navigeren)"),
        "de" to listOf("Kartentasten", "Kartenoptionen ist immer da. Übersicht zeigt einige Sekunden die ganze Route und folgt dir dann wieder", "Kompass", "Ton (während der Navigation)", "Andere Routen (während der Navigation)", "Reduzierte Ansicht (während der Navigation)", "Übersicht (während der Navigation)"),
        "fr" to listOf("Boutons de la carte", "Options de la carte est toujours là. Aperçu montre tout l'itinéraire quelques secondes, puis revient à vous suivre", "Boussole", "Son (pendant la navigation)", "Autres itinéraires (pendant la navigation)", "Vue épurée (pendant la navigation)", "Aperçu (pendant la navigation)"),
        "es" to listOf("Botones del mapa", "Opciones del mapa siempre está. Vista general muestra toda la ruta unos segundos y luego vuelve a seguirte", "Brújula", "Sonido (al navegar)", "Otras rutas (al navegar)", "Vista limpia (al navegar)", "Vista general (al navegar)"),
        "esar" to listOf("Botones del mapa", "Opciones del mapa siempre está. Vista general muestra toda la ruta unos segundos y luego vuelve a seguirte", "Brújula", "Sonido (al navegar)", "Otras rutas (al navegar)", "Vista limpia (al navegar)", "Vista general (al navegar)"),
        "esmx" to listOf("Botones del mapa", "Opciones del mapa siempre está. Vista general muestra toda la ruta unos segundos y luego vuelve a seguirte", "Brújula", "Sonido (al navegar)", "Otras rutas (al navegar)", "Vista limpia (al navegar)", "Vista general (al navegar)"),
        "it" to listOf("Pulsanti della mappa", "Opzioni mappa c'è sempre. Panoramica mostra tutto il percorso per qualche secondo, poi torna a seguirti", "Bussola", "Audio (durante la navigazione)", "Altri percorsi (durante la navigazione)", "Vista pulita (durante la navigazione)", "Panoramica (durante la navigazione)"),
        "pt" to listOf("Botões do mapa", "Opções do mapa está sempre presente. Visão geral mostra a rota toda por alguns segundos e depois volta a seguir você", "Bússola", "Som (ao navegar)", "Outras rotas (ao navegar)", "Vista limpa (ao navegar)", "Visão geral (ao navegar)"),
        "pl" to listOf("Przyciski mapy", "Opcje mapy są zawsze dostępne. Przegląd pokazuje całą trasę przez kilka sekund, po czym wraca do śledzenia Ciebie", "Kompas", "Dźwięk (podczas nawigacji)", "Inne trasy (podczas nawigacji)", "Czysty widok (podczas nawigacji)", "Przegląd (podczas nawigacji)"),
        "th" to listOf("ปุ่มบนแผนที่", "ตัวเลือกแผนที่มีอยู่เสมอ ภาพรวมแสดงเส้นทางทั้งหมดสองสามวินาที แล้วกลับมาตามตำแหน่งคุณ", "เข็มทิศ", "เสียง (ขณะนำทาง)", "เส้นทางอื่น (ขณะนำทาง)", "มุมมองเรียบง่าย (ขณะนำทาง)", "ภาพรวม (ขณะนำทาง)"),
        "ja" to listOf("地図のボタン", "地図オプションは常に表示されます。全体表示はルート全体を数秒間表示し、その後現在地の追従に戻ります", "コンパス", "サウンド（案内中）", "別ルート（案内中）", "シンプル表示（案内中）", "全体表示（案内中）"),
        "id" to listOf("Tombol peta", "Opsi peta selalu ada. Ikhtisar menampilkan seluruh rute beberapa detik, lalu kembali mengikuti Anda", "Kompas", "Suara (saat navigasi)", "Rute lain (saat navigasi)", "Tampilan bersih (saat navigasi)", "Ikhtisar (saat navigasi)"),
        "bg" to listOf("Бутони на картата", "Опциите на картата винаги са налични. Прегледът показва целия маршрут за няколко секунди и после пак ви следва", "Компас", "Звук (при навигация)", "Други маршрути (при навигация)", "Чист изглед (при навигация)", "Преглед (при навигация)"),
        "sl" to listOf("Gumbi zemljevida", "Možnosti zemljevida so vedno tam. Pregled za nekaj sekund prikaže celotno pot, nato spet sledi vam", "Kompas", "Zvok (med navigacijo)", "Druge poti (med navigacijo)", "Čist pogled (med navigacijo)", "Pregled (med navigacijo)"),
        "ro" to listOf("Butoane pe hartă", "Opțiunile hărții sunt mereu acolo. Prezentarea generală arată traseul întreg câteva secunde, apoi revine la urmărirea ta", "Busolă", "Sunet (în timpul navigării)", "Alte trasee (în timpul navigării)", "Vedere curată (în timpul navigării)", "Prezentare generală (în timpul navigării)"),
        "eu" to listOf("Mapako botoiak", "Mapako aukerak beti daude. Ikuspegi orokorrak ibilbide osoa erakusten du segundo batzuetan, eta gero zu jarraitzera itzultzen da", "Iparrorratza", "Soinua (nabigatzen ari zarela)", "Beste ibilbide batzuk (nabigatzen ari zarela)", "Ikuspegi garbia (nabigatzen ari zarela)", "Ikuspegi orokorra (nabigatzen ari zarela)"),
        "zh" to listOf("地圖按鈕", "地圖選項一律會顯示。總覽會顯示整條路線幾秒，然後恢復跟隨你", "指南針", "聲音（導航時）", "其他路線（導航時）", "簡潔檢視（導航時）", "總覽（導航時）"),
        "zhhk" to listOf("地圖按鈕", "地圖選項一律會顯示。總覽會顯示整條路線幾秒，然後恢復跟隨你", "指南針", "聲音（導航時）", "其他路線（導航時）", "簡潔檢視（導航時）", "總覽（導航時）"),
        "zhs" to listOf("地图按钮", "地图选项始终显示。总览会显示整条路线几秒，然后恢复跟随你", "指南针", "声音（导航时）", "其他路线（导航时）", "简洁视图（导航时）", "总览（导航时）"),
        "el" to listOf("Κουμπιά χάρτη", "Οι επιλογές χάρτη υπάρχουν πάντα. Η επισκόπηση δείχνει όλη τη διαδρομή για λίγα δευτερόλεπτα και μετά σας ακολουθεί ξανά", "Πυξίδα", "Ήχος (κατά την πλοήγηση)", "Άλλες διαδρομές (κατά την πλοήγηση)", "Καθαρή προβολή (κατά την πλοήγηση)", "Επισκόπηση (κατά την πλοήγηση)"),
        "cs" to listOf("Tlačítka mapy", "Možnosti mapy jsou vždy k dispozici. Přehled ukáže celou trasu na pár vteřin a pak vás znovu sleduje", "Kompas", "Zvuk (při navigaci)", "Jiné trasy (při navigaci)", "Čistý pohled (při navigaci)", "Přehled (při navigaci)"),
        "fi" to listOf("Karttapainikkeet", "Kartta-asetukset ovat aina näkyvissä. Yleiskuva näyttää koko reitin muutaman sekunnin ja palaa sitten seuraamaan sinua", "Kompassi", "Ääni (navigoinnin aikana)", "Muut reitit (navigoinnin aikana)", "Puhdas näkymä (navigoinnin aikana)", "Yleiskuva (navigoinnin aikana)"),
        "sk" to listOf("Tlačidlá mapy", "Možnosti mapy sú vždy k dispozícii. Prehľad ukáže celú trasu na pár sekúnd a potom vás opäť sleduje", "Kompas", "Zvuk (počas navigácie)", "Iné trasy (počas navigácie)", "Čistý pohľad (počas navigácie)", "Prehľad (počas navigácie)"),
        "da" to listOf("Kortknapper", "Kortindstillinger er altid der. Oversigt viser hele ruten i nogle sekunder og følger derefter dig igen", "Kompas", "Lyd (under navigation)", "Andre ruter (under navigation)", "Ren visning (under navigation)", "Oversigt (under navigation)"),
        "hr" to listOf("Gumbi karte", "Opcije karte uvijek su tu. Pregled prikazuje cijelu rutu nekoliko sekundi, a zatim opet prati vas", "Kompas", "Zvuk (tijekom navigacije)", "Druge rute (tijekom navigacije)", "Čist prikaz (tijekom navigacije)", "Pregled (tijekom navigacije)"),
        "bs" to listOf("Dugmad karte", "Opcije karte su uvijek tu. Pregled prikazuje cijelu rutu nekoliko sekundi, a zatim opet prati vas", "Kompas", "Zvuk (tokom navigacije)", "Druge rute (tokom navigacije)", "Čist prikaz (tokom navigacije)", "Pregled (tokom navigacije)"),
        "sr" to listOf("Дугмад мапе", "Опције мапе су увек ту. Преглед приказује целу руту неколико секунди, а затим опет прати вас", "Компас", "Звук (током навигације)", "Друге руте (током навигације)", "Чист приказ (током навигације)", "Преглед (током навигације)"),
        "ms" to listOf("Butang peta", "Pilihan peta sentiasa ada. Gambaran keseluruhan menunjukkan seluruh laluan beberapa saat, kemudian kembali mengikuti anda", "Kompas", "Bunyi (semasa navigasi)", "Laluan lain (semasa navigasi)", "Paparan bersih (semasa navigasi)", "Gambaran keseluruhan (semasa navigasi)"),
        "hu" to listOf("Térképgombok", "A térképbeállítások mindig ott vannak. Az áttekintés néhány másodpercig az egész útvonalat mutatja, majd újra követi Önt", "Iránytű", "Hang (navigáció közben)", "Más útvonalak (navigáció közben)", "Tiszta nézet (navigáció közben)", "Áttekintés (navigáció közben)"),
        "tr" to listOf("Harita düğmeleri", "Harita seçenekleri her zaman görünür. Genel bakış tüm rotayı birkaç saniye gösterir, sonra yine sizi izler", "Pusula", "Ses (navigasyon sırasında)", "Diğer rotalar (navigasyon sırasında)", "Sade görünüm (navigasyon sırasında)", "Genel bakış (navigasyon sırasında)"),
        "ar" to listOf("أزرار الخريطة", "خيارات الخريطة موجودة دائمًا. تعرض النظرة العامة المسار كاملًا لثوانٍ ثم تعود لتتبعك", "البوصلة", "الصوت (أثناء الملاحة)", "مسارات أخرى (أثناء الملاحة)", "عرض نظيف (أثناء الملاحة)", "نظرة عامة (أثناء الملاحة)"),
        "he" to listOf("כפתורי מפה", "אפשרויות המפה תמיד זמינות. הסקירה מציגה את כל המסלול למספר שניות ואז חוזרת לעקוב אחריך", "מצפן", "צליל (בזמן ניווט)", "מסלולים אחרים (בזמן ניווט)", "תצוגה נקייה (בזמן ניווט)", "סקירה (בזמן ניווט)"),
        "ur" to listOf("نقشے کے بٹن", "نقشے کے اختیارات ہمیشہ موجود رہتے ہیں۔ جائزہ چند سیکنڈ پورا راستہ دکھاتا ہے، پھر دوبارہ آپ کو فالو کرتا ہے", "قطب نما", "آواز (نیویگیشن کے دوران)", "دوسرے راستے (نیویگیشن کے دوران)", "صاف منظر (نیویگیشن کے دوران)", "جائزہ (نیویگیشن کے دوران)"),
        "sv" to listOf("Kartknappar", "Kartalternativ finns alltid. Översikt visar hela rutten några sekunder och följer sedan dig igen", "Kompass", "Ljud (under navigering)", "Andra rutter (under navigering)", "Ren vy (under navigering)", "Översikt (under navigering)"),
        "kn" to listOf("ನಕ್ಷೆ ಬಟನ್‌ಗಳು", "ನಕ್ಷೆ ಆಯ್ಕೆಗಳು ಯಾವಾಗಲೂ ಇರುತ್ತವೆ. ಅವಲೋಕನವು ಕೆಲವು ಸೆಕೆಂಡುಗಳ ಕಾಲ ಇಡೀ ಮಾರ್ಗವನ್ನು ತೋರಿಸಿ, ನಂತರ ಮತ್ತೆ ನಿಮ್ಮನ್ನು ಅನುಸರಿಸುತ್ತದೆ", "ದಿಕ್ಸೂಚಿ", "ಧ್ವನಿ (ನ್ಯಾವಿಗೇಷನ್ ವೇಳೆ)", "ಇತರ ಮಾರ್ಗಗಳು (ನ್ಯಾವಿಗೇಷನ್ ವೇಳೆ)", "ಸ್ವಚ್ಛ ನೋಟ (ನ್ಯಾವಿಗೇಷನ್ ವೇಳೆ)", "ಅವಲೋಕನ (ನ್ಯಾವಿಗೇಷನ್ ವೇಳೆ)"),
        "mr" to listOf("नकाशा बटणे", "नकाशा पर्याय नेहमी असतात. विहंगावलोकन काही सेकंद संपूर्ण मार्ग दाखवते, मग पुन्हा तुम्हाला फॉलो करते", "होकायंत्र", "आवाज (नेव्हिगेशन दरम्यान)", "इतर मार्ग (नेव्हिगेशन दरम्यान)", "स्वच्छ दृश्य (नेव्हिगेशन दरम्यान)", "विहंगावलोकन (नेव्हिगेशन दरम्यान)"),
        "nb" to listOf("Kartknapper", "Kartvalg er alltid der. Oversikt viser hele ruten i noen sekunder og følger deretter deg igjen", "Kompass", "Lyd (under navigering)", "Andre ruter (under navigering)", "Ren visning (under navigering)", "Oversikt (under navigering)"),
        "ru" to listOf("Кнопки карты", "Параметры карты есть всегда. Обзор показывает весь маршрут несколько секунд, затем снова следует за вами", "Компас", "Звук (во время навигации)", "Другие маршруты (во время навигации)", "Чистый вид (во время навигации)", "Обзор (во время навигации)"),
        "uk" to listOf("Кнопки карти", "Параметри карти є завжди. Огляд показує весь маршрут кілька секунд, потім знову стежить за вами", "Компас", "Звук (під час навігації)", "Інші маршрути (під час навігації)", "Чистий вигляд (під час навігації)", "Огляд (під час навігації)"),
        "vi" to listOf("Nút trên bản đồ", "Tùy chọn bản đồ luôn có. Tổng quan hiện toàn bộ tuyến đường vài giây rồi quay lại theo dõi bạn", "La bàn", "Âm thanh (khi dẫn đường)", "Tuyến đường khác (khi dẫn đường)", "Chế độ gọn (khi dẫn đường)", "Tổng quan (khi dẫn đường)"),
        "ml" to listOf("മാപ്പ് ബട്ടണുകൾ", "മാപ്പ് ഓപ്ഷനുകൾ എപ്പോഴും ഉണ്ടാകും. ഓവർവ്യൂ കുറച്ച് സെക്കൻഡ് മുഴുവൻ റൂട്ടും കാണിച്ച ശേഷം വീണ്ടും നിങ്ങളെ പിന്തുടരും", "കോമ്പസ്", "ശബ്ദം (നാവിഗേഷൻ സമയത്ത്)", "മറ്റ് റൂട്ടുകൾ (നാവിഗേഷൻ സമയത്ത്)", "ക്ലീൻ വ്യൂ (നാവിഗേഷൻ സമയത്ത്)", "ഓവർവ്യൂ (നാവിഗേഷൻ സമയത്ത്)"),
        "ta" to listOf("வரைபட பொத்தான்கள்", "வரைபட விருப்பங்கள் எப்போதும் இருக்கும். மேலோட்டம் சில விநாடிகள் முழு வழியையும் காட்டி, பிறகு மீண்டும் உங்களைப் பின்தொடரும்", "திசைகாட்டி", "ஒலி (வழிசெலுத்தும்போது)", "பிற வழிகள் (வழிசெலுத்தும்போது)", "தெளிவான காட்சி (வழிசெலுத்தும்போது)", "மேலோட்டம் (வழிசெலுத்தும்போது)"),
        "te" to listOf("మ్యాప్ బటన్లు", "మ్యాప్ ఎంపికలు ఎల్లప్పుడూ ఉంటాయి. అవలోకనం కొన్ని సెకన్లు మొత్తం మార్గాన్ని చూపి, ఆపై మళ్లీ మిమ్మల్ని అనుసరిస్తుంది", "దిక్సూచి", "ధ్వని (నావిగేషన్ సమయంలో)", "ఇతర మార్గాలు (నావిగేషన్ సమయంలో)", "శుభ్రమైన వీక్షణ (నావిగేషన్ సమయంలో)", "అవలోకనం (నావిగేషన్ సమయంలో)"),
        "ko" to listOf("지도 버튼", "지도 옵션은 항상 표시됩니다. 전체 보기는 경로 전체를 몇 초간 보여준 뒤 다시 내 위치를 따라갑니다", "나침반", "소리(길 안내 중)", "다른 경로(길 안내 중)", "깔끔한 보기(길 안내 중)", "전체 보기(길 안내 중)"),
        "ca" to listOf("Botons del mapa", "Opcions del mapa sempre hi és. Vista general mostra tota la ruta uns segons i després torna a seguir-te", "Brúixola", "So (en navegar)", "Altres rutes (en navegar)", "Vista neta (en navegar)", "Vista general (en navegar)")
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
