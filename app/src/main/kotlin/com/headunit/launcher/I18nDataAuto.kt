package com.headunit.launcher

/** Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Offline maps › Update automatically (#428). */
internal object I18nDataAuto {
    private val keys = listOf("Update automatically", "Newer map data, speed limits, cameras and town names download by themselves when you're not driving. Off asks first")

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Automatisch bijwerken", "Nieuwere kaartgegevens, snelheidslimieten, flitspalen en plaatsnamen worden vanzelf gedownload als je niet rijdt. Uit vraagt het eerst"),
        "de" to listOf("Automatisch aktualisieren", "Neuere Kartendaten, Tempolimits, Blitzer und Ortsnamen werden von selbst geladen, wenn du nicht fährst. Aus fragt vorher"),
        "fr" to listOf("Mettre à jour automatiquement", "Les nouvelles données de carte, limitations, radars et noms de lieux se téléchargent seuls quand vous ne roulez pas. Désactivé demande d'abord"),
        "es" to listOf("Actualizar automáticamente", "Los datos de mapa más nuevos, límites, radares y nombres de poblaciones se descargan solos cuando no conduces. Desactivado pregunta antes"),
        "esar" to listOf("Actualizar automáticamente", "Los datos de mapa más nuevos, límites, radares y nombres de localidades se descargan solos cuando no manejás. Desactivado pregunta antes"),
        "esmx" to listOf("Actualizar automáticamente", "Los datos de mapa más nuevos, límites, radares y nombres de poblaciones se descargan solos cuando no manejas. Desactivado pregunta antes"),
        "it" to listOf("Aggiorna automaticamente", "I dati mappa più recenti, i limiti, gli autovelox e i nomi dei paesi si scaricano da soli quando non guidi. Disattivato chiede prima"),
        "pt" to listOf("Atualizar automaticamente", "Os dados de mapa mais recentes, limites, radares e nomes de localidades transferem-se sozinhos quando não está a conduzir. Desligado pergunta primeiro"),
        "ptbr" to listOf("Atualizar automaticamente", "Os dados de mapa mais novos, limites, radares e nomes de cidades são baixados sozinhos quando você não está dirigindo. Desligado pergunta antes"),
        "pl" to listOf("Aktualizuj automatycznie", "Nowsze dane mapy, ograniczenia, fotoradary i nazwy miejscowości pobierają się same, gdy nie jedziesz. Wyłączone najpierw pyta"),
        "th" to listOf("อัปเดตอัตโนมัติ", "ข้อมูลแผนที่ใหม่ ขีดจำกัดความเร็ว กล้อง และชื่อเมืองจะดาวน์โหลดเองเมื่อคุณไม่ได้ขับรถ ปิดจะถามก่อน"),
        "ja" to listOf("自動更新", "新しい地図データ、制限速度、取締機、市町村名を運転していないときに自動でダウンロードします。オフにすると先に確認します"),
        "id" to listOf("Perbarui otomatis", "Data peta, batas kecepatan, kamera, dan nama kota yang lebih baru terunduh sendiri saat Anda tidak mengemudi. Mati akan bertanya dulu"),
        "bg" to listOf("Автоматично обновяване", "По-новите картни данни, ограничения, камери и имена на селища се изтеглят сами, когато не шофирате. Изключено пита първо"),
        "sl" to listOf("Samodejno posodabljanje", "Novejši podatki zemljevida, omejitve, kamere in imena krajev se prenesejo sami, ko ne vozite. Izklopljeno najprej vpraša"),
        "ro" to listOf("Actualizare automată", "Datele de hartă mai noi, limitele, camerele și numele localităților se descarcă singure când nu conduceți. Oprit întreabă mai întâi"),
        "eu" to listOf("Eguneratu automatikoki", "Mapa-datu berriagoak, abiadura-mugak, kamerak eta herri-izenak berez deskargatzen dira gidatzen ari ez zarenean. Desaktibatuta lehenik galdetzen du"),
        "zh" to listOf("自動更新", "不開車時，較新的地圖資料、速限、測速照相和鄉鎮名稱會自動下載。關閉時會先詢問"),
        "zhhk" to listOf("自動更新", "唔揸車時，較新的地圖資料、車速限制、影快相和地名會自動下載。關閉時會先詢問"),
        "zhs" to listOf("自动更新", "不开车时，较新的地图数据、限速、测速摄像头和乡镇名称会自动下载。关闭时会先询问"),
        "el" to listOf("Αυτόματη ενημέρωση", "Νεότερα δεδομένα χάρτη, όρια, κάμερες και ονόματα οικισμών κατεβαίνουν μόνα τους όταν δεν οδηγείτε. Το Απενεργοποιημένο ρωτά πρώτα"),
        "cs" to listOf("Aktualizovat automaticky", "Novější data mapy, rychlostní limity, radary a názvy obcí se stáhnou samy, když nejedete. Vypnuto se nejdřív zeptá"),
        "fi" to listOf("Päivitä automaattisesti", "Uudemmat kartta-aineistot, nopeusrajoitukset, kamerat ja paikannimet latautuvat itsestään, kun et aja. Pois kysyy ensin"),
        "sk" to listOf("Aktualizovať automaticky", "Novšie dáta mapy, rýchlostné limity, radary a názvy obcí sa stiahnu samy, keď nejazdíte. Vypnuté sa najprv opýta"),
        "da" to listOf("Opdater automatisk", "Nyere kortdata, fartgrænser, fartkameraer og bynavne hentes af sig selv, når du ikke kører. Fra spørger først"),
        "hr" to listOf("Automatsko ažuriranje", "Noviji podaci karte, ograničenja, kamere i nazivi mjesta preuzimaju se sami kad ne vozite. Isključeno najprije pita"),
        "bs" to listOf("Automatsko ažuriranje", "Noviji podaci karte, ograničenja, kamere i nazivi mjesta preuzimaju se sami kad ne vozite. Isključeno prvo pita"),
        "sr" to listOf("Аутоматско ажурирање", "Новији подаци мапе, ограничења, камере и називи места преузимају се сами када не возите. Искључено прво пита"),
        "ms" to listOf("Kemas kini automatik", "Data peta, had laju, kamera dan nama bandar yang lebih baharu dimuat turun sendiri apabila anda tidak memandu. Mati akan bertanya dahulu"),
        "hu" to listOf("Automatikus frissítés", "Az újabb térképadatok, sebességkorlátok, traffipaxok és településnevek maguktól letöltődnek, amikor nem vezetsz. Kikapcsolva előbb rákérdez"),
        "tr" to listOf("Otomatik güncelle", "Daha yeni harita verileri, hız limitleri, kameralar ve yerleşim adları siz araç kullanmıyorken kendiliğinden iner. Kapalı önce sorar"),
        "ar" to listOf("التحديث تلقائيًا", "تُنزَّل بيانات الخريطة الأحدث وحدود السرعة والكاميرات وأسماء البلدات تلقائيًا عندما لا تقود. الإيقاف يسأل أولًا"),
        "he" to listOf("עדכון אוטומטי", "נתוני מפה חדשים, מגבלות מהירות, מצלמות ושמות יישובים יורדים מעצמם כשאינכם נוהגים. כבוי שואל קודם"),
        "ur" to listOf("خودکار اپ ڈیٹ", "نیا نقشہ ڈیٹا، رفتار کی حدیں، کیمرے اور قصبوں کے نام آپ کے گاڑی نہ چلاتے وقت خود ڈاؤن لوڈ ہوتے ہیں۔ بند پہلے پوچھتا ہے"),
        "sv" to listOf("Uppdatera automatiskt", "Nyare kartdata, hastighetsgränser, fartkameror och ortnamn laddas ner av sig själva när du inte kör. Av frågar först"),
        "kn" to listOf("ಸ್ವಯಂಚಾಲಿತವಾಗಿ ನವೀಕರಿಸಿ", "ನೀವು ವಾಹನ ಚಲಾಯಿಸದಿದ್ದಾಗ ಹೊಸ ನಕ್ಷೆ ಡೇಟಾ, ವೇಗ ಮಿತಿಗಳು, ಕ್ಯಾಮೆರಾಗಳು ಮತ್ತು ಊರಿನ ಹೆಸರುಗಳು ತಾವಾಗಿಯೇ ಡೌನ್‌ಲೋಡ್ ಆಗುತ್ತವೆ. ಆಫ್ ಮೊದಲು ಕೇಳುತ್ತದೆ"),
        "mr" to listOf("आपोआप अपडेट करा", "तुम्ही गाडी चालवत नसताना नवीन नकाशा डेटा, वेग मर्यादा, कॅमेरे आणि गावांची नावे आपोआप डाउनलोड होतात. बंद आधी विचारते"),
        "nb" to listOf("Oppdater automatisk", "Nyere kartdata, fartsgrenser, fotobokser og stedsnavn lastes ned av seg selv når du ikke kjører. Av spør først"),
        "ru" to listOf("Обновлять автоматически", "Новые данные карты, ограничения, камеры и названия населённых пунктов загружаются сами, когда вы не за рулём. «Выкл.» сначала спрашивает"),
        "uk" to listOf("Оновлювати автоматично", "Новіші дані мапи, обмеження, камери та назви населених пунктів завантажуються самі, коли ви не за кермом. «Вимк.» спершу питає"),
        "vi" to listOf("Tự động cập nhật", "Dữ liệu bản đồ, giới hạn tốc độ, camera và tên thị trấn mới hơn tự tải xuống khi bạn không lái xe. Tắt sẽ hỏi trước"),
        "ml" to listOf("സ്വയം അപ്‌ഡേറ്റ് ചെയ്യുക", "നിങ്ങൾ വാഹനം ഓടിക്കാത്തപ്പോൾ പുതിയ മാപ്പ് ഡാറ്റ, വേഗപരിധികൾ, ക്യാമറകൾ, പട്ടണപ്പേരുകൾ സ്വയം ഡൗൺലോഡ് ആകും. ഓഫ് ആദ്യം ചോദിക്കും"),
        "ta" to listOf("தானாகப் புதுப்பி", "நீங்கள் வாகனம் ஓட்டாதபோது புதிய வரைபடத் தரவு, வேக வரம்புகள், கேமராக்கள் மற்றும் ஊர்ப் பெயர்கள் தானாகப் பதிவிறங்கும். அணைத்தால் முதலில் கேட்கும்"),
        "te" to listOf("స్వయంచాలకంగా నవీకరించు", "మీరు డ్రైవ్ చేయనప్పుడు కొత్త మ్యాప్ డేటా, వేగ పరిమితులు, కెమెరాలు మరియు ఊర్ల పేర్లు వాటంతట అవే డౌన్‌లోడ్ అవుతాయి. ఆఫ్ ముందుగా అడుగుతుంది"),
        "ko" to listOf("자동 업데이트", "운전 중이 아닐 때 새 지도 데이터, 제한 속도, 단속 카메라, 지명이 자동으로 다운로드됩니다. 끄면 먼저 묻습니다"),
        "ca" to listOf("Actualitza automàticament", "Les dades de mapa més noves, límits, radars i noms de poblacions es baixen sols quan no condueixes. Desactivat pregunta abans"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
