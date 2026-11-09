package com.headunit.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shop and brand logos for search results and the "all locations" map. A place's logo is the small icon its own website uses,
 * found from the website the search gave (TomTom and Mapbox often have one), or for well-known chains from the list below even
 * when the result has none. Fetched once, then kept on the unit (a failed look-up is remembered for a week, so it isn't retried
 * on every search). Nothing is fetched in offline mode or without a signal: the usual icon shows instead.
 */
object Logos {
    // well-known chains (UK first, then Europe and beyond) -> their website, for results that come without one
    private val chains: Map<String, String> = mapOf(
        // supermarkets and shops
        "lidl" to "lidl.co.uk", "aldi" to "aldi.co.uk", "tesco" to "tesco.com", "sainsburys" to "sainsburys.co.uk", "asda" to "asda.com",
        "morrisons" to "morrisons.com", "waitrose" to "waitrose.com", "coop" to "coop.co.uk", "co op" to "coop.co.uk", "iceland" to "iceland.co.uk",
        "marks spencer" to "marksandspencer.com", "m s" to "marksandspencer.com", "spar" to "spar.co.uk", "costcutter" to "costcutter.co.uk",
        "farmfoods" to "farmfoods.co.uk", "home bargains" to "homebargains.co.uk", "b m" to "bmstores.co.uk", "poundland" to "poundland.co.uk",
        "boots" to "boots.com", "superdrug" to "superdrug.com", "argos" to "argos.co.uk", "currys" to "currys.co.uk", "ikea" to "ikea.com",
        "b q" to "diy.com", "screwfix" to "screwfix.com", "toolstation" to "toolstation.com", "wickes" to "wickes.co.uk", "halfords" to "halfords.com",
        "primark" to "primark.com", "next" to "next.co.uk", "costco" to "costco.co.uk", "the range" to "therange.co.uk", "dunelm" to "dunelm.com",
        "carrefour" to "carrefour.com", "auchan" to "auchan.fr", "leclerc" to "e.leclerc", "intermarche" to "intermarche.com", "rewe" to "rewe.de",
        "edeka" to "edeka.de", "kaufland" to "kaufland.de", "penny" to "penny.de", "netto" to "netto-online.de", "albert heijn" to "ah.nl",
        "jumbo" to "jumbo.com", "mercadona" to "mercadona.es", "dia" to "dia.es", "esselunga" to "esselunga.it", "conad" to "conad.it",
        "pingo doce" to "pingodoce.pt", "continente" to "continente.pt", "biedronka" to "biedronka.pl", "zabka" to "zabka.pl",
        "walmart" to "walmart.com", "target" to "target.com",
        // fuel
        "shell" to "shell.co.uk", "bp" to "bp.com", "esso" to "esso.co.uk", "texaco" to "texaco.com", "jet" to "jetlocal.co.uk",
        "gulf" to "gulfoil.com", "total" to "totalenergies.com", "totalenergies" to "totalenergies.com", "applegreen" to "applegreen.com",
        "murco" to "murco.co.uk", "valero" to "valero.com", "aral" to "aral.de", "repsol" to "repsol.com", "cepsa" to "cepsa.com",
        "galp" to "galp.com", "eni" to "eni.com", "q8" to "q8.it", "orlen" to "orlen.pl", "circle k" to "circlek.com", "omv" to "omv.com",
        // EV charging
        "tesla" to "tesla.com", "tesla supercharger" to "tesla.com", "ionity" to "ionity.eu", "gridserve" to "gridserve.com",
        "instavolt" to "instavolt.co.uk", "pod point" to "pod-point.com", "osprey" to "ospreycharging.co.uk", "fastned" to "fastned.nl",
        "bp pulse" to "bppulse.co.uk", "shell recharge" to "shell.co.uk", "mer" to "mer.eco", "allego" to "allego.eu",
        // food and coffee
        "mcdonalds" to "mcdonalds.com", "kfc" to "kfc.co.uk", "burger king" to "burgerking.co.uk", "subway" to "subway.com",
        "greggs" to "greggs.co.uk", "costa" to "costa.co.uk", "costa coffee" to "costa.co.uk", "starbucks" to "starbucks.com",
        "pret" to "pret.co.uk", "pret a manger" to "pret.co.uk", "nandos" to "nandos.co.uk", "dominos" to "dominos.co.uk",
        "pizza hut" to "pizzahut.co.uk", "wendys" to "wendys.com", "five guys" to "fiveguys.co.uk", "taco bell" to "tacobell.com",
        "caffe nero" to "caffenero.com", "tim hortons" to "timhortons.co.uk", "wetherspoon" to "jdwetherspoon.com", "toby carvery" to "tobycarvery.co.uk",
        "harvester" to "harvester.co.uk", "frankie bennys" to "frankieandbennys.com", "wagamama" to "wagamama.com", "leon" to "leon.co",
        // hotels and services
        "premier inn" to "premierinn.com", "travelodge" to "travelodge.co.uk", "holiday inn" to "ihg.com", "moto" to "moto-way.com",
        "welcome break" to "welcomebreak.co.uk", "roadchef" to "roadchef.com", "extra" to "extraservices.co.uk",
        // other
        "post office" to "postoffice.co.uk", "royal mail" to "royalmail.com", "kwik fit" to "kwik-fit.com", "national tyres" to "national.co.uk",
    )

