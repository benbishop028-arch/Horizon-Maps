package com.headunit.launcher

/**
 * Hand-written (not made by tr/genlang.py, so a regenerate won't lose them): Settings › Android Auto › Advanced car-screen sizes.
 * Order: the button that opens them, the note about the turn card keeping its size.
 */
internal object I18nCarSizes {
    private val keys = listOf(
        "Show {0} more",
        "Once you've set the turn card's own sizes, Overall size no longer changes it. Put them back to 100% to have it follow Overall size again"
    )

    private val t: Map<String, List<String>> = mapOf(
        "nl" to listOf("Nog {0} tonen", "Zodra je de eigen maten van de afslagkaart hebt ingesteld, verandert Totale grootte die niet meer. Zet ze terug op 100% om Totale grootte weer te volgen"),
        "de" to listOf("{0} weitere zeigen", "Sobald du die eigenen Größen der Abbiegekarte eingestellt hast, ändert die Gesamtgröße sie nicht mehr. Stelle sie auf 100 % zurück, damit sie wieder der Gesamtgröße folgt"),
        "fr" to listOf("Afficher {0} de plus", "Une fois les tailles propres de la carte de direction réglées, la taille globale ne la change plus. Remettez-les à 100 % pour qu'elle suive à nouveau la taille globale"),
        "es" to listOf("Mostrar {0} más", "Una vez ajustados los tamaños propios de la tarjeta de giro, el tamaño general ya no la cambia. Vuelve a ponerlos al 100 % para que siga de nuevo el tamaño general"),
        "esar" to listOf("Mostrar {0} más", "Una vez ajustados los tamaños propios de la tarjeta de giro, el tamaño general ya no la cambia. Volvé a ponerlos al 100 % para que siga otra vez el tamaño general"),
        "esmx" to listOf("Mostrar {0} más", "Una vez ajustados los tamaños propios de la tarjeta de giro, el tamaño general ya no la cambia. Vuelve a ponerlos al 100 % para que siga de nuevo el tamaño general"),
        "it" to listOf("Mostra altri {0}", "Dopo aver impostato le dimensioni proprie della scheda di svolta, la dimensione generale non la cambia più. Riportale al 100% perché segua di nuovo la dimensione generale"),
        "pt" to listOf("Mostrar mais {0}", "Depois de definir os tamanhos próprios do cartão de mudança de direção, o tamanho geral deixa de o alterar. Volte a pô-los a 100% para que siga de novo o tamanho geral"),
        "ptbr" to listOf("Mostrar mais {0}", "Depois de definir os tamanhos próprios do cartão de conversão, o tamanho geral não o altera mais. Volte-os para 100% para que ele siga de novo o tamanho geral"),
        "pl" to listOf("Pokaż jeszcze {0}", "Po ustawieniu własnych rozmiarów karty skrętu Rozmiar ogólny już jej nie zmienia. Ustaw je z powrotem na 100%, aby znów podążała za Rozmiarem ogólnym"),
        "th" to listOf("แสดงเพิ่มอีก {0}", "เมื่อตั้งขนาดของการ์ดเลี้ยวเองแล้ว ขนาดโดยรวมจะไม่เปลี่ยนการ์ดนี้อีก ตั้งกลับเป็น 100% เพื่อให้ตามขนาดโดยรวมอีกครั้ง"),
        "ja" to listOf("さらに{0}件を表示", "ターンカード独自のサイズを設定すると、全体のサイズで変わらなくなります。100%に戻すと再び全体のサイズに合わせます"),
        "id" to listOf("Tampilkan {0} lagi", "Setelah ukuran kartu belok diatur sendiri, Ukuran keseluruhan tidak lagi mengubahnya. Kembalikan ke 100% agar mengikuti Ukuran keseluruhan lagi"),
        "bg" to listOf("Покажи още {0}", "След като зададете собствените размери на картата за завой, Общият размер вече не я променя. Върнете ги на 100%, за да следва отново Общия размер"),
        "sl" to listOf("Pokaži še {0}", "Ko nastavite lastne velikosti kartice zavoja, je Skupna velikost ne spreminja več. Vrnite jih na 100 %, da spet sledi Skupni velikosti"),
        "ro" to listOf("Arată încă {0}", "După ce setezi dimensiunile proprii ale cardului de viraj, Dimensiunea generală nu îl mai schimbă. Readu-le la 100% ca să urmeze din nou Dimensiunea generală"),
        "eu" to listOf("Erakutsi beste {0}", "Bira-txartelaren tamainak ezarri ondoren, Tamaina orokorrak ez du gehiago aldatzen. Jarri berriro % 100ean Tamaina orokorrari jarrai diezaion"),
        "zh" to listOf("再顯示 {0} 項", "設定轉彎卡片自己的大小後，整體大小就不會再改變它。改回 100% 即可再次跟隨整體大小"),
        "zhhk" to listOf("再顯示 {0} 項", "設定轉彎卡片自己的大小後，整體大小就不會再改變它。改回 100% 即可再次跟隨整體大小"),
        "zhs" to listOf("再显示 {0} 项", "设置转弯卡片自己的大小后，整体大小就不会再改变它。改回 100% 即可再次跟随整体大小"),
        "el" to listOf("Εμφάνιση {0} ακόμη", "Αφού ορίσετε τα δικά της μεγέθη, το Συνολικό μέγεθος δεν αλλάζει πια την κάρτα στροφής. Επαναφέρετέ τα στο 100% για να ακολουθεί ξανά το Συνολικό μέγεθος"),
        "cs" to listOf("Zobrazit dalších {0}", "Jakmile nastavíte vlastní velikosti karty odbočení, Celková velikost ji už nemění. Vraťte je na 100 %, aby znovu sledovala Celkovou velikost"),
        "fi" to listOf("Näytä {0} lisää", "Kun olet asettanut käännöskortin omat koot, Kokonaiskoko ei enää muuta sitä. Palauta ne 100 %:iin, niin se seuraa taas Kokonaiskokoa"),
        "sk" to listOf("Zobraziť ďalších {0}", "Keď nastavíte vlastné veľkosti karty odbočenia, Celková veľkosť ju už nemení. Vráťte ich na 100 %, aby znova sledovala Celkovú veľkosť"),
        "da" to listOf("Vis {0} mere", "Når du har sat svingkortets egne størrelser, ændrer Samlet størrelse det ikke længere. Sæt dem tilbage til 100 % for at følge Samlet størrelse igen"),
        "hr" to listOf("Prikaži još {0}", "Kad postavite vlastite veličine kartice skretanja, Ukupna veličina je više ne mijenja. Vratite ih na 100 % da opet prati Ukupnu veličinu"),
        "bs" to listOf("Prikaži još {0}", "Kad postavite vlastite veličine kartice skretanja, Ukupna veličina je više ne mijenja. Vratite ih na 100 % da opet prati Ukupnu veličinu"),
        "sr" to listOf("Прикажи још {0}", "Кад подесите сопствене величине картице скретања, Укупна величина је више не мења. Вратите их на 100 % да опет прати Укупну величину"),
        "ms" to listOf("Tunjukkan {0} lagi", "Selepas saiz kad belok sendiri ditetapkan, Saiz keseluruhan tidak lagi mengubahnya. Kembalikan kepada 100% untuk mengikut Saiz keseluruhan semula"),
        "hu" to listOf("Még {0} megjelenítése", "Ha beállítottad a kanyarkártya saját méreteit, a Teljes méret már nem változtatja. Állítsd vissza őket 100%-ra, hogy újra a Teljes méretet kövesse"),
        "tr" to listOf("{0} tane daha göster", "Dönüş kartının kendi boyutlarını ayarladıktan sonra Genel boyut onu artık değiştirmez. Yeniden Genel boyutu izlemesi için %100'e geri alın"),
        "ar" to listOf("عرض {0} أخرى", "بعد ضبط أحجام بطاقة الانعطاف الخاصة بها، لا يغيّرها الحجم العام بعد الآن. أعدها إلى 100% لتتبع الحجم العام من جديد"),
        "he" to listOf("הצג עוד {0}", "אחרי שהגדרת את הגדלים של כרטיס הפנייה עצמו, הגודל הכללי כבר לא משנה אותו. החזר אותם ל-100% כדי שיחזור לעקוב אחרי הגודל הכללי"),
        "ur" to listOf("مزید {0} دکھائیں", "موڑ کارڈ کے اپنے سائز طے کرنے کے بعد مجموعی سائز اسے مزید نہیں بدلتا۔ دوبارہ مجموعی سائز کے ساتھ چلانے کے لیے انہیں 100% پر واپس کریں"),
        "sv" to listOf("Visa {0} till", "När du har ställt in svängkortets egna storlekar ändrar Total storlek det inte längre. Sätt tillbaka dem till 100 % för att följa Total storlek igen"),
        "kn" to listOf("ಇನ್ನೂ {0} ತೋರಿಸಿ", "ತಿರುವು ಕಾರ್ಡ್‌ನ ಸ್ವಂತ ಗಾತ್ರಗಳನ್ನು ಹೊಂದಿಸಿದ ನಂತರ ಒಟ್ಟು ಗಾತ್ರ ಅದನ್ನು ಬದಲಾಯಿಸುವುದಿಲ್ಲ. ಮತ್ತೆ ಒಟ್ಟು ಗಾತ್ರವನ್ನು ಅನುಸರಿಸಲು 100% ಗೆ ಹಿಂತಿರುಗಿಸಿ"),
        "mr" to listOf("आणखी {0} दाखवा", "वळण कार्डचे स्वतःचे आकार ठरवल्यानंतर एकूण आकार ते बदलत नाही. पुन्हा एकूण आकाराप्रमाणे होण्यासाठी ते 100% वर परत आणा"),
        "nb" to listOf("Vis {0} til", "Når du har stilt inn svingkortets egne størrelser, endrer Samlet størrelse det ikke lenger. Sett dem tilbake til 100 % for å følge Samlet størrelse igjen"),
        "ru" to listOf("Показать ещё {0}", "Когда размеры карточки поворота заданы отдельно, Общий размер её больше не меняет. Верните их на 100%, чтобы она снова следовала Общему размеру"),
        "uk" to listOf("Показати ще {0}", "Коли розміри картки повороту задано окремо, Загальний розмір її більше не змінює. Поверніть їх на 100%, щоб вона знову слідувала Загальному розміру"),
        "vi" to listOf("Hiện thêm {0}", "Khi đã đặt kích thước riêng cho thẻ rẽ, Kích thước tổng thể không còn thay đổi nó. Đặt lại về 100% để nó theo Kích thước tổng thể"),
        "ml" to listOf("{0} എണ്ണം കൂടി കാണിക്കുക", "തിരിവ് കാർഡിന്റെ സ്വന്തം വലുപ്പങ്ങൾ സജ്ജമാക്കിയാൽ മൊത്തം വലുപ്പം അതിനെ മാറ്റില്ല. വീണ്ടും മൊത്തം വലുപ്പം പിന്തുടരാൻ 100% ആക്കുക"),
        "ta" to listOf("மேலும் {0} காட்டு", "திருப்ப அட்டையின் சொந்த அளவுகளை அமைத்த பிறகு மொத்த அளவு அதை மாற்றாது. மீண்டும் மொத்த அளவைப் பின்பற்ற 100% க்குத் திருப்புங்கள்"),
        "te" to listOf("మరో {0} చూపించు", "మలుపు కార్డ్ సొంత పరిమాణాలు సెట్ చేసిన తర్వాత మొత్తం పరిమాణం దాన్ని మార్చదు. మళ్లీ మొత్తం పరిమాణాన్ని అనుసరించడానికి వాటిని 100%కి తిరిగి పెట్టండి"),
        "ko" to listOf("{0}개 더 보기", "회전 카드 자체 크기를 설정하면 전체 크기가 더 이상 바꾸지 않습니다. 다시 전체 크기를 따르게 하려면 100%로 되돌리세요"),
        "ca" to listOf("Mostra'n {0} més", "Un cop definides les mides pròpies de la targeta de gir, la mida general ja no la canvia. Torna-les al 100% perquè torni a seguir la mida general"),
        "mk" to listOf("Прикажи уште {0}", "Откако ќе ги поставите сопствените големини на картичката за свртување, Вкупната големина веќе не ја менува. Вратете ги на 100% за повторно да ја следи Вкупната големина"),
    )

    fun rows(code: String): List<Pair<String, String>> = t[code]?.let { v -> keys.zip(v) } ?: emptyList()
}
