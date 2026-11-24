package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Keep the screen on without a route (#369).
 * Order: the setting's name, its explanation.
 */
internal object I18nKeepOn {
    private val keys = listOf(
        "Keep the screen on without a route",
        "On a phone the screen stays on only while you navigate. Turn this on to keep it on while you just watch the map and traffic. Uses more battery"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Scherm aan houden zonder route", "Op een telefoon blijft het scherm alleen aan tijdens het navigeren. Zet dit aan om het aan te houden terwijl je alleen de kaart en het verkeer bekijkt. Gebruikt meer batterij"),
        "de" to listOf("Bildschirm ohne Route anlassen", "Auf einem Handy bleibt der Bildschirm nur während der Navigation an. Aktiviere dies, damit er auch beim reinen Ansehen von Karte und Verkehr an bleibt. Verbraucht mehr Akku"),
        "fr" to listOf("Garder l'écran allumé sans itinéraire", "Sur un téléphone, l'écran ne reste allumé que pendant la navigation. Activez cette option pour le garder allumé quand vous regardez juste la carte et le trafic. Consomme plus de batterie"),
        "es" to listOf("Mantener la pantalla encendida sin ruta", "En un teléfono la pantalla solo se queda encendida mientras navegas. Actívalo para mantenerla encendida cuando solo miras el mapa y el tráfico. Gasta más batería"),
        "esar" to listOf("Mantener la pantalla encendida sin ruta", "En un teléfono la pantalla solo se queda encendida mientras navegas. Actívalo para mantenerla encendida cuando solo miras el mapa y el tráfico. Gasta más batería"),
        "esmx" to listOf("Mantener la pantalla encendida sin ruta", "En un teléfono la pantalla solo se queda encendida mientras navegas. Actívalo para mantenerla encendida cuando solo miras el mapa y el tráfico. Gasta más batería"),
        "it" to listOf("Tieni lo schermo acceso senza percorso", "Su un telefono lo schermo resta acceso solo durante la navigazione. Attivalo per tenerlo acceso mentre guardi solo la mappa e il traffico. Consuma più batteria"),
        "pt" to listOf("Manter o ecrã ligado sem rota", "Num telemóvel o ecrã só fica ligado durante a navegação. Ative para o manter ligado enquanto vê apenas o mapa e o trânsito. Gasta mais bateria"),
        "pl" to listOf("Nie wygaszaj ekranu bez trasy", "Na telefonie ekran pozostaje włączony tylko podczas nawigacji. Włącz, aby został włączony, gdy tylko oglądasz mapę i ruch. Zużywa więcej baterii"),
        "th" to listOf("เปิดหน้าจอค้างไว้เมื่อไม่มีเส้นทาง", "บนโทรศัพท์ หน้าจอจะเปิดค้างเฉพาะขณะนำทาง เปิดตัวเลือกนี้เพื่อให้เปิดค้างขณะดูแผนที่และการจราจรเท่านั้น ใช้แบตเตอรี่มากขึ้น"),
        "ja" to listOf("ルートがなくても画面を点灯したままにする", "スマートフォンでは、案内中のみ画面が点灯したままになります。地図と渋滞を見ているだけのときも点灯させるにはオンにします。バッテリーの消費が増えます"),
        "id" to listOf("Biarkan layar menyala tanpa rute", "Di ponsel, layar hanya tetap menyala saat bernavigasi. Aktifkan agar tetap menyala saat hanya melihat peta dan lalu lintas. Memakai lebih banyak baterai"),
        "bg" to listOf("Екранът да остава включен без маршрут", "На телефон екранът остава включен само при навигация. Включете, за да остава включен, докато само гледате картата и трафика. Изразходва повече батерия"),
        "sl" to listOf("Zaslon naj ostane vklopljen brez poti", "Na telefonu zaslon ostane vklopljen samo med navigacijo. Vklopite, da ostane vklopljen, ko samo gledate zemljevid in promet. Porabi več baterije"),
        "ro" to listOf("Păstrează ecranul aprins fără traseu", "Pe telefon, ecranul rămâne aprins doar în timpul navigării. Activează pentru a-l păstra aprins când te uiți doar la hartă și trafic. Consumă mai multă baterie"),
        "eu" to listOf("Mantendu pantaila piztuta ibilbiderik gabe", "Telefonoan pantaila nabigatzen ari zarenean soilik geratzen da piztuta. Aktibatu mapa eta trafikoa soilik ikusten ari zarenean piztuta mantentzeko. Bateria gehiago erabiltzen du"),
        "zh" to listOf("沒有路線時保持螢幕開啟", "在手機上，螢幕只會在導航時保持開啟。開啟此選項，可在只查看地圖和路況時也保持螢幕開啟。會多耗電"),
        "zhhk" to listOf("沒有路線時保持螢幕開啟", "在手機上，螢幕只會在導航時保持開啟。開啟此選項，可在只查看地圖和路況時也保持螢幕開啟。會多耗電"),
        "zhs" to listOf("没有路线时保持屏幕常亮", "在手机上，屏幕只会在导航时保持常亮。开启此选项，可在只查看地图和路况时也保持屏幕常亮。会多耗电"),
        "el" to listOf("Η οθόνη να μένει αναμμένη χωρίς διαδρομή", "Στο κινητό η οθόνη μένει αναμμένη μόνο κατά την πλοήγηση. Ενεργοποιήστε το για να μένει αναμμένη όταν βλέπετε μόνο τον χάρτη και την κίνηση. Καταναλώνει περισσότερη μπαταρία"),
        "cs" to listOf("Nechat displej zapnutý bez trasy", "V telefonu zůstane displej zapnutý jen při navigaci. Zapněte, aby zůstal zapnutý, když jen sledujete mapu a dopravu. Spotřebuje více baterie"),
        "fi" to listOf("Pidä näyttö päällä ilman reittiä", "Puhelimessa näyttö pysyy päällä vain navigoinnin aikana. Ota käyttöön, jos haluat näytön pysyvän päällä, kun katsot vain karttaa ja liikennettä. Kuluttaa enemmän akkua"),
        "sk" to listOf("Nechať displej zapnutý bez trasy", "V telefóne zostane displej zapnutý len počas navigácie. Zapnite, aby zostal zapnutý, keď len sledujete mapu a dopravu. Spotrebuje viac batérie"),
        "da" to listOf("Hold skærmen tændt uden rute", "På en telefon er skærmen kun tændt under navigation. Slå til for at holde den tændt, mens du blot ser på kortet og trafikken. Bruger mere batteri"),
        "hr" to listOf("Ostavi zaslon uključen bez rute", "Na telefonu zaslon ostaje uključen samo tijekom navigacije. Uključite da ostane uključen dok samo gledate kartu i promet. Troši više baterije"),
        "bs" to listOf("Ostavi ekran uključen bez rute", "Na telefonu ekran ostaje uključen samo tokom navigacije. Uključite da ostane uključen dok samo gledate kartu i saobraćaj. Troši više baterije"),
        "sr" to listOf("Остави екран укључен без руте", "На телефону екран остаје укључен само током навигације. Укључите да остане укључен док само гледате мапу и саобраћај. Троши више батерије"),
        "ms" to listOf("Kekalkan skrin hidup tanpa laluan", "Pada telefon, skrin hanya kekal hidup semasa navigasi. Hidupkan untuk mengekalkannya hidup semasa anda hanya melihat peta dan trafik. Menggunakan lebih banyak bateri"),
        "hu" to listOf("Képernyő bekapcsolva tartása útvonal nélkül", "Telefonon a képernyő csak navigáció közben marad bekapcsolva. Kapcsolja be, hogy akkor is bekapcsolva maradjon, amikor csak a térképet és a forgalmat nézi. Több akkumulátort használ"),
        "tr" to listOf("Rota yokken ekranı açık tut", "Telefonda ekran yalnızca navigasyon sırasında açık kalır. Yalnızca haritaya ve trafiğe bakarken de açık kalması için bunu açın. Daha fazla pil kullanır"),
        "ar" to listOf("إبقاء الشاشة مضاءة بدون مسار", "على الهاتف تبقى الشاشة مضاءة فقط أثناء الملاحة. فعّل هذا الخيار لإبقائها مضاءة أثناء مشاهدة الخريطة وحركة المرور فقط. يستهلك مزيدًا من البطارية"),
        "he" to listOf("השאר את המסך דולק ללא מסלול", "בטלפון המסך נשאר דולק רק בזמן ניווט. הפעל כדי שיישאר דולק גם כשאתה רק צופה במפה ובתנועה. צורך יותר סוללה"),
        "ur" to listOf("روٹ کے بغیر اسکرین آن رکھیں", "فون پر اسکرین صرف نیویگیشن کے دوران آن رہتی ہے۔ صرف نقشہ اور ٹریفک دیکھتے وقت بھی آن رکھنے کے لیے اسے آن کریں۔ زیادہ بیٹری استعمال ہوتی ہے"),
        "sv" to listOf("Håll skärmen på utan rutt", "På en telefon är skärmen bara på under navigering. Slå på för att hålla den på medan du bara tittar på kartan och trafiken. Använder mer batteri"),
        "kn" to listOf("ಮಾರ್ಗವಿಲ್ಲದೆ ಪರದೆಯನ್ನು ಆನ್ ಆಗಿರಿಸಿ", "ಫೋನ್‌ನಲ್ಲಿ ನ್ಯಾವಿಗೇಷನ್ ವೇಳೆ ಮಾತ್ರ ಪರದೆ ಆನ್ ಆಗಿರುತ್ತದೆ. ನಕ್ಷೆ ಮತ್ತು ಟ್ರಾಫಿಕ್ ನೋಡುತ್ತಿರುವಾಗಲೂ ಆನ್ ಆಗಿರಲು ಇದನ್ನು ಆನ್ ಮಾಡಿ. ಹೆಚ್ಚು ಬ್ಯಾಟರಿ ಬಳಸುತ್ತದೆ"),
        "mr" to listOf("मार्गाशिवाय स्क्रीन चालू ठेवा", "फोनवर स्क्रीन फक्त नेव्हिगेशन दरम्यान चालू राहते. फक्त नकाशा आणि ट्रॅफिक पाहताना देखील चालू ठेवण्यासाठी हे चालू करा. जास्त बॅटरी वापरते"),
        "nb" to listOf("Hold skjermen på uten rute", "På en telefon er skjermen bare på under navigering. Slå på for å holde den på mens du bare ser på kartet og trafikken. Bruker mer batteri"),
        "ru" to listOf("Не выключать экран без маршрута", "На телефоне экран остаётся включённым только во время навигации. Включите, чтобы он не гас, пока вы просто смотрите карту и пробки. Расходует больше заряда"),
        "uk" to listOf("Не вимикати екран без маршруту", "На телефоні екран залишається ввімкненим лише під час навігації. Увімкніть, щоб він не гас, поки ви просто дивитеся карту й затори. Витрачає більше заряду"),
        "vi" to listOf("Giữ màn hình sáng khi không có tuyến đường", "Trên điện thoại, màn hình chỉ sáng liên tục khi dẫn đường. Bật để màn hình luôn sáng khi bạn chỉ xem bản đồ và giao thông. Tốn pin hơn"),
        "ml" to listOf("റൂട്ട് ഇല്ലാതെയും സ്ക്രീൻ ഓണാക്കി വയ്ക്കുക", "ഫോണിൽ നാവിഗേഷൻ സമയത്ത് മാത്രമേ സ്ക്രീൻ ഓണായി നിൽക്കൂ. മാപ്പും ട്രാഫിക്കും മാത്രം നോക്കുമ്പോഴും ഓണായി നിൽക്കാൻ ഇത് ഓണാക്കുക. കൂടുതൽ ബാറ്ററി ഉപയോഗിക്കും"),
        "ta" to listOf("வழி இல்லாதபோதும் திரையை இயக்கத்தில் வைத்திரு", "தொலைபேசியில் வழிசெலுத்தும்போது மட்டுமே திரை இயக்கத்தில் இருக்கும். வரைபடத்தையும் போக்குவரத்தையும் மட்டும் பார்க்கும்போதும் இயக்கத்தில் வைக்க இதை இயக்கவும். அதிக பேட்டரி பயன்படுத்தும்"),
        "te" to listOf("మార్గం లేకపోయినా స్క్రీన్‌ను ఆన్‌లో ఉంచండి", "ఫోన్‌లో నావిగేషన్ సమయంలో మాత్రమే స్క్రీన్ ఆన్‌లో ఉంటుంది. మ్యాప్ మరియు ట్రాఫిక్ మాత్రమే చూస్తున్నప్పుడు కూడా ఆన్‌లో ఉంచడానికి దీన్ని ఆన్ చేయండి. ఎక్కువ బ్యాటరీ వాడుతుంది"),
        "ko" to listOf("경로가 없어도 화면 켜 두기", "휴대전화에서는 길 안내 중에만 화면이 켜져 있습니다. 지도와 교통 상황만 볼 때도 켜 두려면 이 옵션을 켜세요. 배터리를 더 많이 사용합니다"),
        "ca" to listOf("Mantén la pantalla encesa sense ruta", "En un telèfon la pantalla només es manté encesa mentre navegues. Activa-ho per mantenir-la encesa quan només mires el mapa i el trànsit. Gasta més bateria")
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
