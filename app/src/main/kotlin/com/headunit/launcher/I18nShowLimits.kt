package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Settings › Screen & sizes › Speed display › Show
 * speed limits (#445). Order: the switch, its explanation.
 */
internal object I18nShowLimits {
    private val keys = listOf(
        "Show speed limits",
        "Off hides the speed limit sign, the warnings for going over it and the limit signs on the map"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Snelheidslimieten tonen", "Uit verbergt het limietbord, de waarschuwingen voor te hard rijden en de limietborden op de kaart"),
        "de" to listOf("Tempolimits anzeigen", "Aus blendet das Tempolimit-Schild, die Warnungen bei Überschreitung und die Limit-Schilder auf der Karte aus"),
        "fr" to listOf("Afficher les limitations de vitesse", "Désactivé : masque le panneau de limite, les alertes de dépassement et les panneaux sur la carte"),
        "es" to listOf("Mostrar límites de velocidad", "Desactivado oculta la señal de límite, los avisos por superarlo y las señales en el mapa"),
        "esar" to listOf("Mostrar límites de velocidad", "Desactivado oculta la señal de límite, los avisos por superarlo y las señales en el mapa"),
        "esmx" to listOf("Mostrar límites de velocidad", "Desactivado oculta la señal de límite, los avisos por excederlo y las señales en el mapa"),
        "it" to listOf("Mostra i limiti di velocità", "Disattivato nasconde il cartello del limite, gli avvisi di superamento e i cartelli sulla mappa"),
        "pt" to listOf("Mostrar limites de velocidade", "Desligado esconde o sinal do limite, os avisos de excesso e os sinais no mapa"),
        "ptbr" to listOf("Mostrar limites de velocidade", "Desligado esconde a placa do limite, os avisos de excesso e as placas no mapa"),
        "pl" to listOf("Pokazuj ograniczenia prędkości", "Wyłączone ukrywa znak ograniczenia, ostrzeżenia o przekroczeniu i znaki na mapie"),
        "th" to listOf("แสดงขีดจำกัดความเร็ว", "เมื่อปิด จะซ่อนป้ายขีดจำกัด คำเตือนเมื่อขับเร็วเกิน และป้ายบนแผนที่"),
        "ja" to listOf("制限速度を表示", "オフにすると、制限速度の標識、超過の警告、地図上の標識を隠します"),
        "id" to listOf("Tampilkan batas kecepatan", "Jika mati, rambu batas, peringatan melebihi batas, dan rambu di peta disembunyikan"),
        "bg" to listOf("Показвай ограниченията на скоростта", "Изключено скрива знака за ограничение, предупрежденията при превишаване и знаците на картата"),
        "sl" to listOf("Pokaži omejitve hitrosti", "Izklopljeno skrije znak omejitve, opozorila ob prekoračitvi in znake na zemljevidu"),
        "ro" to listOf("Arată limitele de viteză", "Dezactivat ascunde semnul limitei, avertizările de depășire și semnele de pe hartă"),
        "eu" to listOf("Erakutsi abiadura-mugak", "Itzalita dagoenean, mugaren seinalea, muga gainditzearen abisuak eta mapako seinaleak ezkutatzen dira"),
        "zh" to listOf("顯示速限", "關閉後會隱藏速限標誌、超速警告和地圖上的標誌"),
        "zhhk" to listOf("顯示速限", "關閉後會隱藏速限標誌、超速警告和地圖上的標誌"),
        "zhs" to listOf("显示限速", "关闭后会隐藏限速标志、超速警告和地图上的标志"),
        "el" to listOf("Εμφάνιση ορίων ταχύτητας", "Όταν είναι απενεργοποιημένο, κρύβει την πινακίδα ορίου, τις προειδοποιήσεις υπέρβασης και τις πινακίδες στον χάρτη"),
        "cs" to listOf("Zobrazovat rychlostní limity", "Vypnuto skryje značku limitu, upozornění na překročení a značky na mapě"),
        "fi" to listOf("Näytä nopeusrajoitukset", "Pois päältä piilottaa rajoitusmerkin, ylinopeusvaroitukset ja merkit kartalla"),
        "sk" to listOf("Zobrazovať rýchlostné limity", "Vypnuté skryje značku limitu, upozornenia na prekročenie a značky na mape"),
        "da" to listOf("Vis hastighedsgrænser", "Slået fra skjuler grænseskiltet, advarslerne om at køre for stærkt og skiltene på kortet"),
        "hr" to listOf("Prikaži ograničenja brzine", "Isključeno skriva znak ograničenja, upozorenja o prekoračenju i znakove na karti"),
        "bs" to listOf("Prikaži ograničenja brzine", "Isključeno skriva znak ograničenja, upozorenja o prekoračenju i znakove na mapi"),
        "sr" to listOf("Прикажи ограничења брзине", "Искључено крије знак ограничења, упозорења о прекорачењу и знакове на мапи"),
        "ms" to listOf("Tunjukkan had laju", "Jika dimatikan, tanda had, amaran melebihi had dan tanda di peta disembunyikan"),
        "hu" to listOf("Sebességkorlátozások mutatása", "Kikapcsolva elrejti a korlátozó táblát, a túllépési figyelmeztetéseket és a térképen lévő táblákat"),
        "tr" to listOf("Hız sınırlarını göster", "Kapalıyken sınır tabelasını, aşım uyarılarını ve haritadaki tabelaları gizler"),
        "ar" to listOf("إظهار حدود السرعة", "عند الإيقاف تُخفى لافتة الحد وتحذيرات تجاوزه واللافتات على الخريطة"),
        "he" to listOf("הצג מגבלות מהירות", "כבוי מסתיר את תמרור המגבלה, את האזהרות על חריגה ואת התמרורים במפה"),
        "ur" to listOf("رفتار کی حدیں دکھائیں", "بند ہونے پر حد کا نشان، حد سے تجاوز کی وارننگ اور نقشے پر نشان چھپ جاتے ہیں"),
        "sv" to listOf("Visa hastighetsgränser", "Av döljer gränsskylten, varningarna för för hög fart och skyltarna på kartan"),
        "kn" to listOf("ವೇಗ ಮಿತಿಗಳನ್ನು ತೋರಿಸಿ", "ಆಫ್ ಮಾಡಿದರೆ ಮಿತಿ ಫಲಕ, ಮಿತಿ ಮೀರಿದ ಎಚ್ಚರಿಕೆಗಳು ಮತ್ತು ನಕ್ಷೆಯ ಫಲಕಗಳು ಮರೆಯಾಗುತ್ತವೆ"),
        "mr" to listOf("वेग मर्यादा दाखवा", "बंद केल्यास मर्यादेचा फलक, मर्यादा ओलांडल्याचे इशारे आणि नकाशावरील फलक लपतात"),
        "nb" to listOf("Vis fartsgrenser", "Av skjuler fartsgrenseskiltet, advarslene om for høy fart og skiltene på kartet"),
        "ru" to listOf("Показывать ограничения скорости", "Выключено скрывает знак ограничения, предупреждения о превышении и знаки на карте"),
        "uk" to listOf("Показувати обмеження швидкості", "Вимкнено ховає знак обмеження, попередження про перевищення і знаки на мапі"),
        "vi" to listOf("Hiện giới hạn tốc độ", "Khi tắt, ẩn biển giới hạn, cảnh báo vượt tốc độ và các biển trên bản đồ"),
        "ml" to listOf("വേഗപരിധി കാണിക്കുക", "ഓഫാക്കിയാൽ പരിധി ബോർഡ്, പരിധി കടന്നാലുള്ള മുന്നറിയിപ്പുകൾ, മാപ്പിലെ ബോർഡുകൾ എന്നിവ മറയും"),
        "ta" to listOf("வேக வரம்புகளைக் காட்டு", "அணைத்தால் வரம்புப் பலகை, வரம்பு மீறல் எச்சரிக்கைகள், வரைபடத்தின் பலகைகள் மறையும்"),
        "te" to listOf("వేగ పరిమితులను చూపు", "ఆఫ్ చేస్తే పరిమితి బోర్డు, పరిమితి మించిన హెచ్చరికలు, మ్యాప్‌లోని బోర్డులు దాగుతాయి"),
        "ko" to listOf("제한 속도 표시", "끄면 제한 속도 표지, 초과 경고, 지도의 표지가 숨겨집니다"),
        "ca" to listOf("Mostra els límits de velocitat", "Desactivat amaga el senyal de límit, els avisos d'excés i els senyals al mapa"),
        "mk" to listOf("Прикажи ограничувања на брзина", "Исклучено го крие знакот за ограничување, предупредувањата за надминување и знаците на картата"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { keys.zip(it) } ?: emptyList()
}
