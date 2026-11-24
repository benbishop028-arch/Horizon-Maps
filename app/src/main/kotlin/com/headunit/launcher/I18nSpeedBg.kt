package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Speed alert in the background (#335).
 * Order: the setting's name, its explanation.
 */
internal object I18nSpeedBg {
    private val keys = listOf(
        "Speed alert in the background",
        "Keeps the speed alert going while another app is in front, even with no route. Uses more battery and shows a notification"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Snelheidswaarschuwing op de achtergrond", "Houdt de snelheidswaarschuwing actief terwijl een andere app voorop staat, ook zonder route. Gebruikt meer batterij en toont een melding"),
        "de" to listOf("Tempowarnung im Hintergrund", "Hält die Tempowarnung aktiv, während eine andere App vorne ist, auch ohne Route. Verbraucht mehr Akku und zeigt eine Benachrichtigung"),
        "fr" to listOf("Alerte de vitesse en arrière-plan", "Garde l'alerte de vitesse active quand une autre appli est au premier plan, même sans itinéraire. Consomme plus de batterie et affiche une notification"),
        "es" to listOf("Aviso de velocidad en segundo plano", "Mantiene el aviso de velocidad mientras otra app está delante, incluso sin ruta. Gasta más batería y muestra una notificación"),
        "esar" to listOf("Aviso de velocidad en segundo plano", "Mantiene el aviso de velocidad mientras otra app está delante, incluso sin ruta. Gasta más batería y muestra una notificación"),
        "esmx" to listOf("Aviso de velocidad en segundo plano", "Mantiene el aviso de velocidad mientras otra app está delante, incluso sin ruta. Gasta más batería y muestra una notificación"),
        "it" to listOf("Avviso di velocità in background", "Mantiene attivo l'avviso di velocità mentre un'altra app è in primo piano, anche senza percorso. Consuma più batteria e mostra una notifica"),
        "pt" to listOf("Aviso de velocidade em segundo plano", "Mantém o aviso de velocidade ativo enquanto outra app está à frente, mesmo sem rota. Gasta mais bateria e mostra uma notificação"),
        "pl" to listOf("Ostrzeżenie o prędkości w tle", "Utrzymuje ostrzeżenie o prędkości, gdy na wierzchu jest inna aplikacja, nawet bez trasy. Zużywa więcej baterii i pokazuje powiadomienie"),
        "th" to listOf("เตือนความเร็วในพื้นหลัง", "เปิดการเตือนความเร็วต่อไปขณะที่แอปอื่นอยู่ด้านหน้า แม้ไม่มีเส้นทาง ใช้แบตเตอรี่มากขึ้นและแสดงการแจ้งเตือน"),
        "ja" to listOf("バックグラウンドでの速度警告", "他のアプリを使用中でも、ルートがなくても速度警告を続けます。バッテリーの消費が増え、通知が表示されます"),
        "id" to listOf("Peringatan kecepatan di latar belakang", "Menjaga peringatan kecepatan tetap aktif saat aplikasi lain di depan, bahkan tanpa rute. Memakai lebih banyak baterai dan menampilkan notifikasi"),
        "bg" to listOf("Предупреждение за скорост на заден план", "Поддържа предупреждението за скорост, докато друго приложение е отпред, дори без маршрут. Изразходва повече батерия и показва известие"),
        "sl" to listOf("Opozorilo o hitrosti v ozadju", "Opozorilo o hitrosti deluje, tudi ko je v ospredju druga aplikacija, in brez poti. Porabi več baterije in prikaže obvestilo"),
        "ro" to listOf("Alerta de viteză în fundal", "Păstrează alerta de viteză activă cât timp altă aplicație e în față, chiar și fără traseu. Consumă mai multă baterie și afișează o notificare"),
        "eu" to listOf("Abiadura-abisua atzeko planoan", "Abiadura-abisua aktibo mantentzen du beste aplikazio bat aurrean dagoenean ere, ibilbiderik gabe ere. Bateria gehiago erabiltzen du eta jakinarazpen bat erakusten du"),
        "zh" to listOf("背景超速提醒", "其他應用程式在前景時，即使沒有路線，也持續超速提醒。會多耗電並顯示通知"),
        "zhhk" to listOf("背景超速提醒", "其他應用程式在前景時，即使沒有路線，也持續超速提醒。會多耗電並顯示通知"),
        "zhs" to listOf("后台超速提醒", "其他应用在前台时，即使没有路线，也持续超速提醒。会多耗电并显示通知"),
        "el" to listOf("Ειδοποίηση ταχύτητας στο παρασκήνιο", "Διατηρεί την ειδοποίηση ταχύτητας ενώ άλλη εφαρμογή είναι μπροστά, ακόμη και χωρίς διαδρομή. Καταναλώνει περισσότερη μπαταρία και εμφανίζει ειδοποίηση"),
        "cs" to listOf("Upozornění na rychlost na pozadí", "Udržuje upozornění na rychlost, i když je vpředu jiná aplikace, a to i bez trasy. Spotřebuje více baterie a zobrazí oznámení"),
        "fi" to listOf("Nopeusvaroitus taustalla", "Pitää nopeusvaroituksen päällä, vaikka toinen sovellus on edessä, myös ilman reittiä. Kuluttaa enemmän akkua ja näyttää ilmoituksen"),
        "sk" to listOf("Upozornenie na rýchlosť na pozadí", "Udržiava upozornenie na rýchlosť, aj keď je vpredu iná aplikácia, a to aj bez trasy. Spotrebuje viac batérie a zobrazí oznámenie"),
        "da" to listOf("Fartadvarsel i baggrunden", "Holder fartadvarslen i gang, mens en anden app er forrest, også uden rute. Bruger mere batteri og viser en notifikation"),
        "hr" to listOf("Upozorenje o brzini u pozadini", "Održava upozorenje o brzini dok je druga aplikacija ispred, čak i bez rute. Troši više baterije i prikazuje obavijest"),
        "bs" to listOf("Upozorenje o brzini u pozadini", "Održava upozorenje o brzini dok je druga aplikacija ispred, čak i bez rute. Troši više baterije i prikazuje obavijest"),
        "sr" to listOf("Упозорење о брзини у позадини", "Одржава упозорење о брзини док је друга апликација испред, чак и без руте. Троши више батерије и приказује обавештење"),
        "ms" to listOf("Amaran kelajuan di latar belakang", "Mengekalkan amaran kelajuan semasa apl lain di hadapan, walaupun tanpa laluan. Menggunakan lebih banyak bateri dan menunjukkan pemberitahuan"),
        "hu" to listOf("Sebességfigyelmeztetés a háttérben", "Működésben tartja a sebességfigyelmeztetést, amíg másik alkalmazás van elöl, útvonal nélkül is. Több akkumulátort használ, és értesítést mutat"),
        "tr" to listOf("Arka planda hız uyarısı", "Başka bir uygulama öndeyken, rota olmasa bile hız uyarısını sürdürür. Daha fazla pil kullanır ve bir bildirim gösterir"),
        "ar" to listOf("تنبيه السرعة في الخلفية", "يُبقي تنبيه السرعة يعمل بينما تطبيق آخر في الواجهة، حتى بدون مسار. يستهلك بطارية أكثر ويعرض إشعارًا"),
        "he" to listOf("התראת מהירות ברקע", "משאיר את התראת המהירות פעילה בזמן שאפליקציה אחרת בחזית, גם ללא מסלול. צורך יותר סוללה ומציג התראה"),
        "ur" to listOf("پس منظر میں رفتار کا انتباہ", "جب کوئی اور ایپ سامنے ہو تو بھی، راستے کے بغیر بھی رفتار کا انتباہ جاری رکھتا ہے۔ زیادہ بیٹری استعمال ہوتی ہے اور اطلاع دکھاتا ہے"),
        "sv" to listOf("Fartvarning i bakgrunden", "Håller fartvarningen igång medan en annan app är i förgrunden, även utan rutt. Använder mer batteri och visar en avisering"),
        "kn" to listOf("ಹಿನ್ನೆಲೆಯಲ್ಲಿ ವೇಗ ಎಚ್ಚರಿಕೆ", "ಬೇರೆ ಆ್ಯಪ್ ಮುಂದಿರುವಾಗಲೂ, ಮಾರ್ಗವಿಲ್ಲದಿದ್ದರೂ ವೇಗ ಎಚ್ಚರಿಕೆಯನ್ನು ಮುಂದುವರಿಸುತ್ತದೆ. ಹೆಚ್ಚು ಬ್ಯಾಟರಿ ಬಳಸುತ್ತದೆ ಮತ್ತು ಅಧಿಸೂಚನೆ ತೋರಿಸುತ್ತದೆ"),
        "mr" to listOf("पार्श्वभूमीत वेग इशारा", "दुसरे अॅप समोर असतानाही, मार्गाशिवायही वेग इशारा सुरू ठेवतो. जास्त बॅटरी वापरतो आणि सूचना दाखवतो"),
        "nb" to listOf("Fartsvarsel i bakgrunnen", "Holder fartsvarselet i gang mens en annen app er foran, også uten rute. Bruker mer batteri og viser et varsel"),
        "ru" to listOf("Предупреждение о скорости в фоне", "Сохраняет предупреждение о скорости, пока поверх открыто другое приложение, даже без маршрута. Расходует больше заряда и показывает уведомление"),
        "uk" to listOf("Попередження про швидкість у фоні", "Зберігає попередження про швидкість, поки поверх відкрито інший застосунок, навіть без маршруту. Витрачає більше заряду й показує сповіщення"),
        "vi" to listOf("Cảnh báo tốc độ chạy nền", "Giữ cảnh báo tốc độ hoạt động khi ứng dụng khác ở phía trước, kể cả khi không có tuyến đường. Tốn pin hơn và hiện thông báo"),
        "ml" to listOf("പശ്ചാത്തലത്തിൽ വേഗ മുന്നറിയിപ്പ്", "മറ്റൊരു ആപ്പ് മുന്നിലുള്ളപ്പോഴും, റൂട്ട് ഇല്ലെങ്കിലും വേഗ മുന്നറിയിപ്പ് തുടരുന്നു. കൂടുതൽ ബാറ്ററി ഉപയോഗിക്കുകയും അറിയിപ്പ് കാണിക്കുകയും ചെയ്യും"),
        "ta" to listOf("பின்னணியில் வேக எச்சரிக்கை", "வேறு ஆப் முன்னால் இருக்கும்போதும், வழி இல்லாவிட்டாலும் வேக எச்சரிக்கையைத் தொடரும். அதிக பேட்டரி பயன்படுத்தி அறிவிப்பைக் காட்டும்"),
        "te" to listOf("బ్యాక్‌గ్రౌండ్‌లో వేగ హెచ్చరిక", "మరో యాప్ ముందున్నప్పుడు కూడా, రూట్ లేకపోయినా వేగ హెచ్చరికను కొనసాగిస్తుంది. ఎక్కువ బ్యాటరీ వాడుతుంది, నోటిఫికేషన్ చూపుతుంది"),
        "ko" to listOf("백그라운드 속도 경고", "다른 앱이 앞에 있어도, 경로가 없어도 속도 경고를 유지합니다. 배터리를 더 사용하고 알림이 표시됩니다"),
        "ca" to listOf("Avís de velocitat en segon pla", "Manté l'avís de velocitat mentre una altra aplicació és al davant, fins i tot sense ruta. Gasta més bateria i mostra una notificació"),
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
