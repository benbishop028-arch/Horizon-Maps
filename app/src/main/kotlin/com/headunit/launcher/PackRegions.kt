package com.headunit.launcher

import java.net.HttpURLConnection
import java.net.URL

/**
 * The areas an offline pack can be downloaded for. Each pack is built (once, on GitHub) from the matching Geofabrik extract of
 * OpenStreetMap and published as a release called "pack-<id>". England & Wales and Great Britain keep their original releases.
 *
 * The id comes from the Geofabrik path with the continent dropped: europe/france → "france", north-america/us/georgia → "us-georgia".
 * The "Build country packs" workflow uses exactly the same rule, so the app always knows where a pack will be.
 */
data class PackRegion(val id: String, val name: String, val group: String, val path: String, val country: String) {
    // the original UK packs live in the HeadUnitOrbis repo; every other area is built in the Horizon-Maps repo
    val url: String get() = if (id == "ew" || id == "gb") "$RELEASES/pack-$id" else "$COUNTRY_RELEASES/pack-$id"
    /** Where the speed limit and camera files are: the same release, except the UK packs, whose extras are built in Horizon-Maps. */
    val extrasUrl: String get() = "$COUNTRY_RELEASES/pack-$id"
}

const val RELEASES = "https://github.com/benbishop028-arch/HeadUnitOrbis/releases/download"
const val COUNTRY_RELEASES = "https://github.com/benbishop028-arch/Horizon-Maps/releases/download"

object PackRegions {
    fun idOf(path: String): String {
        val parts = path.trim().trim('/').split('/')
        return (if (parts.size > 1) parts.drop(1) else parts).joinToString("-")
    }

    private fun r(group: String, path: String, name: String, cc: String) = PackRegion(idOf(path), name, group, path, cc)

    val groups = listOf("UK & Ireland", "Europe", "United States", "Canada", "Mexico & Central America", "South America", "Asia", "Africa", "Oceania")

