package com.headunit.launcher

/**
 * Brazilian Portuguese (GitHub #284): made from the app's European Portuguese as it loads, with Brazil's own words and forms
 * (tela for ecrã, celular for telemóvel, salvar for guardar, pedágio for portagem, "Calculando" for "A calcular", quilômetro
 * for quilómetro...). New Portuguese text is covered automatically. [fixes] holds whole phrases where the word swaps aren't
 * enough, as Brazilian speakers point them out.
 */
object I18nPtBr {
    /** Whole-phrase corrections (English to Brazilian Portuguese), used in place of the converted European wording. */
    fun fixes(): List<Pair<String, String>> = emptyList()

    // European word -> Brazilian word (whole words; a capital first letter is kept)
    private val words: List<Pair<String, String>> = listOf(
        "ecrãs" to "telas", "ecrã" to "tela", "telemóveis" to "celulares", "telemóvel" to "celular",
        "ficheiros" to "arquivos", "ficheiro" to "arquivo",
        "guardar" to "salvar", "guardado" to "salvo", "guardada" to "salva", "guardados" to "salvos", "guardadas" to "salvas",
        "guarde" to "salve", "guardá-lo" to "salvá-lo", "guardá-la" to "salvá-la", "guardou" to "salvou",
        "autocarros" to "ônibus", "autocarro" to "ônibus", "comboios" to "trens", "comboio" to "trem",
        "utilizadores" to "usuários", "utilizador" to "usuário", "utilizar" to "usar", "utilize" to "use", "utiliza" to "usa",
        "utilizado" to "usado", "utilizada" to "usada", "utilizados" to "usados", "utilizadas" to "usadas",
        "definições" to "configurações", "definição" to "configuração",
        "portagens" to "pedágios", "portagem" to "pedágio", "autoestradas" to "rodovias", "autoestrada" to "rodovia",
        "auto-estradas" to "rodovias", "auto-estrada" to "rodovia",
        "rotundas" to "rotatórias", "rotunda" to "rotatória", "passadeiras" to "faixas de pedestres", "passadeira" to "faixa de pedestres",
        "peões" to "pedestres", "peão" to "pedestre", "camiões" to "caminhões", "camião" to "caminhão",
        "travar" to "frear", "trave" to "freie", "travagem" to "frenagem",
        "contactos" to "contatos", "contacto" to "contato", "facto" to "fato", "secções" to "seções", "secção" to "seção",
        "receção" to "recepção", "equipa" to "equipe", "câmaras" to "câmeras", "câmara" to "câmera",
        "registos" to "registros", "registo" to "registro", "registar" to "registrar",
        "transferir" to "baixar", "transferido" to "baixado", "transferida" to "baixada",
        "aceder" to "acessar", "acede" to "acessa", "gasóleo" to "diesel",
        "quilómetros" to "quilômetros", "quilómetro" to "quilômetro", "económico" to "econômico", "económica" to "econômica",
        "económicos" to "econômicos", "eletrónico" to "eletrônico", "electrónico" to "eletrônico", "eletrónica" to "eletrônica",
        "prémio" to "prêmio", "prémios" to "prêmios", "anónimo" to "anônimo", "autónomo" to "autônomo", "fenómeno" to "fenômeno",
        "bebé" to "bebê", "género" to "gênero", "objecto" to "objeto", "direcção" to "direção", "actual" to "atual", "actualizar" to "atualizar",
        "telefónico" to "telefônico", "telefónica" to "telefônica", "pequeno-almoço" to "café da manhã",
    )
    private val phrases: List<Pair<String, String>> = listOf(
        // (ecrã is masculine and tela feminine; portagem feminine and pedágio masculine: the little words change with them)
        "no ecrã" to "na tela", "do ecrã" to "da tela", "ao ecrã" to "à tela", "pelo ecrã" to "pela tela", "este ecrã" to "esta tela",
        "esse ecrã" to "essa tela", "um ecrã" to "uma tela", "o ecrã" to "a tela", "ecrã inteiro" to "tela cheia",
        "nos ecrãs" to "nas telas", "dos ecrãs" to "das telas", "os ecrãs" to "as telas",
        "na portagem" to "no pedágio", "da portagem" to "do pedágio", "a portagem" to "o pedágio", "uma portagem" to "um pedágio",
        "nas portagens" to "nos pedágios", "das portagens" to "dos pedágios", "as portagens" to "os pedágios",
        "bombas de gasolina" to "postos de gasolina", "bomba de gasolina" to "posto de gasolina",
        "casas de banho" to "banheiros", "casa de banho" to "banheiro",
        // ("a seguir" is "next" in Portugal: "em seguida" in Brazil. Only after "keeps following" has become "continua seguindo")
        "a seguir" to "em seguida",
    )
    private val letter = "A-Za-zÀ-ÖØ-öø-ÿ"
    // (whole words only: "a portagem" must not match inside "para portagem")
    private val phraseRx: List<Pair<Regex, String>> by lazy { phrases.map { (eu, br) -> Regex("(?<![$letter-])(?i:${Regex.escape(eu)})(?![$letter-])") to br } }
    private val wordRx: List<Pair<Regex, String>> = words.map { (eu, br) -> Regex("(?<![$letter-])(?i:${Regex.escape(eu)})(?![$letter-])") to br }
    // "está a calcular" -> "está calculando"; "A calcular…" at the start -> "Calculando…"
    private val progressive = Regex("\\b(está|estão|estou|estamos|continua|continuam|fica|ficam) a ([$letter]+?)(a|e|i)r\\b")
    // (not "A partir de", which is "from": the same in Brazil)
    private val startProgressive = Regex("^A (?!partir\\b)([$letter]+?)(a|e|i)r\\b")
    // "o seu destino" -> "seu destino" (Brazil usually drops the article), but "do seu" / "no seu" stay as they are
    private val possessive = Regex("(?<![$letter])(?:o|a|os|as) (seu|sua|seus|suas)\\b")

    /** One European Portuguese phrase in Brazilian Portuguese. */
    fun convert(s: String): String {
        var out = s
        out = progressive.replace(out) { m -> "${m.groupValues[1]} ${m.groupValues[2]}${m.groupValues[3]}ndo" }
        for ((rx, br) in phraseRx) out = rx.replace(out) { m -> if (m.value.first().isUpperCase()) br.replaceFirstChar { it.uppercase() } else br }
        for ((rx, br) in wordRx) out = rx.replace(out) { m -> if (m.value.first().isUpperCase()) br.replaceFirstChar { it.uppercase() } else br }
        out = startProgressive.replace(out) { m -> (m.groupValues[1] + m.groupValues[2] + "ndo").replaceFirstChar { it.uppercase() } }
        out = possessive.replace(out) { m -> val w = m.groupValues[1]; if (m.range.first == 0) w.replaceFirstChar { it.uppercase() } else w }
        return out
    }
}
