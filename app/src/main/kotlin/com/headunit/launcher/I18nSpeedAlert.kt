package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the speed alert sound choice (#335).
 * Order: Speed alert sound, Double beep, Siren, Low buzz, the explanation.
 */
internal object I18nSpeedAlert {
    private val keys = listOf(
        "Speed alert sound", "Double beep", "Siren", "Low buzz",
        "The sound for going over the limit or the top speed, so it can't be mistaken for the camera and hazard beeps"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Geluid snelheidswaarschuwing", "Dubbele piep", "Sirene", "Laag gezoem", "Het geluid voor te hard rijden of de topsnelheid, zodat het niet te verwarren is met de piepjes voor camera's en gevaren"),
        "de" to listOf("Ton der Tempowarnung", "Doppelton", "Sirene", "Tiefes Brummen", "Der Ton für Überschreiten des Tempolimits oder der Höchstgeschwindigkeit, damit er nicht mit den Tönen für Blitzer und Gefahren verwechselt wird"),
        "fr" to listOf("Son de l'alerte de vitesse", "Double bip", "Sirène", "Bourdonnement grave", "Le son en cas de dépassement de la limite ou de la vitesse maximale, pour ne pas le confondre avec les bips des radars et des dangers"),
        "es" to listOf("Sonido del aviso de velocidad", "Doble pitido", "Sirena", "Zumbido grave", "El sonido al superar el límite o la velocidad máxima, para no confundirlo con los pitidos de radares y peligros"),
        "esar" to listOf("Sonido del aviso de velocidad", "Doble pitido", "Sirena", "Zumbido grave", "El sonido al superar el límite o la velocidad máxima, para no confundirlo con los pitidos de radares y peligros"),
        "esmx" to listOf("Sonido del aviso de velocidad", "Doble pitido", "Sirena", "Zumbido grave", "El sonido al superar el límite o la velocidad máxima, para no confundirlo con los pitidos de radares y peligros"),
        "it" to listOf("Suono dell'avviso di velocità", "Doppio bip", "Sirena", "Ronzio grave", "Il suono per il superamento del limite o della velocità massima, per non confonderlo con i bip di autovelox e pericoli"),
        "pt" to listOf("Som do aviso de velocidade", "Bip duplo", "Sirene", "Zumbido grave", "O som ao exceder o limite ou a velocidade máxima, para não ser confundido com os bips de radares e perigos"),
        "pl" to listOf("Dźwięk ostrzeżenia o prędkości", "Podwójny sygnał", "Syrena", "Niski brzęczyk", "Dźwięk przekroczenia limitu lub prędkości maksymalnej, aby nie pomylić go z sygnałami fotoradarów i zagrożeń"),
        "th" to listOf("เสียงเตือนความเร็ว", "บี๊ปสองครั้ง", "ไซเรน", "เสียงหึ่งต่ำ", "เสียงเมื่อเกินความเร็วจำกัดหรือความเร็วสูงสุด เพื่อไม่ให้สับสนกับเสียงเตือนกล้องและอันตราย"),
        "ja" to listOf("速度警告の音", "ダブルビープ", "サイレン", "低いブザー", "制限速度や最高速度を超えたときの音です。カメラや危険の警告音と間違えないようにできます"),
        "id" to listOf("Suara peringatan kecepatan", "Bip ganda", "Sirene", "Dengung rendah", "Suara saat melebihi batas atau kecepatan tertinggi, agar tidak tertukar dengan bip kamera dan bahaya"),
        "bg" to listOf("Звук за предупреждение за скорост", "Двоен сигнал", "Сирена", "Ниско бръмчене", "Звукът при превишаване на ограничението или най-високата скорост, за да не се бърка със сигналите за камери и опасности"),
        "sl" to listOf("Zvok opozorila o hitrosti", "Dvojni pisk", "Sirena", "Nizko brnenje", "Zvok ob preseganju omejitve ali najvišje hitrosti, da ga ne zamenjate s piski za radarje in nevarnosti"),
        "ro" to listOf("Sunetul alertei de viteză", "Bip dublu", "Sirenă", "Bâzâit grav", "Sunetul pentru depășirea limitei sau a vitezei maxime, ca să nu fie confundat cu bipurile de la radare și pericole"),
        "eu" to listOf("Abiadura-abisuaren soinua", "Bip bikoitza", "Sirena", "Burrunba grabea", "Muga edo abiadura maximoa gainditzean entzuten den soinua, kamera eta arrisku bipekin nahas ez dadin"),
        "zh" to listOf("超速警示音", "雙響嗶聲", "警報聲", "低沉嗡聲", "超過速限或最高速度時的提示音，避免與測速照相和危險提示音混淆"),
        "zhhk" to listOf("超速警示音", "雙響嗶聲", "警報聲", "低沉嗡聲", "超過速限或最高速度時的提示音，避免與測速相機和危險提示音混淆"),
        "zhs" to listOf("超速警示音", "双响哔声", "警报声", "低沉嗡声", "超过限速或最高速度时的提示音，避免与测速摄像头和危险提示音混淆"),
        "el" to listOf("Ήχος ειδοποίησης ταχύτητας", "Διπλό μπιπ", "Σειρήνα", "Χαμηλό βουητό", "Ο ήχος για υπέρβαση του ορίου ή της ανώτατης ταχύτητας, ώστε να μη συγχέεται με τα μπιπ των καμερών και των κινδύνων"),
        "cs" to listOf("Zvuk upozornění na rychlost", "Dvojité pípnutí", "Siréna", "Hluboké bzučení", "Zvuk při překročení limitu nebo nejvyšší rychlosti, aby se nepletl s pípáním u radarů a nebezpečí"),
        "fi" to listOf("Nopeusvaroituksen ääni", "Kaksoispiippaus", "Sireeni", "Matala surina", "Ääni rajoituksen tai huippunopeuden ylittämisestä, jotta sitä ei sekoita kameroiden ja vaarojen piippauksiin"),
        "sk" to listOf("Zvuk upozornenia na rýchlosť", "Dvojité pípnutie", "Siréna", "Hlboké bzučanie", "Zvuk pri prekročení limitu alebo najvyššej rýchlosti, aby sa nepomýlil s pípaním pri radaroch a nebezpečenstvách"),
        "da" to listOf("Lyd til fartadvarsel", "Dobbelt bip", "Sirene", "Lav summen", "Lyden ved overskridelse af hastighedsgrænsen eller topfarten, så den ikke forveksles med bip for fotokasser og farer"),
        "hr" to listOf("Zvuk upozorenja o brzini", "Dvostruki zvuk", "Sirena", "Duboko zujanje", "Zvuk za prekoračenje ograničenja ili najveće brzine, da se ne zamijeni sa zvukovima za kamere i opasnosti"),
        "bs" to listOf("Zvuk upozorenja o brzini", "Dvostruki zvuk", "Sirena", "Duboko zujanje", "Zvuk za prekoračenje ograničenja ili najveće brzine, da se ne zamijeni sa zvukovima za kamere i opasnosti"),
        "sr" to listOf("Звук упозорења о брзини", "Двоструки звук", "Сирена", "Дубоко зујање", "Звук за прекорачење ограничења или највеће брзине, да се не помеша са звуцима за камере и опасности"),
        "ms" to listOf("Bunyi amaran kelajuan", "Bip berganda", "Siren", "Dengung rendah", "Bunyi apabila melebihi had atau kelajuan tertinggi, supaya tidak keliru dengan bip kamera dan bahaya"),
        "hu" to listOf("Sebességfigyelmeztetés hangja", "Dupla sípszó", "Sziréna", "Mély búgás", "A sebességhatár vagy a végsebesség túllépésekor megszólaló hang, hogy ne lehessen összetéveszteni a traffipax és veszélyjelzések hangjával"),
        "tr" to listOf("Hız uyarısı sesi", "Çift bip", "Siren", "Alçak uğultu", "Limiti veya azami hızı aşınca çalan ses; kamera ve tehlike bipleriyle karışmaz"),
        "ar" to listOf("صوت تنبيه السرعة", "صفير مزدوج", "صفارة إنذار", "طنين منخفض", "الصوت عند تجاوز الحد أو السرعة القصوى، حتى لا يُخلط بينه وبين صفير الكاميرات والمخاطر"),
        "he" to listOf("צליל התראת מהירות", "צפצוף כפול", "סירנה", "זמזום נמוך", "הצליל בחריגה מהמהירות המותרת או ממהירות העל, כדי שלא יתבלבל עם צפצופי מצלמות וסכנות"),
        "ur" to listOf("رفتار کے انتباہ کی آواز", "دوہری بیپ", "سائرن", "دھیمی بھنبھناہٹ", "حد یا زیادہ سے زیادہ رفتار سے تجاوز کرنے کی آواز، تاکہ کیمروں اور خطرات کی بیپ سے نہ ملے"),
        "sv" to listOf("Ljud för fartvarning", "Dubbelt pip", "Siren", "Lågt surr", "Ljudet när du kör över gränsen eller topphastigheten, så att det inte förväxlas med pip för kameror och faror"),
        "kn" to listOf("ವೇಗ ಎಚ್ಚರಿಕೆಯ ಶಬ್ದ", "ಎರಡು ಬೀಪ್", "ಸೈರನ್", "ಕಡಿಮೆ ಝೇಂಕಾರ", "ಮಿತಿ ಅಥವಾ ಗರಿಷ್ಠ ವೇಗ ಮೀರಿದಾಗ ಕೇಳುವ ಶಬ್ದ; ಕ್ಯಾಮೆರಾ ಮತ್ತು ಅಪಾಯದ ಬೀಪ್‌ಗಳೊಂದಿಗೆ ಗೊಂದಲವಾಗದಂತೆ"),
        "mr" to listOf("वेग इशाऱ्याचा आवाज", "दुहेरी बीप", "सायरन", "मंद गुणगुण", "मर्यादा किंवा कमाल वेग ओलांडल्यावरचा आवाज, जेणेकरून तो कॅमेरा आणि धोक्याच्या बीपशी गोंधळणार नाही"),
        "nb" to listOf("Lyd for fartsvarsel", "Dobbelt pip", "Sirene", "Lav summing", "Lyden når du overskrider fartsgrensen eller topphastigheten, slik at den ikke forveksles med pip for kameraer og farer"),
        "ru" to listOf("Звук предупреждения о скорости", "Двойной сигнал", "Сирена", "Низкий гудок", "Звук при превышении ограничения или максимальной скорости, чтобы его не путать с сигналами камер и опасностей"),
        "uk" to listOf("Звук попередження про швидкість", "Подвійний сигнал", "Сирена", "Низьке гудіння", "Звук при перевищенні обмеження або максимальної швидкості, щоб його не плутати із сигналами камер і небезпек"),
        "vi" to listOf("Âm cảnh báo tốc độ", "Bíp đôi", "Còi hú", "Tiếng ù trầm", "Âm thanh khi vượt giới hạn hoặc tốc độ tối đa, để không nhầm với tiếng bíp của camera và nguy hiểm"),
        "ml" to listOf("വേഗ മുന്നറിയിപ്പിന്റെ ശബ്ദം", "ഇരട്ട ബീപ്", "സൈറൺ", "താഴ്ന്ന മൂളൽ", "പരിധി അല്ലെങ്കിൽ പരമാവധി വേഗം കടക്കുമ്പോഴുള്ള ശബ്ദം; ക്യാമറ, അപകട ബീപ്പുകളുമായി മാറിപ്പോകാതിരിക്കാൻ"),
        "ta" to listOf("வேக எச்சரிக்கை ஒலி", "இரட்டை பீப்", "சைரன்", "தாழ்ந்த ரீங்காரம்", "வரம்பு அல்லது அதிகபட்ச வேகத்தை மீறும்போது ஒலி; கேமரா, ஆபத்து பீப்களுடன் குழம்பாமல் இருக்க"),
        "te" to listOf("వేగ హెచ్చరిక శబ్దం", "డబుల్ బీప్", "సైరన్", "తక్కువ రొద", "పరిమితి లేదా గరిష్ఠ వేగం దాటినప్పుడు శబ్దం; కెమెరా, ప్రమాద బీప్‌లతో కలవకుండా"),
        "ko" to listOf("과속 경고음", "두 번 삐 소리", "사이렌", "낮은 부저음", "제한 속도나 최고 속도를 넘을 때의 소리로, 카메라와 위험 알림음과 헷갈리지 않게 합니다"),
        "ca" to listOf("So de l'avís de velocitat", "Doble pitada", "Sirena", "Brunzit greu", "El so en superar el límit o la velocitat màxima, per no confondre'l amb els pits de radars i perills"),
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
