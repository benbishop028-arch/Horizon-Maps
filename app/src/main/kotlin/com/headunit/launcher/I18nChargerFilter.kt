package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): the charger filter (#343).
 * Order: Any, Charger speed, the speed explanation, Only these providers, the providers explanation, Filter: {0}.
 */
internal object I18nChargerFilter {
    private val keys = listOf(
        "Any", "Charger speed", "Hide chargers slower than this. Chargers whose speed isn't known are still shown",
        "Only these providers", "Names separated by commas. Blank shows all", "Filter: {0}"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Alle", "Laadsnelheid", "Verberg laadpunten die langzamer zijn dan dit. Laadpunten waarvan de snelheid onbekend is blijven zichtbaar", "Alleen deze aanbieders", "Namen gescheiden door komma's. Leeg toont alles", "Filter: {0}"),
        "de" to listOf("Alle", "Ladegeschwindigkeit", "Ladestationen ausblenden, die langsamer sind. Stationen mit unbekannter Leistung bleiben sichtbar", "Nur diese Anbieter", "Namen durch Kommas getrennt. Leer zeigt alle", "Filter: {0}"),
        "fr" to listOf("Toutes", "Vitesse de recharge", "Masquer les bornes plus lentes. Les bornes dont la puissance est inconnue restent affichées", "Seulement ces opérateurs", "Noms séparés par des virgules. Vide : tous", "Filtre : {0}"),
        "es" to listOf("Cualquiera", "Velocidad de carga", "Oculta los cargadores más lentos. Los de velocidad desconocida se siguen mostrando", "Solo estos proveedores", "Nombres separados por comas. En blanco muestra todos", "Filtro: {0}"),
        "esar" to listOf("Cualquiera", "Velocidad de carga", "Oculta los cargadores más lentos. Los de velocidad desconocida se siguen mostrando", "Solo estos proveedores", "Nombres separados por comas. En blanco muestra todos", "Filtro: {0}"),
        "esmx" to listOf("Cualquiera", "Velocidad de carga", "Oculta los cargadores más lentos. Los de velocidad desconocida se siguen mostrando", "Solo estos proveedores", "Nombres separados por comas. En blanco muestra todos", "Filtro: {0}"),
        "it" to listOf("Qualsiasi", "Velocità di ricarica", "Nascondi le colonnine più lente. Quelle di cui non si conosce la potenza restano visibili", "Solo questi gestori", "Nomi separati da virgole. Vuoto mostra tutti", "Filtro: {0}"),
        "pt" to listOf("Qualquer", "Velocidade de carregamento", "Esconde os carregadores mais lentos. Os de velocidade desconhecida continuam a aparecer", "Só estes operadores", "Nomes separados por vírgulas. Vazio mostra todos", "Filtro: {0}"),
        "pl" to listOf("Dowolna", "Szybkość ładowania", "Ukryj ładowarki wolniejsze niż ta wartość. Ładowarki o nieznanej mocy nadal się pokazują", "Tylko ci operatorzy", "Nazwy oddzielone przecinkami. Puste pokazuje wszystkich", "Filtr: {0}"),
        "th" to listOf("ทั้งหมด", "ความเร็วในการชาร์จ", "ซ่อนที่ชาร์จที่ช้ากว่านี้ ที่ชาร์จที่ไม่ทราบความเร็วจะยังแสดงอยู่", "เฉพาะผู้ให้บริการเหล่านี้", "ชื่อคั่นด้วยเครื่องหมายจุลภาค เว้นว่างเพื่อแสดงทั้งหมด", "ตัวกรอง: {0}"),
        "ja" to listOf("指定なし", "充電速度", "これより遅い充電器を非表示にします。速度が不明な充電器は表示されます", "このプロバイダーのみ", "名前をカンマで区切ります。空欄ならすべて表示", "フィルター: {0}"),
        "id" to listOf("Semua", "Kecepatan pengisian", "Sembunyikan pengisi daya yang lebih lambat. Yang kecepatannya tidak diketahui tetap ditampilkan", "Hanya penyedia ini", "Nama dipisahkan koma. Kosong menampilkan semua", "Filter: {0}"),
        "bg" to listOf("Всякаква", "Скорост на зареждане", "Скрива по-бавните зарядни. Зарядните с неизвестна скорост все пак се показват", "Само тези доставчици", "Имена, разделени със запетаи. Празно показва всички", "Филтър: {0}"),
        "sl" to listOf("Poljubna", "Hitrost polnjenja", "Skrije počasnejše polnilnice. Polnilnice z neznano hitrostjo se še vedno prikažejo", "Samo ti ponudniki", "Imena, ločena z vejicami. Prazno prikaže vse", "Filter: {0}"),
        "ro" to listOf("Oricare", "Viteza de încărcare", "Ascunde stațiile mai lente. Stațiile cu viteză necunoscută rămân afișate", "Doar acești furnizori", "Nume separate prin virgule. Gol arată toți", "Filtru: {0}"),
        "eu" to listOf("Edozein", "Kargatze-abiadura", "Ezkutatu hau baino motelagoak diren kargagailuak. Abiadura ezezaguna dutenak ikusten dira", "Hornitzaile hauek soilik", "Izenak komaz bereizita. Hutsik dagoenean guztiak", "Iragazkia: {0}"),
        "zh" to listOf("不限", "充電速度", "隱藏比此更慢的充電站。速度不明的充電站仍會顯示", "只顯示這些供應商", "名稱以逗號分隔。留空則顯示全部", "篩選：{0}"),
        "zhhk" to listOf("不限", "充電速度", "隱藏比此更慢的充電站。速度不明的充電站仍會顯示", "只顯示這些供應商", "名稱以逗號分隔。留空則顯示全部", "篩選：{0}"),
        "zhs" to listOf("不限", "充电速度", "隐藏比此更慢的充电桩。速度未知的充电桩仍会显示", "只显示这些运营商", "名称以逗号分隔。留空则显示全部", "筛选：{0}"),
        "el" to listOf("Οποιαδήποτε", "Ταχύτητα φόρτισης", "Κρύβει τους πιο αργούς φορτιστές. Όσοι έχουν άγνωστη ταχύτητα εμφανίζονται ακόμα", "Μόνο αυτοί οι πάροχοι", "Ονόματα χωρισμένα με κόμμα. Κενό εμφανίζει όλους", "Φίλτρο: {0}"),
        "cs" to listOf("Libovolná", "Rychlost nabíjení", "Skryje pomalejší nabíječky. Nabíječky s neznámou rychlostí se stále zobrazí", "Jen tito provozovatelé", "Jména oddělená čárkami. Prázdné zobrazí všechny", "Filtr: {0}"),
        "fi" to listOf("Mikä tahansa", "Latausnopeus", "Piilota tätä hitaammat laturit. Laturit, joiden nopeus ei ole tiedossa, näytetään silti", "Vain nämä palveluntarjoajat", "Nimet pilkuilla erotettuina. Tyhjä näyttää kaikki", "Suodatin: {0}"),
        "sk" to listOf("Ľubovoľná", "Rýchlosť nabíjania", "Skryje pomalšie nabíjačky. Nabíjačky s neznámou rýchlosťou sa stále zobrazia", "Len títo prevádzkovatelia", "Mená oddelené čiarkami. Prázdne zobrazí všetkých", "Filter: {0}"),
        "da" to listOf("Alle", "Ladehastighed", "Skjul ladere, der er langsommere end dette. Ladere med ukendt hastighed vises stadig", "Kun disse udbydere", "Navne adskilt af kommaer. Tomt viser alle", "Filter: {0}"),
        "hr" to listOf("Bilo koja", "Brzina punjenja", "Sakrij punjače sporije od ovoga. Punjači nepoznate brzine i dalje se prikazuju", "Samo ovi pružatelji", "Imena odvojena zarezima. Prazno prikazuje sve", "Filtar: {0}"),
        "bs" to listOf("Bilo koja", "Brzina punjenja", "Sakrij punjače sporije od ovoga. Punjači nepoznate brzine se i dalje prikazuju", "Samo ovi pružaoci", "Imena odvojena zarezima. Prazno prikazuje sve", "Filter: {0}"),
        "sr" to listOf("Било која", "Брзина пуњења", "Сакриј пуњаче спорије од ове. Пуњачи непознате брзине се и даље приказују", "Само ови пружаоци", "Имена одвојена зарезима. Празно приказује све", "Филтер: {0}"),
        "ms" to listOf("Mana-mana", "Kelajuan pengecasan", "Sembunyikan pengecas yang lebih perlahan. Pengecas yang kelajuannya tidak diketahui masih dipaparkan", "Hanya penyedia ini", "Nama dipisahkan koma. Kosong paparkan semua", "Tapis: {0}"),
        "hu" to listOf("Bármilyen", "Töltési sebesség", "Elrejti az ennél lassabb töltőket. Az ismeretlen sebességű töltők továbbra is megjelennek", "Csak ezek a szolgáltatók", "Nevek vesszővel elválasztva. Üresen mindet mutatja", "Szűrő: {0}"),
        "tr" to listOf("Herhangi", "Şarj hızı", "Bundan yavaş şarj istasyonlarını gizler. Hızı bilinmeyenler yine de gösterilir", "Yalnızca bu sağlayıcılar", "İsimler virgülle ayrılır. Boş bırakırsanız hepsi gösterilir", "Filtre: {0}"),
        "ar" to listOf("أي", "سرعة الشحن", "يُخفي الشواحن الأبطأ من هذا. تبقى الشواحن غير المعروفة السرعة ظاهرة", "هذه الجهات فقط", "الأسماء مفصولة بفواصل. اتركه فارغًا لإظهار الكل", "الفلتر: {0}"),
        "he" to listOf("כל מהירות", "מהירות טעינה", "מסתיר עמדות איטיות מזו. עמדות שמהירותן לא ידועה ממשיכות להופיע", "רק הספקים האלה", "שמות מופרדים בפסיקים. ריק מציג הכול", "מסנן: {0}"),
        "ur" to listOf("کوئی بھی", "چارجنگ کی رفتار", "اس سے سست چارجر چھپائیں۔ جن کی رفتار معلوم نہیں وہ پھر بھی دکھائے جاتے ہیں", "صرف یہ فراہم کنندگان", "نام کوما سے الگ کریں۔ خالی ہو تو سب دکھائے جاتے ہیں", "فلٹر: {0}"),
        "sv" to listOf("Alla", "Laddhastighet", "Dölj laddare som är långsammare än detta. Laddare med okänd hastighet visas ändå", "Bara dessa leverantörer", "Namn åtskilda med kommatecken. Tomt visar alla", "Filter: {0}"),
        "kn" to listOf("ಯಾವುದೇ", "ಚಾರ್ಜಿಂಗ್ ವೇಗ", "ಇದಕ್ಕಿಂತ ನಿಧಾನವಾದ ಚಾರ್ಜರ್‌ಗಳನ್ನು ಮರೆಮಾಡಿ. ವೇಗ ತಿಳಿಯದ ಚಾರ್ಜರ್‌ಗಳು ಇನ್ನೂ ತೋರಿಸಲ್ಪಡುತ್ತವೆ", "ಈ ಪೂರೈಕೆದಾರರು ಮಾತ್ರ", "ಹೆಸರುಗಳನ್ನು ಅಲ್ಪವಿರಾಮದಿಂದ ಬೇರ್ಪಡಿಸಿ. ಖಾಲಿ ಇದ್ದರೆ ಎಲ್ಲವನ್ನೂ ತೋರಿಸುತ್ತದೆ", "ಫಿಲ್ಟರ್: {0}"),
        "mr" to listOf("कोणताही", "चार्जिंगचा वेग", "यापेक्षा हळू चार्जर लपवा. वेग माहीत नसलेले चार्जर तरीही दिसतात", "फक्त हे पुरवठादार", "नावे स्वल्पविरामाने वेगळी करा. रिकामे असल्यास सर्व दिसतात", "फिल्टर: {0}"),
        "nb" to listOf("Alle", "Laderhastighet", "Skjul ladere som er tregere enn dette. Ladere med ukjent hastighet vises fortsatt", "Bare disse leverandørene", "Navn atskilt med komma. Tomt viser alle", "Filter: {0}"),
        "ru" to listOf("Любая", "Скорость зарядки", "Скрывает станции медленнее этой скорости. Станции с неизвестной скоростью остаются в списке", "Только эти операторы", "Названия через запятую. Пусто — показывать всех", "Фильтр: {0}"),
        "uk" to listOf("Будь-яка", "Швидкість заряджання", "Приховує станції повільніші за цю швидкість. Станції з невідомою швидкістю залишаються у списку", "Лише ці оператори", "Назви через кому. Порожньо — показувати всіх", "Фільтр: {0}"),
        "vi" to listOf("Bất kỳ", "Tốc độ sạc", "Ẩn các trạm sạc chậm hơn mức này. Trạm không rõ tốc độ vẫn được hiển thị", "Chỉ các nhà cung cấp này", "Các tên cách nhau bằng dấu phẩy. Để trống để hiện tất cả", "Bộ lọc: {0}"),
        "ml" to listOf("ഏതും", "ചാർജിംഗ് വേഗത", "ഇതിലും വേഗത കുറഞ്ഞ ചാർജറുകൾ മറയ്ക്കുക. വേഗത അറിയാത്തവ ഇപ്പോഴും കാണിക്കും", "ഈ ദാതാക്കൾ മാത്രം", "പേരുകൾ കോമ കൊണ്ട് വേർതിരിക്കുക. ശൂന്യമെങ്കിൽ എല്ലാം കാണിക്കും", "ഫിൽട്ടർ: {0}"),
        "ta" to listOf("எதுவும்", "சார்ஜிங் வேகம்", "இதைவிட மெதுவான சார்ஜர்களை மறைக்கும். வேகம் தெரியாதவை இன்னும் காட்டப்படும்", "இந்த வழங்குநர்கள் மட்டும்", "பெயர்களை கமாவால் பிரிக்கவும். காலியாக இருந்தால் அனைத்தும் காட்டப்படும்", "வடிகட்டி: {0}"),
        "te" to listOf("ఏదైనా", "ఛార్జింగ్ వేగం", "దీని కంటే నెమ్మదైన ఛార్జర్‌లను దాచండి. వేగం తెలియనివి ఇంకా చూపబడతాయి", "ఈ ప్రొవైడర్లు మాత్రమే", "పేర్లను కామాతో వేరు చేయండి. ఖాళీగా ఉంటే అన్నీ చూపబడతాయి", "ఫిల్టర్: {0}"),
        "ko" to listOf("제한 없음", "충전 속도", "이보다 느린 충전기를 숨깁니다. 속도를 알 수 없는 충전기는 계속 표시됩니다", "이 사업자만", "이름을 쉼표로 구분하세요. 비워 두면 모두 표시", "필터: {0}"),
        "ca" to listOf("Qualsevol", "Velocitat de càrrega", "Amaga els carregadors més lents. Els de velocitat desconeguda es continuen mostrant", "Només aquests proveïdors", "Noms separats per comes. En blanc mostra tots", "Filtre: {0}")
    )

    fun rows(code: String): List<Pair<String, String>> {
        val v = t[code] ?: return emptyList()
        return keys.zip(v)
    }
}
