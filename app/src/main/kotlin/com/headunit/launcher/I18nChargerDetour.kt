package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Charger detour setting (#343).
 * Order: the setting's name, the "usual" choice, its explanation.
 */
internal object I18nChargerDetour {
    private val keys = listOf(
        "Charger detour",
        "Usual",
        "On a route, how many minutes off it a charger can be and still be listed. Bigger finds chargers further from the road"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Omweg naar laadpalen", "Gebruikelijk", "Hoeveel minuten van de route af een laadpaal mag liggen om nog in de lijst te staan. Hoger vindt laadpalen verder van de weg"),
        "de" to listOf("Umweg zu Ladestationen", "Üblich", "Wie viele Minuten abseits der Route eine Ladestation liegen darf und trotzdem aufgelistet wird. Mehr findet Ladestationen weiter von der Straße"),
        "fr" to listOf("Détour vers les bornes", "Habituel", "Sur un itinéraire, combien de minutes de détour une borne peut représenter pour rester listée. Plus grand trouve des bornes plus loin de la route"),
        "es" to listOf("Desvío a cargadores", "Habitual", "En una ruta, cuántos minutos fuera de ella puede estar un cargador para aparecer en la lista. Más minutos encuentra cargadores más lejos de la carretera"),
        "esar" to listOf("Desvío a cargadores", "Habitual", "En una ruta, cuántos minutos fuera de ella puede estar un cargador para aparecer en la lista. Más minutos encuentra cargadores más lejos de la carretera"),
        "esmx" to listOf("Desvío a cargadores", "Habitual", "En una ruta, cuántos minutos fuera de ella puede estar un cargador para aparecer en la lista. Más minutos encuentra cargadores más lejos de la carretera"),
        "it" to listOf("Deviazione per le colonnine", "Abituale", "In un percorso, quanti minuti fuori strada può essere una colonnina per restare in elenco. Più alto trova colonnine più lontane dalla strada"),
        "pt" to listOf("Desvio para carregadores", "Habitual", "Numa rota, quantos minutos fora dela pode estar um carregador para continuar na lista. Mais minutos encontra carregadores mais longe da estrada"),
        "pl" to listOf("Objazd do ładowarek", "Zwykły", "Na trasie: ile minut od niej może być ładowarka, by nadal była na liście. Więcej znajdzie ładowarki dalej od drogi"),
        "th" to listOf("การอ้อมไปยังที่ชาร์จ", "ปกติ", "ในเส้นทาง ที่ชาร์จห่างจากเส้นทางได้กี่นาทีแล้วยังแสดงในรายการ ค่ามากขึ้นจะพบที่ชาร์จที่ไกลจากถนนมากขึ้น"),
        "ja" to listOf("充電器への寄り道", "通常", "ルート上で、リストに表示する充電器の、ルートからの寄り道の最大分数です。大きくするとより道路から離れた充電器も見つかります"),
        "id" to listOf("Jalan memutar ke pengisi daya", "Biasa", "Pada rute, berapa menit dari rute sebuah pengisi daya boleh berada agar tetap terdaftar. Lebih besar menemukan pengisi daya yang lebih jauh dari jalan"),
        "bg" to listOf("Отклонение до зарядни", "Обичайно", "При маршрут: колко минути извън него може да е една зарядна, за да остане в списъка. По-голямо намира зарядни по-далеч от пътя"),
        "sl" to listOf("Ovinek do polnilnic", "Običajno", "Na poti: koliko minut stran od nje sme biti polnilnica, da je še na seznamu. Več najde polnilnice dlje od ceste"),
        "ro" to listOf("Ocolire spre stații de încărcare", "Obișnuit", "Pe traseu: câte minute în afara lui poate fi o stație de încărcare ca să rămână în listă. Mai mult găsește stații mai departe de drum"),
        "eu" to listOf("Kargagailuetarako bidegurutzea", "Ohikoa", "Ibilbide batean, ibilbidetik zenbat minutura egon daitekeen kargagailu bat zerrendan agertzeko. Handiagoak errepidetik urrunago dauden kargagailuak aurkitzen ditu"),
        "zh" to listOf("充電站繞路時間", "一般", "路線中，充電站離開路線最多幾分鐘仍會列出。數字越大，越能找到離道路較遠的充電站"),
        "zhhk" to listOf("充電站繞路時間", "一般", "路線中，充電站離開路線最多幾分鐘仍會列出。數字越大，越能找到離道路較遠的充電站"),
        "zhs" to listOf("充电桩绕路时间", "一般", "路线中，充电桩离开路线最多几分钟仍会列出。数字越大，越能找到离道路较远的充电桩"),
        "el" to listOf("Παράκαμψη προς φορτιστές", "Συνήθης", "Σε διαδρομή, πόσα λεπτά εκτός της μπορεί να είναι ένας φορτιστής ώστε να εμφανίζεται. Μεγαλύτερο βρίσκει φορτιστές πιο μακριά από τον δρόμο"),
        "cs" to listOf("Zajížďka k nabíječkám", "Obvyklé", "Na trase: kolik minut od ní smí nabíječka být, aby byla v seznamu. Více najde nabíječky dál od silnice"),
        "fi" to listOf("Lisäkierros latauspisteille", "Tavallinen", "Reitillä: kuinka monta minuuttia reitin sivussa latauspiste saa olla näkyäkseen luettelossa. Suurempi löytää latauspisteitä kauempaa tiestä"),
        "sk" to listOf("Zachádzka k nabíjačkám", "Bežné", "Na trase: koľko minút od nej môže byť nabíjačka, aby bola v zozname. Viac nájde nabíjačky ďalej od cesty"),
        "da" to listOf("Omvej til ladere", "Sædvanlig", "På en rute: hvor mange minutter fra den en lader må ligge og stadig vises. Højere finder ladere længere fra vejen"),
        "hr" to listOf("Zaobilazak do punionica", "Uobičajeno", "Na ruti: koliko minuta od nje punionica smije biti da bude na popisu. Veće pronalazi punionice dalje od ceste"),
        "bs" to listOf("Zaobilazak do punionica", "Uobičajeno", "Na ruti: koliko minuta od nje punionica smije biti da bude na listi. Veće pronalazi punionice dalje od puta"),
        "sr" to listOf("Заобилазак до пуњача", "Уобичајено", "На рути: колико минута од ње пуњач сме бити да буде на листи. Веће налази пуњаче даље од пута"),
        "ms" to listOf("Lencongan ke pengecas", "Biasa", "Pada laluan, berapa minit dari laluan sesebuah pengecas boleh berada dan masih disenaraikan. Lebih besar menemui pengecas yang lebih jauh dari jalan"),
        "hu" to listOf("Kerülő a töltőkhöz", "Szokásos", "Útvonalon: hány percnyire lehet a töltő az útvonaltól, hogy még szerepeljen a listán. Nagyobb érték az úttól távolabbi töltőket is megtalálja"),
        "tr" to listOf("Şarj istasyonu sapması", "Olağan", "Rotada, bir şarj istasyonunun listede kalması için rotadan en fazla kaç dakika uzakta olabileceği. Büyük değer yoldan daha uzaktaki istasyonları bulur"),
        "ar" to listOf("الانحراف إلى الشواحن", "المعتاد", "على المسار، كم دقيقة يمكن أن يبعد الشاحن عنه ويبقى في القائمة. الرقم الأكبر يجد شواحن أبعد عن الطريق"),
        "he" to listOf("עקיפה לעמדות טעינה", "רגיל", "במסלול: כמה דקות מהמסלול יכולה עמדת טעינה להיות ועדיין להופיע ברשימה. ערך גדול יותר מוצא עמדות רחוקות יותר מהכביש"),
        "ur" to listOf("چارجرز تک چکر", "معمول", "راستے میں، چارجر راستے سے کتنے منٹ دور ہو سکتا ہے اور پھر بھی فہرست میں رہے۔ بڑی قدر سڑک سے دور چارجر ڈھونڈتی ہے"),
        "sv" to listOf("Avstickare till laddare", "Vanlig", "På en rutt: hur många minuter från den en laddare får ligga och ändå visas. Högre hittar laddare längre från vägen"),
        "kn" to listOf("ಚಾರ್ಜರ್‌ಗಳಿಗೆ ಸುತ್ತು", "ಸಾಮಾನ್ಯ", "ಮಾರ್ಗದಲ್ಲಿ, ಚಾರ್ಜರ್ ಮಾರ್ಗದಿಂದ ಎಷ್ಟು ನಿಮಿಷ ದೂರವಿದ್ದರೂ ಪಟ್ಟಿಯಲ್ಲಿ ಕಾಣಿಸುತ್ತದೆ. ಹೆಚ್ಚಿದ್ದರೆ ರಸ್ತೆಯಿಂದ ದೂರದ ಚಾರ್ಜರ್‌ಗಳು ಸಿಗುತ್ತವೆ"),
        "mr" to listOf("चार्जरसाठी वळसा", "नेहमीचा", "मार्गावर, चार्जर मार्गापासून किती मिनिटे दूर असला तरी यादीत दिसेल. जास्त मूल्य रस्त्यापासून दूरचे चार्जर शोधते"),
        "nb" to listOf("Omvei til ladere", "Vanlig", "På en rute: hvor mange minutter fra den en lader kan ligge og likevel vises. Høyere finner ladere lenger fra veien"),
        "ru" to listOf("Крюк до зарядок", "Обычный", "На маршруте: на сколько минут в стороне от него может быть зарядка, чтобы остаться в списке. Больше значение находит зарядки дальше от дороги"),
        "uk" to listOf("Об'їзд до зарядок", "Звичайний", "На маршруті: на скільки хвилин убік від нього може бути зарядка, щоб лишитися в списку. Більше значення знаходить зарядки далі від дороги"),
        "vi" to listOf("Đường vòng đến trạm sạc", "Thông thường", "Trên tuyến đường, trạm sạc có thể lệch bao nhiêu phút mà vẫn được liệt kê. Số lớn hơn tìm thấy trạm sạc xa đường hơn"),
        "ml" to listOf("ചാർജറുകളിലേക്കുള്ള വളവ്", "സാധാരണ", "റൂട്ടിൽ, ചാർജർ റൂട്ടിൽ നിന്ന് എത്ര മിനിറ്റ് അകലെയായാലും ലിസ്റ്റിൽ കാണിക്കും. കൂടുതൽ മൂല്യം റോഡിൽ നിന്ന് അകലെയുള്ള ചാർജറുകൾ കണ്ടെത്തും"),
        "ta" to listOf("சார்ஜர்களுக்கு சுற்றுப்பாதை", "வழக்கமான", "வழியில், சார்ஜர் வழியிலிருந்து எத்தனை நிமிடம் தொலைவில் இருந்தாலும் பட்டியலில் காட்டப்படும். பெரிய மதிப்பு சாலையிலிருந்து தொலைவான சார்ஜர்களைக் கண்டறியும்"),
        "te" to listOf("ఛార్జర్లకు చుట్టుదారి", "సాధారణం", "మార్గంలో, ఛార్జర్ మార్గం నుండి ఎన్ని నిమిషాల దూరంలో ఉన్నా జాబితాలో కనిపిస్తుంది. ఎక్కువ విలువ రోడ్డుకు దూరంగా ఉన్న ఛార్జర్లను కనుగొంటుంది"),
        "ko" to listOf("충전소 우회", "기본", "경로에서 충전소가 몇 분까지 벗어나 있어도 목록에 표시할지 정합니다. 클수록 도로에서 더 먼 충전소도 찾습니다"),
        "ca" to listOf("Desviament cap als carregadors", "Habitual", "En una ruta, quants minuts fora d'ella pot estar un carregador per sortir a la llista. Més minuts troba carregadors més lluny de la carretera")
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
