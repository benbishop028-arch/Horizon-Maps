package com.headunit.launcher

/** Translations for the optional technical lane-data diagnostic setting. */
internal object I18nMapDiagnostics {
    fun rows(code: String): List<Pair<String, String>> {
        val values = when (code) {
            "nl" -> listOf("Toon diagnose van rijstrookgegevens", "Alleen zichtbaar in demomodus; heeft geen invloed op rijstrookbegeleiding.")
            "de" -> listOf("Spurdatendiagnose anzeigen", "Nur im Demomodus sichtbar; die Spurführung bleibt unverändert.")
            "fr" -> listOf("Afficher le diagnostic des voies", "Visible uniquement en mode démo ; n’affecte pas le guidage de voie.")
            "es" -> listOf("Mostrar diagnóstico de carriles", "Solo se muestra en modo demostración; no afecta al guiado por carriles.")
            "it" -> listOf("Mostra diagnostica delle corsie", "Visibile solo in modalità demo; non modifica la guida delle corsie.")
            "pt" -> listOf("Mostrar diagnóstico das faixas", "Visível apenas no modo de demonstração; não afeta a orientação por faixas.")
            "pl" -> listOf("Pokaż diagnostykę pasów", "Widoczne tylko w trybie demonstracyjnym; nie wpływa na prowadzenie po pasach.")
            "th" -> listOf("แสดงการวินิจฉัยข้อมูลช่องทาง", "แสดงเฉพาะในโหมดสาธิต ไม่ส่งผลต่อการนำทางตามช่องทาง")
            "ja" -> listOf("車線データの診断を表示", "デモモードでのみ表示され、車線案内には影響しません。")
            "id" -> listOf("Tampilkan diagnostik data lajur", "Hanya ditampilkan dalam mode demo; tidak memengaruhi panduan lajur.")
            "bg" -> listOf("Показвай диагностика на лентите", "Показва се само в демо режим; не влияе на насочването по ленти.")
            "sl" -> listOf("Prikaži diagnostiko podatkov o pasovih", "Prikazano samo v predstavitvenem načinu; ne vpliva na vodenje po pasovih.")
            "ro" -> listOf("Afișează diagnosticul benzilor", "Apare doar în modul demo; nu afectează ghidarea pe benzi.")
            "eu" -> listOf("Erakutsi errei-datuen diagnostikoa", "Demo moduan bakarrik agertzen da; ez du errei-gidatzea aldatzen.")
            "zh" -> listOf("顯示車道資料診斷", "只會在示範模式中顯示；不會影響車道指引。")
            "zhhk" -> listOf("顯示車道資料診斷", "只會在示範模式中顯示；不會影響車道指引。")
            "el" -> listOf("Εμφάνιση διαγνωστικών δεδομένων λωρίδων", "Εμφανίζεται μόνο στη λειτουργία επίδειξης· δεν επηρεάζει την καθοδήγηση λωρίδων.")
            "cs" -> listOf("Zobrazit diagnostiku jízdních pruhů", "Zobrazuje se pouze v demo režimu; neovlivňuje navádění v pruzích.")
            "fi" -> listOf("Näytä kaistatietojen diagnostiikka", "Näkyy vain esittelytilassa; ei vaikuta kaistaopastukseen.")
            "zhs" -> listOf("显示车道数据诊断", "仅在演示模式下显示；不会影响车道引导。")
            "sk" -> listOf("Zobraziť diagnostiku jazdných pruhov", "Zobrazuje sa iba v demo režime; neovplyvňuje navádzanie v pruhoch.")
            "da" -> listOf("Vis diagnosticering af vognbanedata", "Vises kun i demotilstand; påvirker ikke vognbanevejledningen.")
            "hr" -> listOf("Prikaži dijagnostiku podataka o trakama", "Prikazuje se samo u demo načinu; ne utječe na navođenje po trakama.")
            "ms" -> listOf("Tunjukkan diagnostik data lorong", "Hanya dipaparkan dalam mod demo; tidak menjejaskan panduan lorong.")
            "hu" -> listOf("Sávadat-diagnosztika megjelenítése", "Csak demó módban jelenik meg; nem befolyásolja a sávvezetést.")
            "tr" -> listOf("Şerit verisi tanılamasını göster", "Yalnızca demo modunda gösterilir; şerit yönlendirmesini etkilemez.")
            "sr" -> listOf("Прикажи дијагностику података о тракама", "Приказује се само у демо режиму; не утиче на вођење по тракама.")
            "ar" -> listOf("إظهار تشخيص بيانات المسارات", "يظهر في الوضع التجريبي فقط؛ ولا يؤثر في إرشادات المسارات.")
            "he" -> listOf("הצג אבחון נתוני נתיבים", "מוצג רק במצב הדגמה; אינו משפיע על הנחיית הנתיבים.")
            "ur" -> listOf("لین ڈیٹا کی تشخیص دکھائیں", "صرف ڈیمو موڈ میں دکھایا جاتا ہے؛ لین رہنمائی پر اثر نہیں پڑتا۔")
            "sv" -> listOf("Visa diagnostik för körfältsdata", "Visas endast i demoläge och påverkar inte körfältsguidningen.")
            "bs" -> listOf("Prikaži dijagnostiku podataka o trakama", "Prikazuje se samo u demo režimu; ne utiče na navođenje po trakama.")
            "kn" -> listOf("ಲೇನ್ ಡೇಟಾ ರೋಗನಿರ್ಣಯ ತೋರಿಸಿ", "ಡೆಮೊ ಮೋಡ್‌ನಲ್ಲಿ ಮಾತ್ರ ತೋರಿಸಲಾಗುತ್ತದೆ; ಲೇನ್ ಮಾರ್ಗದರ್ಶನಕ್ಕೆ ಪರಿಣಾಮ ಬೀರುವುದಿಲ್ಲ.")
            "mr" -> listOf("लेन डेटाचे निदान दाखवा", "फक्त डेमो मोडमध्ये दिसते; लेन मार्गदर्शनावर परिणाम होत नाही.")
            "nb" -> listOf("Vis diagnostikk for kjørefeltdata", "Vises bare i demomodus; påvirker ikke kjørefeltveiledningen.")
            "ru" -> listOf("Показывать диагностику данных полос", "Показывается только в деморежиме и не влияет на подсказки по полосам.")
            "uk" -> listOf("Показувати діагностику даних смуг", "Відображається лише в деморежимі й не впливає на підказки щодо смуг.")
            "vi" -> listOf("Hiển thị chẩn đoán dữ liệu làn đường", "Chỉ hiển thị ở chế độ demo; không ảnh hưởng đến hướng dẫn làn đường.")
            "ml" -> listOf("ലെയിൻ ഡാറ്റ ഡയഗ്നോസ്റ്റിക്സ് കാണിക്കുക", "ഡെമോ മോഡിൽ മാത്രം കാണിക്കും; ലെയിൻ മാർഗനിർദേശത്തെ ബാധിക്കില്ല.")
            "ca" -> listOf("Mostra el diagnòstic de dades dels carrils", "Només es mostra en mode de demostració; no afecta la guia de carrils.")
            "ko" -> listOf("차로 데이터 진단 표시", "데모 모드에서만 표시되며 차로 안내에는 영향을 주지 않습니다.")
            "ta" -> listOf("வழித்தடத் தரவு கண்டறிதலைக் காட்டு", "செயல்விளக்கப் பயன்முறையில் மட்டுமே காட்டப்படும்; வழித்தட வழிகாட்டுதலைப் பாதிக்காது.")
            "te" -> listOf("లేన్ డేటా నిర్ధారణను చూపించు", "డెమో మోడ్‌లో మాత్రమే చూపబడుతుంది; లేన్ మార్గదర్శకత్వంపై ప్రభావం ఉండదు.")
            "esar" -> listOf("Mostrar diagnóstico de carriles", "Solo se muestra en modo demostración; no afecta la guía por carriles.")
            "esmx" -> listOf("Mostrar diagnóstico de carriles", "Solo se muestra en modo demostración; no afecta la guía por carriles.")
            else -> return emptyList()
        }
        return listOf(
            "Show lane data diagnostics" to values[0],
            "Only shown in Demo mode; does not affect lane guidance." to values[1]
        )
    }
}