    val all: List<PackRegion> = buildList {
        // the original UK packs (built by the "Build offline region pack" workflow)
        add(PackRegion("ew", "England & Wales", "UK & Ireland", "europe/great-britain", "GB"))
        // the rest of the UK is built like every other area (in Horizon-Maps), straight from Geofabrik's UK extracts
        add(r("UK & Ireland", "europe/great-britain", "Great Britain (England, Scotland & Wales)", "GB"))
        add(r("UK & Ireland", "europe/united-kingdom/england", "England", "GB"))
        add(r("UK & Ireland", "europe/united-kingdom/scotland", "Scotland", "GB"))
        add(r("UK & Ireland", "europe/united-kingdom/wales", "Wales", "GB"))
        add(r("UK & Ireland", "europe/ireland-and-northern-ireland", "Ireland & Northern Ireland", "IE"))
        add(r("UK & Ireland", "europe/isle-of-man", "Isle of Man", "IM"))
        add(r("UK & Ireland", "europe/guernsey-jersey", "Guernsey & Jersey", "GG"))
        val eu = listOf(
            "albania" to ("Albania" to "AL"), "andorra" to ("Andorra" to "AD"), "austria" to ("Austria" to "AT"), "belarus" to ("Belarus" to "BY"),
            "belgium" to ("Belgium" to "BE"), "bosnia-herzegovina" to ("Bosnia & Herzegovina" to "BA"), "bulgaria" to ("Bulgaria" to "BG"),
            "croatia" to ("Croatia" to "HR"), "cyprus" to ("Cyprus" to "CY"), "czech-republic" to ("Czechia" to "CZ"), "denmark" to ("Denmark" to "DK"),
            "estonia" to ("Estonia" to "EE"), "faroe-islands" to ("Faroe Islands" to "FO"), "finland" to ("Finland" to "FI"), "france" to ("France" to "FR"),
            "georgia" to ("Georgia" to "GE"), "germany" to ("Germany" to "DE"), "greece" to ("Greece" to "GR"), "hungary" to ("Hungary" to "HU"),
            "iceland" to ("Iceland" to "IS"), "italy" to ("Italy" to "IT"), "kosovo" to ("Kosovo" to "XK"), "latvia" to ("Latvia" to "LV"),
            "liechtenstein" to ("Liechtenstein" to "LI"), "lithuania" to ("Lithuania" to "LT"), "luxembourg" to ("Luxembourg" to "LU"),
            "macedonia" to ("North Macedonia" to "MK"), "malta" to ("Malta" to "MT"), "moldova" to ("Moldova" to "MD"), "monaco" to ("Monaco" to "MC"),
            "montenegro" to ("Montenegro" to "ME"), "netherlands" to ("Netherlands" to "NL"), "norway" to ("Norway" to "NO"), "poland" to ("Poland" to "PL"),
            "portugal" to ("Portugal" to "PT"), "romania" to ("Romania" to "RO"), "serbia" to ("Serbia" to "RS"), "slovakia" to ("Slovakia" to "SK"),
            "slovenia" to ("Slovenia" to "SI"), "spain" to ("Spain" to "ES"), "sweden" to ("Sweden" to "SE"), "switzerland" to ("Switzerland" to "CH"),
            "turkey" to ("Turkey" to "TR"), "ukraine" to ("Ukraine" to "UA"),
        )
        for ((p, nc) in eu) add(r("Europe", "europe/$p", nc.first, nc.second))
        add(r("Europe", "europe/azores", "Azores", "PT"))
        add(r("Europe", "russia", "Russia", "RU"))
        val states = listOf(
            "alabama", "alaska", "arizona", "arkansas", "california", "colorado", "connecticut", "delaware", "district-of-columbia", "florida",
            "georgia", "hawaii", "idaho", "illinois", "indiana", "iowa", "kansas", "kentucky", "louisiana", "maine", "maryland", "massachusetts",
            "michigan", "minnesota", "mississippi", "missouri", "montana", "nebraska", "nevada", "new-hampshire", "new-jersey", "new-mexico",
            "new-york", "north-carolina", "north-dakota", "ohio", "oklahoma", "oregon", "pennsylvania", "puerto-rico", "rhode-island",
            "south-carolina", "south-dakota", "tennessee", "texas", "utah", "vermont", "virginia", "washington", "west-virginia", "wisconsin", "wyoming",
        )
        for (s in states) add(r("United States", "north-america/us/$s", title(s), "US"))
        val provinces = listOf(
            "alberta", "british-columbia", "manitoba", "new-brunswick", "newfoundland-and-labrador", "northwest-territories", "nova-scotia",
            "nunavut", "ontario", "prince-edward-island", "quebec", "saskatchewan", "yukon",
        )
        for (s in provinces) add(r("Canada", "north-america/canada/$s", title(s), "CA"))
        add(r("Mexico & Central America", "north-america/mexico", "Mexico", "MX"))
        add(r("Mexico & Central America", "central-america", "Central America (all)", ""))
        val ca = listOf(
            "bahamas" to ("Bahamas" to "BS"), "belize" to ("Belize" to "BZ"), "costa-rica" to ("Costa Rica" to "CR"), "cuba" to ("Cuba" to "CU"),
            "el-salvador" to ("El Salvador" to "SV"), "guatemala" to ("Guatemala" to "GT"), "haiti-and-domrep" to ("Haiti & Dominican Republic" to "DO"),
            "honduras" to ("Honduras" to "HN"), "jamaica" to ("Jamaica" to "JM"), "nicaragua" to ("Nicaragua" to "NI"), "panama" to ("Panama" to "PA"),
        )
        for ((p, nc) in ca) add(r("Mexico & Central America", "central-america/$p", nc.first, nc.second))
        val sa = listOf(
            "argentina" to ("Argentina" to "AR"), "bolivia" to ("Bolivia" to "BO"), "brazil" to ("Brazil" to "BR"), "chile" to ("Chile" to "CL"),
            "colombia" to ("Colombia" to "CO"), "ecuador" to ("Ecuador" to "EC"), "paraguay" to ("Paraguay" to "PY"), "peru" to ("Peru" to "PE"),
            "suriname" to ("Suriname" to "SR"), "guyana" to ("Guyana" to "GY"), "uruguay" to ("Uruguay" to "UY"), "venezuela" to ("Venezuela" to "VE"),
        )
        for ((p, nc) in sa) add(r("South America", "south-america/$p", nc.first, nc.second))
        val asia = listOf(
            "afghanistan" to ("Afghanistan" to "AF"), "armenia" to ("Armenia" to "AM"), "azerbaijan" to ("Azerbaijan" to "AZ"), "bangladesh" to ("Bangladesh" to "BD"),
            "bhutan" to ("Bhutan" to "BT"), "cambodia" to ("Cambodia" to "KH"), "china" to ("China" to "CN"), "east-timor" to ("East Timor" to "TL"),
            "gcc-states" to ("Gulf States (UAE, Saudi Arabia, Qatar…)" to "AE"), "india" to ("India" to "IN"), "indonesia" to ("Indonesia" to "ID"),
            "iran" to ("Iran" to "IR"), "iraq" to ("Iraq" to "IQ"), "israel-and-palestine" to ("Israel & Palestine" to "IL"), "japan" to ("Japan" to "JP"),
            "jordan" to ("Jordan" to "JO"), "kazakhstan" to ("Kazakhstan" to "KZ"), "kyrgyzstan" to ("Kyrgyzstan" to "KG"), "laos" to ("Laos" to "LA"),
            "lebanon" to ("Lebanon" to "LB"), "malaysia-singapore-brunei" to ("Malaysia, Singapore & Brunei" to "MY"), "maldives" to ("Maldives" to "MV"),
            "mongolia" to ("Mongolia" to "MN"), "myanmar" to ("Myanmar" to "MM"), "nepal" to ("Nepal" to "NP"), "north-korea" to ("North Korea" to "KP"),
            "pakistan" to ("Pakistan" to "PK"), "philippines" to ("Philippines" to "PH"), "south-korea" to ("South Korea" to "KR"), "sri-lanka" to ("Sri Lanka" to "LK"),
            "syria" to ("Syria" to "SY"), "taiwan" to ("Taiwan" to "TW"), "tajikistan" to ("Tajikistan" to "TJ"), "thailand" to ("Thailand" to "TH"),
            "turkmenistan" to ("Turkmenistan" to "TM"), "uzbekistan" to ("Uzbekistan" to "UZ"), "vietnam" to ("Vietnam" to "VN"), "yemen" to ("Yemen" to "YE"),
        )
        for ((p, nc) in asia) add(r("Asia", "asia/$p", nc.first, nc.second))
        val africa = listOf(
            "algeria" to ("Algeria" to "DZ"), "angola" to ("Angola" to "AO"), "benin" to ("Benin" to "BJ"), "botswana" to ("Botswana" to "BW"),
            "burkina-faso" to ("Burkina Faso" to "BF"), "burundi" to ("Burundi" to "BI"), "cameroon" to ("Cameroon" to "CM"), "canary-islands" to ("Canary Islands" to "ES"),
            "cape-verde" to ("Cape Verde" to "CV"), "central-african-republic" to ("Central African Republic" to "CF"), "chad" to ("Chad" to "TD"),
            "comores" to ("Comoros" to "KM"), "congo-brazzaville" to ("Congo (Brazzaville)" to "CG"), "congo-democratic-republic" to ("Congo (Kinshasa)" to "CD"),
            "djibouti" to ("Djibouti" to "DJ"), "egypt" to ("Egypt" to "EG"), "equatorial-guinea" to ("Equatorial Guinea" to "GQ"), "eritrea" to ("Eritrea" to "ER"),
            "ethiopia" to ("Ethiopia" to "ET"), "gabon" to ("Gabon" to "GA"), "ghana" to ("Ghana" to "GH"), "guinea" to ("Guinea" to "GN"),
            "guinea-bissau" to ("Guinea-Bissau" to "GW"), "ivory-coast" to ("Ivory Coast" to "CI"), "kenya" to ("Kenya" to "KE"), "lesotho" to ("Lesotho" to "LS"),
            "liberia" to ("Liberia" to "LR"), "libya" to ("Libya" to "LY"), "madagascar" to ("Madagascar" to "MG"), "malawi" to ("Malawi" to "MW"),
            "mali" to ("Mali" to "ML"), "mauritania" to ("Mauritania" to "MR"), "mauritius" to ("Mauritius" to "MU"), "morocco" to ("Morocco" to "MA"),
            "mozambique" to ("Mozambique" to "MZ"), "namibia" to ("Namibia" to "NA"), "niger" to ("Niger" to "NE"), "nigeria" to ("Nigeria" to "NG"),
            "rwanda" to ("Rwanda" to "RW"), "saint-helena-ascension-and-tristan-da-cunha" to ("St Helena, Ascension & Tristan da Cunha" to "SH"),
            "sao-tome-and-principe" to ("São Tomé & Príncipe" to "ST"), "senegal-and-gambia" to ("Senegal & Gambia" to "SN"), "seychelles" to ("Seychelles" to "SC"),
            "sierra-leone" to ("Sierra Leone" to "SL"), "somalia" to ("Somalia" to "SO"), "south-africa" to ("South Africa" to "ZA"), "south-sudan" to ("South Sudan" to "SS"),
            "sudan" to ("Sudan" to "SD"), "swaziland" to ("Eswatini" to "SZ"), "tanzania" to ("Tanzania" to "TZ"), "togo" to ("Togo" to "TG"),
            "tunisia" to ("Tunisia" to "TN"), "uganda" to ("Uganda" to "UG"), "zambia" to ("Zambia" to "ZM"), "zimbabwe" to ("Zimbabwe" to "ZW"),
        )
        for ((p, nc) in africa) add(r("Africa", "africa/$p", nc.first, nc.second))
        val oceania = listOf(
            "australia" to ("Australia" to "AU"), "new-zealand" to ("New Zealand" to "NZ"), "american-oceania" to ("American Oceania (Guam, Samoa…)" to "GU"),
            "cook-islands" to ("Cook Islands" to "CK"), "fiji" to ("Fiji" to "FJ"), "kiribati" to ("Kiribati" to "KI"), "marshall-islands" to ("Marshall Islands" to "MH"),
            "micronesia" to ("Micronesia" to "FM"), "nauru" to ("Nauru" to "NR"), "new-caledonia" to ("New Caledonia" to "NC"), "niue" to ("Niue" to "NU"),
            "palau" to ("Palau" to "PW"), "papua-new-guinea" to ("Papua New Guinea" to "PG"), "polynesie-francaise" to ("French Polynesia" to "PF"),
            "samoa" to ("Samoa" to "WS"), "solomon-islands" to ("Solomon Islands" to "SB"), "tonga" to ("Tonga" to "TO"), "tuvalu" to ("Tuvalu" to "TV"),
            "vanuatu" to ("Vanuatu" to "VU"), "wallis-et-futuna" to ("Wallis & Futuna" to "WF"),
        )
        for ((p, nc) in oceania) add(r("Oceania", "australia-oceania/$p", nc.first, nc.second))
    }

