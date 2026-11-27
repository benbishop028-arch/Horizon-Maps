package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Settings › Android Auto › Use the car's GPS.
 * Order: the switch, its explanation.
 */
internal object I18nCarGps {
    private val keys = listOf(
        "Use the car's GPS",
        "Off uses only the phone's GPS for your position and direction. Try off if the arrow turns sideways or lags behind"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("GPS van de auto gebruiken", "Uit gebruikt alleen de GPS van de telefoon voor je positie en richting. Probeer uit als de pijl zijwaarts draait of achterloopt"),
        "de" to listOf("GPS des Autos verwenden", "Aus nutzt nur das GPS des Handys für Position und Richtung. Probiere Aus, wenn sich der Pfeil seitwärts dreht oder hinterherhinkt"),
        "fr" to listOf("Utiliser le GPS de la voiture", "Désactivé : seul le GPS du téléphone donne la position et la direction. À essayer si la flèche tourne de travers ou prend du retard"),
        "es" to listOf("Usar el GPS del coche", "Desactivado usa solo el GPS del teléfono para la posición y la dirección. Pruébalo si la flecha gira de lado o se retrasa"),
        "esar" to listOf("Usar el GPS del auto", "Desactivado usa solo el GPS del teléfono para la posición y la dirección. Probalo si la flecha gira de costado o se atrasa"),
        "esmx" to listOf("Usar el GPS del auto", "Desactivado usa solo el GPS del teléfono para la posición y la dirección. Pruébalo si la flecha gira de lado o se retrasa"),
        "it" to listOf("Usa il GPS dell'auto", "Disattivato usa solo il GPS del telefono per posizione e direzione. Prova a disattivarlo se la freccia gira di lato o è in ritardo"),
        "pt" to listOf("Usar o GPS do carro", "Desligado usa apenas o GPS do telemóvel para a posição e a direção. Experimente desligar se a seta rodar de lado ou atrasar"),
        "ptbr" to listOf("Usar o GPS do carro", "Desligado usa apenas o GPS do celular para a posição e a direção. Experimente desligar se a seta girar de lado ou atrasar"),
        "pl" to listOf("Używaj GPS samochodu", "Wyłączone używa tylko GPS telefonu do pozycji i kierunku. Wypróbuj, jeśli strzałka obraca się bokiem lub spóźnia"),
        "th" to listOf("ใช้ GPS ของรถ", "เมื่อปิด จะใช้เฉพาะ GPS ของโทรศัพท์สำหรับตำแหน่งและทิศทาง ลองปิดถ้าลูกศรหมุนไปด้านข้างหรือช้ากว่าจริง"),
        "ja" to listOf("車のGPSを使う", "オフにすると、位置と向きにスマホのGPSだけを使います。矢印が横を向く、または遅れる場合はオフにしてみてください"),
        "id" to listOf("Gunakan GPS mobil", "Jika mati, hanya GPS ponsel yang dipakai untuk posisi dan arah. Coba matikan jika panah berputar menyamping atau tertinggal"),
        "bg" to listOf("Използвай GPS на колата", "Изключено ползва само GPS на телефона за позиция и посока. Опитайте, ако стрелката се върти настрани или изостава"),
        "sl" to listOf("Uporabi GPS avtomobila", "Izklopljeno uporablja samo GPS telefona za položaj in smer. Poskusite, če se puščica obrača vstran ali zaostaja"),
        "ro" to listOf("Folosește GPS-ul mașinii", "Dezactivat folosește doar GPS-ul telefonului pentru poziție și direcție. Încearcă dezactivat dacă săgeata se învârte într-o parte sau rămâne în urmă"),
        "eu" to listOf("Erabili autoaren GPSa", "Itzalita dagoenean, telefonoaren GPSa bakarrik erabiltzen da kokapenerako eta norabiderako. Saiatu itzaltzen gezia alboratzen bada edo atzeratzen bada"),
        "zh" to listOf("使用車輛的 GPS", "關閉後，位置和方向只使用手機的 GPS。如果箭頭橫向轉動或落後，請試著關閉"),
        "zhhk" to listOf("使用車輛的 GPS", "關閉後，位置和方向只使用手機的 GPS。如果箭咀橫向轉動或落後，請試下關閉"),
        "zhs" to listOf("使用车辆的 GPS", "关闭后，位置和方向只使用手机的 GPS。如果箭头横向转动或滞后，请尝试关闭"),
        "el" to listOf("Χρήση του GPS του αυτοκινήτου", "Όταν είναι ανενεργό, χρησιμοποιείται μόνο το GPS του τηλεφώνου για θέση και κατεύθυνση. Δοκιμάστε το αν το βέλος στρέφεται πλάγια ή καθυστερεί"),
        "cs" to listOf("Používat GPS auta", "Vypnuto používá jen GPS telefonu pro polohu a směr. Vyzkoušejte, pokud se šipka otáčí bokem nebo zaostává"),
        "fi" to listOf("Käytä auton GPS:ää", "Pois päältä käyttää vain puhelimen GPS:ää sijaintiin ja suuntaan. Kokeile, jos nuoli kääntyy sivuttain tai jää jälkeen"),
        "sk" to listOf("Používať GPS auta", "Vypnuté používa len GPS telefónu pre polohu a smer. Vyskúšajte, ak sa šípka otáča bokom alebo zaostáva"),
        "da" to listOf("Brug bilens GPS", "Slået fra bruger kun telefonens GPS til position og retning. Prøv det, hvis pilen drejer sidelæns eller hænger bagefter"),
        "hr" to listOf("Koristi GPS automobila", "Isključeno koristi samo GPS telefona za položaj i smjer. Isprobaj ako se strelica okreće postrance ili kasni"),
        "bs" to listOf("Koristi GPS automobila", "Isključeno koristi samo GPS telefona za poziciju i smjer. Probaj ako se strelica okreće bočno ili kasni"),
        "sr" to listOf("Користи GPS аутомобила", "Искључено користи само GPS телефона за положај и смер. Пробај ако се стрелица окреће постранце или касни"),
        "ms" to listOf("Guna GPS kereta", "Jika dimatikan, hanya GPS telefon digunakan untuk kedudukan dan arah. Cuba matikan jika anak panah berputar ke sisi atau ketinggalan"),
        "hu" to listOf("Az autó GPS-ének használata", "Kikapcsolva csak a telefon GPS-ét használja a helyzethez és az irányhoz. Próbáld ki, ha a nyíl oldalra fordul vagy késik"),
        "tr" to listOf("Aracın GPS'ini kullan", "Kapalıyken konum ve yön için yalnızca telefonun GPS'i kullanılır. Ok yana dönüyor veya geride kalıyorsa kapatmayı deneyin"),
        "ar" to listOf("استخدام GPS السيارة", "عند الإيقاف يُستخدم GPS الهاتف فقط للموقع والاتجاه. جرّب الإيقاف إذا دار السهم جانبًا أو تأخر"),
        "he" to listOf("שימוש ב-GPS של הרכב", "כבוי משתמש רק ב-GPS של הטלפון למיקום ולכיוון. נסו לכבות אם החץ מסתובב הצידה או מפגר"),
        "ur" to listOf("گاڑی کا GPS استعمال کریں", "بند ہونے پر مقام اور سمت کے لیے صرف فون کا GPS استعمال ہوتا ہے۔ اگر تیر ایک طرف مڑے یا پیچھے رہ جائے تو بند کر کے دیکھیں"),
        "sv" to listOf("Använd bilens GPS", "Av använder bara telefonens GPS för position och riktning. Prova av om pilen vrider sig i sidled eller hänger efter"),
        "kn" to listOf("ಕಾರಿನ GPS ಬಳಸಿ", "ಆಫ್ ಮಾಡಿದರೆ ಸ್ಥಾನ ಮತ್ತು ದಿಕ್ಕಿಗೆ ಫೋನ್‌ನ GPS ಮಾತ್ರ ಬಳಸಲಾಗುತ್ತದೆ. ಬಾಣ ಪಕ್ಕಕ್ಕೆ ತಿರುಗಿದರೆ ಅಥವಾ ಹಿಂದೆ ಉಳಿದರೆ ಆಫ್ ಮಾಡಿ ನೋಡಿ"),
        "mr" to listOf("गाडीचा GPS वापरा", "बंद केल्यास स्थान आणि दिशेसाठी फक्त फोनचा GPS वापरला जातो. बाण बाजूला वळला किंवा मागे राहिला तर बंद करून पहा"),
        "nb" to listOf("Bruk bilens GPS", "Av bruker bare telefonens GPS til posisjon og retning. Prøv av hvis pilen snur seg sidelengs eller henger etter"),
        "ru" to listOf("Использовать GPS автомобиля", "Выключено — для положения и направления используется только GPS телефона. Попробуйте, если стрелка поворачивается боком или отстаёт"),
        "uk" to listOf("Використовувати GPS автомобіля", "Вимкнено — для положення і напрямку використовується лише GPS телефона. Спробуйте, якщо стрілка повертається боком або відстає"),
        "vi" to listOf("Dùng GPS của xe", "Tắt sẽ chỉ dùng GPS của điện thoại cho vị trí và hướng. Hãy thử tắt nếu mũi tên xoay ngang hoặc bị trễ"),
        "ml" to listOf("കാറിന്റെ GPS ഉപയോഗിക്കുക", "ഓഫാക്കിയാൽ സ്ഥാനത്തിനും ദിശയ്ക്കും ഫോണിന്റെ GPS മാത്രം ഉപയോഗിക്കും. അമ്പ് വശത്തേക്ക് തിരിയുകയോ പിന്നിലാകുകയോ ചെയ്താൽ ഓഫാക്കി നോക്കുക"),
        "ta" to listOf("காரின் GPS ஐப் பயன்படுத்து", "அணைத்தால் இடத்திற்கும் திசைக்கும் தொலைபேசியின் GPS மட்டுமே பயன்படும். அம்பு பக்கவாட்டில் திரும்பினால் அல்லது பின்தங்கினால் அணைத்துப் பாருங்கள்"),
        "te" to listOf("కారు GPS ఉపయోగించు", "ఆఫ్ చేస్తే స్థానం, దిశ కోసం ఫోన్ GPS మాత్రమే ఉపయోగిస్తారు. బాణం పక్కకు తిరిగితే లేదా వెనుకబడితే ఆఫ్ చేసి చూడండి"),
        "ko" to listOf("차량 GPS 사용", "끄면 위치와 방향에 휴대전화 GPS만 사용합니다. 화살표가 옆으로 돌거나 뒤처지면 꺼 보세요"),
        "ca" to listOf("Usa el GPS del cotxe", "Desactivat només usa el GPS del telèfon per a la posició i la direcció. Prova-ho si la fletxa gira de costat o va endarrerida"),
        "mk" to listOf("Користи го GPS на автомобилот", "Исклучено користи само GPS на телефонот за положба и насока. Пробајте ако стрелката се врти настрана или заостанува"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
