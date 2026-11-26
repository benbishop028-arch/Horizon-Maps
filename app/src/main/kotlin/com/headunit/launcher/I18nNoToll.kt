package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the route choice's "No tolls" option.
 * Order: Tolls, No tolls, No tolls · +{0} min, Offer a route without tolls, its explanation.
 */
internal object I18nNoToll {
    private val keys = listOf(
        "Tolls",
        "No tolls",
        "No tolls · +{0} min",
        "Offer a route without tolls",
        "When the fastest route uses toll roads, also show one without them, with how much longer it takes"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Tol", "Geen tol", "Geen tol · +{0} min", "Een route zonder tol aanbieden", "Als de snelste route tolwegen gebruikt, ook een route zonder tol tonen, met hoeveel langer die duurt"),
        "de" to listOf("Maut", "Keine Maut", "Keine Maut · +{0} Min.", "Route ohne Maut anbieten", "Wenn die schnellste Route Mautstraßen nutzt, auch eine Route ohne Maut zeigen, mit der zusätzlichen Fahrzeit"),
        "fr" to listOf("Péages", "Sans péage", "Sans péage · +{0} min", "Proposer un itinéraire sans péage", "Quand l'itinéraire le plus rapide a des péages, en montrer aussi un sans péage, avec le temps en plus"),
        "es" to listOf("Peajes", "Sin peajes", "Sin peajes · +{0} min", "Ofrecer una ruta sin peajes", "Cuando la ruta más rápida usa peajes, mostrar también una sin ellos, con cuánto más tarda"),
        "esar" to listOf("Peajes", "Sin peajes", "Sin peajes · +{0} min", "Ofrecer una ruta sin peajes", "Cuando la ruta más rápida usa peajes, mostrar también una sin ellos, con cuánto más tarda"),
        "esmx" to listOf("Casetas", "Sin casetas", "Sin casetas · +{0} min", "Ofrecer una ruta sin casetas", "Cuando la ruta más rápida usa casetas de cobro, mostrar también una sin ellas, con cuánto más tarda"),
        "it" to listOf("Pedaggi", "Senza pedaggi", "Senza pedaggi · +{0} min", "Offri un percorso senza pedaggi", "Quando il percorso più veloce ha pedaggi, mostra anche uno senza, con quanto tempo in più richiede"),
        "pt" to listOf("Portagens", "Sem portagens", "Sem portagens · +{0} min", "Oferecer um percurso sem portagens", "Quando o percurso mais rápido usa portagens, mostrar também um sem elas, com quanto mais demora"),
        "ptbr" to listOf("Pedágios", "Sem pedágios", "Sem pedágios · +{0} min", "Oferecer uma rota sem pedágios", "Quando a rota mais rápida usa pedágios, mostrar também uma sem eles, com quanto tempo a mais leva"),
        "pl" to listOf("Opłaty", "Bez opłat", "Bez opłat · +{0} min", "Proponuj trasę bez opłat", "Gdy najszybsza trasa ma płatne drogi, pokaż też trasę bez nich i o ile dłużej trwa"),
        "th" to listOf("ค่าผ่านทาง", "ไม่มีค่าผ่านทาง", "ไม่มีค่าผ่านทาง · +{0} นาที", "แสดงเส้นทางที่ไม่เสียค่าผ่านทาง", "เมื่อเส้นทางที่เร็วที่สุดมีถนนเก็บค่าผ่านทาง ให้แสดงเส้นทางที่ไม่มีด้วย พร้อมเวลาที่เพิ่มขึ้น"),
        "ja" to listOf("有料", "有料なし", "有料なし · +{0}分", "有料道路を使わないルートも提案", "最速ルートに有料道路がある場合、使わないルートも表示し、増える時間も示します"),
        "id" to listOf("Tol", "Tanpa tol", "Tanpa tol · +{0} mnt", "Tawarkan rute tanpa tol", "Jika rute tercepat melewati jalan tol, tampilkan juga rute tanpa tol beserta tambahan waktunya"),
        "bg" to listOf("Такси", "Без такси", "Без такси · +{0} мин", "Предлагай маршрут без такси", "Когато най-бързият маршрут минава по платени пътища, показвай и такъв без тях, с колко повече време отнема"),
        "sl" to listOf("Cestnine", "Brez cestnin", "Brez cestnin · +{0} min", "Ponudi pot brez cestnin", "Če najhitrejša pot uporablja cestninske ceste, pokaži tudi pot brez njih, z dodatnim časom"),
        "ro" to listOf("Taxe", "Fără taxe", "Fără taxe · +{0} min", "Oferă un traseu fără taxe", "Când cel mai rapid traseu are drumuri cu taxă, arată și unul fără, cu cât durează mai mult"),
        "eu" to listOf("Ordainketak", "Ordainketarik gabe", "Ordainketarik gabe · +{0} min", "Eskaini ordainketarik gabeko ibilbidea", "Ibilbiderik azkarrenak ordainpeko errepideak baditu, erakutsi horiek gabeko bat ere, zenbat denbora gehiago behar duen adieraziz"),
        "zh" to listOf("收費", "無收費", "無收費 · +{0} 分鐘", "提供不收費路線", "當最快路線有收費道路時，同時顯示不收費的路線，並標示多出的時間"),
        "zhhk" to listOf("收費", "免收費", "免收費 · +{0} 分鐘", "提供免收費路線", "當最快路線有收費道路時，同時顯示免收費路線，並標示多出的時間"),
        "zhs" to listOf("收费", "无收费", "无收费 · +{0} 分钟", "提供无收费路线", "当最快路线有收费道路时，同时显示无收费的路线，并标出多出的时间"),
        "el" to listOf("Διόδια", "Χωρίς διόδια", "Χωρίς διόδια · +{0} λεπτά", "Πρόταση διαδρομής χωρίς διόδια", "Όταν η ταχύτερη διαδρομή έχει διόδια, εμφάνιση και μίας χωρίς, με τον επιπλέον χρόνο"),
        "cs" to listOf("Mýtné", "Bez mýtného", "Bez mýtného · +{0} min", "Nabízet trasu bez mýtného", "Když nejrychlejší trasa vede po zpoplatněných silnicích, zobrazit i trasu bez nich a o kolik trvá déle"),
        "fi" to listOf("Tietullit", "Ei tietulleja", "Ei tietulleja · +{0} min", "Tarjoa reitti ilman tietulleja", "Kun nopein reitti käyttää tietullillisia teitä, näytä myös reitti ilman niitä ja kuinka paljon pidempi se on"),
        "sk" to listOf("Mýto", "Bez mýta", "Bez mýta · +{0} min", "Ponúkať trasu bez mýta", "Keď najrýchlejšia trasa vedie po spoplatnených cestách, zobraziť aj trasu bez nich a o koľko trvá dlhšie"),
        "da" to listOf("Afgifter", "Uden afgifter", "Uden afgifter · +{0} min", "Tilbyd en rute uden afgifter", "Når den hurtigste rute bruger betalingsveje, vis også en uden, med hvor meget længere den tager"),
        "hr" to listOf("Cestarina", "Bez cestarine", "Bez cestarine · +{0} min", "Ponudi rutu bez cestarine", "Kad najbrža ruta ide cestama s naplatom, prikaži i rutu bez njih, s dodatnim vremenom"),
        "bs" to listOf("Putarina", "Bez putarine", "Bez putarine · +{0} min", "Ponudi rutu bez putarine", "Kad najbrža ruta ide cestama s naplatom, prikaži i rutu bez njih, s dodatnim vremenom"),
        "sr" to listOf("Путарина", "Без путарине", "Без путарине · +{0} мин", "Понуди руту без путарине", "Када најбржа рута користи путеве са наплатом, прикажи и руту без њих, са додатним временом"),
        "ms" to listOf("Tol", "Tanpa tol", "Tanpa tol · +{0} min", "Tawarkan laluan tanpa tol", "Apabila laluan terpantas menggunakan jalan bertol, paparkan juga laluan tanpa tol dengan tambahan masa"),
        "hu" to listOf("Útdíj", "Útdíj nélkül", "Útdíj nélkül · +{0} perc", "Útdíjmentes útvonal felkínálása", "Ha a leggyorsabb útvonal fizetős utat használ, mutasson egy díjmentest is, a többletidővel"),
        "tr" to listOf("Ücretli", "Ücretsiz", "Ücretsiz · +{0} dk", "Ücretsiz rota öner", "En hızlı rota ücretli yollar kullanıyorsa, onlarsız bir rotayı da ne kadar uzun sürdüğüyle göster"),
        "ar" to listOf("رسوم", "بدون رسوم", "بدون رسوم · +{0} د", "اقتراح مسار بدون رسوم", "عندما يستخدم أسرع مسار طرقًا ذات رسوم، اعرض أيضًا مسارًا بدونها مع الوقت الإضافي"),
        "he" to listOf("אגרה", "ללא אגרה", "ללא אגרה · +{0} דק׳", "להציע מסלול ללא אגרה", "כשהמסלול המהיר ביותר עובר בכבישי אגרה, להציג גם מסלול בלעדיהם, עם הזמן הנוסף"),
        "ur" to listOf("ٹول", "بغیر ٹول", "بغیر ٹول · +{0} منٹ", "بغیر ٹول کا راستہ پیش کریں", "جب تیز ترین راستے میں ٹول سڑکیں ہوں تو ان کے بغیر راستہ بھی دکھائیں، اضافی وقت کے ساتھ"),
        "sv" to listOf("Avgifter", "Utan avgifter", "Utan avgifter · +{0} min", "Erbjud en rutt utan avgifter", "När den snabbaste rutten använder avgiftsvägar, visa även en utan dem, med hur mycket längre den tar"),
        "kn" to listOf("ಟೋಲ್", "ಟೋಲ್ ಇಲ್ಲ", "ಟೋಲ್ ಇಲ್ಲ · +{0} ನಿಮಿ", "ಟೋಲ್ ಇಲ್ಲದ ಮಾರ್ಗವನ್ನು ಸೂಚಿಸಿ", "ವೇಗದ ಮಾರ್ಗದಲ್ಲಿ ಟೋಲ್ ರಸ್ತೆಗಳಿದ್ದರೆ, ಅವುಗಳಿಲ್ಲದ ಮಾರ್ಗವನ್ನೂ ಹೆಚ್ಚುವರಿ ಸಮಯದೊಂದಿಗೆ ತೋರಿಸಿ"),
        "mr" to listOf("टोल", "टोल नाही", "टोल नाही · +{0} मि", "टोल नसलेला मार्ग सुचवा", "सर्वात जलद मार्गावर टोल रस्ते असतील तर त्यांच्याशिवायचा मार्गही अतिरिक्त वेळेसह दाखवा"),
        "nb" to listOf("Bompenger", "Uten bompenger", "Uten bompenger · +{0} min", "Tilby en rute uten bompenger", "Når den raskeste ruten bruker bomveier, vis også en uten, med hvor mye lengre den tar"),
        "ru" to listOf("Платные", "Без платных дорог", "Без платных дорог · +{0} мин", "Предлагать маршрут без платных дорог", "Если самый быстрый маршрут идёт по платным дорогам, показывать и маршрут без них, с дополнительным временем"),
        "uk" to listOf("Платні", "Без платних доріг", "Без платних доріг · +{0} хв", "Пропонувати маршрут без платних доріг", "Якщо найшвидший маршрут іде платними дорогами, показувати й маршрут без них, з додатковим часом"),
        "vi" to listOf("Thu phí", "Không thu phí", "Không thu phí · +{0} phút", "Đề xuất tuyến không thu phí", "Khi tuyến nhanh nhất đi đường thu phí, hiện thêm một tuyến không thu phí cùng thời gian tăng thêm"),
        "ml" to listOf("ടോൾ", "ടോൾ ഇല്ല", "ടോൾ ഇല്ല · +{0} മി", "ടോൾ ഇല്ലാത്ത റൂട്ട് നിർദ്ദേശിക്കുക", "ഏറ്റവും വേഗമുള്ള റൂട്ടിൽ ടോൾ റോഡുകളുണ്ടെങ്കിൽ, അവയില്ലാത്ത റൂട്ടും അധിക സമയത്തോടെ കാണിക്കുക"),
        "ta" to listOf("சுங்கம்", "சுங்கம் இல்லை", "சுங்கம் இல்லை · +{0} நி", "சுங்கம் இல்லாத வழியை வழங்கு", "வேகமான வழியில் சுங்கச் சாலைகள் இருந்தால், அவை இல்லாத வழியையும் கூடுதல் நேரத்துடன் காட்டு"),
        "te" to listOf("టోల్", "టోల్ లేదు", "టోల్ లేదు · +{0} నిమి", "టోల్ లేని మార్గాన్ని సూచించు", "వేగవంతమైన మార్గంలో టోల్ రోడ్లు ఉంటే, అవి లేని మార్గాన్ని కూడా అదనపు సమయంతో చూపించు"),
        "ko" to listOf("통행료", "통행료 없음", "통행료 없음 · +{0}분", "통행료 없는 경로 제안", "가장 빠른 경로에 유료 도로가 있으면 없는 경로도 추가 시간과 함께 표시합니다"),
        "ca" to listOf("Peatges", "Sense peatges", "Sense peatges · +{0} min", "Oferir una ruta sense peatges", "Quan la ruta més ràpida té peatges, mostrar-ne també una sense, amb quant més triga"),
        "mk" to listOf("Патарини", "Без патарини", "Без патарини · +{0} мин", "Понуди рута без патарини", "Кога најбрзата рута користи патишта со наплата, прикажи и рута без нив, со дополнителното време"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