    private fun title(s: String) = s.split('-').joinToString(" ") { w -> if (w == "of" || w == "and") w else w.replaceFirstChar { it.uppercase() } }

    /** The area with this id; one that isn't in the list (built by hand) gets its id as its name. */
    fun byId(id0: String): PackRegion = (if (id0 == "gb") "great-britain" else id0).let { id -> all.firstOrNull { it.id == id }
        ?: PackRegion(id, id.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }, "Other", "", "") }

    /** The best guess at a pack for the country you're in (GB → England & Wales). */
    fun suggestFor(cc: String): PackRegion? = when (cc.uppercase()) {
        "", "US", "CA" -> null          // these are split by state / province: let the person pick
        "GB" -> byId("ew")
        else -> all.firstOrNull { it.country.equals(cc, true) }
    }

    /**
     * Whether the pack for [r] has been built and published yet: its routing, map and search files are all there (they are built
     * side by side, so one can arrive before the others). Returns null when it couldn't be checked (no signal).
     */
    /** Whether the speed limit file has been published for [r] (so an older download can pick it up with Update). Null: couldn't check. */
    fun hasExtras(r: PackRegion): Boolean? = try {
        val c = URL("${r.extrasUrl}/limits.bin").openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = true
        val ok = c.responseCode in 200..299
        c.disconnect()
        ok
    } catch (e: Exception) { null }

    /**
     * Whether newer map data has been published for [r] than what's in [d] (the downloaded area's folder): a rebuilt pack, or speed
     * limit / camera files that are new or have been redone since. Null when it couldn't be checked (no signal).
     */
    fun newerOnline(r: PackRegion, d: java.io.File): Boolean? = try {
        fun head(u: String): Long? {
            val c = URL(u).openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"; c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = true
            val ok = c.responseCode in 200..299
            val lm = c.lastModified
            c.disconnect()
            return if (ok) lm else null
        }
        var newer = false
        // the routing data (rebuilt with the rest of the pack): published well after this copy was downloaded
        val tiles = java.io.File(d, "tiles.tar")
        val t = head("${r.url}/tiles.tar") ?: head("${r.url}/tiles.tar.part00")
        if (t != null && t > 0L && tiles.exists() && t > tiles.lastModified() + 3_600_000L) newer = true
        // speed limits and cameras: new for this area, or redone since they were downloaded
        if (!newer) for (name in listOf("limits.bin", "cameras.tsv")) {
            val h = head("${r.extrasUrl}/$name") ?: continue
            val f = java.io.File(d, name)
            if (!f.exists() || (h > 0L && h > f.lastModified() + 600_000L)) { newer = true; break }
        }
        newer
    } catch (e: Exception) { null }

    fun available(r: PackRegion): Boolean? = try {
        fun has(name: String): Boolean {
            val c = URL("${r.url}/$name").openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"; c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = true
            val ok = c.responseCode in 200..299
            c.disconnect()
            return ok
        }
        // the original UK packs came from the older workflow, where the map was optional: the routing files are enough there
        if (r.id == "ew" || r.id == "gb") has("valhalla.json")
        else has("valhalla.json") && (has("map.pmtiles") || has("map.pmtiles.part00")) && (has("places.tsv") || has("places.tsv.part00"))
    } catch (e: Exception) { null }
}
