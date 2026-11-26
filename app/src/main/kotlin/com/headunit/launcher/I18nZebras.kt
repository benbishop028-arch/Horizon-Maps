package com.headunit.launcher

/** Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the Zebra crossings setting's new explanation (#427). */
internal object I18nZebras {
    private const val KEY = "Crossings painted on the road, with Belisha beacons at zebras (close in): on your route, and round you in towns when there's no route"

    private val t: Map<String, String> = mapOf(
        "nl" to "Oversteekplaatsen op de weg getekend, met knipperbollen bij zebrapaden (dichtbij): op je route, en om je heen in de bebouwde kom als er geen route is",
        "de" to "Überwege auf die Straße gemalt, mit Blinkleuchten an Zebrastreifen (aus der Nähe): auf deiner Route und ohne Route rund um dich in Orten",
        "fr" to "Passages piétons peints sur la route, avec feux clignotants aux passages zébrés (de près) : sur votre itinéraire, et autour de vous en ville sans itinéraire",
        "es" to "Pasos de peatones pintados en la calzada, con balizas en los pasos de cebra (de cerca): en tu ruta, y a tu alrededor en poblado cuando no hay ruta",
        "esar" to "Sendas peatonales pintadas en la calle, con balizas en las de cebra (de cerca): en tu ruta, y a tu alrededor en zonas urbanas cuando no hay ruta",
        "esmx" to "Cruces peatonales pintados en la calle, con balizas en los pasos de cebra (de cerca): en tu ruta, y a tu alrededor en la ciudad cuando no hay ruta",
        "it" to "Attraversamenti dipinti sulla strada, con lampeggianti alle strisce zebrate (da vicino): sul percorso, e intorno a te in città quando non c'è un percorso",
        "pt" to "Passadeiras pintadas na estrada, com balizas nas zebras (de perto): no seu percurso, e à sua volta nas localidades quando não há rota",
        "ptbr" to "Faixas de pedestres pintadas na via, com balizas nas zebras (de perto): na sua rota, e ao seu redor na cidade quando não há rota",
        "pl" to "Przejścia namalowane na jezdni, z lampami przy zebrach (z bliska): na trasie, a bez trasy wokół ciebie w terenie zabudowanym",
        "th" to "ทางม้าลายบนถนน พร้อมไฟกะพริบที่ทางม้าลาย (เมื่อซูมใกล้): บนเส้นทาง และรอบตัวคุณในเมืองเมื่อไม่มีเส้นทาง",
        "ja" to "道路に描かれた横断歩道。ゼブラには点滅灯（近くで表示）：ルート上、ルートがないときは市街地で周辺にも",
        "id" to "Penyeberangan yang dicat di jalan, dengan lampu suar di zebra cross (dari dekat): di rute Anda, dan di sekitar Anda di kota saat tidak ada rute",
        "bg" to "Пешеходни пътеки, нарисувани на пътя, с мигащи светлини при зебрите (отблизо): по маршрута, а без маршрут около вас в населени места",
        "sl" to "Prehodi, narisani na cesti, z utripajočimi lučmi pri zebrah (od blizu): na poti, brez poti pa okoli vas v naselju",
        "ro" to "Treceri pictate pe drum, cu lumini intermitente la zebre (de aproape): pe traseu, iar fără traseu în jurul tău în localități",
        "eu" to "Errepidean margotutako pasabideak, zebra-pasabideetan argi keinukariekin (gertutik): zure ibilbidean, eta ibilbiderik ez dagoenean zure inguruan herrietan",
        "zh" to "畫在路面上的行人穿越道，斑馬線附近有閃燈（近看時）：在路線上，沒有路線時則在市區你的周圍",
        "zhhk" to "畫在路面上的行人過路處，斑馬線附近有閃燈（近看時）：在路線上，冇路線時則在市區你的周圍",
        "zhs" to "画在路面上的人行横道，斑马线旁有闪灯（近看时）：在路线上，没有路线时则在市区你的周围",
        "el" to "Διαβάσεις ζωγραφισμένες στον δρόμο, με φανάρια στις διαβάσεις ζέβρα (από κοντά): στη διαδρομή σας, και χωρίς διαδρομή γύρω σας μέσα σε πόλεις",
        "cs" to "Přechody namalované na silnici, u zeber s blikajícími světly (zblízka): na trase, a bez trasy kolem vás v obci",
        "fi" to "Tielle maalatut suojatiet, seepraraidoilla vilkkuvalot (läheltä): reitilläsi, ja ilman reittiä ympärilläsi taajamissa",
        "sk" to "Priechody namaľované na ceste, pri zebrách s blikajúcimi svetlami (zblízka): na trase, a bez trasy okolo vás v obci",
        "da" to "Fodgængerovergange malet på vejen, med blinklygter ved zebrastriber (tæt på): på din rute, og uden rute omkring dig i byer",
        "hr" to "Pješački prijelazi oslikani na cesti, sa svjetlima kod zebri (izbliza): na ruti, a bez rute oko vas u naselju",
        "bs" to "Pješački prelazi oslikani na putu, sa svjetlima kod zebri (izbliza): na ruti, a bez rute oko vas u naselju",
        "sr" to "Пешачки прелази исцртани на путу, са светлима код зебри (изблиза): на рути, а без руте око вас у насељу",
        "ms" to "Lintasan yang dicat di jalan, dengan lampu suar di lintasan zebra (dari dekat): di laluan anda, dan di sekeliling anda di bandar apabila tiada laluan",
        "hu" to "Az útra festett gyalogátkelők, zebráknál villogó lámpákkal (közelről): az útvonaladon, útvonal nélkül pedig körülötted lakott területen",
        "tr" to "Yola boyanmış yaya geçitleri, zebra geçitlerde yanıp sönen ışıklarla (yakından): rotanızda, rota yokken de şehir içinde çevrenizde",
        "ar" to "معابر مرسومة على الطريق، مع أضواء وامضة عند معابر الحمار الوحشي (عن قرب): على مسارك، وحولك داخل المدن عند عدم وجود مسار",
        "he" to "מעברי חצייה מצוירים על הכביש, עם פנסים מהבהבים במעברי זברה (מקרוב): במסלול שלך, ובלי מסלול סביבך בעיר",
        "ur" to "سڑک پر بنی کراسنگز، زیبرا کراسنگ پر چمکتی بتیوں کے ساتھ (قریب سے): آپ کے راستے پر، اور راستہ نہ ہو تو شہر میں آپ کے ارد گرد",
        "sv" to "Övergångsställen målade på vägen, med blinkande ljus vid zebralinjer (på nära håll): på din rutt, och utan rutt runt dig i tätort",
        "kn" to "ರಸ್ತೆಯ ಮೇಲೆ ಬಣ್ಣ ಬಳಿದ ಪಾದಚಾರಿ ದಾಟುವಿಕೆಗಳು, ಜೀಬ್ರಾ ಕ್ರಾಸಿಂಗ್‌ಗಳಲ್ಲಿ ಮಿನುಗುವ ದೀಪಗಳೊಂದಿಗೆ (ಹತ್ತಿರದಿಂದ): ನಿಮ್ಮ ಮಾರ್ಗದಲ್ಲಿ, ಮತ್ತು ಮಾರ್ಗ ಇಲ್ಲದಿದ್ದಾಗ ಪಟ್ಟಣಗಳಲ್ಲಿ ನಿಮ್ಮ ಸುತ್ತ",
        "mr" to "रस्त्यावर रंगवलेले पादचारी मार्ग, झेब्रा क्रॉसिंगवर लुकलुकणारे दिवे (जवळून): तुमच्या मार्गावर, आणि मार्ग नसताना शहरात तुमच्या आजूबाजूला",
        "nb" to "Fotgjengeroverganger malt på veien, med blinklys ved sebrastriper (på nært hold): på ruten din, og uten rute rundt deg i tettsteder",
        "ru" to "Пешеходные переходы на дороге, с мигающими огнями у «зебр» (вблизи): на маршруте, а без маршрута — вокруг вас в городе",
        "uk" to "Пішохідні переходи на дорозі, з блимаючими вогнями біля «зебр» (зблизька): на маршруті, а без маршруту — навколо вас у місті",
        "vi" to "Vạch qua đường vẽ trên mặt đường, có đèn nháy ở vạch ngựa vằn (khi ở gần): trên tuyến đường, và quanh bạn trong đô thị khi không có tuyến",
        "ml" to "റോഡിൽ വരച്ച ക്രോസിംഗുകൾ, സീബ്ര ക്രോസിംഗുകളിൽ മിന്നുന്ന വിളക്കുകളോടെ (അടുത്ത്): നിങ്ങളുടെ റൂട്ടിൽ, റൂട്ട് ഇല്ലാത്തപ്പോൾ നഗരങ്ങളിൽ നിങ്ങളുടെ ചുറ്റും",
        "ta" to "சாலையில் வரையப்பட்ட கடவைகள், வரிக்குதிரைக் கடவைகளில் மின்னும் விளக்குகளுடன் (அருகில்): உங்கள் வழியில், வழி இல்லாதபோது நகரங்களில் உங்களைச் சுற்றி",
        "te" to "రోడ్డుపై గీసిన పాదచారుల దాటులు, జీబ్రా క్రాసింగ్‌ల వద్ద మెరిసే దీపాలతో (దగ్గరగా): మీ మార్గంలో, మార్గం లేనప్పుడు పట్టణాల్లో మీ చుట్టూ",
        "ko" to "도로에 그려진 횡단보도, 얼룩말 횡단보도에는 점멸등(가까이서): 경로 위, 경로가 없을 때는 시내에서 내 주변",
        "ca" to "Passos de vianants pintats a la calçada, amb balises als passos de zebra (de prop): a la teva ruta, i al teu voltant a les poblacions quan no hi ha ruta",
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { listOf(KEY to it) } ?: emptyList()
}
