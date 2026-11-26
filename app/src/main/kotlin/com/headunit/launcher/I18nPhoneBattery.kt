package com.headunit.launcher

/** Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Settings › Android Auto › Phone battery (#430). */
internal object I18nPhoneBattery {
    private val keys = listOf("Phone battery", "Phone battery on the car screen", "The phone's battery level beside the speedometer")

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Telefoonbatterij", "Telefoonbatterij op het autoscherm", "Het batterijniveau van de telefoon naast de snelheidsmeter"),
        "de" to listOf("Handy-Akku", "Handy-Akku auf dem Autobildschirm", "Der Akkustand des Handys neben dem Tacho"),
        "fr" to listOf("Batterie du téléphone", "Batterie du téléphone sur l'écran de la voiture", "Le niveau de batterie du téléphone à côté du compteur"),
        "es" to listOf("Batería del teléfono", "Batería del teléfono en la pantalla del coche", "El nivel de batería del teléfono junto al velocímetro"),
        "esar" to listOf("Batería del teléfono", "Batería del teléfono en la pantalla del auto", "El nivel de batería del teléfono al lado del velocímetro"),
        "esmx" to listOf("Batería del teléfono", "Batería del teléfono en la pantalla del auto", "El nivel de batería del teléfono junto al velocímetro"),
        "it" to listOf("Batteria del telefono", "Batteria del telefono sullo schermo dell'auto", "Il livello della batteria del telefono accanto al tachimetro"),
        "pt" to listOf("Bateria do telemóvel", "Bateria do telemóvel no ecrã do carro", "O nível da bateria do telemóvel ao lado do velocímetro"),
        "ptbr" to listOf("Bateria do celular", "Bateria do celular na tela do carro", "O nível da bateria do celular ao lado do velocímetro"),
        "pl" to listOf("Bateria telefonu", "Bateria telefonu na ekranie auta", "Poziom baterii telefonu obok prędkościomierza"),
        "th" to listOf("แบตเตอรี่โทรศัพท์", "แบตเตอรี่โทรศัพท์บนหน้าจอรถ", "ระดับแบตเตอรี่โทรศัพท์ข้างมาตรวัดความเร็ว"),
        "ja" to listOf("スマホのバッテリー", "車の画面にスマホのバッテリー", "速度計の横にスマホのバッテリー残量"),
        "id" to listOf("Baterai ponsel", "Baterai ponsel di layar mobil", "Level baterai ponsel di samping speedometer"),
        "bg" to listOf("Батерия на телефона", "Батерия на телефона на екрана в колата", "Нивото на батерията на телефона до скоростомера"),
        "sl" to listOf("Baterija telefona", "Baterija telefona na zaslonu avta", "Raven baterije telefona ob merilniku hitrosti"),
        "ro" to listOf("Bateria telefonului", "Bateria telefonului pe ecranul mașinii", "Nivelul bateriei telefonului lângă vitezometru"),
        "eu" to listOf("Telefonoaren bateria", "Telefonoaren bateria autoaren pantailan", "Telefonoaren bateria-maila abiadura-neurgailuaren ondoan"),
        "zh" to listOf("手機電量", "在車輛螢幕顯示手機電量", "速度表旁的手機電量"),
        "zhhk" to listOf("手機電量", "在車輛螢幕顯示手機電量", "速度錶旁的手機電量"),
        "zhs" to listOf("手机电量", "在车辆屏幕显示手机电量", "速度表旁的手机电量"),
        "el" to listOf("Μπαταρία κινητού", "Μπαταρία κινητού στην οθόνη του αυτοκινήτου", "Η στάθμη μπαταρίας του κινητού δίπλα στο ταχύμετρο"),
        "cs" to listOf("Baterie telefonu", "Baterie telefonu na obrazovce auta", "Stav baterie telefonu vedle tachometru"),
        "fi" to listOf("Puhelimen akku", "Puhelimen akku auton näytöllä", "Puhelimen akun varaus nopeusmittarin vieressä"),
        "sk" to listOf("Batéria telefónu", "Batéria telefónu na obrazovke auta", "Stav batérie telefónu vedľa tachometra"),
        "da" to listOf("Telefonens batteri", "Telefonens batteri på bilens skærm", "Telefonens batteriniveau ved speedometeret"),
        "hr" to listOf("Baterija mobitela", "Baterija mobitela na zaslonu automobila", "Razina baterije mobitela uz brzinomjer"),
        "bs" to listOf("Baterija telefona", "Baterija telefona na ekranu automobila", "Nivo baterije telefona pored brzinomjera"),
        "sr" to listOf("Батерија телефона", "Батерија телефона на екрану аутомобила", "Ниво батерије телефона поред брзиномера"),
        "ms" to listOf("Bateri telefon", "Bateri telefon pada skrin kereta", "Paras bateri telefon di sebelah meter laju"),
        "hu" to listOf("Telefon akkumulátora", "Telefon akkumulátora az autó kijelzőjén", "A telefon töltöttsége a sebességmérő mellett"),
        "tr" to listOf("Telefon pili", "Araç ekranında telefon pili", "Hız göstergesinin yanında telefonun pil seviyesi"),
        "ar" to listOf("بطارية الهاتف", "بطارية الهاتف على شاشة السيارة", "مستوى بطارية الهاتف بجانب عداد السرعة"),
        "he" to listOf("סוללת הטלפון", "סוללת הטלפון במסך הרכב", "רמת הסוללה של הטלפון ליד מד המהירות"),
        "ur" to listOf("فون کی بیٹری", "گاڑی کی اسکرین پر فون کی بیٹری", "اسپیڈومیٹر کے ساتھ فون کی بیٹری کی سطح"),
        "sv" to listOf("Telefonens batteri", "Telefonens batteri på bilens skärm", "Telefonens batterinivå bredvid hastighetsmätaren"),
        "kn" to listOf("ಫೋನ್ ಬ್ಯಾಟರಿ", "ಕಾರಿನ ಪರದೆಯಲ್ಲಿ ಫೋನ್ ಬ್ಯಾಟರಿ", "ಸ್ಪೀಡೋಮೀಟರ್ ಪಕ್ಕದಲ್ಲಿ ಫೋನ್‌ನ ಬ್ಯಾಟರಿ ಮಟ್ಟ"),
        "mr" to listOf("फोनची बॅटरी", "गाडीच्या स्क्रीनवर फोनची बॅटरी", "स्पीडोमीटरजवळ फोनची बॅटरी पातळी"),
        "nb" to listOf("Telefonens batteri", "Telefonens batteri på bilskjermen", "Telefonens batterinivå ved speedometeret"),
        "ru" to listOf("Батарея телефона", "Батарея телефона на экране машины", "Уровень заряда телефона рядом со спидометром"),
        "uk" to listOf("Батарея телефона", "Батарея телефона на екрані авто", "Рівень заряду телефона поруч зі спідометром"),
        "vi" to listOf("Pin điện thoại", "Pin điện thoại trên màn hình xe", "Mức pin điện thoại cạnh đồng hồ tốc độ"),
        "ml" to listOf("ഫോൺ ബാറ്ററി", "കാറിന്റെ സ്ക്രീനിൽ ഫോൺ ബാറ്ററി", "സ്പീഡോമീറ്ററിനടുത്ത് ഫോണിന്റെ ബാറ്ററി നില"),
        "ta" to listOf("தொலைபேசி பேட்டரி", "காரின் திரையில் தொலைபேசி பேட்டரி", "வேகமானிக்கு அருகில் தொலைபேசியின் பேட்டரி நிலை"),
        "te" to listOf("ఫోన్ బ్యాటరీ", "కారు స్క్రీన్‌పై ఫోన్ బ్యాటరీ", "స్పీడోమీటర్ పక్కన ఫోన్ బ్యాటరీ స్థాయి"),
        "ko" to listOf("휴대전화 배터리", "차량 화면에 휴대전화 배터리", "속도계 옆에 휴대전화 배터리 잔량"),
        "ca" to listOf("Bateria del telèfon", "Bateria del telèfon a la pantalla del cotxe", "El nivell de bateria del telèfon al costat del velocímetre"),
        "mk" to listOf("Батерија на телефонот", "Батеријата на телефонот на екранот во автомобилот", "Нивото на батеријата на телефонот до брзиномерот"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