    // everyday words too: these only count when they're the whole name ("Next", not "Next Door Cafe")
    private val exactOnly = setOf("next", "extra", "target", "jet", "mer", "dia", "leon", "total", "penny", "moto", "spar", "eni", "mer", "boots", "iceland", "gulf", "subway", "costa", "netto", "jumbo")

    /** "Sainsbury's Local" -> "sainsburys local": letters and numbers only, for matching names to each other. */
    fun norm(s: String): String =
        s.lowercase().replace("&", " ").replace("'", "").replace("’", "")
            .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") }
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The website to take a place's logo from: its own, or a known chain's (by the start of its name). Null when there's none. */
    fun domainFor(p: Place): String? {
        host(p.web)?.let { return it }
        val n = norm(p.name)
        if (n.isEmpty()) return null
        // the longest chain name the place's name starts with ("bp pulse" before "bp")
        return chains.entries.filter { n == it.key || (it.key !in exactOnly && n.startsWith(it.key + " ")) }.maxByOrNull { it.key.length }?.value
    }

    private fun host(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val u = if (url.contains("://")) url else "https://$url"
            URL(u).host.lowercase().removePrefix("www.").takeIf { it.contains('.') }
        } catch (e: Exception) { null }
    }

    private val mem = android.util.LruCache<String, Bitmap>(80)
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

    /** A logo already loaded (or none): never waits, for drawing straight away. */
    fun cached(domain: String?): Bitmap? = domain?.let { mem.get(it) }

    /**
     * The logo for [domain], from the unit's store or fetched (needs a signal). Blocking: call it off the main thread. Null when
     * there isn't a usable one (a blank or tiny icon is treated as none, so the usual icon shows).
     */
    fun load(ctx: Context, domain: String?): Bitmap? {
        if (domain.isNullOrBlank()) return null
        mem.get(domain)?.let { return it }
        if (domain in missing) return null
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        val file = File(dir, domain.replace(Regex("[^a-z0-9.-]"), "_") + ".png")
        val none = File(dir, file.name + ".none")
        if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { mem.put(domain, it); return it }
        // looked for within the last week and there wasn't one
        if (none.exists() && System.currentTimeMillis() - none.lastModified() < 7 * 86_400_000L) { missing += domain; return null }
        if (Net.offline) return null
        return try {
            val c = URL("https://www.google.com/s2/favicons?domain=" + java.net.URLEncoder.encode(domain, "UTF-8") + "&sz=128")
                .also { Net.check() }.openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 7000
            c.setRequestProperty("User-Agent", "HorizonNav/1.0")
            val bytes = if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            // the service gives a tiny generic globe when it has nothing: that isn't a logo
            if (bmp == null || bmp.width < 32 || bmp.height < 32) {
                none.writeText(""); missing += domain; null
            } else {
                try { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { }
                mem.put(domain, bmp); bmp
            }
        } catch (e: Exception) { null }
    }
}
