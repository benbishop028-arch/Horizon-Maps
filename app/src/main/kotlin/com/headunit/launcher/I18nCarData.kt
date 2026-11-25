package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the car's own speed, battery and position over
 * Android Auto. Order: group name, footer, row name, the search entry's explanation, "from car" next to the battery %.
 */
internal object I18nCarData {
    private val keys = listOf(
        "Car data",
        "Lets Android Auto pass on the car's speed, battery level and position. Without it the app uses GPS and the battery % you set.",
        "Speed, battery and position",
        "The car's own speed, battery level and position over Android Auto",
        "from car"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Autogegevens", "Laat Android Auto de snelheid, het accuniveau en de positie van de auto doorgeven. Zonder dit gebruikt de app gps en het ingestelde accupercentage.", "Snelheid, accu en positie", "De eigen snelheid, het accuniveau en de positie van de auto via Android Auto", "van auto"),
        "de" to listOf("Fahrzeugdaten", "Lässt Android Auto Geschwindigkeit, Akkustand und Position des Autos weitergeben. Ohne das nutzt die App GPS und den eingestellten Akku-%.", "Geschwindigkeit, Akku und Position", "Geschwindigkeit, Akkustand und Position direkt vom Auto über Android Auto", "vom Auto"),
        "fr" to listOf("Données du véhicule", "Permet à Android Auto de transmettre la vitesse, le niveau de batterie et la position de la voiture. Sinon, l'app utilise le GPS et le % de batterie réglé.", "Vitesse, batterie et position", "La vitesse, le niveau de batterie et la position de la voiture via Android Auto", "de la voiture"),
        "es" to listOf("Datos del coche", "Permite que Android Auto pase la velocidad, el nivel de batería y la posición del coche. Sin esto, la app usa el GPS y el % de batería que hayas puesto.", "Velocidad, batería y posición", "La velocidad, el nivel de batería y la posición del propio coche a través de Android Auto", "del coche"),
        "esar" to listOf("Datos del auto", "Permite que Android Auto pase la velocidad, el nivel de batería y la posición del auto. Sin esto, la app usa el GPS y el % de batería que hayas puesto.", "Velocidad, batería y posición", "La velocidad, el nivel de batería y la posición del propio auto a través de Android Auto", "del auto"),
        "esmx" to listOf("Datos del auto", "Permite que Android Auto pase la velocidad, el nivel de batería y la posición del auto. Sin esto, la app usa el GPS y el % de batería que pusiste.", "Velocidad, batería y posición", "La velocidad, el nivel de batería y la posición del propio auto a través de Android Auto", "del auto"),
        "it" to listOf("Dati dell'auto", "Consente ad Android Auto di passare velocità, livello della batteria e posizione dell'auto. Senza, l'app usa il GPS e la % di batteria impostata.", "Velocità, batteria e posizione", "Velocità, livello della batteria e posizione dell'auto tramite Android Auto", "dall'auto"),
        "pt" to listOf("Dados do carro", "Permite que o Android Auto passe a velocidade, o nível da bateria e a posição do carro. Sem isto, a app usa o GPS e a % de bateria definida.", "Velocidade, bateria e posição", "A velocidade, o nível da bateria e a posição do próprio carro através do Android Auto", "do carro"),
        "pl" to listOf("Dane samochodu", "Pozwala Android Auto przekazywać prędkość, poziom baterii i pozycję samochodu. Bez tego aplikacja używa GPS i ustawionego % baterii.", "Prędkość, bateria i pozycja", "Prędkość, poziom baterii i pozycja samochodu przez Android Auto", "z auta"),
        "th" to listOf("ข้อมูลรถ", "ให้ Android Auto ส่งความเร็ว ระดับแบตเตอรี่ และตำแหน่งของรถ หากไม่อนุญาต แอปจะใช้ GPS และ % แบตเตอรี่ที่คุณตั้งไว้", "ความเร็ว แบตเตอรี่ และตำแหน่ง", "ความเร็ว ระดับแบตเตอรี่ และตำแหน่งของรถผ่าน Android Auto", "จากรถ"),
        "ja" to listOf("車両データ", "Android Auto が車の速度、バッテリー残量、位置を渡せるようにします。許可しない場合は GPS と設定したバッテリー % を使います。", "速度、バッテリー、位置", "Android Auto 経由の車の速度、バッテリー残量、位置", "車から"),
        "id" to listOf("Data mobil", "Izinkan Android Auto meneruskan kecepatan, level baterai, dan posisi mobil. Tanpa ini, aplikasi memakai GPS dan % baterai yang Anda atur.", "Kecepatan, baterai, dan posisi", "Kecepatan, level baterai, dan posisi mobil melalui Android Auto", "dari mobil"),
        "bg" to listOf("Данни от колата", "Позволява на Android Auto да предава скоростта, нивото на батерията и позицията на колата. Без това приложението ползва GPS и зададения % батерия.", "Скорост, батерия и позиция", "Скоростта, нивото на батерията и позицията на колата през Android Auto", "от колата"),
        "sl" to listOf("Podatki avtomobila", "Android Auto lahko posreduje hitrost, raven baterije in položaj avtomobila. Brez tega aplikacija uporablja GPS in nastavljeni % baterije.", "Hitrost, baterija in položaj", "Hitrost, raven baterije in položaj avtomobila prek Android Auto", "iz avta"),
        "ro" to listOf("Datele mașinii", "Permite Android Auto să transmită viteza, nivelul bateriei și poziția mașinii. Fără asta, aplicația folosește GPS-ul și % bateriei setat.", "Viteză, baterie și poziție", "Viteza, nivelul bateriei și poziția mașinii prin Android Auto", "de la mașină"),
        "eu" to listOf("Autoaren datuak", "Android Autori autoaren abiadura, bateria-maila eta kokapena pasatzen uzten dio. Horrela ez bada, aplikazioak GPSa eta ezarritako bateria-% erabiltzen ditu.", "Abiadura, bateria eta kokapena", "Autoaren abiadura, bateria-maila eta kokapena Android Auto bidez", "autotik"),
        "zh" to listOf("車輛資料", "讓 Android Auto 傳送車輛的速度、電池電量和位置。若不允許，應用程式會使用 GPS 和你設定的電池 %。", "速度、電池和位置", "透過 Android Auto 取得車輛本身的速度、電池電量和位置", "來自車輛"),
        "zhhk" to listOf("車輛資料", "讓 Android Auto 傳送車輛的速度、電池電量和位置。若不允許，應用程式會使用 GPS 和你設定的電池 %。", "速度、電池和位置", "透過 Android Auto 取得車輛本身的速度、電池電量和位置", "來自車輛"),
        "zhs" to listOf("车辆数据", "让 Android Auto 传送车辆的速度、电池电量和位置。若不允许，应用会使用 GPS 和你设置的电池 %。", "速度、电池和位置", "通过 Android Auto 获取车辆本身的速度、电池电量和位置", "来自车辆"),
        "el" to listOf("Δεδομένα αυτοκινήτου", "Επιτρέπει στο Android Auto να μεταφέρει την ταχύτητα, τη στάθμη μπαταρίας και τη θέση του αυτοκινήτου. Χωρίς αυτό η εφαρμογή χρησιμοποιεί GPS και το % μπαταρίας που ορίσατε.", "Ταχύτητα, μπαταρία και θέση", "Η ταχύτητα, η στάθμη μπαταρίας και η θέση του αυτοκινήτου μέσω Android Auto", "από το αυτοκίνητο"),
        "cs" to listOf("Data z auta", "Umožní Android Auto předávat rychlost, stav baterie a polohu auta. Bez toho aplikace používá GPS a nastavené % baterie.", "Rychlost, baterie a poloha", "Rychlost, stav baterie a poloha auta přes Android Auto", "z auta"),
        "fi" to listOf("Auton tiedot", "Antaa Android Auton välittää auton nopeuden, akun varaustason ja sijainnin. Ilman sitä sovellus käyttää GPS:ää ja asettamaasi akun %:a.", "Nopeus, akku ja sijainti", "Auton oma nopeus, akun varaustaso ja sijainti Android Auton kautta", "autosta"),
        "sk" to listOf("Údaje z auta", "Umožní Android Auto odovzdávať rýchlosť, stav batérie a polohu auta. Bez toho aplikácia používa GPS a nastavené % batérie.", "Rýchlosť, batéria a poloha", "Rýchlosť, stav batérie a poloha auta cez Android Auto", "z auta"),
        "da" to listOf("Bildata", "Lader Android Auto videregive bilens hastighed, batteriniveau og position. Uden det bruger appen GPS og den batteri-% du har sat.", "Hastighed, batteri og position", "Bilens egen hastighed, batteriniveau og position via Android Auto", "fra bilen"),
        "hr" to listOf("Podaci automobila", "Omogućuje Android Autu da prenosi brzinu, razinu baterije i položaj automobila. Bez toga aplikacija koristi GPS i postavljeni % baterije.", "Brzina, baterija i položaj", "Brzina, razina baterije i položaj automobila putem Android Auta", "iz auta"),
        "bs" to listOf("Podaci automobila", "Omogućava Android Autu da prenosi brzinu, nivo baterije i položaj automobila. Bez toga aplikacija koristi GPS i postavljeni % baterije.", "Brzina, baterija i položaj", "Brzina, nivo baterije i položaj automobila putem Android Auta", "iz auta"),
        "sr" to listOf("Подаци аутомобила", "Омогућава Android Auto-у да преноси брзину, ниво батерије и положај аутомобила. Без тога апликација користи GPS и постављени % батерије.", "Брзина, батерија и положај", "Брзина, ниво батерије и положај аутомобила преко Android Auto-а", "из аута"),
        "ms" to listOf("Data kereta", "Benarkan Android Auto menghantar kelajuan, tahap bateri dan kedudukan kereta. Tanpanya, apl menggunakan GPS dan % bateri yang anda tetapkan.", "Kelajuan, bateri dan kedudukan", "Kelajuan, tahap bateri dan kedudukan kereta melalui Android Auto", "dari kereta"),
        "hu" to listOf("Autó adatai", "Engedi, hogy az Android Auto átadja az autó sebességét, akkuszintjét és helyzetét. Enélkül az app GPS-t és a beállított akku %-ot használja.", "Sebesség, akku és helyzet", "Az autó saját sebessége, akkuszintje és helyzete Android Autón keresztül", "az autóból"),
        "tr" to listOf("Araç verileri", "Android Auto'nun aracın hızını, batarya seviyesini ve konumunu aktarmasına izin verir. İzin yoksa uygulama GPS'i ve ayarladığınız batarya %'sini kullanır.", "Hız, batarya ve konum", "Aracın kendi hızı, batarya seviyesi ve konumu Android Auto üzerinden", "araçtan"),
        "ar" to listOf("بيانات السيارة", "يسمح لـ Android Auto بنقل سرعة السيارة ومستوى البطارية وموقعها. بدونه يستخدم التطبيق GPS ونسبة البطارية التي حددتها.", "السرعة والبطارية والموقع", "سرعة السيارة ومستوى بطاريتها وموقعها عبر Android Auto", "من السيارة"),
        "he" to listOf("נתוני הרכב", "מאפשר ל-Android Auto להעביר את המהירות, רמת הסוללה והמיקום של הרכב. בלי זה האפליקציה משתמשת ב-GPS ובאחוז הסוללה שהגדרת.", "מהירות, סוללה ומיקום", "המהירות, רמת הסוללה והמיקום של הרכב דרך Android Auto", "מהרכב"),
        "ur" to listOf("گاڑی کا ڈیٹا", "Android Auto کو گاڑی کی رفتار، بیٹری کی سطح اور مقام بھیجنے دیں۔ اس کے بغیر ایپ GPS اور آپ کی مقرر کردہ بیٹری % استعمال کرتی ہے۔", "رفتار، بیٹری اور مقام", "Android Auto کے ذریعے گاڑی کی اپنی رفتار، بیٹری کی سطح اور مقام", "گاڑی سے"),
        "sv" to listOf("Bildata", "Låter Android Auto skicka vidare bilens hastighet, batterinivå och position. Utan det använder appen GPS och den batteri-% du har ställt in.", "Hastighet, batteri och position", "Bilens egen hastighet, batterinivå och position via Android Auto", "från bilen"),
        "kn" to listOf("ಕಾರಿನ ಡೇಟಾ", "ಕಾರಿನ ವೇಗ, ಬ್ಯಾಟರಿ ಮಟ್ಟ ಮತ್ತು ಸ್ಥಾನವನ್ನು Android Auto ಕಳುಹಿಸಲು ಬಿಡುತ್ತದೆ. ಇಲ್ಲದಿದ್ದರೆ ಆ್ಯಪ್ GPS ಮತ್ತು ನೀವು ಹೊಂದಿಸಿದ ಬ್ಯಾಟರಿ % ಬಳಸುತ್ತದೆ.", "ವೇಗ, ಬ್ಯಾಟರಿ ಮತ್ತು ಸ್ಥಾನ", "Android Auto ಮೂಲಕ ಕಾರಿನ ಸ್ವಂತ ವೇಗ, ಬ್ಯಾಟರಿ ಮಟ್ಟ ಮತ್ತು ಸ್ಥಾನ", "ಕಾರಿನಿಂದ"),
        "mr" to listOf("गाडीचा डेटा", "Android Auto ला गाडीचा वेग, बॅटरी पातळी आणि स्थान पाठवू देते. याशिवाय ॲप GPS आणि तुम्ही सेट केलेली बॅटरी % वापरते.", "वेग, बॅटरी आणि स्थान", "Android Auto द्वारे गाडीचा स्वतःचा वेग, बॅटरी पातळी आणि स्थान", "गाडीकडून"),
        "nb" to listOf("Bildata", "Lar Android Auto sende videre bilens hastighet, batterinivå og posisjon. Uten dette bruker appen GPS og batteri-% du har satt.", "Hastighet, batteri og posisjon", "Bilens egen hastighet, batterinivå og posisjon via Android Auto", "fra bilen"),
        "ru" to listOf("Данные автомобиля", "Позволяет Android Auto передавать скорость, уровень заряда и положение автомобиля. Без этого приложение использует GPS и заданный % заряда.", "Скорость, заряд и положение", "Скорость, уровень заряда и положение самого автомобиля через Android Auto", "от авто"),
        "uk" to listOf("Дані автомобіля", "Дозволяє Android Auto передавати швидкість, рівень заряду та положення автомобіля. Без цього застосунок використовує GPS і заданий % заряду.", "Швидкість, заряд і положення", "Швидкість, рівень заряду та положення самого автомобіля через Android Auto", "від авто"),
        "vi" to listOf("Dữ liệu xe", "Cho phép Android Auto chuyển tốc độ, mức pin và vị trí của xe. Nếu không, ứng dụng dùng GPS và % pin bạn đã đặt.", "Tốc độ, pin và vị trí", "Tốc độ, mức pin và vị trí của chính xe qua Android Auto", "từ xe"),
        "ml" to listOf("കാറിന്റെ ഡാറ്റ", "കാറിന്റെ വേഗം, ബാറ്ററി നില, സ്ഥാനം എന്നിവ Android Auto വഴി കൈമാറാൻ അനുവദിക്കുന്നു. ഇല്ലെങ്കിൽ ആപ്പ് GPS-ഉം നിങ്ങൾ സജ്ജമാക്കിയ ബാറ്ററി %-ഉം ഉപയോഗിക്കും.", "വേഗം, ബാറ്ററി, സ്ഥാനം", "Android Auto വഴി കാറിന്റെ സ്വന്തം വേഗം, ബാറ്ററി നില, സ്ഥാനം", "കാറിൽ നിന്ന്"),
        "ta" to listOf("காரின் தரவு", "காரின் வேகம், பேட்டரி அளவு மற்றும் நிலையை Android Auto அனுப்ப அனுமதிக்கிறது. இல்லையெனில் ஆப் GPS மற்றும் நீங்கள் அமைத்த பேட்டரி %-ஐப் பயன்படுத்தும்.", "வேகம், பேட்டரி மற்றும் நிலை", "Android Auto வழியாக காரின் சொந்த வேகம், பேட்டரி அளவு மற்றும் நிலை", "காரிலிருந்து"),
        "te" to listOf("కారు డేటా", "కారు వేగం, బ్యాటరీ స్థాయి మరియు స్థానాన్ని Android Auto పంపడానికి అనుమతిస్తుంది. లేకపోతే యాప్ GPS మరియు మీరు సెట్ చేసిన బ్యాటరీ %ని వాడుతుంది.", "వేగం, బ్యాటరీ మరియు స్థానం", "Android Auto ద్వారా కారు స్వంత వేగం, బ్యాటరీ స్థాయి మరియు స్థానం", "కారు నుండి"),
        "ko" to listOf("차량 데이터", "Android Auto가 차량의 속도, 배터리 잔량, 위치를 전달하도록 허용합니다. 허용하지 않으면 앱은 GPS와 설정한 배터리 %를 사용합니다.", "속도, 배터리, 위치", "Android Auto를 통한 차량 자체의 속도, 배터리 잔량, 위치", "차량 제공"),
        "ca" to listOf("Dades del cotxe", "Permet que Android Auto passi la velocitat, el nivell de bateria i la posició del cotxe. Sense això, l'app fa servir el GPS i el % de bateria que hagis posat.", "Velocitat, bateria i posició", "La velocitat, el nivell de bateria i la posició del cotxe a través d'Android Auto", "del cotxe")
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
