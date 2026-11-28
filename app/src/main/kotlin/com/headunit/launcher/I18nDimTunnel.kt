package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Settings › Screen › Dim the screen in tunnels (#471).
 * Order: the switch, its explanation.
 */
internal object I18nDimTunnel {
    private val keys = listOf(
        "Dim the screen in tunnels",
        "While your route goes through a tunnel, day or night, so the screen doesn't dazzle"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Scherm dimmen in tunnels", "Terwijl je route door een tunnel gaat, dag of nacht, zodat het scherm niet verblindt"),
        "de" to listOf("Bildschirm im Tunnel dimmen", "Solange deine Route durch einen Tunnel führt, bei Tag und Nacht, damit der Bildschirm nicht blendet"),
        "fr" to listOf("Atténuer l'écran dans les tunnels", "Pendant que l'itinéraire passe dans un tunnel, de jour comme de nuit, pour que l'écran n'éblouisse pas"),
        "es" to listOf("Atenuar la pantalla en túneles", "Mientras la ruta pasa por un túnel, de día o de noche, para que la pantalla no deslumbre"),
        "esar" to listOf("Atenuar la pantalla en túneles", "Mientras la ruta pasa por un túnel, de día o de noche, para que la pantalla no encandile"),
        "esmx" to listOf("Atenuar la pantalla en túneles", "Mientras la ruta pasa por un túnel, de día o de noche, para que la pantalla no deslumbre"),
        "it" to listOf("Attenua lo schermo nelle gallerie", "Mentre il percorso attraversa una galleria, di giorno o di notte, perché lo schermo non abbagli"),
        "pt" to listOf("Reduzir o brilho nos túneis", "Enquanto o percurso passa por um túnel, de dia ou de noite, para o ecrã não encandear"),
        "ptbr" to listOf("Reduzir o brilho nos túneis", "Enquanto a rota passa por um túnel, de dia ou de noite, para a tela não ofuscar"),
        "pl" to listOf("Przyciemniaj ekran w tunelach", "Gdy trasa prowadzi przez tunel, w dzień i w nocy, żeby ekran nie oślepiał"),
        "th" to listOf("หรี่หน้าจอในอุโมงค์", "ขณะที่เส้นทางผ่านอุโมงค์ ทั้งกลางวันและกลางคืน เพื่อไม่ให้หน้าจอแยงตา"),
        "ja" to listOf("トンネルで画面を暗くする", "ルートがトンネルを通る間、昼夜を問わず画面を暗くしてまぶしさを防ぎます"),
        "id" to listOf("Redupkan layar di terowongan", "Saat rute melewati terowongan, siang atau malam, agar layar tidak menyilaukan"),
        "bg" to listOf("Затъмняване на екрана в тунели", "Докато маршрутът минава през тунел, денем и нощем, за да не заслепява екранът"),
        "sl" to listOf("Zatemni zaslon v predorih", "Medtem ko pot poteka skozi predor, podnevi in ponoči, da zaslon ne zaslepi"),
        "ro" to listOf("Estompează ecranul în tuneluri", "Cât timp traseul trece printr-un tunel, ziua sau noaptea, ca ecranul să nu orbească"),
        "eu" to listOf("Ilundu pantaila tunelean", "Ibilbideak tunel batetik pasatzen den bitartean, egunez zein gauez, pantailak liluratu ez dezan"),
        "zh" to listOf("在隧道內調暗螢幕", "路線經過隧道時，不論白天或夜晚，都調暗螢幕以免刺眼"),
        "zhhk" to listOf("在隧道內調暗螢幕", "路線經過隧道時，不論日夜，都調暗螢幕以免刺眼"),
        "zhs" to listOf("在隧道内调暗屏幕", "路线经过隧道时，无论白天还是夜晚，都调暗屏幕以免刺眼"),
        "el" to listOf("Μείωση φωτεινότητας σε σήραγγες", "Όσο η διαδρομή περνά από σήραγγα, μέρα ή νύχτα, για να μην θαμπώνει η οθόνη"),
        "cs" to listOf("Ztlumit obrazovku v tunelech", "Dokud trasa vede tunelem, ve dne i v noci, aby obrazovka neoslňovala"),
        "fi" to listOf("Himmennä näyttö tunneleissa", "Niin kauan kuin reitti kulkee tunnelin läpi, päivällä ja yöllä, jotta näyttö ei häikäise"),
        "sk" to listOf("Stlmiť obrazovku v tuneloch", "Kým trasa vedie tunelom, vo dne aj v noci, aby obrazovka neoslňovala"),
        "da" to listOf("Dæmp skærmen i tunneler", "Mens ruten går gennem en tunnel, dag eller nat, så skærmen ikke blænder"),
        "hr" to listOf("Zatamni zaslon u tunelima", "Dok ruta prolazi kroz tunel, danju i noću, da zaslon ne zasljepljuje"),
        "bs" to listOf("Zatamni ekran u tunelima", "Dok ruta prolazi kroz tunel, danju i noću, da ekran ne zasljepljuje"),
        "sr" to listOf("Затамни екран у тунелима", "Док рута пролази кроз тунел, дању и ноћу, да екран не заслепљује"),
        "ms" to listOf("Malapkan skrin dalam terowong", "Semasa laluan melalui terowong, siang atau malam, supaya skrin tidak menyilaukan"),
        "hu" to listOf("Képernyő tompítása alagútban", "Amíg az útvonal alagúton halad át, nappal és éjjel is, hogy a képernyő ne vakítson"),
        "tr" to listOf("Tünellerde ekranı karart", "Rota bir tünelden geçerken, gece gündüz, ekran gözü almasın diye"),
        "ar" to listOf("تعتيم الشاشة في الأنفاق", "أثناء مرور المسار عبر نفق، نهارًا أو ليلًا، حتى لا تبهر الشاشة"),
        "he" to listOf("עמעום המסך במנהרות", "כשהמסלול עובר במנהרה, ביום ובלילה, כדי שהמסך לא יסנוור"),
        "ur" to listOf("سرنگوں میں اسکرین مدھم کریں", "جب راستہ سرنگ سے گزرے، دن ہو یا رات، تاکہ اسکرین آنکھوں کو نہ چبھے"),
        "sv" to listOf("Dämpa skärmen i tunnlar", "Medan rutten går genom en tunnel, dag eller natt, så att skärmen inte bländar"),
        "kn" to listOf("ಸುರಂಗಗಳಲ್ಲಿ ಪರದೆ ಮಂದಗೊಳಿಸಿ", "ಮಾರ್ಗವು ಸುರಂಗದ ಮೂಲಕ ಹಾದುಹೋಗುವಾಗ, ಹಗಲು ಅಥವಾ ರಾತ್ರಿ, ಪರದೆ ಕಣ್ಣು ಕುಕ್ಕದಂತೆ"),
        "mr" to listOf("बोगद्यांमध्ये स्क्रीन मंद करा", "मार्ग बोगद्यातून जात असताना, दिवस असो वा रात्र, स्क्रीन डोळ्यांना त्रास देऊ नये म्हणून"),
        "nb" to listOf("Dim skjermen i tunneler", "Mens ruten går gjennom en tunnel, dag eller natt, så skjermen ikke blender"),
        "ru" to listOf("Приглушать экран в тоннелях", "Пока маршрут идёт через тоннель, днём и ночью, чтобы экран не слепил"),
        "uk" to listOf("Приглушувати екран у тунелях", "Поки маршрут іде через тунель, вдень і вночі, щоб екран не засліплював"),
        "vi" to listOf("Giảm độ sáng màn hình trong hầm", "Khi lộ trình đi qua hầm, cả ngày lẫn đêm, để màn hình không chói"),
        "ml" to listOf("തുരങ്കങ്ങളിൽ സ്ക്രീൻ മങ്ങിക്കുക", "റൂട്ട് തുരങ്കത്തിലൂടെ പോകുമ്പോൾ, പകലോ രാത്രിയോ, സ്ക്രീൻ കണ്ണ് മഞ്ഞളിപ്പിക്കാതിരിക്കാൻ"),
        "ta" to listOf("சுரங்கப்பாதைகளில் திரையை மங்கலாக்கு", "வழி சுரங்கப்பாதை வழியாகச் செல்லும்போது, பகலோ இரவோ, திரை கண்ணைக் கூசாமல் இருக்க"),
        "te" to listOf("సొరంగాల్లో స్క్రీన్ మసకబారు", "మార్గం సొరంగం గుండా వెళ్తున్నప్పుడు, పగలైనా రాత్రైనా, స్క్రీన్ కళ్ళు చెదరకుండా"),
        "ko" to listOf("터널에서 화면 어둡게", "경로가 터널을 지나는 동안 낮이든 밤이든 화면이 눈부시지 않게 어둡게 합니다"),
        "ca" to listOf("Atenua la pantalla als túnels", "Mentre la ruta passa per un túnel, de dia o de nit, perquè la pantalla no enlluerni"),
        "mk" to listOf("Затемни го екранот во тунели", "Додека рутата поминува низ тунел, дење и ноќе, за екранот да не заслепува"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
