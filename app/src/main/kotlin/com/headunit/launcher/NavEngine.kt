package com.headunit.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class NavEngine(private val ctx: Context, val prefs: Prefs, val map: MapBinding) : LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Place searches run here, apart from everything else, so one stuck on a slow server can just be left behind. */
    private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- observable state ----
    var fix by mutableStateOf<Fix?>(null)
    var routes by mutableStateOf<List<Route>>(emptyList())
    var routeFrac by mutableStateOf(0f)
    var active by mutableIntStateOf(0)
    var dest by mutableStateOf<Place?>(null)
    var turn by mutableStateOf<TurnInfo?>(null)
    var eta by mutableStateOf<EtaInfo?>(null)
    var following by mutableStateOf(true)
    // (remembered: it used to start in 3D every time, even if you'd left it in 2D)
    var tilt3d by mutableStateOf(prefs.b(PreferenceKeys.VIEW_3D, true))
    var simMode by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var previewing by mutableStateOf(false)
    var northUp by mutableStateOf(false)
    var results by mutableStateOf<List<Place>>(emptyList())
    var alternatives by mutableStateOf(false)
    var weather by mutableStateOf<Weather.Now?>(null)
    var zoomOffset by mutableDoubleStateOf(0.0)
    /** Your own normal zoom when not on a route (Settings > Map look), in levels from the standard one. */
    val defaultZoom: Double get() = (prefs.s(PreferenceKeys.DEFAULT_ZOOM, "0").toDoubleOrNull() ?: 0.0).coerceIn(-2.0, 2.0)
    /** The flat traffic view after being stopped a while is showing. */
    var stoppedOverview by mutableStateOf(false)
    private var stoppedSince = 0L
    /** A place sent from your phone, waiting to be started (head unit). */
    var phoneDest by mutableStateOf<Place?>(null)
    /** Additional auto-zoom adjustment for Android Auto's usable viewport. Negative values zoom out
     * on compact or narrow displays, where the same camera zoom shows too little road ahead. */
    @Volatile private var carViewportZoomOffset = 0.0

    /** Recalculate the camera's automatic zoom for the actual Android Auto display, not just its pixel count.
     * Inputs are surface pixels and the display's DPI. The short edge controls the base adjustment and
     * aspect ratio adds a smaller correction for unusually narrow/tall or very wide screens. */
    fun setCarViewport(widthPx: Int, heightPx: Int, dpi: Int) {
        if (!isCar || widthPx <= 0 || heightPx <= 0 || dpi <= 0) return
        // Keep viewport sizing as a pure policy so its boundaries can be unit-tested without a map SDK.
        carViewportZoomOffset = NavigationPolicy.carViewportZoomOffset(widthPx, heightPx, dpi)
    }

    // saved places, departure planner, road info
    val places = PlaceStore(prefs)
    val trips = TripStore(prefs)
    var pendingSave by mutableStateOf<String?>(null)
    var departMin by mutableIntStateOf(0)
    var departAtMs by mutableLongStateOf(0L)
    var speedLimit by mutableStateOf<Double?>(null)     // m/s
    var limitEstimated by mutableStateOf(false)
    private var limitOkAt = 0L
    private var limitOkPos: LL? = null
    private var limitFails = 0
    var cameraDist by mutableStateOf<Int?>(null)
    var incidentAhead by mutableStateOf<IncidentAhead?>(null)
    private var incAt = 0L
    private var incMatches: List<Triple<Incident, Double, String>> = emptyList()
    private var routeCams: List<Pair<LL, Double>> = emptyList()
    private var camsRoute: Route? = null
    private var camsAt = 0L
    private var hazardSig = 0
    private var hzProgress = 0.0
    private var policeSig = 0
    private val policeAlerted = HashSet<Long>()
    private val incAlerted = HashSet<String>()
    var nearby by mutableStateOf<List<Nearby>>(emptyList())
    var nearbyKind by mutableStateOf<String?>(null)
    var nearbyBusy by mutableStateOf(false)

    // trip computer (real GPS only)
    var tripDist by mutableDoubleStateOf(0.0)
    var tripMax by mutableDoubleStateOf(0.0)
    var tripMoving by mutableDoubleStateOf(0.0)
    var tripStart by mutableStateOf(System.currentTimeMillis())
    /** Where the trip being driven started (for "Route back" on the arrival card). */
    var tripFrom: LL? = null
    /** A short, quiet piece of advice for the conditions ("Heavy rain · take it steady"), or null. */
    var advice by mutableStateOf<String?>(null)
    private var adviceAt = 0L

    /** Flying over the chosen route in 3D (Fly over route): the start countdown waits while it plays. */
    var flyingOver by mutableStateOf(false)

    /** A 3D flight along the whole of the chosen route, then back to the overview. Touching the map stops it. */
    fun flyOver() {
        val r = routes.getOrNull(active) ?: return
        if (!previewing) return
        flyingOver = true
        val allPts = routes.take(3).flatMap { x -> x.pts.filterIndexed { i, _ -> i % 8 == 0 } + x.pts.last() }
        map.flyRoute(r.pts, r.cum, r.len, 62.0, full = true) { flyingOver = false; if (previewing) map.fit(allPts) }
    }

    /** The way back to where this trip started (home when it started near home). */
    fun routeBack() {
        val from = tripFrom ?: return
        val home = places.slot("home")
        val name = if (home != null && Geo.dist(home.pos, from) < 250.0) home.name.ifBlank { "Home" } else "Where you started"
        dismissArrival()
        startRoute(Place(name, "", if (home != null && Geo.dist(home.pos, from) < 250.0) home.pos else from))
    }

    // extras
    val habits = HabitStore(prefs)
    var overLimit by mutableStateOf(false)
    var via by mutableStateOf<List<Place>>(emptyList())
    var stopMode by mutableStateOf(false)
    var destRain by mutableStateOf<String?>(null)
    var routeWx by mutableStateOf<List<RouteWx>>(emptyList())
    var arriveFlash by mutableStateOf(false)
    var arrival by mutableStateOf<ArrivalInfo?>(null)
    /** A quicker route than the one being followed, found while driving. */
    class FasterRoute(val route: Route, val savedMin: Int)
    var faster by mutableStateOf<FasterRoute?>(null)
    var liveAlts by mutableStateOf<List<LiveAlt>>(emptyList())
    var radar by mutableStateOf<List<RadarItem>>(emptyList())
    private var radarSig = 0
    private var bendsFor: Route? = null
    private var bends: List<Pair<Double, Int>> = emptyList()
    var prediction by mutableStateOf<Prediction?>(null)
    private var predictionAt = 0L
    var arrivalParking by mutableStateOf<Nearby?>(null)
    /** Roads driven on this trip, counted into the familiar-roads list when the trip ends. */
    private val tripRoads = HashSet<String>()
    private val roadFam: HashMap<String, Int> by lazy {
        val m = HashMap<String, Int>()
        try { val o = org.json.JSONObject(prefs.s(PreferenceKeys.ROADFAM, "{}")); for (k in o.keys()) m[k] = o.optInt(k) } catch (e: Exception) { }
        m
    }
    private var lastFasterCheck = 0L
    /** Destination of a trip that was still running when the app last closed. */
    var resume by mutableStateOf<Place?>(null)
    private var navStartMs = 0L
    private var navStartDist = 0.0
    var brakeFlash by mutableStateOf(false)
    /** True for a few seconds after the speed limit changes, so the sign can pulse. */
    var limitPulse by mutableStateOf(false)
    /** The alert banner currently on screen (camera, speed limit, incident, rain, break). */
    var alert by mutableStateOf<AlertInfo?>(null)
    private var alertToken = 0

    // ---- route extras: average speed checks, services, bumps, schools, zones, the route bar ----
    /** The average speed check you are in (or about to enter), or null. */
    var avgInfo by mutableStateOf<AvgInfo?>(null)
    /** The next motorway services ahead on the route (up to two). */
    var servicesAhead by mutableStateOf<List<ServiceAhead>>(emptyList())
    /** Markers for the route bar, and how far along the route you are (updated about once a second). */
    var barEvents by mutableStateOf<List<BarEvent>>(emptyList())
    var barProgress by mutableDoubleStateOf(0.0)
    /** Clean air / low emission zones the chosen route goes into (shown on the route options). */
    var routeZones by mutableStateOf<List<String>>(emptyList())
    /** True while the GPS has dropped out (a tunnel) and the position is being estimated along the route. */
    var deadReckoning by mutableStateOf(false)
    /** Text shared from another app, to be put in the search box. */
    var externalQuery by mutableStateOf<String?>(null)
    /** Goes up each time another app hands over a destination (closes the settings screen). */
    var externalTick by mutableIntStateOf(0)
    /** The stops editor is open. */
    var stopsEditor by mutableStateOf(false)
    /** How the fuel list is sorted: "near" or "cheap". */
    var nearbySort by mutableStateOf("near")
    /** Name of the road you are on (route or map data), for the free-driving card. */
    var roadNow by mutableStateOf("")
    /** Roads you asked to avoid on this trip (as boxes). */
    private val sessionAvoid = ArrayList<Pair<LL, LL>>()

    /** A trip planned with "arrive by": where, when to be there, and when to leave (kept until it starts or is cancelled). */
    class PlannedTrip(val dest: Place, val arriveBy: Long, val leaveBy: Long)
    var planned by mutableStateOf<PlannedTrip?>(null)
    private var plannedJob: Job? = null
    /** A good place to fill up on a long trip (cheapest around the middle of the route, or the quickest detour). */
    var fuelSuggestion by mutableStateOf<Nearby?>(null)
    private var wrongCount = 0
    private var wrongAt = 0L

    private class Placed(
        val route: Route, val services: List<PoiOnRoute>, val serviceFuel: Map<RoutePoi, String>, val hz: List<PoiOnRoute>,
        val avg: List<AvgOnRoute>, val stops: List<Double>, val zones: List<Pair<String, Double>>,
    )
    private var rawServices: List<RoutePoi> = emptyList()
    private var servicesFor: Place? = null
    private var servicesBusy = false
    private var servicesRetryAt = 0L
    private var rawHz: List<RoutePoi> = emptyList()
    private var rawAvg: List<AvgDef> = emptyList()
    private var hzWinEnd = -1.0
    private var hzWinRoute: Route? = null
    private var hzBusy = false
    private var hzRetryAt = 0L
    private var hzLastAt = 0L
    private var placed: Placed? = null
    private var placing = false
    private var placedSig = 0
    private var zonesVersion = 0
    private var avgIn: AvgOnRoute? = null
    private var avgEnterAt = 0L
    private var avgEnterProg = 0.0
    private var avgOverWarned = false
    private val avgAlerted = HashSet<Long>()
    private val hzAlerted = HashSet<String>()
    private var lastBumpAt = 0L
    private val zoneAlerted = HashSet<String>()
    private var barSig = 0
    private var servicesSig = 0
    /** Moving at speed on a route: the map UI simplifies itself. */
    val driving: Boolean get() = route != null && (fix?.speed ?: 0.0) > 8.0
    private var lastSpd = 0.0
    private var lastSpdAt = 0L
    private var limitCandidate = -1
    private var candidateHits = 0
    private var announcedLimit = -1
    private var lastBeep = 0L
    private var breakNext = 7200L
    private var tripPts = ArrayList<LL>()

    val route: Route? get() = routes.getOrNull(active)
    val imperial: Boolean get() = prefs.s(PreferenceKeys.DISTANCE_UNIT, if (defaultUnits() == "imperial") "mi" else "km") == "mi"
    /** 0 = all sound, 1 = alerts only, 2 = off. Falls back to the old on/off voice setting. */
    val soundMode: Int get() = prefs.i(PreferenceKeys.SOUND_MODE, if (prefs.b(PreferenceKeys.VOICE, true)) 0 else 2).coerceIn(0, 2)
    val voiceOn: Boolean get() = soundMode != 2
    fun cycleSound() {
        val n = (soundMode + 1) % 3
        prefs.put(PreferenceKeys.SOUND_MODE, n)
        toast(listOf("Sound: all", "Sound: alerts only", "Sound: off")[n], 1800)
    }

    /** Miles in the UK and US (and the few others that use them), kilometres everywhere else; before the country is known, miles. */
    private fun defaultUnits(): String {
        val cc = detectedCountry.uppercase()
        return if (cc.isBlank() || cc in MILES_COUNTRIES) "imperial" else "metric"
    }

    val apiKey: String
        get() = prefs.s(PreferenceKeys.API_KEY, "").ifBlank { BuildConfig.TOMTOM_API_KEY }.trim()
    val hasKey: Boolean get() = apiKey.isNotEmpty() && !apiKey.startsWith("YOUR_")

    /** Your Mapbox access token (Settings > Keys & accounts), for the Mapbox map, routes, search and traffic. */
    val mbToken: String get() = MapboxRest.clean(prefs.s(PreferenceKeys.MAPBOX_TOKEN, ""))
    val hasMapbox: Boolean get() = MapboxRest.looksValid(mbToken) && refusedMb != mbToken
    /** A Mapbox token that Mapbox turned away: not used again until it's changed. (Screen state, so the map can say so.) */
    private var refusedMb: String? by mutableStateOf<String?>(null)
    /** The Mapbox token in Settings was turned down by Mapbox (401). */
    val mbKeyRefused: Boolean get() = refusedMb != null && refusedMb == mbToken

    /**
     * Who plans routes and does the place search and live traffic: "tomtom", "mapbox", or "" for none (then the offline pack and
     * OpenStreetMap). Settings choose; "auto" uses Mapbox when it's the map source, otherwise TomTom when there's a key, otherwise
     * Mapbox when there's a token.
     */
    val provider: String get() {
        val chosen = when (prefs.s(PreferenceKeys.DATAPROV, "auto")) {
            "tomtom" -> if (hasKey) "tomtom" else if (hasMapbox) "mapbox" else ""
            "mapbox" -> if (hasMapbox) "mapbox" else if (hasKey) "tomtom" else ""
            // (eco routes are TomTom's: Mapbox has none, so with Eco routes on and a TomTom key, TomTom plans them)
            else -> if (prefs.b(PreferenceKeys.ROUTE_ECO, false) && hasKey) "tomtom"
                else if (prefs.mapSource() == "mapbox" && hasMapbox) "mapbox" else if (hasKey) "tomtom" else if (hasMapbox) "mapbox" else ""
        }
        // the chosen one isn't working right now (its limit reached, the key refused, an outage): the other one meanwhile
        val now = System.currentTimeMillis()
        return when {
            chosen == "tomtom" && now < ttDownUntil && hasMapbox -> "mapbox"
            chosen == "mapbox" && now < mbDownUntil && hasKey -> "tomtom"
            else -> chosen
        }
    }

    /** Until when TomTom / Mapbox is skipped after failing (0 = working). */
    @Volatile private var ttDownUntil = 0L
    @Volatile private var mbDownUntil = 0L
    /** TomTom is being skipped for now (so the speed limits and incidents come from Mapbox too). */
    private fun ttDown() = System.currentTimeMillis() < ttDownUntil && hasMapbox

    /**
     * Runs [call] with the service in use, and if that service itself fails (not a lack of signal, and not "no route there"),
     * runs it again with the other one, which is then used for a while: an hour after a refused key or a reached limit, ten
     * minutes after an outage. Anyone with both a TomTom key and a Mapbox token is never left without routes or search.
     */
    private fun <T> withBackup(call: (String) -> T): T {
        val p = provider
        try { return call(p) }
        catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val m = e.message ?: ""
            val serviceFault = m.startsWith("HTTP") || m.contains("TomTom") || m.contains("Mapbox") || m.contains("too many") || m.contains("limit") || m == "Routing failed"
            val backup = if (p == "tomtom" && hasMapbox) "mapbox" else if (p == "mapbox" && hasKey) "tomtom" else ""
            if (!serviceFault || backup.isEmpty()) throw e
            val wait = if (m.contains("401") || m.contains("403") || m.contains("accept") || m.contains("limit") || m.contains("too many")) 3_600_000L else 600_000L
            if (p == "tomtom") ttDownUntil = System.currentTimeMillis() + wait else mbDownUntil = System.currentTimeMillis() + wait
            val name = if (p == "tomtom") "TomTom" else "Mapbox"
            scope.launch { toast("$name isn't working right now, so ${if (backup == "tomtom") "TomTom" else "Mapbox"} is being used instead", 4500) }
            return call(backup)
        }
    }
    /** Some online service can plan routes and search. */
    val hasOnline: Boolean get() = provider.isNotEmpty()

    private fun noteMbRefused(e: Exception) {
        val m = e.message ?: ""
        if (m.contains("(401)")) { refusedMb = mbToken; scope.launch { toast("Mapbox didn't accept your access token, so it isn't being used. Check it in Settings > Keys & accounts.", 8000) } }
    }

    /** Routes from whichever service is set up (see [provider]). */
    private fun netRoutes(from: LL, to: LL, alternatives: Boolean, departAtMs: Long = 0L, via: List<LL> = emptyList(), headingDeg: Double = -1.0, quick: Boolean = false): List<Route> =
        withBackup { p ->
            lastRouteProvider = p
            if (p == "mapbox") try { MapboxRest.routes(mbToken, from, to, alternatives, departAtMs, via, headingDeg, quick, !mirrored) } catch (e: Exception) { noteMbRefused(e); throw e }
            else TomTomRest.routes(apiKey, from, to, alternatives, departAtMs, via, headingDeg, quick)
        }.also { rs -> if (lastRouteProvider == "tomtom" && rs.isNotEmpty()) mapboxLanesFor(rs, from, to, via, headingDeg) }

    /**
     * A TomTom route's lane list isn't from the map Mapbox draws the lanes on, so it often doesn't match what's painted. Once the
     * route is out, Mapbox's own route for the same trip is asked for in the background (one request) and its lane list is put onto
     * the route, junction by junction, where Mapbox's route passes the same junction in the same direction. Junctions it doesn't
     * match keep TomTom's lanes. Silent if there's no token, no signal or it's switched off.
     */
    private fun mapboxLanesFor(rs: List<Route>, from: LL, to: LL, via: List<LL>, headingDeg: Double) {
        if (!hasMapbox || !prefs.b(PreferenceKeys.LANES_MAPBOX, true) || !online()) return
        searchScope.launch {
            try {
                val mb = MapboxRest.routes(mbToken, from, to, false, 0L, via, headingDeg, true, !mirrored).firstOrNull() ?: return@launch
                if (mb.lanes.isEmpty()) return@launch
                var matched = 0
                for (r in rs.take(3)) {
                    val out = ArrayList<LaneSec>()
                    for (ls in mb.lanes) {
                        val p = pointAt(mb, ls.end).first
                        val pr = MapRouteGeometry.projectOn(r.pts, p) ?: continue
                        if (Geo.dist(pr.second, p) > 20.0) continue
                        val offTT = r.cum[pr.first] + Geo.dist(r.pts[pr.first], pr.second)
                        val st = r.steps.firstOrNull { Math.abs(it.off - offTT) < 40.0 } ?: continue
                        // the same way through the junction: a lane Mapbox says to use must suit TomTom's turn here
                        if (ls.lanes.none { it.follow != null && laneHit(it.follow, st.maneuver) }) continue
                        out += LaneSec(max(0.0, offTT - (ls.end - ls.off)), offTT, ls.lanes)
                    }
                    if (out.isEmpty()) continue
                    matched += out.size
                    val keep = r.lanes.filter { t -> out.none { Math.abs(it.end - t.end) < 60.0 } }
                    r.lanes = (keep + out).sortedBy { it.off }
                    laneKey = null
                }
                laneSource = "Mapbox lane list on $matched of ${mb.lanes.size} junctions"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }
    /** Where the lane list came from, for the feedback report. */
    @Volatile var laneSource = ""
        private set
    @Volatile private var lastRouteProvider = ""

    /** The departure time was used for the last route (it isn't with every service or route). */
    private val departApplied: Boolean get() = if (lastRouteProvider == "mapbox") MapboxRest.departApplied else TomTomRest.departApplied

    /**
     * Place search. With a TomTom key it's TomTom's search, the same as the original Horizon Nav (fuzzy, finds house numbers near
     * you as you type), topped up with Mapbox's when TomTom finds little; Settings > Keys & accounts > Search with can pick Mapbox
     * instead. Without a key it's Mapbox's (with OpenStreetMap's alongside), or OpenStreetMap's alone with neither.
     */
    private fun netGeocode(country: String, q: String, near: LL?): List<Place> {
        val with = prefs.s(PreferenceKeys.SEARCHWITH, "auto")
        val tomtomOk = hasKey && refusedKey != apiKey && System.currentTimeMillis() >= ttDownUntil
        if (tomtomOk && (with == "tomtom" || (with == "auto") || !hasMapbox)) {
            val tt = try { TomTomRest.geocode(apiKey, country, q, near) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; null }
            if (tt != null && (tt.size >= 4 || !hasMapbox)) return tt
            if (!hasMapbox) return tt ?: OsmSearch.geocode(country, q, near)
            val mb = try { MapboxRest.geocode(mbToken, country, q, near) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; emptyList() }
            val out = ArrayList(tt ?: emptyList())
            for (p in mb) if (out.none { (it.name.equals(p.name, true) && Geo.dist(it.pos, p.pos) < 80) || Geo.dist(it.pos, p.pos) < 15 }) out += p
            return out
        }
        if (hasMapbox) {
            val r = MapboxRest.geocode(mbToken, country, q, near)
            if (r.isEmpty()) MapboxRest.lastError?.let { err -> scope.launch { toast("Mapbox search didn't work: ${err.take(120)}", 6000) } }
            return r
        }
        // no key or token: OpenStreetMap's free search
        return OsmSearch.geocode(country, q, near)
    }

    /** Places of a kind nearby from whichever service is set up. */
    private fun netPoiNearby(kind: String, c: LL, radius: Int): List<Nearby> =
        withBackup { p -> if (p == "mapbox") MapboxRest.poiNearby(mbToken, kind, c, radius) else TomTomRest.poiNearby(apiKey, kind, c, radius) }

    // ---- internals ----
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val said = HashSet<String>()
    private var lastIdx = 0
    private var offCount = 0
    /** How far along the route you last were for certain, and when: a jump far beyond it is a different road, not progress. */
    private var trustProg = -1.0
    private var trustAt = 0L
    private var lastReroute = 0L
    private var lastDraw = 0L
    private var drawnWhole = false
    private var lastDrawProg = 0.0
    private var simJob: Job? = null
    private var simDist = 0.0
    private var lastRaw: LL? = null
    private var lastGpsTime = 0L
    private var msgToken = 0
    private var lm: LocationManager? = null
    private var weatherAt = 0L
    private var limitAt = 0L
    private var limitPos: LL? = null
    private var camAt = 0L
    private var camPos: LL? = null
    private var cameras: List<LL> = emptyList()
    private val camAlerted = HashSet<LL>()
    private var drove = false
    private var stillSince = 0L
    private var posSavedAt = 0L
    private var tripOn = false
    private var tripT0 = 0L
    private var tripDistance = 0.0
    private var tripMoveSec = 0L
    private var tripTop = 0.0
    private var tripLast: LL? = null
    private var tripDest = ""

    init {
        // an older pack on the unit goes into its own area folder (it's the area that was chosen then)
        try { migratePack() } catch (e: Exception) { }
        // speed limits and the wrong-way warning read the roads from a downloaded area when one covers you (no network needed)
        Overpass.localWays = { p -> OfflineLimits.waysNear(ctx, p) }
        // the phone and car screens share the trip (joined a moment after start, once this engine is fully set up)
        scope.launch { delay(1500); RouteSync.register(this@NavEngine) }
        // a place sent from your phone (Settings > Places & trips): looked for shortly after starting, then every minute
        scope.launch {
            delay(6000)
            while (true) {
                try {
                    val code = prefs.s(PreferenceKeys.LINK_CODE, "")
                    if (!isCar && code.isNotBlank() && prefs.s(PreferenceKeys.LINK_ROLE, "") == "car" && appVisible && route == null && phoneDest == null && online()) {
                        val last = prefs.s(PreferenceKeys.LINK_LAST, "")
                        val got = withContext(Dispatchers.IO) { try { CarLink.latest(code, last) } catch (e: Exception) { null } }
                        if (got != null && got.id != last) { prefs.put(PreferenceKeys.LINK_LAST, got.id); if (route == null) phoneDest = got.place }
                    }
                } catch (e: Exception) { }
                delay(60_000)
            }
        }
        // the performance meter (Settings > Smoothness), once a second while it's on
        scope.launch {
            while (true) {
                delay(1000)
                PerfStats.on = prefs.b(PreferenceKeys.PERFORMANCE_METER, false)
                if (PerfStats.on) perfSample() else if (perfLine.isNotEmpty()) perfLine = ""
                // (the traffic view when stopped shouldn't depend on the GPS still sending positions while you're stationary)
                if (stoppedSince > 0L && !stoppedOverview && following && android.os.SystemClock.elapsedRealtime() - stoppedSince > 30_500L &&
                    prefs.b(PreferenceKeys.STOPPED_OVERVIEW, true)) fix?.let { try { onPosition(it, true) } catch (e: Exception) { } }
            }
        }
        // look for a newer app and newer map data straight after starting (the checks are a few small requests)
        scope.launch {
            if (Updater.enabled) try { withContext(Dispatchers.IO) { Updater.cleanUp(ctx) } } catch (e: Exception) { }
            delay(3_000)
            // (the phone's app does the update checks; the car screen's engine doesn't repeat them)
            if (!isCar) onOpened()
        }
        try {
            val o = org.json.JSONObject(prefs.s(PreferenceKeys.RESUME, "{}"))
            val age = System.currentTimeMillis() - o.optLong("b", o.optLong("t"))
            if (o.has("la") && age < 4 * 60_000L) {
                // the app was navigating a moment ago (Android restarted it, e.g. when the screen switched between day and night):
                // carry straight on, stops included, without asking
                val d = Place(o.optString("n"), o.optString("s"), LL(o.getDouble("la"), o.getDouble("lo")))
                val vs = o.optJSONArray("v")
                val stops = if (vs == null) emptyList() else (0 until vs.length()).mapNotNull { i -> try { val q = vs.getJSONObject(i); Place(q.optString("n"), q.optString("s"), LL(q.getDouble("la"), q.getDouble("lo"))) } catch (e: Exception) { null } }
                scope.launch {
                    var n = 0
                    while (fix == null && n < 40) { delay(500); n++ }
                    if (route != null) return@launch
                    via = stops
                    toast("Carrying on to ${d.name}", 3500)
                    startRoute(d, 0, keepVia = true, startNow = true)
                }
            } else if (o.has("la") && age < 15 * 60_000L)
                // stopped navigating 4 to 15 minutes ago: offer to carry on with the "Resume trip" card
                resume = Place(o.optString("n"), o.optString("s"), LL(o.getDouble("la"), o.getDouble("lo")))
        } catch (e: Exception) { }
        tts = TextToSpeech(ctx) { status ->
            if (status != TextToSpeech.SUCCESS) {
                // no working text-to-speech on this unit: the beeps still sound, but nothing can be said (this used to fail silently)
                scope.launch { toast("No voice for directions: install or turn on a text-to-speech engine (Settings > Sound & voice > Voice and language)", 9000) }
            }
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val r = tts?.setLanguage(Locale.getDefault())
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val r2 = tts?.setLanguage(Locale.UK)
                    if (r2 == TextToSpeech.LANG_MISSING_DATA || r2 == TextToSpeech.LANG_NOT_SUPPORTED)
                        scope.launch { toast("The text-to-speech voice has no language installed, so directions can't be spoken. Add one in Settings > Sound & voice > Voice and language", 9000) }
                }
                voiceChan = prefs.s(PreferenceKeys.VOICE_CHANNEL, "nav")
                tts?.setAudioAttributes(speechAttrs)
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { unduck() }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { unduck() }
                })
            }
        }
        // holding on a place you've just tapped on the map goes there by its name (a shop, a station...), anywhere else to a dropped pin
        // (while you're driving a route, holding adds the place as a stop on the way instead of replacing where you're going)
        map.onLongPress = { p ->
            val t = tappedPoi
            val place = if (t != null && Geo.dist(t.second, p) < 60.0 && android.os.SystemClock.uptimeMillis() - tappedAt < 120_000L) { tappedPoi = null; Place(t.first, "", t.second) }
                else Place("Dropped pin", "", p)
            if (route != null && !previewing && dest != null) addStop(place) else startRoute(place)
        }
        map.onPoiTap = { name, p -> tappedPoi = name to p; tappedAt = android.os.SystemClock.uptimeMillis(); toast(if (route != null && !previewing) "$name: hold on it to add it as a stop" else "$name: hold on it to go there", 3000) }
        map.onUserGesture = { if (following) { following = false }; scheduleRecentre(12_000) }
        map.onAltTap = { i -> switchToAlt(i) }
        // the parked-car spot is no longer used (the app lives in the car): drop any old one
        if (prefs.s(PreferenceKeys.PLACE_PARKED, "").isNotBlank()) places.clearParked()
        map.onError = { msg -> onMapError(msg) }
        // with no signal Mapbox's map keeps showing what it has stored; only when it starts missing pieces does the pack's map take over
        map.onTileMissing = {
            val now = android.os.SystemClock.uptimeMillis()
            if (!mapboxGaps && !online()) {
                if (now - gapFirstAt > 20_000L) { gapFirstAt = now; gapCount = 0 }
                if (++gapCount >= 3) { mapboxGaps = true; scope.launch { applyStyle(lastDark) } }
            }
        }
        map.onStyleLoaded = { styleRetry = 0 }
        try {
            val o = org.json.JSONObject(prefs.s(PreferenceKeys.PLANNED, "{}"))
            if (o.has("la") && o.optLong("a") > System.currentTimeMillis())
                planned = PlannedTrip(Place(o.optString("n"), o.optString("s"), LL(o.getDouble("la"), o.getDouble("lo"))), o.getLong("a"), o.getLong("l"))
        } catch (e: Exception) { }
        if (planned != null) watchPlanned()
        // clean air zones are downloaded once and refreshed monthly
        scope.launch {
            delay(15_000)
            try { if (Zones.needsRefresh(ctx) && online()) { withContext(Dispatchers.IO) { Zones.fetch(ctx) }; zonesVersion++ } } catch (e: Exception) { }
        }
    }

    /** Which sound channel the voice uses: "nav" (navigation, the normal one) or "media" (Settings > Sound > Voice channel). Some
     *  head units have the navigation channel turned right down or not wired to the speakers, so the beeps (on the media channel)
     *  sounded but the spoken directions never did. */
    private var voiceChan = "nav"
    private val speechAttrs: android.media.AudioAttributes get() = android.media.AudioAttributes.Builder()
        .setUsage(if (voiceChan == "media") android.media.AudioAttributes.USAGE_MEDIA else android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val audioMgr = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var focusReq: android.media.AudioFocusRequest? = null

    /** Lowers the music while a spoken instruction plays, so it can be heard. */
    private fun duck() {
        try {
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(speechAttrs).build()
            focusReq = req
            audioMgr.requestAudioFocus(req)
        } catch (e: Exception) { }
    }

    private fun unduck() {
        try { focusReq?.let { audioMgr.abandonAudioFocusRequest(it) } } catch (e: Exception) { }
        focusReq = null
    }

    /** Puts an alert on screen as a prominent banner. */
    fun raiseAlert(kind: String, text: String, ms: Long = 5000) {
        alert = AlertInfo(kind, text, ms)
        val t = ++alertToken
        scope.launch { delay(ms); if (t == alertToken) alert = null }
    }

    /** Plays a sample alert (chime, banner and voice) so the sound can be checked. */
    fun testAlert() {
        chime(android.media.ToneGenerator.TONE_PROP_PROMPT)
        raiseAlert("camera", "Test alert: speed camera ahead")
        speak("Speed camera ahead", true)
    }

    fun shutdown() {
        RouteSync.unregister(this)
        stopLocation(); simJob?.cancel(); tts?.shutdown()
        // everything this engine had running (its timers, lookups and the map glide) stops with it: the car screen makes a new
        // engine each time the phone is plugged in, and the old one must not keep going in the background
        try { scope.coroutineContext[Job]?.cancel() } catch (e: Exception) { }
        try { toneGen?.release() } catch (e: Exception) { }; toneGen = null
    }

    fun toast(msg: String, ms: Long = 4000) {
        message = msg
        val token = ++msgToken
        scope.launch { delay(ms); if (token == msgToken) message = null }
    }

    // ---- style ----
    fun styleUrl(dark: Boolean): String {
        val traffic = prefs.b(PreferenceKeys.TRAFFIC, true)
        return if (hasKey && prefs.mapSource() == "tomtom" && refusedKey != apiKey) TomTomRest.styleUrl(apiKey, dark, traffic)
        else OsmStyle.json(dark)
    }


    /**
     * ISO country the place search is limited to; blank means anywhere. Until you choose in Settings ("auto"), it's the country
     * you're in, worked out from your GPS position, so people outside the UK can find their own addresses straight away.
     */
    /**
     * Left-hand drive (driver on the left, as in the US and most of Europe): the screen is mirrored, with the cards on the right
     * and the round buttons on the left. Until it's chosen in Settings, it follows the country you're in.
     */
    val mirrored: Boolean get() = when (prefs.s(PreferenceKeys.DRIVE, "")) {
        "lhd" -> true
        "rhd" -> false
        else -> detectedCountry.isNotBlank() && detectedCountry.uppercase() !in KEEP_LEFT
    }

    /** The speed limit sign to draw: the white "SPEED LIMIT" board in the US, "MAXIMUM" in Canada, the red roundel elsewhere. */
    val limitSignStyle: String get() = when (detectedCountry.uppercase()) { "US", "PR", "GU", "VI", "AS", "MP" -> "us"; "CA" -> "ca"; else -> "round" }

    fun countryCode(): String { val v = prefs.s(PreferenceKeys.COUNTRY, "auto"); return if (v == "auto") detectedCountry else v }

    /** The country you're in (from the GPS), remembered between drives; blank until it's known. */
    var detectedCountry by mutableStateOf(prefs.s(PreferenceKeys.COUNTRYDETECTED, ""))
        private set
    private var countryChecked = false
    private var countryTryAt = 0L

    private fun detectCountry(lat: Double, lon: Double) {
        val now = android.os.SystemClock.elapsedRealtime()
        // once per app start; if it couldn't tell (no signal), try again a couple of minutes later
        if (countryChecked || (countryTryAt != 0L && now - countryTryAt < 120_000L)) return
        countryChecked = true; countryTryAt = now
        scope.launch {
            val cc = withContext(Dispatchers.IO) {
                val g = try {
                    @Suppress("DEPRECATION")
                    android.location.Geocoder(ctx, Locale.UK).getFromLocation(lat, lon, 1)?.firstOrNull()?.countryCode
                } catch (e: Exception) { null }
                // no geocoder on this unit (or no signal): a rough outline of Great Britain and Northern Ireland
                g ?: if (lat in 49.85..60.9 && lon in -8.2..1.8 && !(lon < -5.45 && lat < 54.05) && !(lat < 51.0 && lon > 1.0)) "GB" else null
            }
            if (cc == null) { countryChecked = false; return@launch }
            val up = cc.uppercase()
            if (up != detectedCountry) { detectedCountry = up; prefs.put(PreferenceKeys.COUNTRYDETECTED, up) }
        }
    }

    private var styleJob: kotlinx.coroutines.Job? = null

    private var lastDark = true

    // ---- offline pack download (runs here so it carries on when you leave the settings page) ----
    var packBusy by mutableStateOf(false)
    var packStatus by mutableStateOf("")
    var packRev by mutableStateOf(0)

    /** The area being downloaded (shown on its row in Settings), or null. */
    var packDownloading by mutableStateOf<String?>(null)

    fun downloadPack(url: String, id: String, extrasUrl: String = url, onDone: () -> Unit = {}) {
        if (packBusy || url.isBlank()) return
        packBusy = true; packStatus = "Starting…"; packDownloading = id
        scope.launch {
            try {
                withContext(Dispatchers.IO) { OfflineRouter.download(ctx, url, id, extrasUrl) { packStatus = it } }
                onDone()
                packStatus = "Pack ready"
                toast("Offline pack ready", 3000)
            } catch (e: Throwable) {
                packStatus = "Download failed: ${e.message ?: e.javaClass.simpleName}. Tap Download again to carry on."
            } finally { packBusy = false; packDownloading = null; packRev++; restyle() }
        }
    }

    /** Removes one downloaded area. */
    fun deletePack(id: String) {
        if (packBusy && packDownloading == id) return
        scope.launch {
            withContext(Dispatchers.IO) { OfflineRouter.deleteArea(ctx, id) }
            packStatus = ""; packRev++; restyle()
        }
    }

    // ---- several areas: the one you're in is used automatically ----
    private var areaCheckAt = 0L
    private var areaPending: String? = null

    /**
     * About once a minute: if another downloaded area fits where you are better (the smallest one that covers you), switch to it
     * once it's been the better fit on two checks in a row (so a border, where boxes overlap, doesn't flip it back and forth).
     */
    private fun checkArea(p: LL) {
        val now = System.currentTimeMillis()
        if (now - areaCheckAt < 60_000L || packBusy) return
        areaCheckAt = now
        scope.launch {
            val (cur, best) = withContext(Dispatchers.IO) {
                val ids = OfflineRouter.installedIds(ctx)
                if (ids.size < 2) return@withContext null to null
                OfflineRouter.activeId(ctx) to OfflineRouter.areaFor(ctx, p)
            }
            if (best == null || best == cur) { areaPending = null; return@launch }
            if (areaPending != best) { areaPending = best; areaCheckAt = now - 45_000L; return@launch }   // look again in ~15 s
            areaPending = null
            withContext(Dispatchers.IO) { OfflineRouter.setActive(ctx, best) }
            packRev++
            if (useOfflineMap()) restyle()
        }
    }
    fun restyle() = applyStyle(lastDark)

    /** True when the map should be drawn from the downloaded pack: always, or (in auto) when there is no signal. */
    private var styleRetry = 0

    /** The map style failed to load (usually a signal blip): try again a few times, then fall back to the downloaded map if there is one. */
    /** A key TomTom's map server turned away (it may still work for search and routes): the OpenStreetMap map is used instead. */
    @Volatile private var refusedKey: String? = null

    private fun onMapError(msg: String) {
        val m = msg.lowercase()
        // Mapbox turned the token away: say so (the map can't show without a working token)
        if (m.contains("401") || m.contains("403") || m.contains("unauthor") || m.contains("forbidden") || m.contains("access token")) {
            if (m.contains("401")) refusedMb = mbToken
            toast("Mapbox turned down your access token (${if (m.contains("403")) "403" else "401"}), so the map can't load. Check the token in Settings > Keys & accounts and at account.mapbox.com.", 9000)
            return
        }
        val network = m.contains("resolve") || m.contains("host") || m.contains("timeout") || m.contains("timed out") || m.contains("connect") || m.contains("network") || m.contains("unreachable")
        if (!network) { toast("Map: $msg", 5000); return }
        styleRetry++
        if (styleRetry == 1) toast("No signal for the map: trying again…", 3000)
        // still no signal: the downloaded pack's own map meanwhile
        if (styleRetry == 2 && OfflineMap.installed(ctx) && OfflineMap.hasFonts(ctx) && prefs.s(PreferenceKeys.OFF_MAP, "auto") != "never") {
            try { map.setStyleUrl(OfflineServer.styleJson(ctx, lastDark)) } catch (e: Exception) { }
        }
        if (styleRetry > 8) return
        val wait = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 30_000L, 60_000L, 60_000L)[(styleRetry - 1).coerceIn(0, 7)]
        scope.launch { delay(wait); applyStyle(lastDark) }
    }

    /**
     * The Mapbox edition draws Mapbox's map (which keeps recently seen areas for when signal drops); the downloaded packs' own map
     * isn't drawn yet (their offline routing, places, limits, cameras and lights all still work).
     */
    /** The last place tapped on Mapbox's map (name and spot), and when. */
    private var tappedPoi: Pair<String, LL>? = null
    private var tappedAt = 0L

    /** With no signal, Mapbox's map has started missing pieces (an area it hasn't stored): the pack's map is used until signal returns. */
    @Volatile private var mapboxGaps = false
    private var gapFirstAt = 0L
    private var gapCount = 0

    fun useOfflineMap(): Boolean {
        if (!OfflineMap.installed(ctx) || !OfflineMap.hasFonts(ctx)) return false
        return when (prefs.s(PreferenceKeys.OFF_MAP, "auto")) {
            "always" -> true
            "never" -> false
            // no signal: Mapbox's map carries on with what it has stored (areas seen recently, and saved Mapbox areas); the pack's
            // own map only takes over once Mapbox's starts missing pieces, and not where a saved Mapbox area covers you
            else -> !online() && mapboxGaps && !MapboxOffline.covers(ctx, fix?.pos ?: map.center())
        }
    }

    private var netWatching = false
    /** Switches between the online and downloaded map by itself when the connection comes or goes. */
    fun watchNetwork() {
        if (netWatching) return
        netWatching = true
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { mapboxGaps = false; scope.launch { delay(2000); applyStyle(lastDark); checkForUpdate(); checkDataUpdates() } }
                override fun onLost(network: android.net.Network) { scope.launch { delay(800); applyStyle(lastDark) } }
            })
        } catch (e: Exception) { }
    }

    private var noTokenToldAt = 0L
    private var lightJob: kotlinx.coroutines.Job? = null

    /**
     * Standard's light for now: dawn and dusk around sunrise and sunset, otherwise day or night (the app's own day / night choice).
     * "Day" or "Night" picked in Settings is kept as it is.
     */
    private fun lightPreset(dark: Boolean): String {
        val theme = prefs.s(PreferenceKeys.THEME, "auto")
        if (theme == "day") return "day"
        if (theme == "night") return "night"
        if (theme == "times") return if (dark) "night" else "day"
        val pos = fix?.pos ?: prefs.s(PreferenceKeys.LASTLAT, "").toDoubleOrNull()?.let { la -> prefs.s(PreferenceKeys.LASTLON, "").toDoubleOrNull()?.let { lo -> LL(la, lo) } }
        val ph = pos?.let { Sun.phase(it.lat, it.lon, System.currentTimeMillis()) } ?: ""
        return when {
            ph == "dawn" || ph == "dusk" -> ph
            dark -> "night"
            else -> "day"
        }
    }

    /**
     * The map. It's always drawn by Mapbox's map engine (so it always needs the Mapbox token), showing, as chosen in Settings > Map:
     * Mapbox's own map (Standard, its Lite / Lightest versions, or Standard Satellite), TomTom's map in the Horizon colours,
     * or the OpenStreetMap map. With no signal the downloaded pack's own map is shown (unless a saved Mapbox area covers where
     * you are). Day and night on Mapbox's map only change its light (a smooth fade, no reload).
     */
    fun applyStyle(dark: Boolean) {
        lastDark = dark
        map.nightStyle = dark
        if (map.onRestyle == null) map.onRestyle = { restyle() }
        watchNetwork()
        styleJob?.cancel()
        map.setTraffic(prefs.b(PreferenceKeys.TRAFFIC, true))
        map.setLightPreset(lightPreset(dark))
        // the light follows the sun (dawn, day, dusk, night) without waiting for the day / night switch
        if (lightJob == null) lightJob = scope.launch { while (true) { delay(60_000L); try { map.setLightPreset(lightPreset(lastDark)) } catch (e: Throwable) { } } }
        if (!hasMapbox) {
            map.setToken("")
            val now = System.currentTimeMillis()
            if (refusedMb != mbToken && now - noTokenToldAt > 60_000L) {
                noTokenToldAt = now
                toast("Add your Mapbox access token (it starts pk.) in Settings > Keys & accounts to show the map", 8000)
            }
            return
        }
        map.setToken(mbToken)
        map.keepBaseLook = false
        map.recolourBase = false
        // no signal (or "Downloaded" chosen): the pack's own map, served from the phone. Satellite is Mapbox's photos, so it
        // wins while there's a connection (with "Downloaded" chosen, switching satellite on used to do nothing at all)
        if (useOfflineMap() && !(map.isSatellite && online())) {
            if (map.isSatellite) toast("Satellite view needs a connection: showing the downloaded map until it's back", 5000)
            try { map.setStyleUrl(OfflineServer.styleJson(ctx, dark)); return } catch (e: Exception) { android.util.Log.w("NavEngine", "offline map", e) }
        }
        OfflineServer.stop()
        val src = prefs.mapSource()
        when {
            map.isSatellite || src == "mapbox" -> map.setStyleUrl(map.baseStyle())
            src == "tomtom" -> {
                if (hasKey && refusedKey != apiKey) {
                    // TomTom's map in the Horizon colours, as in Horizon Nav
                    map.recolourBase = true
                    map.setStyleUrl(TomTomRest.styleUrl(apiKey, dark, prefs.b(PreferenceKeys.TRAFFIC, true)))
                } else {
                    toast(if (hasKey) "TomTom turned down the key for its map, so Mapbox's map is showing" else "Add a TomTom key in Settings > Keys & accounts for TomTom's map. Showing Mapbox's map", 5000)
                    map.setStyleUrl(map.baseStyle())
                }
            }
            else -> map.setStyleUrl(OsmStyle.json(dark))
        }
    }

    // ---- location ----
    @SuppressLint("MissingPermission")
    fun startLocation() {
        if (lm != null) return
        val m = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = m
        try {
            if (m.allProviders.contains(LocationManager.GPS_PROVIDER))
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            if (m.allProviders.contains(LocationManager.NETWORK_PROVIDER))
                m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 0f, this, Looper.getMainLooper())
            (m.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                ?.let { handleLocation(it, true) }
        } catch (e: SecurityException) {
            lm = null
            toast("Location permission is needed for navigation")
        } catch (e: Exception) {
            lm = null
            android.util.Log.w("NavEngine", "startLocation failed", e)
        }
    }

    fun stopLocation() {
        try { lm?.removeUpdates(this) } catch (_: Exception) {}
        lm = null
    }

    override fun onLocationChanged(l: Location) = handleLocation(l, false)
    // Android 10 and older have no built-in versions of these: without them the app crashed as soon as GPS was switched off or on
    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Needed on Android 10 and older")
    @Suppress("DEPRECATION")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    private var startSavedAt = 0L

    private fun handleLocation(l: Location, first: Boolean) {
        try { handleLocationInner(l, first) } catch (e: Exception) { android.util.Log.w("NavEngine", "location handling failed", e) }
    }

    private fun handleLocationInner(l: Location, first: Boolean) {
        run {
            val t = System.currentTimeMillis()
            // where you are exactly (and which way you're facing), for the map to open right there next time
            if (t - startSavedAt > 15_000 && (!l.hasAccuracy() || l.accuracy < 60f)) {
                startSavedAt = t
                prefs.put(PreferenceKeys.STARTPOS, "%.5f,%.5f,%.0f".format(Locale.US, l.latitude, l.longitude, if (l.hasBearing()) l.bearing.toDouble() else -1.0))
            }
            if (t - posSavedAt > 600_000) {
                posSavedAt = t
                prefs.put(PreferenceKeys.LASTLAT, Math.round(l.latitude * 10) / 10.0)
                prefs.put(PreferenceKeys.LASTLON, Math.round(l.longitude * 10) / 10.0)
            }
        }
        if (simMode != 0) return
        detectCountry(l.latitude, l.longitude)
        val now = System.currentTimeMillis()
        if (l.provider == LocationManager.GPS_PROVIDER) lastGpsTime = now
        else if (now - lastGpsTime < 10000) return
        // with the GPS gone (a tunnel) a rough mobile-network position would throw the route off: keep estimating along the route instead
        else if (route != null && !previewing && glideOnRoute && now - lastGpsTime < 120_000 && (!l.hasAccuracy() || l.accuracy > 30f)) return
        val cur = LL(l.latitude, l.longitude)
        var h = fix?.heading ?: 0.0
        if (l.hasBearing() && l.hasSpeed() && l.speed > 1.0f) h = l.bearing.toDouble()
        else lastRaw?.let { if (Geo.dist(it, cur) > 6) h = Geo.bearing(it, cur) }
        val prev = lastRaw
        if (prev == null || Geo.dist(prev, cur) > 6) lastRaw = cur
        val speed = if (l.hasSpeed()) l.speed.toDouble() else 0.0
        if (prev != null && speed > 0.8) {
            val d = Geo.dist(prev, cur)
            if (d in 3.0..200.0) { tripDist += d; tripMoving += 1.0 }
        }
        if (speed > tripMax) tripMax = speed
        val firstFix = fix == null
        onPosition(Fix(cur, h, speed, if (l.hasAltitude()) l.altitude else 0.0, if (l.hasAccuracy()) l.accuracy.toDouble() else 0.0), firstFix || first)
        if (firstFix) {
            map.cameraTo(cur, null, if (tilt3d) 55.0 else 0.0, 15.5, 0, 0.0)
        }
        // current weather is only used for the rain effect on the map, so it is only fetched when that is switched on
        if (prefs.b(PreferenceKeys.RAIN_EFFECTS, false) && now - weatherAt > 20 * 60 * 1000L) {
            weatherAt = now
            scope.launch { weather = withContext(Dispatchers.IO) { Weather.fetch(cur) } ?: weather }
        }
    }

    // ---- extras: journey times, rain at destination, stops, arriving home ----
    private fun loadRain(to: Place, routeSecs: Double) {
        destRain = null
        scope.launch {
            val f = withContext(Dispatchers.IO) { Weather.forecast(to.pos) } ?: return@launch
            val idx = Math.round(routeSecs / 3600.0).toInt().coerceIn(0, f.hours.size - 1)
            val h = f.hours.getOrNull(idx) ?: return@launch
            val wet = h.code in 51..67 || h.code in 80..82 || h.code in 95..99
            val snow = h.code in 71..77 || h.code in 85..86
            if (h.rain >= 50 || wet || snow) destRain = (if (snow) "Snow" else "Rain") + " likely at arrival (${h.rain}%)"
        }
    }

    /** Forecast at points along the route, timed for when you will be there. */
    private fun loadRouteWeather(r: Route) {
        routeWx = emptyList()
        if (!prefs.b(PreferenceKeys.ROUTEWX, true) || r.cum.isEmpty()) return
        val total = r.cum.last()
        if (total < 10_000) return
        val fracs = if (total < 40_000) listOf(0.5, 1.0) else listOf(0.25, 0.5, 0.75, 1.0)
        scope.launch {
            val out = ArrayList<RouteWx>()
            for (fr in fracs) {
                val secs = r.time * fr
                val pos = pointAt(r, total * fr).first
                val f = withContext(Dispatchers.IO) { Weather.forecast(pos) } ?: continue
                val h = f.hours.getOrNull(Math.round(secs / 3600.0).toInt().coerceIn(0, f.hours.size - 1)) ?: continue
                out += RouteWx(Math.round(secs / 60.0).toInt(), h.code, h.temp, h.rain)
            }
            routeWx = out
        }
    }

    // one tone generator, kept and reused: making a new one for every beep leaked audio resources and caused hiccups over time
    private var toneGen: android.media.ToneGenerator? = null
    private fun chime(tone: Int = android.media.ToneGenerator.TONE_PROP_ACK) {
        if (soundMode == 2 || quiet()) return
        try {
            val g = toneGen ?: android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100).also { toneGen = it }
            g.startTone(tone, 250)
        } catch (e: Exception) { toneGen = null }
    }

    private fun checkLimitChange(newMs: Double, estimated: Boolean) {
        if (!prefs.b(PreferenceKeys.LIMITCHANGE, true) || estimated) return
        val k = if (imperial) 2.23694 else 3.6
        val n = (Math.round(newMs * k / 5.0) * 5).toInt()
        if (announcedLimit < 0) { announcedLimit = n; return }
        if (n == announcedLimit) { candidateHits = 0; limitCandidate = -1; return }
        if (n == limitCandidate) candidateHits++ else { limitCandidate = n; candidateHits = 1 }
        if (candidateHits >= 2 && (fix?.speed ?: 0.0) > 3.0) {
            announcedLimit = n; candidateHits = 0; limitCandidate = -1
            chime()
            raiseAlert("limit", "Speed limit $n")
            speak("Speed limit $n", true)
            limitPulse = true
            scope.launch { delay(3500); limitPulse = false }
        }
    }

    fun addStop(p: Place) {
        val d = dest ?: return
        via = via + p
        stopMode = false
        clearNearby()
        toast("Stop added: ${p.name}", 2500)
        startRoute(d, departMin, true, startNow = route != null && !previewing)
    }

    /** Route settings (avoid motorways, tolls...) changed: work the route out again with them, if there is one. */
    fun rerouteForSettings() {
        val d = dest ?: return
        startRoute(d, departMin, true, startNow = route != null && !previewing)
    }

    /** Replaces the stops on the way (in order) and works the route out again. */
    fun setStops(stops: List<Place>) {
        val d = dest ?: return
        via = stops
        startRoute(d, departMin, true, startNow = route != null && !previewing)
    }

    /** Which clean air / low emission zones the chosen route goes into, for the route options. */
    private fun updateRouteZones() {
        val r = route ?: run { routeZones = emptyList(); return }
        scope.launch {
            val names = withContext(Dispatchers.Default) { try { Zones.entries(r, Zones.all(ctx)).map { it.first.name }.distinct() } catch (e: Exception) { emptyList() } }
            if (route === r) routeZones = names
        }
    }

    // ---- feedback ----
    /** Everything useful for a bug report, with your own words first: the app and unit, the main settings, the areas and the last crash. */
    fun feedbackReport(note: String): String {
        val dm = ctx.resources.displayMetrics
        val crash = try { java.io.File(ctx.filesDir, "lastcrash.txt").takeIf { it.exists() }?.readText()?.take(2500) } catch (e: Exception) { null }
        return buildString {
            if (note.isNotBlank()) { appendLine(note.trim()); appendLine() }
            appendLine("---")
            appendLine("Horizon Nav ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})")
            appendLine("Unit: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), screen ${dm.widthPixels}x${dm.heightPixels}")
            appendLine("Country: ${detectedCountry.ifBlank { "unknown" }} · map: ${prefs.mapSource()} · TomTom key: ${if (hasKey) "yes" else "no"} · Mapbox token: ${if (hasMapbox) "yes" else "no"} · routes from: ${provider.ifBlank { "offline" }} · online: ${if (online()) "yes" else "no"}")
            appendLine("Offline area: ${installedPackId() ?: "none"}")
            appendLine("Frame rate: ${prefs.s(PreferenceKeys.MAP_FPS, "auto")} · map detail: ${prefs.mapDetail()} · driving side: ${if (mirrored) "left-hand" else "right-hand"}")
            if (perfLine.isNotBlank()) appendLine("Performance: $perfLine")
            try { append(SessionWatch.report(ctx)) } catch (e: Exception) { }
            if (laneDiag.isNotBlank()) appendLine(laneDiag)
            appendLine("Lane list: ${if (laneSource.isNotBlank()) laneSource else "TomTom's / the route's own"}")
            if (crash != null) { appendLine(); appendLine("Last crash:"); appendLine(crash) }
            val freeze = try { FreezeWatch.file(ctx).takeIf { it.exists() }?.readText()?.take(2500) } catch (e: Exception) { null }
            if (freeze != null) { appendLine(); appendLine("Last freeze:"); appendLine(freeze) }
        }
    }

    // ---- parking near the destination ----
    /** A few car parks close to where you're going, offered about a mile before you arrive. */
    var parkingNear by mutableStateOf<List<Nearby>>(emptyList())
    private var parkingFor: Route? = null

    private fun checkParking(r: Route, rem: Double) {
        if (parkingFor === r || rem > 1700.0 || rem < 150.0 || r.len < 3000.0 || previewing || !prefs.b(PreferenceKeys.PARKPROMPT, true)) return
        val d = dest ?: return
        parkingFor = r
        // going to a car park already: nothing to offer
        val nm = d.name.lowercase()
        if (nm.contains("parking") || nm.contains("car park") || nm.contains("multi-storey") || nm.contains("multistorey")) return
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val tt = if (hasOnline && online()) try { netPoiNearby("parking", d.pos, 700) } catch (e: Exception) { null } else null
                (tt?.takeIf { it.isNotEmpty() } ?: try { if (online()) Overpass.nearby("parking", d.pos, 700) else emptyList() } catch (e: Exception) { emptyList() })
                    .map { it.copy(dist = Geo.dist(d.pos, it.place.pos)) }.filter { it.dist < 800.0 }.sortedBy { it.dist }.take(3)
            }
            if (route === r && dest === d) parkingNear = list
        }
    }

    /** Drives to a car park from the list instead (you walk the last bit). */
    fun parkAt(n: Nearby) {
        val d = dest
        parkingNear = emptyList()
        startRoute(Place(n.place.name, "Car park" + (if (d != null) " near ${d.name}" else ""), n.place.pos), startNow = true)
        parkingFor = route
    }

    // ---- the list of turns (tap the turn card) ----
    var itineraryOpen by mutableStateOf(false)

    class ItinRow(val index: Int, val maneuver: String, val text: String, val tag: String, val dist: Double, val arrive: String, val off: Double, val road: String, val exit: Int)

    /** Every turn still ahead on the route, nearest first, with how far away it is and roughly when you'll get there. */
    fun itinerary(): List<ItinRow> {
        val r = route ?: return emptyList()
        val prog = curProgress
        val left = max(1.0, r.len - prog)
        val secs = eta?.secs ?: (r.time * left / max(1.0, r.len))
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val out = ArrayList<ItinRow>()
        r.steps.forEachIndexed { i, s ->
            if (s.off <= prog + 4) return@forEachIndexed
            val arriving = s.maneuver.uppercase().startsWith("ARRIVE")
            val road = s.street.ifBlank { s.sign }
            val text = if (arriving) "Arrive at " + (dest?.name ?: "your destination")
                       else Geo.maneuverText(s.maneuver, s.roundExit).replaceFirstChar { it.uppercase() } + (if (road.isNotBlank()) " onto $road" else "")
            val d = s.off - prog
            out += ItinRow(i, s.maneuver, text, Geo.stepTag(s), d, fmt.format(Date(now + (secs * d / left * 1000).toLong())), s.off, if (arriving) "" else road, s.roundExit)
        }
        return out
    }

    /** Shows one junction from the list on the map (the camera goes back to the car when the list closes). */
    fun previewTurn(row: ItinRow) {
        val r = route ?: return
        following = false
        val pts = (-3..3).map { k -> pointAt(r, (row.off + k * 50.0).coerceIn(0.0, r.len)).first }
        map.fit(pts)
    }

    fun closeItinerary() {
        itineraryOpen = false
        if (!following && route != null && !previewing) recenter()
    }

    /** Plans the route again without the road a turn in the list leads onto (the first 3 km of it). */
    fun avoidTurnRoad(row: ItinRow) {
        val r = route ?: return
        val next = r.steps.getOrNull(row.index + 1)?.off ?: r.len
        val p0 = row.off + 40.0
        val p1 = min(next - 40.0, p0 + 3000.0)
        if (p1 <= p0) { toast("That stretch is too short to avoid", 2500); return }
        itineraryOpen = false
        avoidStretch(r, p0, p1)
    }

    /** Works the route out again without the road you are on now (the stretch up to the next turn). */
    fun avoidRoadAhead() {
        val r = route ?: return
        if (previewing || dest == null) return
        if (fix == null) { toast("No GPS fix yet"); return }
        val p0 = curProgress + 60.0
        val toTurn = turn?.dist ?: 1500.0
        val p1 = p0 + toTurn.coerceIn(300.0, 3000.0)
        avoidStretch(r, p0, p1)
    }

    /** Keeps the next route out of the part of [r] between [p0] and [p1] metres along it, and plans again. */
    private fun avoidStretch(r: Route, p0: Double, p1: Double) {
        val d = dest ?: return
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        // the ends of the stretch as well as the route's own points (a long straight road can have none in between)
        val inside = r.pts.indices.filter { r.cum[it] in p0..p1 }.map { r.pts[it] } + pointAt(r, p0).first + pointAt(r, p1).first
        for (q in inside) {
            minLat = min(minLat, q.lat); maxLat = max(maxLat, q.lat); minLon = min(minLon, q.lon); maxLon = max(maxLon, q.lon)
        }
        if (minLat > maxLat) { toast("Nothing to avoid ahead", 2500); return }
        val pad = 0.0004
        sessionAvoid += LL(minLat - pad, minLon - pad) to LL(maxLat + pad, maxLon + pad)
        toast("Looking for a way round…", 2500)
        startRoute(d, 0, true, startNow = true)
    }

    fun resetTrip() { tripDist = 0.0; tripMax = 0.0; tripMoving = 0.0; tripStart = System.currentTimeMillis() }

    // ---- search + routing ----
    /** True when the device has a network that claims internet access (it can still be too weak to use). */
    fun online(): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val n = cm.activeNetwork
            if (n == null) false else cm.getNetworkCapabilities(n)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) { true }
    }

    /** True on Wi-Fi or another network that isn't metered (safe for the big offline download). */
    fun onWifi(): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    } catch (e: Exception) { false }

    /** How many days old the downloaded offline pack is, or null when there isn't one. */
    fun packAgeDays(): Int? {
        if (!OfflineRouter.installed(ctx)) return null
        val t = java.io.File(OfflineRouter.dir(ctx), "tiles.tar").lastModified()
        if (t <= 0L) return null
        return ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
    }

    /** The offline pack is old enough to be worth updating (about 4 months), and the reminder wasn't put off in the last 30 days. */
    fun packNeedsUpdate(): Boolean {
        val age = packAgeDays() ?: return false
        if (age < 120 || packBusy) return false
        val snoozed = prefs.s(PreferenceKeys.PACKNAG, "0").toLongOrNull() ?: 0L
        return System.currentTimeMillis() - snoozed > 30L * 86_400_000L
    }

    fun snoozePackUpdate() { prefs.put(PreferenceKeys.PACKNAG, System.currentTimeMillis().toString()) }

    // ---- app updates (the shareable build only) ----
    /** A newer version of the app that's been published, shown as a card on the map until you update or say later. */
    var appUpdate by mutableStateOf<Updater.Update?>(null)
    var updateBusy by mutableStateOf(false)
    var updateStatus by mutableStateOf("")

    /** Guards the check before its coroutine is launched, preventing overlapping lifecycle/network callbacks. */
    @Volatile private var updateCheckInFlight = false

    /** When the last automatic app / map-data checks ran (opening the app, or the connection coming back). */
    private var lastAutoUpdateCheck = 0L
    private var lastAutoDataCheck = 0L
    /** Automatic checks run at most this often: the head unit opens and resumes the app, and drops and regains its
     *  connection, many times a drive, and each check is network work (GitHub also limits how often it can be asked). */
    private val autoCheckGapMs = 30 * 60_000L

    /** Check when the app is opened or resumed (at most every half hour). Network work runs on IO. */
    fun onOpened() {
        if (!online()) return
        checkForUpdate()
        checkDataUpdates()
    }

    @Synchronized
    fun checkForUpdate(manual: Boolean = false) {
        if (!Updater.enabled || updateBusy || updateCheckInFlight) return
        val now = System.currentTimeMillis()
        if (!manual) {
            // "Look for updates by itself" switched off in Settings: only the Check button looks
            if (!prefs.b(PreferenceKeys.UPDCHECK, true)) return
            if (now - lastAutoUpdateCheck < autoCheckGapMs) return
            lastAutoUpdateCheck = now
        }
        updateCheckInFlight = true
        if (manual) updateStatus = "Checking…"
        scope.launch {
            val u = try { withContext(Dispatchers.IO) { Updater.check() } } catch (e: Exception) { if (manual) updateStatus = "Couldn't check: no connection"; return@launch } finally { updateCheckInFlight = false }
            prefs.put(PreferenceKeys.UPDATE_LAST_CHECK, now.toString())
            if (u == null) { if (manual) updateStatus = "You have the latest version"; return@launch }
            if (manual) updateStatus = "Version ${u.version} is available"
            // "later" keeps the card away for a few days, unless you check by hand
            val snoozed = (prefs.s(PreferenceKeys.UPDSNOOZE, "0").toLongOrNull() ?: 0L) > now
            if (manual || !snoozed) appUpdate = u
        }
    }

    fun snoozeAppUpdate() { prefs.put(PreferenceKeys.UPDSNOOZE, (System.currentTimeMillis() + 12 * 3600_000L).toString()); appUpdate = null }

    // ---- newer map data for the downloaded areas ----
    /** Downloaded areas with newer data published (a rebuilt pack, or new speed limit / camera files). */
    var dataUpdates by mutableStateOf<List<String>>(emptyList())

    fun checkDataUpdates() {
        if (packBusy) return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastAutoDataCheck < autoCheckGapMs) return
        lastAutoDataCheck = nowMs
        val snoozed = (prefs.s(PreferenceKeys.DATASNOOZE, "0").toLongOrNull() ?: 0L) > System.currentTimeMillis()
        if (snoozed) return
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                OfflineRouter.installedIds(ctx).filter { PackRegions.newerOnline(PackRegions.byId(it), OfflineRouter.packDir(ctx, it)) == true }
            }
            dataUpdates = found
        }
    }

    /** Updates the first area with newer data (the others follow, one at a time, as each finishes). */
    fun updateData(id: String) {
        dataUpdates = dataUpdates - id
        downloadRegion(PackRegions.byId(id))
    }

    fun snoozeDataUpdates() { prefs.put(PreferenceKeys.DATASNOOZE, (System.currentTimeMillis() + 3 * 24 * 3600_000L).toString()); dataUpdates = emptyList() }

    /** Downloads the new version and opens Android's installer (asking first for permission to install, if it's needed). */
    fun installAppUpdate() {
        val u = appUpdate ?: return
        if (updateBusy) return
        if (!Updater.canInstall(ctx)) {
            toast("Allow Horizon Nav to install updates, then tap Update again", 6000)
            try { Updater.openInstallSetting(ctx) } catch (e: Exception) { }
            return
        }
        updateBusy = true; updateStatus = "Starting the download…"
        // installed with AAEnabler: the update is saved to Downloads for AAEnabler to install, so it stays in Android Auto
        val forAaTool = Updater.installedViaAaTool(ctx) && android.os.Build.VERSION.SDK_INT >= 29
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Updater.downloadAndInstall(ctx, u, toDownloads = forAaTool) { updateStatus = it } }
                updateStatus = if (forAaTool) "Saved to Downloads as HorizonNav-${u.version}.apk. Install it with AAEnabler to keep it in Android Auto"
                    else "Follow the installer to finish"
            } catch (e: Exception) {
                // Never bypass checksum verification by opening the APK URL after a failed download.
                // Browser fallback is reserved for devices that genuinely lack DownloadManager.
                val message = e.message.orEmpty()
                if (message.contains("No download service", ignoreCase = true)) {
                    updateStatus = "Download service unavailable; opening the download in your browser…"
                    try { Updater.openInBrowser(ctx, u) }
                    catch (_: Exception) { updateStatus = "Couldn't open the download. Please try again." }
                } else {
                    updateStatus = when {
                        message.contains("checksum", ignoreCase = true) -> "Verification failed. The downloaded file was removed; please try again."
                        message.contains("timed out", ignoreCase = true) -> "Download timed out. Please try again."
                        message.contains("cancelled", ignoreCase = true) -> "Download cancelled. Please try again."
                        message.isNotBlank() -> message.take(140)
                        else -> "Update failed. Please try again."
                    }
                }
            } finally { updateBusy = false }
        }
    }

    /** Updates the offline pack that's on the unit (or the one chosen in Settings). */
    fun updatePack() = downloadRegion(PackRegions.byId(installedPackId() ?: prefs.s(PreferenceKeys.PACK_REGION, "ew")))

    /** The area in use (the one you're in, when several are downloaded), or null when there's none. */
    fun installedPackId(): String? = OfflineRouter.activeId(ctx)

    /** Every downloaded area. */
    fun installedPacks(): List<String> = OfflineRouter.installedIds(ctx)

    /** Moves a pack from before several areas could be kept into its own area folder (once). */
    private fun migratePack() {
        if (!OfflineRouter.installedIn(OfflineRouter.legacyDir(ctx))) return
        val id = prefs.s(PreferenceKeys.PACKINSTALLED, "").ifBlank { prefs.s(PreferenceKeys.PACK_REGION, "ew").takeIf { it == "gb" } ?: "ew" }
        OfflineRouter.migrateLegacy(ctx, id)
    }

    /** The area chosen for the next download: what you picked, else the one in use, else a guess from the country you're in. */
    fun chosenPack(): PackRegion {
        val picked = prefs.s(PreferenceKeys.PACK_REGION, "")
        if (picked.isNotBlank()) return PackRegions.byId(picked)
        installedPackId()?.let { return PackRegions.byId(it) }
        return PackRegions.suggestFor(detectedCountry) ?: PackRegions.byId("ew")
    }

    /** Switches to a downloaded area by hand (it switches back by itself if you're somewhere another area covers better). */
    fun useArea(id: String) {
        OfflineRouter.setActive(ctx, id); areaPending = null; areaCheckAt = System.currentTimeMillis()
        packRev++
        if (useOfflineMap()) restyle()
    }

    /**
     * Downloads the pack for [r] alongside any others already here (or updates it). It checks the pack has been built first, and that
     * there's room for it.
     */
    fun downloadRegion(r: PackRegion) {
        if (packBusy) return
        packBusy = true; packStatus = "Checking the ${r.name} pack…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) { PackRegions.available(r) }
            when (ok) {
                false -> { packBusy = false; packStatus = "The ${r.name} pack isn't available yet. It appears here once it has been built."; return@launch }
                null -> { packBusy = false; packStatus = "Couldn't reach the download. Check the connection and try again."; return@launch }
                true -> {}
            }
            packBusy = false
            downloadPack(r.url, r.id, r.extrasUrl) {
                // a new area becomes the one in use straight away (it switches to whichever covers you within a minute or so)
                OfflineRouter.setActive(ctx, r.id)
            }
        }
    }

    /** Places that can be found without signal: saved, favourite and previously used ones, then the downloaded UK towns and villages. */
    private suspend fun offlineMatches(q: String, near: LL?): List<Place> = withContext(Dispatchers.IO) {
        // a search replaced by a newer one (you typed another letter) stops reading the pack's lists at once
        val job = coroutineContext[kotlinx.coroutines.Job]
        val alive = { job?.isActive != false }
        try {
            val own = places.searchLocal(q, near)
            val addr = OfflineRouter.searchAddresses(ctx, q, near, alive).filter { t -> own.none { Geo.dist(it.pos, t.pos) < 30 } }
            val pack = (if (q.trim().length >= 3) {
                // the area in use first; the other downloaded areas too when it has little (a place over the border, say)
                val first = OfflineRouter.searchPlaces(ctx, q, near, alive)
                if (first.size >= 8) first else {
                    val act = OfflineRouter.activeId(ctx)
                    first + OfflineRouter.installedIds(ctx).filter { it != act }.flatMap { id ->
                        if (alive()) OfflineRouter.searchPlaces(ctx, q, near, alive, OfflineRouter.packDir(ctx, id)) else emptyList()
                    }
                }
            } else emptyList()).filter { t -> own.none { Geo.dist(it.pos, t.pos) < 150 } }
            val towns = OfflinePlaces.search(ctx, q, near).filter { t -> own.none { Geo.dist(it.pos, t.pos) < 400 } && pack.none { Geo.dist(it.pos, t.pos) < 1500 && it.name.equals(t.name, true) } }
            own + addr + pack + towns
        } catch (e: Throwable) { emptyList() }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    /** Puts exact and "starts with" name matches first (nearest first), then the rest with online results ahead of offline ones. */
    private fun rankResults(q: String, near: LL?, net: List<Place>, extra: List<Place>): List<Place> {
        val full = q.trim().lowercase()
        val all = ArrayList<Pair<Place, Int>>()
        net.forEachIndexed { i, p -> all += p to i }
        extra.forEachIndexed { i, p -> if (all.none { it.first.name.equals(p.name, true) && Geo.dist(it.first.pos, p.pos) < 300 || Geo.dist(it.first.pos, p.pos) < 40 }) all += p to (1000 + i) }
        fun strong(p: Place) = p.name.lowercase().let { it == full || it.startsWith(full) }
        fun d(p: Place) = if (near != null) Geo.dist(near, p.pos) else 0.0
        val top = all.filter { strong(it.first) }.sortedBy { d(it.first) }
        val rest = all.filter { !strong(it.first) }.sortedBy { it.second }
        return (top + rest).map { it.first }.take(15)
    }

    fun search(q: String) {
        if (q.length < 2) { results = emptyList(); return }
        val near = fix?.pos ?: map.center()
        searchJob?.cancel()
        searchJob = scope.launch {
            // with signal it always searches online (TomTom, Mapbox, or OpenStreetMap with neither); with none, the downloaded names
            if (!online()) {
                val local = offlineMatches(q, near)
                results = local
                if (local.isEmpty()) toast(if (OfflinePlaces.installed(ctx) || OfflineRouter.hasPlaces(ctx) || OfflineRouter.hasAddresses(ctx)) "No signal, and nothing saved or downloaded matches" else "No signal. Download the offline pack or place names in Settings > Offline maps to search with no signal", 4500)
                else toast("No signal: showing saved places and offline towns", 3000)
                return@launch
            }
            // the online search and the saved / downloaded places run at the same time, and their results are merged
            val localJob = async { offlineMatches(q, near) }
            val net = try { withContext(Dispatchers.IO) { netGeocode(countryCode(), q, near) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
            // the online results are shown straight away if the downloaded lists are still being searched; those are merged in after
            var local = kotlinx.coroutines.withTimeoutOrNull(500L) { localJob.await() }
            if (local == null) {
                val first = rankResults(q, near, net ?: emptyList(), emptyList())
                if (first.isNotEmpty()) results = first
                local = localJob.await()
            }
            val merged = rankResults(q, near, net ?: emptyList(), local)
            results = merged
            if (merged.isEmpty()) toast(if (net == null) "Search failed: weak or no signal" else "No matches. Try adding a town or postcode.", 3500)
            else if (net == null) toast("Weak signal: showing saved places and offline towns", 3500)
        }
    }

    /** Search used by the stops editor: the same sources as the search box, returned instead of shown. */
    suspend fun findPlaces(q: String): List<Place> {
        val near = fix?.pos ?: map.center()
        if (!online()) return offlineMatches(q, near)
        // both at once, as in the main search
        return kotlinx.coroutines.coroutineScope {
            val localJob = async { offlineMatches(q, near) }
            val net = try { withContext(Dispatchers.IO) { netGeocode(countryCode(), q, near) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
            rankResults(q, near, net ?: emptyList(), localJob.await())
        }
    }

    // a destination chosen with no signal is kept and routed as soon as the connection comes back
    var waitingFor by mutableStateOf<Place?>(null)
    private var waitJob: kotlinx.coroutines.Job? = null

    private fun waitForSignal(to: Place, departInMin: Int, keepVia: Boolean) {
        waitingFor = to
        waitJob?.cancel()
        waitJob = scope.launch {
            var tries = 0
            while (waitingFor === to && tries < 150) {
                delay(8000); tries++
                if (online() && !busy) { startRoute(to, departInMin, keepVia); delay(6000) }
            }
            if (waitingFor === to) waitingFor = null
        }
    }

    fun cancelWaiting() { waitingFor = null; waitJob?.cancel() }

    fun pickPlace(p: Place) {
        val slot = pendingSave
        if (slot != null) {
            places.setSlot(slot, p)
            pendingSave = null
            results = emptyList()
            toast("${if (slot == "home") "Home" else "Work"} saved: ${p.name}", 3500)
        } else startRoute(p)
    }

    fun setDeparture(minutes: Int) {
        val d = dest ?: return
        startRoute(d, minutes, true)
    }

    /** Pushes the route preferences from Settings into the routing client. */
    private fun syncRoutePrefs(from: LL? = null, to: LL? = null) {
        // areas to keep out of: roads you asked to avoid on this trip, and clean air zones when that is switched on
        // (a zone you start or finish in cannot be avoided, so it is left out)
        val rects = ArrayList<Pair<LL, LL>>(sessionAvoid)
        val rings = ArrayList<List<LL>>()
        for ((sw, ne) in sessionAvoid) rings += listOf(sw, LL(sw.lat, ne.lon), ne, LL(ne.lat, sw.lon), sw)
        if (prefs.b(PreferenceKeys.AVOIDZONES, false)) {
            for (z in Zones.all(ctx)) {
                if ((from != null && Zones.inside(z, from)) || (to != null && Zones.inside(z, to))) continue
                if (z.maxLat - z.minLat > 1.2 || z.maxLon - z.minLon > 1.8) continue
                rects += LL(z.minLat, z.minLon) to LL(z.maxLat, z.maxLon)
                rings.addAll(z.rings)
            }
        }
        TomTomRest.avoidRects = rects.take(10)
        OfflineRouter.exclude = rings
        TomTomRest.avoid = listOfNotNull(
            if (prefs.b(PreferenceKeys.AVOID_TOLL, false)) "tollRoads" else null,
            if (prefs.b(PreferenceKeys.AVOID_MOTORWAY, false)) "motorways" else null,
            if (prefs.b(PreferenceKeys.AVOID_FERRY, false)) "ferries" else null,
            if (prefs.b(PreferenceKeys.AVOID_UNPAVED, true)) "unpavedRoads" else null,
        )
        TomTomRest.routeType = if (prefs.b(PreferenceKeys.ROUTE_ECO, false)) "eco" else "fastest"
        // electric: TomTom's eco route for an electric car uses the least battery, from how much the car uses at different speeds
        TomTomRest.evParams = if (isEv && prefs.b(PreferenceKeys.ROUTE_ECO, false)) {
            val k = evKwhPerKm() * 100.0
            val batt = evBatteryKwh()
            val now = batt * prefs.i(PreferenceKeys.EV_CHARGE_PCT, 80).coerceIn(5, 100) / 100.0
            String.format(java.util.Locale.US, "&vehicleEngineType=electric&constantSpeedConsumptionInkWhPerHundredkm=50,%.1f:90,%.1f:130,%.1f&currentChargeInkWh=%.1f&maxChargeInkWh=%.1f",
                k * 0.7, k, k * 1.45, now, batt)
        } else ""
        // tolls avoided only in chosen countries (when "Avoid toll roads" itself is off)
        MapboxRest.tollCountries = if (prefs.b(PreferenceKeys.AVOID_TOLL, false)) emptySet()
            else prefs.s(PreferenceKeys.AVOID_TOLL_COUNTRIES, "").split(',').map { it.trim().uppercase() }.filter { it.length == 2 }.toSet()
    }

    fun startRoute(to: Place, departInMin: Int = 0, keepVia: Boolean = false, startNow: Boolean = false) {
        syncRoutePrefs(fix?.pos ?: map.center(), to.pos)
        resume = null
        if (!keepVia) via = emptyList()
        if (!hasOnline && !OfflineRouter.installed(ctx)) { toast("To plan routes, add a free TomTom key or Mapbox token in Settings > Keys & accounts, or download the offline pack", 7000); return }
        val from = fix?.pos ?: map.center() ?: return
        if (fix == null) toast("No GPS fix yet: routing from the map centre. Use the demo drive to simulate.", 5000)
        busy = true
        scope.launch {
            try {
                val departMs = if (departInMin > 0) System.currentTimeMillis() + departInMin * 60_000L else 0L
                var usedOffline = false
                val rs = withContext(Dispatchers.IO) {
                    val haveOffline = OfflineRouter.installed(ctx)
                    if (haveOffline && (!hasOnline || !online())) { usedOffline = true; OfflineRouter.routes(ctx, from, to.pos, via.map { it.pos }) }
                    else try { netRoutes(from, to.pos, true, departMs, via.map { it.pos }) }
                    catch (e: Exception) {
                        if (haveOffline && NavigationFallbackPolicy.shouldUseOffline(e)) { usedOffline = true; OfflineRouter.routes(ctx, from, to.pos, via.map { it.pos }) } else throw e
                    }
                }
                if (rs.isEmpty()) throw IllegalStateException("No route found")
                if (usedOffline) toast("Offline route: no traffic or live data", 4000)
                if (rs[0].steps.isEmpty()) toast(if (provider == "mapbox") "Mapbox returned no turn instructions for this route" else "This key returned no turn instructions. Check it has TomTom Routing API access", 6000)
                if (departInMin > 0 && !usedOffline && !departApplied) toast("Departure time not supported here: showing live traffic", 4000)
                departMin = departInMin
                departAtMs = if (!usedOffline && departApplied) departMs else 0L
                if (to.name != "Dropped pin") places.addRecent(to)
                if (!keepVia) habits.record(to)
                loadRain(to, rs[0].time)
                loadRouteWeather(rs[0])
                fuelSuggestion = null
                clearLiveAlts()
                if (prefs.b(PreferenceKeys.SMARTFUEL, true)) suggestFuel(rs[0])
                clearNearby()
                results = emptyList()
                stopSim()
                if (TomTomRest.avoidDropped && !usedOffline) toast("Couldn't find a way round the areas you're avoiding, so the route goes through them", 5000)
                dest = to; routes = rs; active = 0
                prediction = null; predictionAt = 0L
                updateRouteZones()
                said.clear(); offCount = 0; lastIdx = 0; trustProg = -1.0
                turn = null; eta = null; routeFrac = 0f
                map.setDest(to.pos)
                drawRoutes()
                following = false
                previewing = true
                val allPts = rs.take(3).flatMap { r -> r.pts.filterIndexed { i, _ -> i % 8 == 0 } + r.pts.last() }
                delay(350)
                // a short fly-through along the new route first (not when stops change mid-drive)
                // (on long routes the fly-through has to load map tiles all along the way, which made the start of a trip stutter;
                // those go straight to the overview)
                if (!startNow && prefs.b(PreferenceKeys.CINEMATIC, true) && rs[0].len < 40_000.0) {
                    val r0 = rs[0]
                    map.flyRoute(r0.pts, r0.cum, r0.len, 50.0) { if (previewing && routes === rs) map.fit(allPts) }
                } else map.fit(allPts)
                waitingFor = null; waitJob?.cancel()
                // changing stops while already driving carries straight on, without the route options in between
                if (startNow) beginNavigation()
            } catch (e: Exception) {
                val m = e.message ?: ""
                if (m.contains("signal")) {
                    toast("No signal: this route will start by itself as soon as there is a connection", 6000)
                    if (waitingFor !== to) waitForSignal(to, departInMin, keepVia)
                } else toast("Route failed: $m", 6000)
            } finally { busy = false }
        }
    }

    fun selectRoute(i: Int) {
        if (i !in routes.indices) return
        map.stopFly()
        active = i; lastIdx = 0
        updateRouteZones()
        drawRoutes()
        val r = routes[i]
        map.fit(r.pts.filterIndexed { n, _ -> n % 6 == 0 } + r.pts.last())
    }

    /** A name for the map strip of the current route: "Southampton → Bristol" style, from where you are to the destination. */
    fun offlineRouteName(): String {
        val to = dest?.name?.takeIf { it.isNotBlank() } ?: "destination"
        val from = onRoad.takeIf { it.isNotBlank() } ?: "here"
        return "$from → $to"
    }

    fun beginNavigation() {
        if (!previewing) return
        map.stopFly()
        previewing = false
        // where this trip started, for "Route back" when you arrive
        tripFrom = fix?.pos
        // the other screen (phone or car) follows onto the same trip
        dest?.let { d -> try { RouteSync.started(this, d, via) } catch (e: Exception) { } }
        said.clear(); lastIdx = 0; zoomOffset = 0.0
        navStartMs = System.currentTimeMillis(); navStartDist = tripDist
        lastFasterCheck = System.currentTimeMillis(); faster = null; lastTrafficAt = 0L
        dest?.let { d -> prefs.put(PreferenceKeys.RESUME, org.json.JSONObject().put("n", d.name).put("s", d.sub).put("la", d.pos.lat).put("lo", d.pos.lon).put("t", System.currentTimeMillis()).toString()) }
        following = true
        // Settings > Offline maps > Save every route's map: the Mapbox map along this route, kept for when signal drops
        try { route?.let { r -> MapboxOffline.autoSaveRoute(ctx, prefs, offlineRouteName(), r.pts, onWifi()) } } catch (e: Throwable) { }
        routeWx.firstOrNull { it.code in 51..67 || it.code in 80..82 || it.code in 95..99 || it.code in 71..77 || it.code in 85..86 || it.rain >= 70 }?.let {
            val snow = it.code in 71..77 || it.code in 85..86
            raiseAlert("rain", (if (snow) "Snow" else "Rain") + " expected in about ${it.minutes} min on your route", 6000)
            speak((if (snow) "Snow" else "Rain") + " expected in about ${it.minutes} minutes on your route", true)
        }
        val f = fix
        if (f != null) onPosition(f, true)
        else { updateGuidance(0.0); toast("No GPS fix yet. Use the play button for a demo drive.", 5000) }
        cardsForStartAlts()
    }

    private fun drawRoutes() {
        val r = route ?: return
        map.setRoute(r.pts, routes.filterIndexed { i, _ -> i != active }.map { it.pts }, r.sev)
        map.setTunnels(r.tunnels.map { (a, b) -> slice(r, a, b) })
    }

    fun cycleRoute() {
        if (routes.size < 2) { toast("No alternative routes. Switch on 'Alternatives' first."); return }
        active = (active + 1) % routes.size
        said.clear(); lastIdx = 0
        drawRoutes()
        fix?.let { onPosition(it, true) } ?: updateGuidance(0.0)
    }

    fun toggleAlternatives() {
        alternatives = !alternatives
        toast(if (alternatives) "Alternative routes on" else "Alternative routes off", 2500)
        val d = dest
        if (d != null) startRoute(d, 0, true)
    }

    private var rerouting = false

    private fun reroute() {
        val d = dest ?: return
        if (rerouting || fix == null) return
        if (System.currentTimeMillis() - lastReroute < 4000) return
        lastReroute = System.currentTimeMillis()
        rerouting = true
        syncRoutePrefs(fix?.pos, d.pos); faster = null
        toast("Rerouting…", 2500)
        scope.launch {
            var err: String? = null
            try {
                for (attempt in 1..3) {
                    val f = fix ?: break
                    try {
                        val hd = if (f.speed > 2.0) f.heading else -1.0
                        val rs = withContext(Dispatchers.IO) {
                            val haveOffline = OfflineRouter.installed(ctx)
                            if (haveOffline && (!hasOnline || !online())) OfflineRouter.routes(ctx, f.pos, d.pos, via.map { it.pos }, hd)
                            else try { netRoutes(f.pos, d.pos, false, 0L, via.map { it.pos }, hd, true) }
                            catch (e: Exception) {
                                if (haveOffline && NavigationFallbackPolicy.shouldUseOffline(e)) OfflineRouter.routes(ctx, f.pos, d.pos, via.map { it.pos }, hd) else throw e
                            }
                        }
                        if (rs.isNotEmpty()) { routes = rs; active = 0; said.clear(); offCount = 0; lastIdx = 0; trustProg = -1.0; clearLiveAlts(); drawRoutes(); cardsForStartAlts(); err = null; break }
                        err = "no route found"
                    } catch (e: Exception) { err = e.message ?: "unknown error" }
                    if (attempt < 3) delay(2500)
                }
            } finally { rerouting = false; lastReroute = System.currentTimeMillis() }
            // it keeps trying on its own while you are still off the route, so this is only a heads-up
            if (err != null) toast(if (err.contains("signal")) "Rerouting: weak signal, still trying…" else "Reroute failed ($err), trying again…", 3500)
        }
    }

    fun endRoute() {
        // (only a trip under way: cancelling a route choice leaves the other screen's trip alone)
        if (route != null && !previewing) try { RouteSync.ended(this) } catch (e: Exception) { }
        itineraryOpen = false
        cancelWaiting()
        map.stopFly()
        faster = null; resume = null; prefs.put(PreferenceKeys.RESUME, "{}")
        incMatches = emptyList(); incidentAhead = null; incAt = 0L; incAlerted.clear()
        routeCams = emptyList(); camsRoute = null; camsAt = 0L; camsCount = -1; hazardSig = 0; policeSig = 0; map.setHazards(emptyList())
        via = emptyList(); stopMode = false; destRain = null; routeWx = emptyList()
        routes = emptyList(); active = 0; dest = null; turn = null; eta = null; lastIdx = 0; previewing = false; routeFrac = 0f; onRoad = ""
        departMin = 0; departAtMs = 0L
        fuelSuggestion = null; parkingNear = emptyList(); parkingFor = null
        turnArrowKey = -1; map.setTurnArrow(emptyList())
        clearLiveAlts(); liveSaid.clear(); radar = emptyList(); radarSig = 0; bendsFor = null; bends = emptyList()
        flushRoads()
        sessionAvoid.clear(); routeZones = emptyList(); avgInfo = null; avgIn = null; servicesAhead = emptyList(); barEvents = emptyList()
        rawServices = emptyList(); servicesFor = null; rawHz = emptyList(); rawAvg = emptyList(); hzWinRoute = null; hzWinEnd = -1.0
        placed = null; placedSig = 0; avgAlerted.clear(); hzAlerted.clear(); zoneAlerted.clear(); stopsEditor = false
        map.setRoute(emptyList(), emptyList()); map.setDest(null); map.setTurnPulse(false); laneKey = null; map.setLanes(emptyList()); map.setLaneSign(null, emptyList()); laneSignFor = -1.0; bubbleLanes = null; map.setLaneBubble(null, emptyList()); map.setRouteShift(emptyList()); laneLatAt = null; laneShiftEnd = 0.0
        map.highlightDestination(null)
        stopSim()
        tts?.stop()
        glideRoute = null; glideOnRoute = false
        refreshRoad()
        map.setTilt(if (tilt3d) 55.0 else 0.0)
    }

    // ---- camera controls ----
    fun recenter() {
        following = true
        zoomOffset = 0.0
        val f = fix
        if (f != null) onPosition(f, true) else toast("No GPS fix yet")
    }

    private var recentreToken = 0

    /** While navigating, snaps the map back to the car after you stop moving it for [ms]. */
    private fun scheduleRecentre(ms: Long) {
        if (route == null || previewing) return
        val t = ++recentreToken
        scope.launch { delay(ms); if (t == recentreToken && !following && route != null && !previewing) recenter() }
    }

    /** Shows the rest of the route on screen, then goes back to following the car after a few seconds. */
    fun overview() {
        val r = route ?: return
        val pts = listOfNotNull(fix?.pos) + r.pts.drop(lastIdx.coerceIn(0, max(0, r.pts.size - 2))).filterIndexed { n, _ -> n % 6 == 0 } + r.pts.last()
        following = false
        map.fit(pts)
        scheduleRecentre(10_000)
    }

    fun toggleNorthUp() {
        northUp = !northUp
        if (northUp) map.resetNorth() else fix?.let { onPosition(it, true) }
    }

    fun toggleTilt() {
        tilt3d = !tilt3d
        prefs.put(PreferenceKeys.VIEW_3D, tilt3d)
        map.setTilt(if (tilt3d) 55.0 else 0.0)
        if (following) fix?.let { onPosition(it, true) }
    }

    fun zoom(delta: Double) {
        if (following && fix != null) { zoomOffset = (zoomOffset + delta).coerceIn(-4.0, 3.0); fix?.let { onPosition(it, true) } }
        else map.zoomBy(delta)
    }


    // ---- speed limit, speed cameras (real GPS + OpenStreetMap) ----
    @Volatile private var limitBusy = false
    @Volatile private var ttAt = 0L
    @Volatile private var ttPos: LL? = null
    @Volatile private var ttWay = -1L
    @Volatile private var ttLim: TomTomRest.RoadLimit? = null
    private var routeLimitAt = 0L
    private var pendLimit = -2.0
    private var pendHits = 0

    /** True while the posted limits that came with the route are being shown (they're exact, so nothing else overrides them). */
    private fun routeLimitLive() = route != null && !previewing && System.currentTimeMillis() - routeLimitAt < 4000L

    /**
     * The limit of the road at [p]: with a TomTom key (built in or your own), TomTom's road data supplies it; OpenStreetMap supplies
     * the road name, and the limit when TomTom has none or can't be reached.
     */
    private val mbTrail = ArrayList<LL>()
    @Volatile private var mbAt = 0L
    @Volatile private var mbPos: LL? = null
    @Volatile private var mbWay = 0L
    @Volatile private var mbLim: Double? = null

    private fun lookUpLimit(p: LL, hd: Double, routeHere: Boolean): Overpass.Limit? {
        // the country you're in (from where you are), not the head unit's language setting, which is often US English anywhere
        val osm = Overpass.speedLimit(p, hd, if (Overpass.countryAt(p) == "GB") "GB" else detectedCountry.ifBlank { Overpass.countryAt(p) })
        // your last few positions, for Mapbox to match to its roads
        synchronized(mbTrail) {
            if (mbTrail.isEmpty() || Geo.dist(mbTrail.last(), p) > 15.0) { mbTrail.add(p); while (mbTrail.size > 5) mbTrail.removeAt(0) }
            if (Geo.dist(mbTrail.first(), p) > 600.0) { mbTrail.clear(); mbTrail.add(p) }
        }
        if (routeHere || !online()) return osm
        if (!hasKey || ttDown()) {
            // no TomTom key (or TomTom not working right now) but a Mapbox token: Mapbox matches your last few positions to its roads and gives that road's limit
            if (provider != "mapbox") return osm
            val now = System.currentTimeMillis()
            val way = osm?.wayId ?: 0L
            val stale = now - mbAt > 20_000L || (mbPos?.let { Geo.dist(it, p) > 250.0 } ?: true)
            // a new stretch of road or an old answer, at most once every 4 seconds
            if ((way != mbWay || stale) && now - mbAt > 4000L) {
                val trail = synchronized(mbTrail) { mbTrail.toList() }
                if (trail.size >= 2) {
                    mbAt = now; mbPos = p; mbWay = way
                    mbLim = try { MapboxRest.speedLimit(mbToken, trail) } catch (e: Exception) { null }
                }
            }
            val v = (if (osm == null || mbWay == way) mbLim else null) ?: return osm
            if (v <= 0.0) return osm
            return Overpass.Limit(v, false, osm?.road ?: "", osm?.wayId ?: 0L, osm?.names ?: emptyList(), true)
        }
        val tNow = System.currentTimeMillis()
        val way = osm?.wayId ?: 0L
        val stale = tNow - ttAt > 15_000L || (ttPos?.let { Geo.dist(it, p) > 200.0 } ?: true)
        // a new stretch of road (OpenStreetMap splits roads where the limit changes) or an old answer: ask TomTom again,
        // but never more than once every 2.5 seconds, which keeps well inside the free allowance
        if ((way != ttWay || stale) && tNow - ttAt > 2500L) {
            ttAt = tNow; ttPos = p; ttWay = way
            ttLim = try { TomTomRest.speedLimit(apiKey, p, hd) } catch (e: Exception) { null }
        }
        // TomTom's limit whenever it has one (it's the more complete and up-to-date source); OpenStreetMap is the backup
        val tt = (if (osm == null || ttWay == way) ttLim else null) ?: return osm
        return Overpass.Limit(tt.ms, false, osm?.road ?: "", osm?.wayId ?: 0L, osm?.names ?: emptyList(), true)
    }

    /**
     * Shows a looked-up limit. A guess from the road type is only shown when "Show estimated limits" is on (a blank sign is better
     * than a confidently wrong one). A change only shows straight away when the road was matched closely; otherwise it has to be
     * seen twice, so passing a side road at a junction doesn't flash its limit.
     */
    private fun showLimit(r: Overpass.Limit, at: LL) {
        val showEst = prefs.b(PreferenceKeys.LIMITEST, false)
        val v = if (r.ms <= 0.0 || (r.estimated && !showEst)) -1.0 else r.ms
        val cur = speedLimit ?: -1.0
        if (abs(v - cur) > 0.3 || (v > 0 && r.estimated != limitEstimated)) {
            if (!r.sure && speedLimit != null) {
                if (abs(v - pendLimit) < 0.3) pendHits++ else { pendLimit = v; pendHits = 1 }
                if (pendHits < 2) return
            }
            pendHits = 0; pendLimit = -2.0
            if (v <= 0.0) { speedLimit = null; limitEstimated = false }
            else { speedLimit = v; limitEstimated = r.estimated }
        }
        if (v > 0.0) checkLimitChange(v, r.estimated)
        limitOkAt = System.currentTimeMillis(); limitOkPos = at
    }

    /** While navigating a TomTom route: the posted limit for exactly where you are on it, updated with every GPS fix. */
    private fun applyRouteLimit(r: Route, prog: Double, p: Fix) {
        val secs = r.limits
        if (secs.isEmpty()) return
        // a few metres ahead, to make up for the GPS being about a second behind
        val at = prog + min(max(p.speed, 0.0) * 0.8, 20.0)
        var lo = 0; var hi = secs.size - 1; var hit: LimitSec? = null
        while (lo <= hi) {
            val m = (lo + hi) ushr 1
            val sc = secs[m]
            if (at < sc.off) hi = m - 1 else if (at >= sc.end) lo = m + 1 else { hit = sc; break }
        }
        if (hit == null) return      // no posted limit known for this stretch: the map data takes over
        routeLimitAt = System.currentTimeMillis()
        pendHits = 0; pendLimit = -2.0
        if (speedLimit != hit.ms || limitEstimated) { speedLimit = hit.ms; limitEstimated = false }
        checkLimitChange(hit.ms, false)
        limitOkAt = routeLimitAt; limitOkPos = p.pos
    }

    private fun updateRoadInfo(p: Fix) {
        val now = System.currentTimeMillis()
        if (simMode == 0) {
            val dt = (now - lastSpdAt) / 1000.0
            if (lastSpdAt > 0 && dt in 0.3..2.5 && lastSpd > 8.0 && (lastSpd - p.speed) / dt > 3.2 && !brakeFlash) {
                brakeFlash = true
                scope.launch { delay(1400); brakeFlash = false }
            }
            lastSpd = p.speed; lastSpdAt = now
        }
        run {
            val lim = speedLimit
            val tolUnits = prefs.s(PreferenceKeys.TRAFFIC_WARNING, "5").toIntOrNull() ?: 5
            val tol = tolUnits * (if (imperial) 0.44704 else 0.27778)
            val over = prefs.b(PreferenceKeys.SPEEDWARN, true) && lim != null && !limitEstimated && p.speed > lim + tol && p.speed > 4.0
            overLimit = over
            if (over && soundMode != 2 && now - lastBeep > 9000) {
                lastBeep = now
                chime(android.media.ToneGenerator.TONE_PROP_BEEP2)
            }
        }
        val lp = limitPos
        val moving = p.speed > 0.5
        // a quick local check every few seconds (the roads around you are downloaded a few hundred metres at a time, so most
        // checks need no network): the sign changes within a few seconds of passing the real one
        if (shadow()) return
        if (!limitBusy && now - limitAt > (if (moving) 3000L else 12000L) && (lp == null || Geo.dist(lp, p.pos) > 25) && (moving || lp == null)) {
            limitAt = now; limitPos = p.pos; limitBusy = true
            // where the car really is: on the route line while navigating, or snapped to the road the map shows
            val look = (if (glideOnRoute) gPos else mPos)?.takeIf { Geo.dist(it, p.pos) < 40.0 } ?: p.pos
            val routeHere = routeLimitLive()
            scope.launch {
                try {
                    val hd = if (p.speed > 2.0) p.heading else -1.0
                    val r = withContext(Dispatchers.IO) { lookUpLimit(look, hd, routeHere) }
                    if (r != null) {
                        if (r.wayId != 0L || r.road.isNotBlank()) noteRoad(r.road, hd); limitFails = 0
                        if (!routeLimitLive()) showLimit(r, p.pos)
                    } else if (Overpass.lastError == null) {
                        // looked fine, but you're not on a mapped road (a car park, a new estate): no road name or limit rather than the
                        // last road's
                        noteRoad("", hd)
                        if (!routeLimitLive()) showLimit(Overpass.Limit(-1.0, true), p.pos)
                    } else {
                        // keep showing the last known limit through a failed lookup; only drop it once it is clearly out of date (10 minutes or 3 km)
                        val okPos = limitOkPos
                        if (!routeLimitLive() && (System.currentTimeMillis() - limitOkAt > 600_000 || (okPos != null && Geo.dist(okPos, p.pos) > 3000))) { speedLimit = null; limitEstimated = false }
                        // the lookup itself failed: try again soon instead of waiting until the car has moved (and let an old road name
                        // drop once it's out of date)
                        refreshRoad()
                        limitFails++
                        limitPos = null
                        limitAt = System.currentTimeMillis() + minOf(limitFails * 8000L, 60_000L)
                    }
                } finally { limitBusy = false }
            }
        }
        try { checkPolice(p) } catch (e: Exception) { }
        try { checkWrongWay(p) } catch (e: Exception) { }
        noteCameraBan()
        if (!camerasOn()) { cameraDist = null; if (cameras.isNotEmpty()) { cameras = emptyList(); map.setCameras(emptyList()) }; camPos = null; return }
        val cp = camPos
        if (now - camAt > 120000 && (cp == null || Geo.dist(cp, p.pos) > 2500)) {
            camAt = now; camPos = p.pos
            // the cameras within 6 km: warned about as you approach, and drawn on the map (route or not)
            scope.launch {
                val found = withContext(Dispatchers.IO) { localCameras(p.pos, 6000.0) ?: Overpass.cameras(p.pos, 6000) }
                // a failed lookup (weak signal, busy server) keeps the ones already shown and tries again in about half a minute
                if (found == null) { camAt = System.currentTimeMillis() - 90_000L; camPos = null }
                else { aroundCams = found; mergeCams() }
            }
        }
        // the cameras on the map follow the route coming and going, and the ones you've passed drop off
        if (now - camsMergedAt > 4000L) { camsMergedAt = now; mergeCams() }
        var best: LL? = null
        var bestD = 1e9
        // warned in good time: at least 500 m, or about 18 seconds ahead at speed
        val warnAt = max(500.0, p.speed * 18.0)
        val rc = routeCams
        if (navigatingNow() && onRouteNow && rc.isNotEmpty()) {
            // on a route: the cameras along it, by distance along the road (works round bends, where "straight ahead" doesn't)
            for ((c, off) in rc) {
                val ahead = off - curProg
                if (ahead < -15.0 || ahead > warnAt || ahead >= bestD) continue
                if (!camFacesUs(c, p.heading)) continue
                best = c; bestD = max(0.0, ahead)
            }
        } else if (p.speed > 3.0) {
            for (c in cameras) {
                val d = Geo.dist(p.pos, c)
                if (d > warnAt || d >= bestD) continue
                val diff = abs(((Geo.bearing(p.pos, c) - p.heading + 540.0) % 360.0) - 180.0)
                // nearly straight ahead; close in, a wider angle (the camera is often beside the road)
                if (diff < (if (d < 150.0) 60.0 else 35.0) && camFacesUs(c, p.heading)) { best = c; bestD = d }
            }
        }
        cameraDist = if (best != null) bestD.toInt() else null
        if (best != null) {
            if (camAlerted.size > 100) camAlerted.clear()
            if (camAlerted.add(best)) { chime(android.media.ToneGenerator.TONE_PROP_PROMPT); raiseAlert("camera", "Speed camera ahead"); speak("Speed camera ahead", true) }
        }
    }

    private fun trackParking(p: Fix) {
        if (simMode != 0) return
        val now = System.currentTimeMillis()
        // ---- trip recording ----
        if (tripOn) {
            val last = tripLast
            if (last != null) {
                val d = Geo.dist(last, p.pos)
                if (d in 2.0..250.0) tripDistance += d
            }
            tripLast = p.pos
            if (p.speed > 1.0) tripMoveSec += 1
            if (p.speed > tripTop) tripTop = p.speed
            val lp = tripPts.lastOrNull()
            if ((lp == null || Geo.dist(lp, p.pos) > 80) && tripPts.size < 3000) tripPts.add(p.pos)
            if (tripMoveSec >= breakNext && prefs.b(PreferenceKeys.BREAKREMIND, true)) {
                val hrs = breakNext / 3600
                breakNext += 7200
                raiseAlert("break", "You have been driving for $hrs hours. Time for a break?", 8000)
                speak("You have been driving for $hrs hours. Consider taking a break.", true)
                if (route == null) loadNearby("fuel", false)
            }
        } else if (p.speed > 3.0) {
            tripOn = true; tripT0 = now; tripDistance = 0.0; tripMoveSec = 0; tripTop = p.speed; tripLast = p.pos
            breakNext = 7200L; tripPts = arrayListOf(p.pos)
        }
        // ---- end of trip (stopped for two minutes) ----
        if (p.speed > 4.0) { drove = true; stillSince = 0L }
        else if (p.speed < 0.8 && drove) {
            if (stillSince == 0L) stillSince = now
            else if (now - stillSince > 120_000) {
                if (tripOn && tripDistance > 300) {
                    trips.add(Trip(tripT0, stillSince, tripDistance, tripMoveSec, tripTop, tripDest))
                }
                tripOn = false
                drove = false; stillSince = 0L
            }
        }
        if (dest != null) tripDest = dest?.name.orEmpty()
    }

    /** Finds [q] and starts routing there (used for calendar appointments). */
    fun routeToText(q: String, then: () -> Unit) {
        if (!online()) { toast("No signal to search for that", 4000); return }
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { netGeocode(countryCode(), q, fix?.pos ?: map.center()) }.firstOrNull()
                if (p == null) toast("Could not find \"$q\"", 3500) else { then(); startRoute(p) }
            } catch (e: Exception) { toast("Search failed: ${e.message}", 4000) }
        }
    }

    /** Finds a place by text and returns it with the driving time in minutes from here. */
    suspend fun travelTo(q: String): Pair<Place, Int>? = withContext(Dispatchers.IO) {
        try {
            val from = fix?.pos ?: return@withContext null
            val pl = netGeocode(countryCode(), q, from).firstOrNull() ?: return@withContext null
            val r = netRoutes(from, pl.pos, false).firstOrNull() ?: return@withContext null
            Pair(pl, Math.round(r.time / 60.0).toInt())
        } catch (e: Exception) { null }
    }

    // ---- nearby fuel / charging / parking ----
    fun clearNearby() { nearby = emptyList(); nearbyKind = null; nearbyBusy = false; stopMode = false }

    private fun distToRoute(r: Route, p: LL): Double {
        var best = 1e9
        var i = lastIdx.coerceIn(0, max(0, r.pts.size - 1))
        while (i < r.pts.size) { best = min(best, Geo.dist(r.pts[i], p)); i += 3 }
        return best
    }

    /**
     * Finds fuel, chargers, parking, food, coffee, toilets or services. With a route, it looks along the road ahead
     * (sorted by how far ahead, with roughly how long the detour adds); otherwise around you.
     * Fuel uses live Fuel Finder prices when you have added your Fuel Finder details in Settings.
     */
    /**
     * One tap from the stops box: finds [kind] along the route ahead and adds the best one as a stop straight away (fuel: the
     * cheapest once a little detour time is counted; everything else: the shortest detour), so nothing needs typing while driving.
     */
    fun addBestStop(kind: String) { loadNearby(kind, false, pickBest = true) }

    fun loadNearby(kind: String, nearDest: Boolean, pickBest: Boolean = false) {
        val centre = (if (nearDest) dest?.pos else null) ?: fix?.pos ?: map.center() ?: run { toast("No position yet"); return }
        val origin = fix?.pos ?: centre
        val rt = route
        val alongRoute = rt != null && !nearDest && kind != "parking"
        val progress = if (rt != null && !previewing) curProgress else 0.0
        val radius = if (nearDest) 900 else if (kind == "parking") 2000 else if (kind == "toilets" || kind == "coffee") 3000 else 6000
        val fuelType = prefs.s(PreferenceKeys.FUEL_TYPE, "E10")
        // (Fuel Finder has no E85 prices, and its station list would show every station rather than ones selling it)
        val fuelLive = kind == "fuel" && FuelFinder.configured(prefs) && fuelType != "E85"
        nearbyKind = kind; nearbyBusy = true; nearby = emptyList()
        scope.launch {
            try {
                // the search runs on its own, and the 30 s limit just stops waiting for it: a blocked network read used to hold the
                // limit up too (the timeout can't end a read that's waiting on a server), so a busy server left it spinning for minutes
                val work = searchScope.async {
                    if (fuelLive) {
                        try { FuelFinder.refresh(ctx, prefs) } catch (e: Exception) { /* use what is stored */ }
                        val st = FuelFinder.stations(ctx)
                        if (st.isNotEmpty()) {
                            val out = ArrayList<Nearby>()
                            // only stations inside the box around the next 120 km of route are checked against the route itself
                            var bMinLat = 90.0; var bMaxLat = -90.0; var bMinLon = 180.0; var bMaxLon = -180.0
                            if (alongRoute && rt != null) for (i in rt.pts.indices) {
                                if (rt.cum[i] < progress - 200 || rt.cum[i] > progress + 120_000) continue
                                val q = rt.pts[i]
                                bMinLat = min(bMinLat, q.lat); bMaxLat = max(bMaxLat, q.lat); bMinLon = min(bMinLon, q.lon); bMaxLon = max(bMaxLon, q.lon)
                            }
                            for (s0 in st) {
                                if (alongRoute && rt != null) {
                                    if (s0.pos.lat < bMinLat - 0.015 || s0.pos.lat > bMaxLat + 0.015 || s0.pos.lon < bMinLon - 0.025 || s0.pos.lon > bMaxLon + 0.025) continue
                                    val pr = RouteExtras.project(rt, s0.pos, 1500.0, progress) ?: continue
                                    if (pr.first < progress - 100 || pr.first - progress > 120_000) continue
                                    val price = FuelFinder.priceOf(ctx, s0.id, fuelType)
                                    out += Nearby(Place(s0.name, listOf(s0.brand, s0.postcode).filter { it.isNotBlank() && it != s0.name }.joinToString(" · "), s0.pos),
                                        pr.first - progress, priceExtra(price, s0.motorway), price?.pence, detour(pr.second), true)
                                } else {
                                    val dd = Geo.dist(centre, s0.pos)
                                    if (dd > radius) continue
                                    val price = FuelFinder.priceOf(ctx, s0.id, fuelType)
                                    out += Nearby(Place(s0.name, listOf(s0.brand, s0.postcode).filter { it.isNotBlank() && it != s0.name }.joinToString(" · "), s0.pos),
                                        Geo.dist(origin, s0.pos), priceExtra(price, s0.motorway), price?.pence)
                                }
                            }
                            if (out.isNotEmpty()) return@async out
                        }
                    }
                    // outside the UK: live prices from the country's own price service (France, Austria, Germany with a key)
                    val cc = detectedCountry.uppercase()
                    if (kind == "fuel" && cc != "GB" && EuFuel.supported(cc, prefs) && EuFuel.hasFuel(cc, fuelType) && online()) {
                        val eu = if (alongRoute && rt != null) EuFuel.along(cc, prefs, RouteExtras.line(rt, progress, progress + 60_000.0, 1500.0, 45), fuelType)
                            else EuFuel.near(cc, prefs, centre, radius / 1000 + 1, fuelType)
                        val out = ArrayList<Nearby>()
                        for (s0 in eu) {
                            if (alongRoute && rt != null) {
                                val pr = RouteExtras.project(rt, s0.pos, 1500.0, progress) ?: continue
                                if (pr.first < progress - 100) continue
                                out += Nearby(Place(s0.name, s0.sub, s0.pos), pr.first - progress, "", s0.cents, detour(pr.second), true, unit = "€")
                            } else if (Geo.dist(centre, s0.pos) <= radius) out += Nearby(Place(s0.name, s0.sub, s0.pos), Geo.dist(origin, s0.pos), "", s0.cents, unit = "€")
                        }
                        if (out.isNotEmpty()) return@async out
                    }
                    // bioethanol: only stations mapped as selling E85 (TomTom's search can't tell which do)
                    val e85 = kind == "fuel" && fuelType == "E85"
                    if (e85) {
                        return@async if (alongRoute && rt != null) {
                            Overpass.nearbyAlong("fuel_e85", RouteExtras.line(rt, progress, progress + 60_000.0, 1500.0, 45), 1500).mapNotNull { n ->
                                val pr = RouteExtras.project(rt, n.place.pos, 2000.0, progress) ?: return@mapNotNull null
                                if (pr.first < progress - 100) null else n.copy(dist = pr.first - progress, detourMin = detour(pr.second), ahead = true)
                            }
                        } else Overpass.nearby("fuel_e85", centre, 15_000).map { it.copy(dist = Geo.dist(origin, it.place.pos)) }
                    }
                    // TomTom's place search first (quick, and it knows the detour time along a route); OpenStreetMap if that fails
                    if (hasOnline && online()) {
                        val tt = try {
                            if (alongRoute && rt != null && provider == "tomtom") {
                                val line = RouteExtras.line(rt, progress, progress + 80_000.0, 600.0, 140)
                                TomTomRest.poiAlong(apiKey, kind, line).mapNotNull { (n, secs) ->
                                    val pr = RouteExtras.project(rt, n.place.pos, 3000.0, progress) ?: return@mapNotNull null
                                    if (pr.first < progress - 100) null
                                    else n.copy(dist = pr.first - progress, detourMin = if (secs >= 0) Math.round(secs / 60.0).toInt() else detour(pr.second), ahead = true)
                                }
                            } else if (alongRoute && rt != null) null   // (along the route: Mapbox can't search along a line, so OpenStreetMap does it below)
                            else netPoiNearby(kind, centre, radius).map { it.copy(dist = Geo.dist(origin, it.place.pos)) }
                        } catch (e: Exception) { null }
                        if (!tt.isNullOrEmpty()) return@async tt
                    }
                    if (alongRoute && rt != null) {
                        val line = RouteExtras.line(rt, progress, progress + 60_000.0, 1500.0, 45)
                        // Mapbox first (a few points along the next 40 km, asked all at once: a second or two); the free OpenStreetMap
                        // servers only when it finds nothing or there's no Mapbox key (they're often busy and can take a long time)
                        val fromMapbox = if (mbToken.isNotBlank() && online()) (try { mapboxAlong(kind, line) } catch (e: Exception) { emptyList() }) else emptyList()
                        val raw = fromMapbox.ifEmpty { Overpass.nearbyAlong(kind, line, if (kind == "services") 400 else 900) }
                        raw.mapNotNull { n ->
                            val pr = RouteExtras.project(rt, n.place.pos, 1200.0, progress) ?: return@mapNotNull null
                            if (pr.first < progress - 100) null else n.copy(dist = pr.first - progress, detourMin = detour(pr.second), ahead = true)
                        }
                    } else Overpass.nearby(kind, centre, radius).map { it.copy(dist = Geo.dist(origin, it.place.pos)) }
                }
                val list = kotlinx.coroutines.withTimeoutOrNull(30_000L) { work.await() }
                if (list == null) { work.cancel(); toast("Search took too long. Check the signal and try again.", 4000); clearNearby(); return@launch }
                if (pickBest) {
                    val ahead = list.filter { it.ahead || route == null }
                    val best = if (ahead.any { it.price != null }) ahead.filter { it.price != null && it.detourMin in 0..10 }.minByOrNull { (it.price ?: 999.0) + it.detourMin }
                        else ahead.filter { it.detourMin >= 0 }.minByOrNull { it.detourMin * 1000.0 + it.dist / 100.0 } ?: ahead.minByOrNull { it.dist }
                    nearbyBusy = false
                    if (best == null) { toast("Nothing found along the route ahead", 3000); clearNearby() }
                    else { clearNearby(); addStop(best.place) }
                    return@launch
                }
                nearby = sortNearby(list).take(15)
                if (nearby.isEmpty()) toast(if (alongRoute) "Nothing found along the route ahead" else "Nothing found nearby", 3000)
            } catch (e: Exception) { toast(searchFailText(e), 4500) }
            finally { nearbyBusy = false }
        }
    }

    /** Places along the route ahead from Mapbox: its category search round a point every 8 km or so (at most 5, asked together). */
    private suspend fun mapboxAlong(kind: String, line: List<LL>): List<Nearby> {
        val pts = ArrayList<LL>(); var last: LL? = null
        for (q in line) if (last == null || Geo.dist(last, q) > 8000.0) { pts += q; last = q }
        val found = kotlinx.coroutines.coroutineScope {
            pts.take(5).map { q -> async(Dispatchers.IO) { try { MapboxRest.poiNearby(mbToken, kind, q, 2500) } catch (e: Exception) { emptyList() } } }.map { it.await() }
        }
        val seen = HashSet<String>(); val out = ArrayList<Nearby>()
        for (l in found) for (n in l) if (seen.add(String.format(java.util.Locale.US, "%.4f,%.4f", n.place.pos.lat, n.place.pos.lon))) out += n
        return out
    }

    /** The zoom you've set with + and - becomes your normal zoom when not on a route (Settings > Map look to change or reset). */
    fun saveZoomAsDefault() {
        val v = (defaultZoom + zoomOffset).coerceIn(-2.0, 2.0)
        prefs.put(PreferenceKeys.DEFAULT_ZOOM, String.format(java.util.Locale.US, "%.2f", v))
        zoomOffset = 0.0
        fix?.let { onPosition(it, true) }
        toast("This is now your default zoom. Change or reset it in Settings > Map look", 4500)
    }

    /** Phone: sends [p] to the paired head unit, which offers to start it (next time it's on, within 12 hours). */
    fun sendToCar(p: Place) {
        val code = prefs.s(PreferenceKeys.LINK_CODE, "")
        if (code.isBlank()) { toast("Pair with your car first: Settings > Places & trips", 4000); return }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { CarLink.send(code, p) }
                toast("Sent to your car: ${p.name}", 3500)
                if (previewing) endRoute()
            } catch (e: Exception) { toast("Couldn't send it: " + (e.message ?: "no signal"), 4500) }
        }
    }

    /** Head unit: the place sent from the phone has been started or turned down. */
    fun takePhoneDest(go: Boolean) {
        val p = phoneDest ?: return
        phoneDest = null
        if (go) startRoute(p)
    }

    /** A search failure in plain words (it used to show the servers' own errors, like "HTTP 429" or "HTTP 502"). */
    private fun searchFailText(e: Exception): String {
        val m = (e.message ?: "").lowercase()
        return when {
            m.contains("no signal") || m.contains("unable to resolve") || m.contains("failed to connect") -> "No signal, so the search couldn't run. Try again in a moment."
            m.contains("429") || m.contains("502") || m.contains("503") || m.contains("504") || m.contains("busy") || m.contains("timeout") || m.contains("timed out") ->
                "The free map servers are busy right now. Try again in a minute." + (if (!hasMapbox) " Add a Mapbox key in Settings > Keys & accounts for more reliable searches." else "")
            m.contains("mapbox") -> e.message ?: "Search failed"
            else -> "Search failed. Try again in a moment."
        }
    }

    private fun priceExtra(p: FuelFinder.Price?, motorway: Boolean): String =
        listOfNotNull(p?.let { FuelFinder.ageText(it.updated).let { a -> if (a.isNotBlank()) "updated $a" else null } }, if (motorway) "Motorway services" else null).joinToString(" · ")

    /** Rough extra minutes to call somewhere [off] metres from the route: there and back at town speed, plus a minute. */
    private fun detour(off: Double): Int = Math.round((2.0 * off / 11.0 + 60.0) / 60.0).toInt()

    private fun sortNearby(l: List<Nearby>): List<Nearby> =
        if (nearbySort == "cheap" && l.any { it.price != null }) l.sortedWith(compareBy<Nearby> { it.price ?: 9999.0 }.thenBy { it.dist }) else l.sortedBy { it.dist }

    fun changeNearbySort(mode: String) { nearbySort = mode; nearby = sortNearby(nearby) }

    /** A destination shared from another app: routes straight to coordinates, or puts text in the search box. */
    fun openShared(t: SharedTarget) {
        externalTick++
        val p = t.pos
        if (p != null) {
            val pl = Place(t.name, "Shared from another app", p)
            startRoute(pl)
            try { RouteSync.offerToCar(this, pl) } catch (e: Exception) { }
        }
        else if (!t.query.isNullOrBlank()) externalQuery = t.query
    }

    // ---- guidance ----
    private fun snap(r: Route, p: LL): RouteSnap {
        val result = RouteMatcher.snap(r, p, lastIdx)
        lastIdx = result.segmentIndex
        return result
    }

    private var lastPosAt = 0L
    private var posDt = 1000L
    private var lastMeasured: Fix? = null

    // ---- glide: the arrow and camera are moved every frame by dead reckoning, and only gently corrected by each GPS fix ----
    private var gPos: LL? = null
    private var gHead = 0.0
    private var gSpeed = 0.0
    private var gProg = 0.0
    private var glideRoute: Route? = null
    private var glideOnRoute = false
    private var mAt = 0L
    private var mProg = 0.0
    private var mSpeed = 0.0
    private var mPos: LL? = null
    private var mHead = 0.0
    // camera targets (set once per fix) and the eased values actually shown
    private var tBMode = 2
    private var tTilt = 0.0
    private var tZoom = 16.0
    private var tPad = 0.0
    private var tLeft = 0.0
    private var camTargetAt = 0L
    private var camReady = false
    private var camCatchUp = false
    private var glCamPos: LL = LL(0.0, 0.0)
    private var camB = 0.0
    private var camT = 0.0
    private var camZ = 16.0
    private var camPad = 0.0
    private var camLeft = 0.0

    private fun angDiff(a: Double, b: Double): Double = ((a - b + 540.0) % 360.0 + 360.0) % 360.0 - 180.0
    private fun easeAngle(cur: Double, tgt: Double, dt: Double, tau: Double): Double =
        ((cur + angDiff(tgt, cur) * (1.0 - Math.exp(-dt / tau))) % 360.0 + 360.0) % 360.0

    private var glideRunning = false
    /** The app is on screen (set by the activity): in the background the per-frame work drops right down. */
    @Volatile var appVisible = true
    private var gLastNs = 0L
    private var gGuideAt = 0L
    private var gFullAt = 0L

    /** Runs the glide once per screen refresh (vsync), so the arrow and camera advance in step with what is actually drawn. */
    private fun ensureGlide() {
        if (glideRunning) return
        glideRunning = true
        gLastNs = 0L
        val ch = android.view.Choreographer.getInstance()
        ch.postFrameCallback(object : android.view.Choreographer.FrameCallback {
            private var lastFrame = 0L
            override fun doFrame(frameTimeNanos: Long) {
                val before = PerfStats.ticks
                val t0 = System.nanoTime()
                try { glideTick(frameTimeNanos) } catch (e: Exception) { android.util.Log.w("NavEngine", "glide failed", e) }
                if (PerfStats.on) {
                    if (lastFrame != 0L) PerfStats.gapMsMax = maxOf(PerfStats.gapMsMax, (frameTimeNanos - lastFrame) / 1_000_000L)
                    if (PerfStats.ticks != before) { val d = System.nanoTime() - t0; PerfStats.tickNsSum += d; PerfStats.tickNsMax = maxOf(PerfStats.tickNsMax, d) }
                }
                lastFrame = frameTimeNanos
                ch.postFrameCallback(this)
            }
        })
    }

    // ---- automatic frame rate ----
    /** Camera updates a second in the automatic mode: starts at 30 and steps down when the head unit can't draw that many. */
    private var autoFps = 30
    private var paceAtNs = 0L
    private var paceRenders = 0
    private var paceTicks = 0
    private var paceLow = 0
    private var paceHigh = 0

    /**
     * Every 2 seconds while the camera is following you on the move: if the map drew noticeably fewer frames than the camera
     * moves asked for, the head unit is falling behind and drops frames unevenly (that's the jerky lag). A lower, steady rate
     * looks smoother than a higher one with gaps, so it steps down to 25 and then 20, and back up when there's room again.
     */
    private fun autoPace(frameNs: Long) {
        if (frameNs - paceAtNs < 2_000_000_000L) return
        val renders = PerfStats.renders - paceRenders
        val ticks = PerfStats.ticks - paceTicks
        paceAtNs = frameNs; paceRenders = PerfStats.renders; paceTicks = PerfStats.ticks
        if (prefs.s(PreferenceKeys.MAP_FPS, "auto") != "auto") return
        val moving = following && !previewing && camReady && gSpeed > 2.0
        if (!moving || ticks < autoFps) { paceLow = 0; return }
        val ratio = renders.toDouble() / ticks
        if (ratio < 0.8) {
            paceHigh = 0
            if (++paceLow >= 2 && autoFps > 20) { autoFps = if (autoFps > 25) 25 else 20; paceLow = 0 }
        } else if (ratio > 0.97) {
            paceLow = 0
            if (++paceHigh >= 10 && autoFps < 30) { autoFps = if (autoFps < 25) 25 else 30; paceHigh = 0 }
        }
    }

    /** One line for the performance meter, refreshed every second while it's turned on. */
    var perfLine by mutableStateOf("")
    private var perfRenders = 0; private var perfTicks = 0; private var perfNs = 0L

    private fun perfSample() {
        val r = PerfStats.renders - perfRenders; val t = PerfStats.ticks - perfTicks
        val ns = PerfStats.tickNsSum - perfNs
        perfRenders = PerfStats.renders; perfTicks = PerfStats.ticks; perfNs = PerfStats.tickNsSum
        val p = prefs.s(PreferenceKeys.MAP_FPS, "auto")
        val cam = if (p == "auto") "$autoFps auto" else p
        perfLine = "Map $r fps · camera $t/s ($cam) · app " + String.format(Locale.UK, "%.1f", if (t > 0) ns / t / 1e6 else 0.0) +
            " ms · longest pause ${PerfStats.gapMsMax} ms · lights ${try { map.signalShort() } catch (e: Throwable) { "?" }}"
        PerfStats.gapMsMax = 0
    }

    private var fpsReadNs = 0L
    private var vsyncLastNs = 0L
    private var vsyncNs = 16_666_667.0
    private var frameCount = 0L
    private var glideFps = 30

    private fun glideTick(frameNs: Long) {
        // never do more work than the map can actually draw: on a slow head unit this frees the CPU for rendering
        if (frameNs - fpsReadNs > 1_000_000_000L) { fpsReadNs = frameNs; val p = prefs.s(PreferenceKeys.MAP_FPS, "auto"); glideFps = if (p == "auto") autoFps else (p.toIntOrNull() ?: 30).coerceIn(15, 60) }
        // with another app in front (guidance carrying on in the background) a few updates a second is plenty: nobody sees the map
        val fps = if (appVisible) glideFps else 5
        // update on a steady beat of whole screen frames (every frame at 60, every 2nd at 30, ...), so the map draws at perfectly even
        // intervals; skipping by elapsed time drifted against the screen's own timing and gave an uneven, slightly jumpy motion
        if (frameNs - vsyncLastNs in 1..100_000_000L) vsyncNs = vsyncNs * 0.9 + (frameNs - vsyncLastNs) * 0.1
        vsyncLastNs = frameNs
        val every = Math.round(1e9 / fps / vsyncNs).toInt().coerceIn(1, 4)
        if (++frameCount % every != 0L) return
        PerfStats.ticks++
        // the flowing light only while you're driving the route (in the overview it kept the map redrawing while tiles loaded)
        if (following && !previewing && appVisible) map.flowFrame()
        autoPace(frameNs)
        val now = android.os.SystemClock.elapsedRealtime()
        val dt = if (gLastNs == 0L) 0.016 else ((frameNs - gLastNs) / 1e9).coerceIn(0.002, 0.1)
        gLastNs = frameNs
        val f = fix ?: return
        // in a tunnel (no fix for a while) the arrow keeps going along the route at the last speed for up to two minutes,
        // instead of stopping and then jumping when the signal comes back
        val drOk = glideOnRoute && glideRoute != null && mSpeed > 3.0 && !previewing && simMode == 0
        val rawAge = (now - mAt) / 1000.0
        val age = rawAge.coerceIn(0.0, if (drOk) 120.0 else 2.5)
        val dr = drOk && rawAge > 3.0 && rawAge < 120.0
        if (dr != deadReckoning) deadReckoning = dr
        if (dr) camTargetAt = now
        gSpeed += (mSpeed - gSpeed) * (1.0 - Math.exp(-dt / 1.2))
        if (mSpeed < 0.2 && gSpeed < 0.15) gSpeed = 0.0
        val r = glideRoute
        var lookB = gHead
        if (glideOnRoute && r != null) {
            // distance along the route: run on at the current speed, nudge towards where the latest fix says we are, and never go backwards
            val target = mProg + gSpeed * age
            var np = gProg + gSpeed * dt
            val err = target - np
            np = if (abs(err) > 60.0) target else np + (err * (1.0 - Math.exp(-dt / 2.5))).coerceIn(-(0.3 * gSpeed + 0.4) * dt, (0.3 * gSpeed + 0.4) * dt)
            if (np < gProg && abs(err) <= 60.0) np = gProg
            gProg = min(np, r.len)
            val cur = pointAt(r, gProg).first
            val ahead = pointAt(r, gProg + 12.0).first
            if (Geo.dist(cur, ahead) > 2.0) gHead = easeAngle(gHead, Geo.bearing(cur, ahead), dt, 0.18)
            gPos = cur
            // the camera looks further ahead (and further still through a roundabout) so it follows the road, not every wiggle of the wheel
            val round = turn?.let { it.maneuver.uppercase().contains("ROUNDABOUT") && it.dist < 90.0 } == true
            val la = pointAt(r, gProg + (if (round) 60.0 else 30.0)).first
            lookB = if (Geo.dist(cur, la) > 3.0) Geo.bearing(cur, la) else gHead
        } else {
            val gp = gPos ?: f.pos
            val mp = mPos ?: f.pos
            val rad = Math.toRadians(mHead)
            val kLon = 111320.0 * cos(Geo.rad(gp.lat))
            val predLat = (mp.lat) + cos(rad) * gSpeed * age / 110574.0
            val predLon = (mp.lon) + Math.sin(rad) * gSpeed * age / kLon
            var nLat = gp.lat + cos(rad) * gSpeed * dt / 110574.0
            var nLon = gp.lon + Math.sin(rad) * gSpeed * dt / kLon
            val eN = (predLat - nLat) * 110574.0; val eE = (predLon - nLon) * kLon
            if (hypot(eN, eE) > 80.0) { nLat = predLat; nLon = predLon }
            else { val k = 1.0 - Math.exp(-dt / 0.9); nLat += (predLat - nLat) * k; nLon += (predLon - nLon) * k }
            gPos = LL(nLat, nLon)
            if (mSpeed > 1.5) gHead = easeAngle(gHead, mHead, dt, 0.35)
            lookB = gHead
        }
        val gp = gPos ?: return
        map.setMe(gp, gHead, 0)
        if (glideOnRoute && r != null && !previewing) {
            if (now - gDoneAt >= (if (following) 350L else 1500L)) { gDoneAt = now; updateDone(r, gProg) }
        } else if (doneShown) { map.setDone(emptyList()); doneShown = false }

        // guidance (distance to the next turn, lane info, spoken prompts) follows the glided position, a few times a second
        if (glideOnRoute && !previewing && now - gGuideAt >= 250L) { gGuideAt = now; val full = now - gFullAt >= 1000L; if (full) gFullAt = now; updateGuidance(gProg, !full) }

        // camera
        val fresh = now - camTargetAt < 4000
        if (!following || previewing || !fresh) { camReady = false; return }
        if (!camReady) {
            val c = map.cameraNow()
            if (c != null) { glCamPos = LL(c[0], c[1]); camB = c[2]; camT = c[3]; camZ = c[4]; camPad = c[5]; camLeft = c[6] }
            else { glCamPos = gp; camB = lookB; camT = tTilt; camZ = tZoom; camPad = tPad; camLeft = tLeft }
            camCatchUp = true; camReady = true; lastCamSet = null
        }
        if (camCatchUp) {
            // gliding in from the overview to the car: a little gentler, so the move (and the map loading in) feels smooth
            val k = 1.0 - Math.exp(-dt / 0.4)
            glCamPos = LL(glCamPos.lat + (gp.lat - glCamPos.lat) * k, glCamPos.lon + (gp.lon - glCamPos.lon) * k)
            if (Geo.dist(glCamPos, gp) < 1.5) camCatchUp = false
        } else glCamPos = gp
        fun ease(c: Double, t: Double, tau: Double) = c + (t - c) * (1.0 - Math.exp(-dt / tau))
        // slower, softer easing: zoom, tilt and framing glide into place instead of nudging
        camZ = ease(camZ, tZoom, 0.9)
        camT = ease(camT, tTilt, 0.8)
        camPad = ease(camPad, tPad, 0.8)
        camLeft = ease(camLeft, tLeft, 0.9)
        val tb = when (tBMode) { 0 -> 0.0; 1 -> lookB; else -> camB }
        var d = angDiff(tb, camB)
        // a small dead-band and a gentle turn rate: slight wheel movements and small roundabouts no longer swing the map around
        // a soft dead-band (instead of a hard 5 degree one that held, then suddenly caught up): tiny wobbles are ignored, and the turn
        // fades in smoothly as the difference grows
        if (tBMode == 1) { val f = (abs(d) / 9.0).coerceIn(0.0, 1.0); d *= f * f * (3 - 2 * f) }
        val step = d * (1.0 - Math.exp(-dt / 1.05))
        val maxStep = (if (camCatchUp) 140.0 else 32.0) * dt
        camB = ((camB + step.coerceIn(-maxStep, maxStep)) % 360.0 + 360.0) % 360.0
        // nothing has moved (parked, or waiting at lights): don't ask the map to draw the same picture again,
        // which lets it sit idle instead of redrawing every frame (a big saving in heat and battery)
        val lc = lastCamSet
        if (lc != null && abs(lc[0] - glCamPos.lat) < 2e-8 && abs(lc[1] - glCamPos.lon) < 2e-8 && abs(angDiff(lc[2], camB)) < 0.03 &&
            abs(lc[3] - camT) < 0.03 && abs(lc[4] - camZ) < 0.002 && abs(lc[5] - camPad) < 0.0005 && abs(lc[6] - camLeft) < 0.0005) return
        lastCamSet = doubleArrayOf(glCamPos.lat, glCamPos.lon, camB, camT, camZ, camPad, camLeft)
        map.cameraSet(glCamPos, camB, camT, camZ, camPad, camLeft)
    }
    private var lastCamSet: DoubleArray? = null

    private var movingNow = false
    /** In portrait: the share of the screen height covered by the trip card at the bottom (the locator is kept above it). */
    var bottomCover = 0.0

    private fun onPosition(p: Fix, force: Boolean) {
        fix = p
        // start/stop moving with a little hysteresis (the full map rests its shop labels while you drive)
        if (!movingNow && p.speed > 4.0) { movingNow = true; map.setMoving(true) }
        else if (movingNow && p.speed < 1.5) { movingNow = false; map.setMoving(false) }
        val tNow = android.os.SystemClock.elapsedRealtime()
        val newFix = p !== lastMeasured
        if (newFix) {
            lastMeasured = p
            posDt = if (lastPosAt == 0L) 1000L else (tNow - lastPosAt).coerceIn(500L, 2500L)
            lastPosAt = tNow
            try { updateRoadInfo(p); trackParking(p) } catch (e: Exception) { android.util.Log.w("NavEngine", "road info failed", e) }
            try { checkArea(p.pos) } catch (e: Exception) { }
        }
        val r = route
        val s = if (!previewing && r != null && r.pts.size >= 2) snap(r, p.pos) else null
        // how far from the route counts as having left it: tighter than before, but widened when the GPS is less certain
        // Ignore very poor fixes for off-route decisions: they can drift across parallel roads and trigger false reroutes.
        // Still use the fix for display/glide fallback, but require a credible position before declaring a route departure.
        val gpsReliableForReroute = NavigationPolicy.gpsReliableForReroute(p.acc)
        val offLim = NavigationPolicy.offRouteLimit(p.acc)
        // Do not carry a partial off-route count through a bad GPS fix: otherwise one noisy fix can
        // combine with an older departure and cause a reroute when accuracy briefly improves.
        if (newFix && !gpsReliableForReroute) offCount = 0
        // having gone a different way, the closest bit of the route can be much further along it (a road that runs near the end of
        // the route, say). That isn't progress: it must not jump you there (it once said "you have arrived" with 5 minutes to go)
        val nowT = android.os.SystemClock.elapsedRealtime()
        val jump = s != null && trustProg >= 0.0 && NavigationPolicy.isProgressJump(
            candidateProgressMeters = s.progress,
            trustedProgressMeters = trustProg,
            speedMps = p.speed,
            elapsedSeconds = (nowT - trustAt) / 1000.0
        )
        val onR = r != null && s != null && !jump && s.dist < (if (glideOnRoute) offLim + 12.0 else 45.0)
        if (onR && s != null && newFix && s.dist <= offLim) { trustProg = s.progress; trustAt = nowT }
        if (newFix) {
            mAt = tNow; mSpeed = p.speed
            if (onR && r != null && s != null) {
                if (glideRoute !== r || gPos == null) { glideRoute = r; gProg = s.progress; gPos = s.point; gHead = p.heading }
                mProg = s.progress
                glideOnRoute = true
            } else {
                glideRoute = null; glideOnRoute = false
                mPos = if (p.speed > 2.0 && prefs.b(PreferenceKeys.SNAPROAD, true)) (Overpass.snapToRoad(p.pos, p.heading) ?: map.snapToRoad(p.pos, p.heading) ?: p.pos) else p.pos
                mHead = p.heading
                val gp = gPos
                if (gp == null || Geo.dist(gp, p.pos) > 150.0) { gPos = p.pos; gHead = p.heading }
            }
        }
        ensureGlide()
        // camera target for this fix; the glide loop eases the shown camera towards it every frame
        if (!previewing && following) {
            val hdg = if (onR && r != null && s != null) gHead else p.heading
            followCamera(Fix(gPos ?: p.pos, hdg, p.speed, p.alt, p.acc), onR)
        }
        if (previewing) return
        if (r == null || r.pts.size < 2 || s == null) return
        if (gpsReliableForReroute && (s.dist > offLim || jump)) {
            if (newFix) {
                // well off the route counts double, so a wrong turn is noticed in about two seconds instead of four or more
                offCount += NavigationPolicy.offRouteStrikeCount(jump, s.dist, offLim)
                if (offCount >= 3) reroute()
            }
        } else if (gpsReliableForReroute && newFix) {
            // Still close to the route, but a vehicle moving away from its direction can indicate a wrong turn.
            val seg = Geo.bearing(r.pts[s.segmentIndex], r.pts[min(s.segmentIndex + 1, r.pts.size - 1)])
            if (NavigationPolicy.shouldCountHeadingAwayStrike(s.dist, p.speed, angDiff(seg, p.heading))) {
                if (++offCount >= 3) reroute()
            } else offCount = 0
        } else if (newFix) offCount = 0
        val now = System.currentTimeMillis()
        // while the map follows you, only a window of the route is drawn (cheap to redraw); once you move or zoom the map yourself,
        // the whole rest of the route is drawn, so zooming out shows all of it (it used to stop a few miles ahead)
        val wantWhole = !following
        if (force || lastDraw == 0L || wantWhole != drawnWhole || abs(s.progress - lastDrawProg) > 500.0 || now - lastDraw > 30_000L) {
            lastDraw = now; lastDrawProg = s.progress; drawnWhole = wantWhole
            // a little behind the car (hidden under the grey trail) to well ahead: further at motorway speed, where the view reaches further
            val ahead = if (wantWhole) r.len else if (p.speed > 25.0) 25_000.0 else 12_000.0
            val i0 = idxAt(r, s.progress - 900.0)
            val i1 = min(r.pts.size - 1, idxAt(r, s.progress + ahead) + 1)
            if (i1 > i0) map.setMainOnly(r.pts.subList(i0, i1 + 1), r.sev, i0)
        }
        // off the route, the distance along it stays where you left it (until the new route comes) instead of following the nearest bit
        val guideProg = if (onR) gProg else if (!jump && s.dist <= offLim) s.progress else if (trustProg >= 0.0) trustProg else s.progress
        onRouteNow = onR
        if (onR) curProg = guideProg
        if (onR && newFix) try { applyRouteLimit(r, guideProg, p) } catch (e: Exception) { }
        updateGuidance(guideProg, onR && android.os.SystemClock.elapsedRealtime() - gFullAt < 1500L)
        syncPulse()
    }

    /** Makes the route line pulse on the map when a turn is close. */
    private var nearTurn = false
    private var pulseUntil = 0L

    private fun syncPulse() {
        val near = !previewing && route != null && (turn?.dist ?: 1e9) < 150.0
        val now = System.currentTimeMillis()
        if (!near) nearTurn = false
        else if (!nearTurn) { nearTurn = true; pulseUntil = now + 5000 }
        map.setTurnPulse(prefs.b(PreferenceKeys.AMBREACT, true) && prefs.b(PreferenceKeys.AMBMAP, true) && near && now < pulseUntil)
    }

    /** Zoom that adapts: closer when a turn is near or the destination is close, wider on fast roads with a long way to go. */
    private var smoothZ = 0.0
    private var smoothZAt = 0L
    // Start each app session with a slightly wider overview. Once real movement is detected,
    // hand control back to the existing speed-based zoom curve for the rest of the session.
    private var hasDetectedMovement = false

    private fun speedZoom(sp: Double): Double = NavigationPolicy.speedZoom(sp)

    /** How far into the junction close-up the camera is (0 none, 1 fully in). */
    private var closeF = 0.0

    private fun autoZoom(sp: Double, navigating: Boolean): Double {
        if (sp > 1.5) hasDetectedMovement = true
        val now = android.os.SystemClock.uptimeMillis()
        speedLog.addLast(now to sp)
        while (speedLog.isNotEmpty() && now - speedLog.first().first > 8000L) speedLog.removeFirst()
        var z = if (navigating) navZoom(sp) else speedZoom(sp)
        if (navigating) {
            // coming up to traffic lights: in close if you're slowing down (you may be stopping), only a little if you're carrying on
            val dl = nextLightDist()
            if (dl != null && dl < 180.0) {
                val near = ((180.0 - dl) / 180.0).coerceIn(0.0, 1.0)
                val peak = speedLog.maxOfOrNull { it.second } ?: sp
                val slowing = sp < 3.0 || sp < peak * 0.72
                z += (if (slowing) 0.25 else 0.08) * near
            }
            val t = turn
            if (t != null) {
                // Ease closer on approach to a real manoeuvre, then hold a useful junction view.
                val near = ((650.0 - t.dist) / 650.0).coerceIn(0.0, 1.0)
                z += 0.38 * near
                if (sp > 18) z -= 0.3 * ((t.dist - 2500.0) / 2500.0).coerceIn(0.0, 1.0)

                // Painted lane arrows can be visible well before the turn card is in its final
                // close-up. Zoom in progressively for that whole lane-guidance section so the
                // road-level arrows are readable, especially on motorways and dual carriageways.
                if (laneKey != null && prefs.b(PreferenceKeys.LANEMAP, true)) {
                    val laneRange = if (laneFast) 1300.0 else 450.0
                    val laneNear = ((laneRange - t.dist) / laneRange).coerceIn(0.0, 1.0)
                    z += (if (laneFast) 0.95 else 0.65) * laneNear
                }
            }
            val e = eta
            if (e != null && e.dist < 300) z = max(z, 16.4)
        }
        // Free drive: let speed determine the overview. At walking/parking speeds stay close;
        // on faster roads pull back progressively. Do not apply a fixed extra zoom-out here:
        // it made the map much too distant when stopped and flattened the speed curve.
        if (!navigating) z -= 0.15 * (sp / 3.0).coerceIn(0.0, 1.0) +
            0.25 * ((sp - 3.0) / 17.0).coerceIn(0.0, 1.0) +
            0.35 * ((sp - 20.0) / 20.0).coerceIn(0.0, 1.0)
        // lane view: with lane strips showing, close in on the approach so the lanes are big enough to read at a glance
        // Leave headroom for the lane-guidance floor applied by followCamera().
        var top = 19.0
        val lt = turn
        if (navigating && laneKey != null && lt != null && lt.dist < (if (laneFast) 1300.0 else 450.0) && prefs.b(PreferenceKeys.LANE_VIEW, true)) {
            val laneNear = ((if (laneFast) 1300.0 else 450.0) - lt.dist) / (if (laneFast) 1300.0 else 450.0)
            z = max(z, 16.9 + 0.9 * laneNear.coerceIn(0.0, 1.0))
            top = 18.2
        }
        if (navigating && closeF > 0.0) { z = max(z, 17.5 + 0.35 * closeF); top = max(top, 18.2) }
        // (the first-open overview zoom-out is applied once, in followCamera; applying it here too doubled it)
        val target = (z - (if (navigating) 0.45 else 0.15))
            .coerceIn(if (navigating) 13.5 else 14.6, top - 0.3)
        // Dampen automatic zoom changes to prevent the camera pulsing in and out around junctions. The limit is per
        // second rather than per GPS fix: at one fix a second a fixed 0.1 step took 10+ seconds to zoom in for a turn.
        val dt = if (smoothZAt == 0L) 1.0 else ((now - smoothZAt) / 1000.0).coerceIn(0.05, 2.0)
        smoothZAt = now
        smoothZ = NavigationPolicy.smoothZoom(smoothZ, target, maxStep = 0.6 * dt)
        return smoothZ
    }

    private fun followCamera(p: Fix, navigating: Boolean) {
        val sp = p.speed
        val im = prefs.s(PreferenceKeys.MAP_THEME, "immersive") == "immersive"
        // Immersive shows more of the road ahead: the car sits lower on a steeper view
        // The padding pushes the locator down the window (centre sits at (1 + pad) / 2 of the height), in 2D and 3D, on and off a route
        val moving = navigating || sp > 2.0
        // the locator sits low on the screen so you see more of what's ahead (centre at (1 + pad) / 2 of the height)
        val flatPad = if (moving) 0.44 else 0.36
        var pad = if (tilt3d) (if (moving) 0.6 else 0.5) else flatPad
        var tilt = if (tilt3d) (if (im) 64.0 else 60.0) else 0.0
        // a lower view shows far fewer distant tiles and labels, which is a large saving on a slow GPU
        val lite = prefs.mapDetail() != "full"
        // the full map stays a touch lower than it used to (fewer distant tiles to draw), the light map lower still
        tilt = minOf(tilt, if (lite) 52.0 else 54.0)
        // tilt with speed: a little flatter when slow (easier to read streets), lower and further-looking at speed
        // (with the lighter map it only ever flattens, so it never draws more than before)
        if (tilt3d && prefs.b(PreferenceKeys.SPEEDTILT, true)) {
            val f = (sp / 31.0).coerceIn(0.0, 1.0)          // 0 when stopped, 1 at about 70 mph
            tilt += -9.0 * (1.0 - f)
        }
        // coming up to a junction or roundabout the view eases down to a flat 2D map so the turn is easy to read, then tilts back up once you are away from it
        if (navigating && tilt3d && prefs.b(PreferenceKeys.TURN2D, true)) {
            val t = turn
            // only for a real turn or roundabout, only in the last stretch before it, and not at speed
            if (t != null && sp < 22.0 && t.maneuver.uppercase().let { it.contains("ROUNDABOUT") || it.contains("TURN") || it.startsWith("SHARP") }) {
                val reach = (sp * 5.0).coerceIn(80.0, 130.0)
                val f = ((t.dist - 25.0) / (reach - 25.0)).coerceIn(0.0, 1.0)     // 0 at the turn, 1 when it is still a way off
                tilt *= f
                pad = pad * f + flatPad * (1.0 - f)
            }
        }
        // junction close-up: at slip roads, motorway exits, forks and lane splits the view moves closer and a little flatter for the last
        // 200 m or so. Never at ordinary turns and roundabouts (those flatten to 2D above), and it never tilts the view lower than
        // usual: it used to swing up to 66 degrees (more than the map's normal limit), which filled the screen with horizon just as
        // the 2D view should have shown the junction.
        closeF = 0.0
        if (navigating && tilt3d && prefs.b(PreferenceKeys.JUNCTION_VIEW, true)) {
            val t = turn
            val m = t?.maneuver?.uppercase() ?: ""
            val ordinaryTurn = m.contains("ROUNDABOUT") || m.contains("ROTARY") || m.contains("TURN") || m.startsWith("SHARP") || m.contains("UTURN")
            val complex = t != null && !ordinaryTurn && (m.startsWith("KEEP") || m.contains("EXIT") || m.contains("RAMP") || m.contains("FORK") || laneKey != null || t.exitNo.isNotBlank())
            if (t != null && complex && t.dist in 5.0..220.0) {
                closeF = ((220.0 - t.dist) / 140.0).coerceIn(0.0, 1.0)
                if (tilt > 44.0) tilt += (44.0 - tilt) * closeF
            }
        }
        // portrait: keep the locator clear of the trip card (its centre sits at (1 + pad) / 2 of the height)
        if (bottomCover > 0.0) pad = minOf(pad, 2.0 * (1.0 - bottomCover - 0.09) - 1.0).coerceAtLeast(0.0)
        tBMode = if (northUp) 0 else if (navigating || sp > 2.0) 1 else 2
        // stopped for 30 s (lights, a queue): a flat top-down view of the traffic round you, back to normal as soon as you move.
        // Not when you've moved the map yourself, nor in the last 400 m of a route
        run {
            val nowMs = android.os.SystemClock.elapsedRealtime()
            if (sp < 0.8) { if (stoppedSince == 0L) stoppedSince = nowMs } else if (sp > 2.5) stoppedSince = 0L
            // (nor waiting at a turn: the close-up of the junction matters more then)
            val nearEnd = navigating && ((eta?.dist ?: Double.MAX_VALUE) < 400.0 || (turn?.dist ?: Double.MAX_VALUE) < 200.0)
            val ov = prefs.b(PreferenceKeys.STOPPED_OVERVIEW, true) && stoppedSince > 0L && nowMs - stoppedSince > 30_000L &&
                !nearEnd && arrival == null && abs(zoomOffset) < 0.01
            if (ov != stoppedOverview) stoppedOverview = ov
            if (ov) { tilt = 0.0; pad = 0.0 }
        }
        tTilt = tilt; tPad = pad
        val requestedZoom = autoZoom(sp, navigating) + zoomOffset + (if (isCar) carViewportZoomOffset else 0.0) + (if (!navigating) defaultZoom else 0.0)
        // Road-level lane ribbons are only useful when their lane boundaries and the route are
        // distinguishable. Keep a firm camera zoom floor for the entire time they are drawn;
        // user/viewport zoom offsets must not silently pull the map back until the ribbons look
        // detached from the road. Tighten the view as the manoeuvre gets closer.
        val laneTurnDistance = if (navigating) turn?.dist else null
        val laneZoomFloor = if (navigating && laneKey != null && prefs.b(PreferenceKeys.LANE_VIEW, true)) {
            when {
                laneTurnDistance != null && laneTurnDistance < 100.0 -> 18.25
                laneTurnDistance != null && laneTurnDistance < 300.0 -> 18.05
                else -> 17.8
            }
        } else Double.NEGATIVE_INFINITY
        // Apply the first-open overview adjustment after the lane zoom floor so that the
        // floor cannot cancel the requested two-step zoom-out while stationary. Once
        // movement is detected, this offset is zero and the existing zoom policy is unchanged.
        val initialOverviewOffset = if (hasDetectedMovement) 0.0 else 0.8
        tZoom = max(requestedZoom, laneZoomFloor) - initialOverviewOffset
        // (the traffic view pulls back about two zoom levels)
        if (stoppedOverview) tZoom = (tZoom - 1.8).coerceAtLeast(12.5)
        // landscape: your arrow moves sideways just enough to clear whatever card is beside it at its height (the search panel
        // and saved places, the turn card, the trip card), and back to the middle when they fold away, e.g. once you're driving.
        // Portrait: the cards span the width, so it stays centred. Mirrored for left-hand drive.
        // Not navigating (on the phone / head unit screen): the arrow sits in the middle of the clear map between the search panel and
        // the buttons, whatever height they reach to, so it's always in the middle of what's left of the map.
        // (Android Auto too: there the outlines are measured within the part of the screen its own buttons leave clear)
        tLeft = if (!navigating && !previewing) idleShift(bottomCover > 0.0)
                else if (bottomCover <= 0.0) arrowShift((1.0 + pad) / 2.0) else 0.0
        map.cardsMirrored = mirrored && !isCar // (Android Auto always has its cards on the left)
        camTargetAt = android.os.SystemClock.elapsedRealtime()
    }

    // outlines of the cards on screen, as parts of its width and height (left, top, right, bottom), reported by the screen
    // (0: the search panel / turn card, 1: the trip card, 2: the map buttons down the other side)
    private val panels = Array(3) { FloatArray(4) }

    fun setPanel(i: Int, l: Float, t: Float, r: Float, b: Float) {
        if (i !in panels.indices) return
        val p = panels[i]; p[0] = l; p[1] = t; p[2] = r; p[3] = b
    }

    /**
     * How far to move the arrow sideways (as the camera's side padding) for an arrow at [y] (a part of the screen height):
     * when a card reaches across that height, the arrow moves to the middle of the clear part of the map, a little nearer the
     * card than the exact middle so it doesn't end up far over. Nothing beside it: dead centre.
     */
    private fun arrowShift(y: Double): Double {
        // Use a slightly taller collision band so the locator clears cards in compact Android Auto
        // surfaces too (full-screen, music split, and app-launcher split layouts report different bounds).
        val band = 0.14
        // Which side the cards are on is read from where they actually are, not from the driving side: Android Auto keeps
        // its cards on the left even for left-hand drive, and assuming they'd swapped sides pushed the arrow under them.
        var coverL = 0.0; var coverR = 0.0
        for (p in panels) {
            if (p[2] - p[0] < 0.02 || p[3] - p[1] < 0.02) continue
            if (p[1] < y + band && p[3] > y - band) {
                if (p[0] + p[2] < 1.0) coverL = max(coverL, p[2].toDouble()) else coverR = max(coverR, 1.0 - p[0])
            }
        }
        val cover = max(coverL, coverR)
        if (cover < 0.05) return 0.0
        // Move farther away from the info/turn card; panel bounds are measured from the actual
        // Compose surface, so this adapts as Android Auto changes between full and split layouts.
        val shift = (cover * 0.78).coerceAtMost(0.42)
        return if (coverR > coverL) -shift else shift
    }

    /**
     * Not navigating: how far to move the arrow sideways so it's centred between the search panel on one side and the map buttons
     * on the other (as a part of the screen width; positive moves it right). Portrait: the search panel is above the arrow, so only the
     * buttons count. Kept small, so it's a gentle nudge off centre rather than a jump.
     */
    private fun idleShift(portrait: Boolean): Double {
        var left = 0.0; var right = 1.0
        for ((i, p) in panels.withIndex()) {
            if (p[2] - p[0] < 0.02 || p[3] - p[1] < 0.02) continue
            if (i == 1) continue                                   // the trip card isn't up when you're not navigating
            if (i == 0 && (portrait || p[2] - p[0] > 0.6f)) continue // full-width in portrait: it's above the arrow, not beside it
            if (p[0] + p[2] < 1.0) left = max(left, p[2].toDouble()) else right = minOf(right, p[0].toDouble())
        }
        if (right - left < 0.2) return 0.0
        // the arrow's spot moves by half the side padding, so twice the distance from the middle
        return (2.0 * ((left + right) / 2.0 - 0.5)).coerceIn(-0.42, 0.42)
    }

    private fun incidentLabel(i: Incident): String = when (i.cat) {
        "roadWorks" -> "Roadworks"; "roadClosed" -> "Road closed"; "accident" -> "Accident"
        "jam" -> "Traffic jam"; "laneClosed" -> "Lane closed"; "danger" -> "Hazard"
        else -> i.text.ifBlank { "Incident" }
    }

    /** Speed cameras within [radius] m from a downloaded area that covers [p] (null when none does, so they're looked up online). */
    private fun localCameras(p: LL, radius: Double): List<LL>? {
        val area = OfflineRouter.areaWith(ctx, "cameras.tsv", p) ?: return null
        val dLat = radius / 111320.0; val dLon = radius / (111320.0 * cos(Math.toRadians(p.lat)).coerceAtLeast(0.2))
        return OfflineRouter.lamps(ctx, p.lat - dLat, p.lon - dLon, p.lat + dLat, p.lon + dLon, "cameras.tsv", OfflineRouter.packDir(ctx, area), 600)
            .map { a -> LL(a[0], a[1]).also { c -> if (a.size > 2) Overpass.camDir[Overpass.camKey(c)] = a[2] } }.filter { Geo.dist(it, p) <= radius }
    }

    /** Camera alerts are on: switched on in Settings, and not in a country that bans them (unless you've chosen to keep them there). */
    fun camerasOn(): Boolean = prefs.b(PreferenceKeys.CAMERA_WARNINGS, true) && !(detectedCountry in CAMERA_BANNED && prefs.b(PreferenceKeys.CAMBANOBEY, true))

    private var banNoted = ""
    /** Says once, on arriving in a country that bans camera warnings, that they've been switched off. */
    private fun noteCameraBan() {
        val cc = detectedCountry
        val name = CAMERA_BANNED[cc]
        if (name == null || !prefs.b(PreferenceKeys.CAMERA_WARNINGS, true) || !prefs.b(PreferenceKeys.CAMBANOBEY, true)) { if (name == null) banNoted = ""; return }
        if (banNoted == cc) return
        banNoted = cc
        toast("Speed camera alerts are off in $name, where they aren't allowed. You can change this in Settings > Alerts.", 7000)
    }

    /** False when the camera is known to check only the other direction (or a road crossing yours), judged against [heading]. */
    private fun camFacesUs(c: LL, heading: Double): Boolean {
        val d = Overpass.camDir[Overpass.camKey(c)] ?: return true
        return abs(angDiff(d, heading)) <= 75.0
    }

    /** The cameras from the search around you (route or not). */
    private var aroundCams: List<LL> = emptyList()

    /** The cameras shown and warned about: the ones around you plus the ones found along the route, within 10 km. */
    private fun mergeCams() {
        val here = fix?.pos
        val seen = HashSet<Long>()
        val out = ArrayList<LL>()
        for (c in aroundCams + knownCams) {
            if (here != null && Geo.dist(here, c) > 10_000.0) continue
            if (seen.add(Math.round(c.lat * 1e5) * 1000003L + Math.round(c.lon * 1e5))) out += c
        }
        cameras = out
        // on a route only the cameras on it are drawn (the ones still ahead); otherwise every camera around you
        val shown = if (navigatingNow()) routeCams.filter { it.second > curProg - 20.0 }.map { it.first } else out
        if (shown != camsShown) { camsShown = shown; map.setCameras(shown) }
    }
    private var camsShown: List<LL>? = null
    private var camsMergedAt = 0L
    /** How far along the route you are (updated with every GPS fix while on it). */
    @Volatile private var curProg = 0.0
    private var onRouteNow = false
    private fun navigatingNow() = route != null && !previewing

    /** Every speed camera found along routes so far (by the map's search along the route, together with the traffic lights). */
    @Volatile private var knownCams: List<LL> = emptyList()
    private var camsCount = -1
    init { map.onRouteCameras = { c -> knownCams = c; if (camerasOn()) mergeCams() } }

    /** How far along the route each traffic light on it is (metres, in order), for zooming in as you come up to one. */
    @Volatile private var lightOffs = DoubleArray(0)
    private var lightRoute: Route? = null
    private var lightPts: List<LL> = emptyList()
    init {
        map.onRouteSignals = { pts ->
            val r = route
            if (r == null || pts.isEmpty()) { lightOffs = DoubleArray(0); lightRoute = null; lightPts = emptyList() }
            // (the same lights on the same route: nothing to work out again)
            else if (r === lightRoute && pts == lightPts) { }
            else scope.launch(Dispatchers.Default) {
                val offs = try { pts.take(600).map { offsetAlong(r, it) }.sorted().toDoubleArray() } catch (e: Throwable) { DoubleArray(0) }
                withContext(Dispatchers.Main) { if (route === r) { lightOffs = offs; lightRoute = r; lightPts = pts } }
            }
        }
    }

    /** How far along [r] the point nearest to [p] is, in metres (on its own: it doesn't touch the position tracking). */
    private fun offsetAlong(r: Route, p: LL): Double {
        val pts = r.pts
        val k = cos(Geo.rad(p.lat)) * 111320.0; val k2 = 110574.0
        var bd = 1e12; var bo = 0.0
        for (i in 0 until pts.size - 1) {
            val ax = (pts[i].lon - p.lon) * k; val ay = (pts[i].lat - p.lat) * k2
            val bx = (pts[i + 1].lon - p.lon) * k; val by = (pts[i + 1].lat - p.lat) * k2
            if ((ax > bd && bx > bd) || (ax < -bd && bx < -bd) || (ay > bd && by > bd) || (ay < -bd && by < -bd)) continue
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = (if (l2 > 0) -(ax * dx + ay * dy) / l2 else 0.0).coerceIn(0.0, 1.0)
            val d = hypot(ax + dx * t, ay + dy * t)
            if (d < bd) { bd = d; bo = r.cum[i] + (r.cum[i + 1] - r.cum[i]) * t }
        }
        return bo
    }

    /** Recent speeds (time, m/s), to tell whether you're slowing down for a traffic light. */
    private val speedLog = ArrayDeque<Pair<Long, Double>>()

    /** Distance to the next traffic light on the route ahead (null when none within 400 m). */
    private fun nextLightDist(): Double? {
        val r = route ?: return null
        if (lightRoute !== r) return null
        val offs = lightOffs
        val prog = curProgress
        for (o in offs) { val d = o - prog; if (d > 5.0) return if (d < 400.0) d else null }
        return null
    }

    /**
     * The driving zoom on a route: closer the slower you go, and by the kind of road you're on (closer on town streets, further out
     * on motorways). About z17.6 at 30 mph in town, z16 at 70 on a motorway.
     */
    private fun navZoom(sp: Double): Double {
        val f = fix
        val cls = if (f != null) try { map.roadAt(f.pos, f.heading)?.cls } catch (e: Throwable) { null } else null
        return NavigationPolicy.routeSpeedZoom(sp, cls)
    }

    /**
     * Speed cameras along the whole route, with how far along it each one is. The cameras come from the map's search along the
     * route (one combined request per stretch with the traffic lights), so this needs no requests of its own: it just matches
     * them to the route whenever more arrive or the route changes.
     */
    private fun updateRouteCams(r: Route, @Suppress("UNUSED_PARAMETER") progress: Double) {
        val cams = knownCams
        if (camsRoute === r && camsCount == cams.size) return
        camsRoute = r; camsCount = cams.size
        if (cams.isEmpty()) { routeCams = emptyList(); return }
        scope.launch {
            val found = withContext(Dispatchers.Default) {
                // points every ~50 m along the route, in a coarse grid so each camera only checks the route nearby
                val grid = HashMap<Long, ArrayList<Int>>()
                val pts = ArrayList<LL>(); val offs = ArrayList<Double>()
                var last = -1e9
                fun key(lat: Double, lon: Double) = Math.floor(lat / 0.002).toLong() * 1000003L + Math.floor(lon / 0.003).toLong()
                for (n in r.pts.indices) if (r.cum[n] - last >= 50.0 || n == r.pts.size - 1) {
                    grid.getOrPut(key(r.pts[n].lat, r.pts[n].lon)) { ArrayList() }.add(pts.size)
                    pts += r.pts[n]; offs += r.cum[n]; last = r.cum[n]
                }
                val out = ArrayList<Pair<LL, Double>>()
                for (c in cams) {
                    var bestD = 45.0; var off = -1.0
                    val k = key(c.lat, c.lon)
                    var bi = -1
                    for (dy in -1..1) for (dx in -1..1) grid[k + dy * 1000003L + dx]?.forEach { i ->
                        val d = Geo.dist(pts[i], c)
                        if (d < bestD) { bestD = d; off = offs[i]; bi = i }
                    }
                    // a camera that checks the other carriageway (it's known which way it points): not on your route
                    val cd = Overpass.camDir[Overpass.camKey(c)]
                    if (off >= 0 && cd != null && bi >= 0 && bi + 1 < pts.size && abs(angDiff(cd, Geo.bearing(pts[bi], pts[bi + 1]))) > 75.0) off = -1.0
                    if (off >= 0) out += c to off
                }
                out.sortBy { it.second }
                out
            }
            if (camsRoute === r) { routeCams = found; hazardSig = 0; mergeCams() }
        }
    }

    /** A report you made from the map: what it is, where, and when. They are kept on this device only. */
    class Report(val kind: String, val pos: LL, val t: Long)

    /** How long each kind of report lasts, its map icon, and what is said when you come up to it. */
    private fun reportLife(k: String) = when (k) { "hazard" -> 3_600_000L; "accident", "breakdown" -> 2 * 3_600_000L; "mobilecam" -> 4 * 3_600_000L; else -> 3 * 3_600_000L }
    private fun reportIcon(k: String) = when (k) { "accident" -> 4; "hazard" -> 5; "breakdown" -> 6; "mobilecam" -> 7; else -> 3 }
    fun reportName(k: String) = when (k) { "accident" -> "Accident"; "hazard" -> "Hazard"; "breakdown" -> "Broken-down vehicle"; "mobilecam" -> "Mobile speed camera"; else -> "Police" }

    /** Reports still in force (older police-only reports are read too). */
    fun reportsActive(): List<Report> {
        val now = System.currentTimeMillis()
        val out = ArrayList<Report>()
        for (r in prefs.s(PreferenceKeys.POLICEREP, "").split(";")) {
            val q = r.split(",")
            val la = q.getOrNull(0)?.toDoubleOrNull(); val lo = q.getOrNull(1)?.toDoubleOrNull(); val t = q.getOrNull(2)?.toLongOrNull()
            if (la != null && lo != null && t != null && now - t < reportLife("police")) out += Report("police", LL(la, lo), t)
        }
        for (r in prefs.s(PreferenceKeys.REPORTS, "").split(";")) {
            val q = r.split(",")
            val k = q.getOrNull(0).orEmpty()
            val la = q.getOrNull(1)?.toDoubleOrNull(); val lo = q.getOrNull(2)?.toDoubleOrNull(); val t = q.getOrNull(3)?.toLongOrNull()
            if (k.isNotBlank() && la != null && lo != null && t != null && now - t < reportLife(k)) out += Report(k, LL(la, lo), t)
        }
        return out
    }

    fun report(kind: String) {
        val p = fix?.pos
        if (p == null) { toast("Waiting for GPS", 2500); return }
        val keep = reportsActive().takeLast(29)
        val list = keep + Report(kind, p, System.currentTimeMillis())
        prefs.put(PreferenceKeys.REPORTS, list.joinToString(";") { "${it.kind},${it.pos.lat},${it.pos.lon},${it.t}" })
        prefs.put(PreferenceKeys.POLICEREP, "")
        toast("${reportName(kind)} report saved here", 3000)
        pushHazards(hzProgress)
    }

    fun reportPolice() = report("police")

    fun clearReports() { prefs.put(PreferenceKeys.REPORTS, ""); prefs.put(PreferenceKeys.POLICEREP, ""); pushHazards(hzProgress); toast("Reports cleared", 2000) }

    /** Refreshes the map icons when reports change or expire, and warns when one is just ahead. */
    private fun checkPolice(p: Fix) {
        val list = reportsActive()
        var sig = list.size
        for (r in list) sig = sig * 31 + (r.t % 100000).toInt()
        if (sig != policeSig) { policeSig = sig; pushHazards(hzProgress) }
        if (!prefs.b(PreferenceKeys.POLICEALERTS, true) || p.speed < 4.0) return
        for (r in list) {
            val d = Geo.dist(p.pos, r.pos)
            if (d < 40 || d > 600) continue
            val diff = abs(((Geo.bearing(p.pos, r.pos) - p.heading + 540.0) % 360.0) - 180.0)
            if (diff < 50 && policeAlerted.add(r.t)) {
                chime(android.media.ToneGenerator.TONE_PROP_PROMPT)
                raiseAlert(if (r.kind == "mobilecam") "camera" else "report", "${reportName(r.kind)} reported ahead")
                speak("${reportName(r.kind)} reported ahead", true)
            }
        }
    }

    /** Pushes the camera, roadworks and closure icons for what is still ahead on the route (only when something changed). */
    private fun pushHazards(progress: Double) {
        hzProgress = progress
        val list = ArrayList<Pair<LL, Int>>()
        if (prefs.b(PreferenceKeys.HAZARD_ICONS, true)) {
            for (r in reportsActive()) list += r.pos to reportIcon(r.kind)
            for ((inc, off, _) in incMatches) {
                if (off <= progress - 20) continue
                val k = when (inc.cat.lowercase()) { "roadclosed" -> 2; "roadworks", "laneclosed" -> 1; else -> -1 }
                if (k >= 0) list += inc.pts.first() to k
            }
        }
        var sig = list.size
        for ((p, k) in list) sig = sig * 31 + (p.lat * 1e5).toInt() * 7 + (p.lon * 1e5).toInt() + k
        if (sig == hazardSig) return
        hazardSig = sig
        map.setHazards(list)
    }

    private fun checkIncidents(r: Route, progress: Double) {
        try { if (camerasOn()) updateRouteCams(r, progress); pushHazards(progress) } catch (e: Exception) { }
        // TomTom's incident data when there's a key; otherwise the incidents that came with a Mapbox route
        val fromRoute = (!hasKey || ttDown()) && r.incidents.isNotEmpty()
        if ((!hasKey && !fromRoute) || simMode != 0 || !prefs.b(PreferenceKeys.INCIDENTS, true)) { incidentAhead = null; return }
        val now = System.currentTimeMillis()
        if (now - incAt > 180_000) {
            incAt = now
            scope.launch {
                val found = withContext(Dispatchers.IO) {
                    try {
                        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
                        var any = false
                        for (n in r.pts.indices) {
                            if (r.cum[n] < progress - 200 || r.cum[n] > progress + 40_000) continue
                            val q = r.pts[n]; any = true
                            minLat = min(minLat, q.lat); maxLat = max(maxLat, q.lat); minLon = min(minLon, q.lon); maxLon = max(maxLon, q.lon)
                        }
                        if (!any) emptyList() else {
                            val list = if (fromRoute) r.incidents else TomTomRest.incidents(apiKey, minLon - 0.01, minLat - 0.01, maxLon + 0.01, maxLat + 0.01)
                            val out = ArrayList<Triple<Incident, Double, String>>()
                            for (inc in list) {
                                var bestOff = -1.0; var bestD = 70.0
                                val q = inc.pts.first()
                                for (n in r.pts.indices) {
                                    if (r.cum[n] < progress - 200 || r.cum[n] > progress + 40_000) continue
                                    val d = Geo.dist(r.pts[n], q)
                                    if (d < bestD) { bestD = d; bestOff = r.cum[n] }
                                }
                                if (bestOff >= 0) out += Triple(inc, bestOff, incidentLabel(inc))
                            }
                            out
                        }
                    } catch (e: Exception) { null }
                }
                if (found != null) { incMatches = found; try { applyIncidentSev(r, found) } catch (e: Exception) { } }
            }
        }
        val next = incMatches.filter { it.second > progress - 30 }.minByOrNull { it.second }
        if (next == null) { incidentAhead = null; return }
        val d = max(0.0, next.second - progress)
        if (d > 15_000) { incidentAhead = null; return }
        incidentAhead = IncidentAhead(next.third, d, next.first.delaySec)
        if (d < 8000 && incAlerted.add(next.first.id)) {
            val mins = next.first.delaySec / 60
            val extra = if (mins >= 2) ", about $mins minutes delay" else ""
            raiseAlert("incident", "${next.third} ahead, ${Geo.fmtDist(d, imperial)}$extra", 7000)
            speak("${next.third} ahead in ${Geo.spoken(d, imperial)}$extra", true)
        }
    }

    private var sevBase: IntArray? = null
    private var sevBaseRoute: Route? = null

    /** Colours the stretch of road around each live incident amber (slow) or red (heavy), on top of the route's own traffic data. */
    private fun applyIncidentSev(r: Route, found: List<Triple<Incident, Double, String>>) {
        if (sevBaseRoute !== r) { sevBase = r.sev.copyOf(); sevBaseRoute = r }
        val base = sevBase ?: return
        if (base.size != r.sev.size) return
        System.arraycopy(base, 0, r.sev, 0, base.size)
        for ((inc, startOff, _) in found) {
            val level = when {
                inc.cat.contains("close", true) || inc.delaySec >= 300 -> 2
                inc.delaySec >= 90 || (inc.cat.contains("jam", true) && inc.delaySec >= 40) -> 1
                else -> 0
            }
            if (level == 0) continue
            var endOff = startOff + 250.0
            if (inc.pts.size > 1) {
                val last = inc.pts.last()
                var bd = 70.0
                for (n in r.pts.indices) {
                    if (r.cum[n] < startOff - 50) continue
                    val d = Geo.dist(r.pts[n], last)
                    if (d < bd) { bd = d; endOff = r.cum[n] }
                }
            }
            if (endOff < startOff) endOff = startOff + 250.0
            for (i in r.sev.indices) if (r.cum[i] >= startOff - 20 && r.cum[i] <= endOff + 20) r.sev[i] = max(r.sev[i], level)
        }
    }

    private fun p0Speed(): Double = fix?.speed ?: 0.0

    /** Every few minutes while driving, asks for fresh routes and offers a different one if it saves a few minutes. */
    private fun checkFaster(cur: Route, remainingSecs: Double) {
        val now = System.currentTimeMillis()
        val f = fix ?: return
        val d = dest ?: return
        val live = prefs.b(PreferenceKeys.LIVE_ALTERNATIVES, true)
        if (previewing || simMode != 0 || faster != null || !prefs.b(PreferenceKeys.FASTER_ROUTE, true) || shadow()) return
        if (remainingSecs < (if (live) 600 else 900) || now - lastFasterCheck < (if (live) 180_000L else 240_000L) || f.speed < 3.0) return
        lastFasterCheck = now
        val ahead = cur.pts.drop(lastIdx.coerceIn(0, max(0, cur.pts.size - 2))).filterIndexed { i, _ -> i % 3 == 0 }
        val viaPts = via.map { it.pos }
        scope.launch {
            try {
                syncRoutePrefs(f.pos, d.pos)
                val rs = withContext(Dispatchers.IO) { netRoutes(f.pos, d.pos, true, 0L, viaPts) }
                if (rs.size < 2 || route !== cur) return@launch
                // how much of each candidate runs along the road we are already following
                fun overlap(r: Route): Double {
                    val s = r.pts.filterIndexed { i, _ -> i % 10 == 0 }
                    if (s.isEmpty()) return 0.0
                    return s.count { p -> ahead.any { Geo.dist(p, it) < 40.0 } }.toDouble() / s.size
                }
                val same = rs.maxByOrNull { overlap(it) } ?: return@launch
                try { refreshTraffic(cur, same) } catch (e: Exception) { }
                if (live) {
                    // live comparison: every other way stays on the map in grey with how it compares, to tap any time
                    val others = rs.filter { it !== same && overlap(it) < 0.8 }.take(2)
                    val list = withContext(Dispatchers.Default) { altsFor(cur, same, others, curProgress) }
                    if (route !== cur) return@launch
                    liveAlts = list
                    map.setLiveAlts(list.map { it.line() }, list.map { it.bubble }, list.map { it.diffMin }, list.map { it.traffic })
                    val best = list.minByOrNull { it.diffMin }
                    if (best != null && best.diffMin <= -3 && liveSaid.add(best.diffMin / 2)) speak("A faster route is available, saving ${-best.diffMin} minutes. Tap it on the map to switch.", true)
                    return@launch
                }
                val best = rs.filter { it !== same && overlap(it) < 0.8 }.minByOrNull { it.time } ?: return@launch
                val saved = ((same.time - best.time) / 60.0).toInt()
                if (saved >= 3 && best.time < same.time * 0.95) {
                    faster = FasterRoute(best, saved)
                    speak("A faster route is available, saving $saved minutes", true)
                    val shown = faster
                    delay(25_000)
                    if (faster === shown) { faster = null; lastFasterCheck = System.currentTimeMillis() + 300_000L }
                }
            } catch (e: Exception) { }
        }
    }

    private var curProgress = 0.0
    private var lastTrafficAt = 0L
    private var trafficBusy = false

    /** Every few minutes asks TomTom for the same trip again, so the amber/red road and the time left follow the traffic as it changes. */
    private fun checkTraffic(cur: Route) {
        val now = System.currentTimeMillis()
        val f = fix ?: return
        val d = dest ?: return
        if (previewing || simMode != 0 || trafficBusy || !hasOnline || shadow()) return
        if (lastTrafficAt == 0L) { lastTrafficAt = now - 120_000L }   // first refresh about a minute after setting off
        if (now - lastTrafficAt < 180_000L) return
        lastTrafficAt = now
        trafficBusy = true
        val viaPts = via.map { it.pos }
        val at = curProgress
        scope.launch {
            try {
                syncRoutePrefs(f.pos, d.pos)
                val rs = withContext(Dispatchers.IO) { netRoutes(f.pos, d.pos, false, 0L, viaPts, -1.0, true) }
                val fresh = rs.firstOrNull() ?: return@launch
                if (route !== cur) return@launch
                refreshTraffic(cur, fresh, at)
            } catch (e: Exception) { }
            finally { trafficBusy = false }
        }
    }

    /** Copies the latest traffic colouring from a fresh request for the same road onto the route being driven, so amber and red stay current. */
    private fun refreshTraffic(cur: Route, fresh: Route, at: Double = curProgress) {
        if (fresh.sev.isEmpty() || cur.sev.isEmpty()) return
        fun key(p: LL) = Math.round(p.lat * 1e4) * 1000003L + Math.round(p.lon * 1e4)
        val m = HashMap<Long, Int>(fresh.sev.size * 2)
        for (i in fresh.sev.indices) m[key(fresh.pts[i])] = fresh.sev[i]
        var hit = 0
        val base = IntArray(cur.sev.size)
        for (i in cur.sev.indices) { val v = m[key(cur.pts[i])]; if (v != null) { base[i] = v; hit++ } else base[i] = cur.sev[i] }
        // only trust it when it is the same road: a good share of the points still ahead must line up with the fresh route
        val firstAhead = max(0, idxAt(cur, at))
        var ahead = 0; var match = 0
        for (i in firstAhead until cur.sev.size) { ahead++; if (m.containsKey(key(cur.pts[i]))) match++ }
        if (ahead > 0 && match < ahead * 0.6) return
        // fresh offsets count from where you were when it was asked, so shift them onto the full route
        if (fresh.len > 0) {
            cur.freeFlow = max(0.0, fresh.time - fresh.delay) / fresh.len * cur.len
            cur.delays = fresh.delays.map { DelaySeg(it.off + at, it.end + at, it.secs) }
        }
        sevBase = base.copyOf(); sevBaseRoute = cur
        System.arraycopy(base, 0, cur.sev, 0, base.size)
        try { applyIncidentSev(cur, incMatches) } catch (e: Exception) { }
        lastDraw = 0L
    }

    fun acceptFaster() {
        val fr = faster ?: return
        routes = listOf(fr.route); active = 0
        said.clear(); offCount = 0; lastIdx = 0; trustProg = -1.0
        faster = null
        drawRoutes()
        toast("Switched to the faster route", 2500)
    }

    fun dismissFaster() { faster = null; lastFasterCheck = System.currentTimeMillis() + 300_000L }

    private val liveSaid = HashSet<Int>()
    private var lastBeat = 0L

    /** While navigating, notes every 20 s that a route is running (with its stops), so a restart can carry straight on. */
    private fun saveResumeBeat() {
        val now = System.currentTimeMillis()
        if (now - lastBeat < 20_000L || previewing || simMode != 0) return
        lastBeat = now
        val d = dest ?: return
        try {
            val o = org.json.JSONObject(prefs.s(PreferenceKeys.RESUME, "{}"))
            o.put("n", d.name).put("s", d.sub).put("la", d.pos.lat).put("lo", d.pos.lon).put("b", now)
            if (!o.has("t")) o.put("t", now)
            val a = org.json.JSONArray()
            for (v in via) a.put(org.json.JSONObject().put("n", v.name).put("s", v.sub).put("la", v.pos.lat).put("lo", v.pos.lon))
            o.put("v", a)
            prefs.put(PreferenceKeys.RESUME, o.toString())
        } catch (e: Exception) { }
    }

    /** Switches to one of the grey alternatives on the map. */
    fun switchToAlt(i: Int) {
        val a = liveAlts.getOrNull(i) ?: return
        val old = route; val oldProg = curProgress
        routes = listOf(a.route); active = 0
        said.clear(); offCount = 0; lastIdx = 0; trustProg = -1.0; liveSaid.clear()
        clearLiveAlts()
        lastFasterCheck = System.currentTimeMillis()
        drawRoutes()
        updateRouteZones()
        toast(if (a.diffMin < 0) "Switched: about ${-a.diffMin} min quicker" else if (a.diffMin > 0) "Switched: about ${a.diffMin} min longer" else "Switched route", 3000)
        fix?.let { onPosition(it, true) }
        // the way you were going stays on the map as an alternative, with its card (the other way round: slower becomes faster)
        if (old != null && old !== a.route && prefs.b(PreferenceKeys.LIVE_ALTERNATIVES, true)) {
            val nr = a.route
            scope.launch {
                val back = withContext(Dispatchers.Default) { try { altsFor(nr, nr, listOf(old), curProgress, oldProg) } catch (e: Exception) { emptyList() } }
                    .map { LiveAlt(it.route, -a.diffMin, it.bubble, it.divergeOff, -a.traffic, it.divIdx) }
                if (route !== nr || back.isEmpty()) return@launch
                liveAlts = back
                map.setLiveAlts(back.map { it.line() }, back.map { it.bubble }, back.map { it.diffMin }, back.map { it.traffic })
            }
        }
    }

    /**
     * The other ways to go, as cards on the map: where each leaves your road, a spot a little way along its own road for its card,
     * the minutes it saves or costs compared with [same] (your way, worked out at the same moment) and how its traffic compares.
     */
    private fun altsFor(cur: Route, same: Route, others: List<Route>, progress: Double, altFrom: Double = 0.0): List<LiveAlt> = others.mapNotNull { alt ->
        // [altFrom]: how far along the other route you are (it may have started further back, like the way you just left)
        var div = -1
        for (i in alt.pts.indices step 2) {
            if (alt.cum[i] < altFrom) continue
            if (alt.cum[i] > altFrom + 60_000.0) break
            if (RouteExtras.project(cur, alt.pts[i], 35.0, progress) == null) { div = i; break }
        }
        if (div < 0 || cur.pts.isEmpty()) return@mapNotNull null
        // the line starts right where it leaves your road (back to its last point that's still on it), so the two lines join up
        var join = div
        while (join > 0 && alt.cum[join - 1] >= altFrom && RouteExtras.project(cur, alt.pts[join - 1], 6.0, progress) == null) join--
        if (join > 0) join--
        val divOff = RouteExtras.project(cur, alt.pts[max(0, div - 2)], 60.0, progress)?.first ?: progress
        val own = alt.cum[div]
        // the card a short way along it, so it's on screen near the turn-off (it used to be miles away, off the screen)
        val bi = alt.cum.indexOfFirst { it >= own + min(320.0, (alt.len - own) * 0.3) }.let { if (it < 0) alt.pts.size / 2 else it }
        // traffic: the delay on it compared with the delay on your way (two minutes either way counts as similar)
        val tr = when { alt.delay < same.delay - 120.0 -> -1; alt.delay > same.delay + 120.0 -> 1; else -> 0 }
        LiveAlt(alt, Math.round((alt.time - same.time) / 60.0).toInt(), alt.pts[bi], divOff, tr, join)
    }

    /**
     * Setting off with alternatives on the map: they get their cards straight away (they used to stay as plain grey lines until
     * the first check for a faster way, a few minutes in).
     */
    private fun cardsForStartAlts() {
        val cur = route ?: return
        if (!prefs.b(PreferenceKeys.FASTER_ROUTE, true) || !prefs.b(PreferenceKeys.LIVE_ALTERNATIVES, true)) return
        val others = routes.filter { it !== cur }
        if (others.isEmpty()) return
        scope.launch {
            val list = withContext(Dispatchers.Default) { try { altsFor(cur, cur, others, 0.0) } catch (e: Exception) { emptyList() } }
            if (route !== cur || previewing || list.isEmpty()) return@launch
            liveAlts = list
            // the plain grey lines make way for the ones with cards
            map.setRoute(cur.pts, emptyList(), cur.sev)
            map.setTunnels(cur.tunnels.map { (a, b) -> slice(r = cur, a = a, b = b) })
            map.setLiveAlts(list.map { it.line() }, list.map { it.bubble }, list.map { it.diffMin }, list.map { it.traffic })
        }
    }

    private fun clearLiveAlts() { if (liveAlts.isNotEmpty()) liveAlts = emptyList(); map.setLiveAlts(emptyList(), emptyList(), emptyList(), emptyList()) }

    /** Drops alternatives whose turn-off is already behind you. */
    private fun pruneLiveAlts(progress: Double) {
        val l = liveAlts
        if (l.isEmpty()) return
        val keep = l.filter { it.divergeOff > progress - 15.0 }
        if (keep.size != l.size) {
            liveAlts = keep
            map.setLiveAlts(keep.map { it.line() }, keep.map { it.bubble }, keep.map { it.diffMin }, keep.map { it.traffic })
        }
    }

    private fun updateGuidance(progress: Double, light: Boolean = false) {
        val r = route ?: return
        val rem = max(0.0, r.len - progress)
        val frac = if (r.len > 0) rem / r.len else 0.0
        if (!light) {
            try { checkIncidents(r, progress) } catch (e: Exception) { }
            fix?.let { f -> if (via.isNotEmpty() && Geo.dist(f.pos, via[0].pos) < 80) { toast("Stop reached: ${via[0].name}", 3000); via = via.drop(1) } }
            if (via.isEmpty()) checkParking(r, rem)
            curProgress = progress
            // time left = what the rest of the road takes with no traffic + the delays that are still ahead of you
            val ahead = if (r.delays.isEmpty()) r.delay * frac else r.delays.sumOf { d ->
                val span = max(1.0, d.end - d.off)
                d.secs * ((d.end - max(progress, d.off)) / span).coerceIn(0.0, 1.0)
            }
            val secs = r.freeFlow * frac + ahead
            val delayMin = Math.round(ahead / 60.0).toInt()
            val arrive = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (secs * 1000).toLong()))
            eta = EtaInfo(arrive, rem, secs, delayMin)
            routeFrac = (1.0 - frac).toFloat().coerceIn(0f, 1f)
            pruneLiveAlts(progress)
            saveResumeBeat()
            checkFaster(r, secs)
            try { checkTraffic(r) } catch (e: Exception) { }
        }

        if (!light && !shadow()) try { routeExtrasTick(r, progress) } catch (e: Exception) { android.util.Log.w("NavEngine", "route extras failed", e) }
        val ins = r.steps
        var k = ins.indexOfFirst { it.off > progress + 4 }
        if (k < 0) k = ins.size - 1
        if (k >= 0) {
            val cur = ins[k]
            val d = max(0.0, cur.off - progress)
            val arriving = cur.maneuver.uppercase().startsWith("ARRIVE")
            val side = if (arriving) arrivalSide(r, cur.maneuver) else ""
            val nxt = ins.getOrNull(k + 1)
            val street = if (arriving) (dest?.name ?: "Destination") + (if (side.isNotEmpty()) " · on the $side" else "") else cur.street.ifBlank { Geo.maneuverText(cur.maneuver, cur.roundExit).replaceFirstChar { it.uppercase() } }
            // several lane sections can touch one junction (the road often widens on the approach): use the one with the most lanes, then the nearest to the junction
            // on motorways and dual carriageways the lanes are looked at (and shown, and spoken) much earlier, as there's further to move over
            val fastRoad = cur.exitNo.isNotBlank() || (fix?.let { f -> try { map.roadAt(f.pos, f.heading)?.cls } catch (e: Throwable) { null } }?.let { it.startsWith("motorway") || it.startsWith("trunk") } == true)
            laneFast = fastRoad
            val realSec = if (d < (if (fastRoad) 1900 else 1000) && !arriving) r.lanes.filter { progress < it.end + 5 && abs(it.end - cur.off) < 200 }.sortedWith(compareByDescending<LaneSec> { it.lanes.size }.thenBy { abs(it.end - cur.off) }).firstOrNull() else null
            val laneSec = realSec
            val laneAdv = laneSec?.let { Geo.laneAdvice(it, cur.maneuver) }
            // the early lane call on fast roads, about a mile out (once per junction): "In about a mile, move into the left two lanes"
            if (fastRoad && laneAdv != null && d in 1250.0..1850.0 && earlyLaneFor != cur.off) {
                earlyLaneFor = cur.off
                val dist = if (imperial) "In about a mile" else "In about one and a half kilometres"
                speak(dist + ", " + laneAdv.replaceFirst("Use the", "move into the").replaceFirst("Use lane", "move into lane"))
            }
            updateLaneMarks(r, cur, laneSec, d)
            updateTurnArrow(r, k, cur, d, arriving)
            // (after an unnamed slip road the step's "street" is the exit sign's text, not a road: the map's own name is used then)
            onRoad = ins.getOrNull(k - 1)?.let { st -> if (st.exitNo.isNotBlank() || st.maneuver.uppercase().contains("RAMP")) "" else st.street } ?: ""
            refreshRoad()
            // the turn after this one: only when it comes soon after it (about 15 seconds at your speed, and never less than 300 m),
            // unless set to always show or never
            val gap = if (nxt != null) nxt.off - cur.off else Double.MAX_VALUE
            val thenOn = nxt != null && !arriving && when (prefs.s(PreferenceKeys.THEN_MODE, "close")) {
                "always" -> true
                "off" -> false
                else -> gap <= max(300.0, (fix?.speed ?: 0.0) * 15.0)
            }
            val newTurn = TurnInfo(
                d, street, cur.maneuver, if (thenOn) nxt?.maneuver else null,
                if (thenOn && nxt != null) "Then " + Geo.maneuverText(nxt.maneuver, nxt.roundExit) + (if (nxt.street.isNotBlank()) " onto ${nxt.street}" else "") else "",
                laneSec, Geo.stepTag(cur), laneAdv, cur.roundExit, cur.sign,
                exitNo = cur.exitNo, rbIn = cur.rbIn, rbOut = cur.rbOut, rbExits = cur.rbExits, thenGap = if (thenOn) gap else 0.0, thenExit = if (thenOn) (nxt?.roundExit ?: 0) else 0,
            )
            // only publish a new turn card when something a driver can see changed (about 3 m of distance), so the screen is not redrawn four times a second for nothing
            val ot = turn
            if (ot == null || ot.maneuver != newTurn.maneuver || ot.street != newTurn.street || ot.lanes !== newTurn.lanes || ot.thenText != newTurn.thenText || abs(ot.dist - newTurn.dist) >= 3.0 || newTurn.dist < 30.0) turn = newTurn
            val word = Geo.maneuverText(cur.maneuver, cur.roundExit)
            // (Settings > Sound > Say road names off: just the direction, "In 200 metres, turn left")
            val tail = if (arriving || cur.street.isBlank() || !prefs.b(PreferenceKeys.VOICE_ROADS, true)) "" else " onto ${cur.street}"
            val lane = if (laneAdv != null) ". $laneAdv" else ""
            if (onRoad.isNotBlank()) tripRoads.add(onRoad)
            // smarter voice: junction numbers, sign destinations and the next move when it follows quickly
            val phrase = if (arriving) word else smartPhrase(cur, nxt, word, tail)
            // on roads you drive a lot the early heads-ups are skipped and the main one comes a little later
            val familiar = prefs.b(PreferenceKeys.SMARTVOICE, true) && onRoad.isNotBlank() && (roadFam[onRoad] ?: 0) >= 6
            val fFrom = if (familiar) 330.0 else 520.0
            // a heads-up well ahead on fast roads, then the main call-out, then the final one at the junction
            if (!familiar && d <= 1700 && d > 1100 && p0Speed() > 18.0 && !arriving && said.add("${k}e"))
                speak("In ${Geo.spoken(d, imperial)}, $phrase")
            if (d <= fFrom && d > 200 && said.add("${k}f"))
                speak(if (arriving) "In ${Geo.spoken(d, imperial)}, you will arrive" + (if (side.isNotEmpty()) ". Your destination is on the $side" else "") else "In ${Geo.spoken(d, imperial)}, $phrase$lane")
            if (d <= 120 && said.add("${k}n"))
                speak(if (arriving) (if (side.isNotEmpty()) "Your destination is on the $side" else "You have arrived") else phrase.replaceFirstChar { it.uppercase() })
        }
        // arrived: at the end of the route and really there (not just level with the end of the route on another road)
        val atEnd = rem < 25 && fix.let { f -> f == null || Geo.dist(f.pos, dest?.pos ?: r.pts.last()) < 120.0 || Geo.dist(f.pos, r.pts.last()) < 120.0 }
        if (atEnd && said.add("end")) {
            toast("You have arrived", 5000)
            if (navStartMs > 0) {
                val secsT = (System.currentTimeMillis() - navStartMs) / 1000.0
                val dd = max(0.0, tripDist - navStartDist)
                val sideA = try { arrivalSide(r, r.steps.lastOrNull()?.maneuver ?: "") } catch (e: Exception) { "" }
                val a = ArrivalInfo(dest, dest?.name ?: "Destination", dd, secsT, if (secsT > 0) dd / secsT else 0.0, sideA)
                arrival = a
                navStartMs = 0L
                arrivalMoment(a)
                scope.launch { delay(20_000); if (arrival === a) dismissArrival() }
            }
            arriveFlash = true
            scope.launch { delay(7000); arriveFlash = false }
            val doneRoute = r
            scope.launch { delay(4000); if (route === doneRoute || route == null) endRoute() }
        }
    }

    // ---- live rain radar ----
    private var radarJob: Job? = null
    private var radarOnNow = false

    /** Switches the rain radar layer on or off; while on it picks up the newest radar picture every 10 minutes. */
    fun setRadarOn(on: Boolean) {
        if (on == radarOnNow) return
        radarOnNow = on
        radarJob?.cancel(); radarJob = null
        if (!on) { map.setRadar(null); return }
        radarJob = scope.launch {
            var warned = false
            while (true) {
                val url = withContext(Dispatchers.IO) {
                    try {
                        val c = java.net.URL("https://api.rainviewer.com/public/weather-maps.json").openConnection() as java.net.HttpURLConnection
                        c.connectTimeout = 10_000; c.readTimeout = 15_000
                        val body = c.inputStream.bufferedReader().use { it.readText() }
                        val o = org.json.JSONObject(body)
                        val host = o.optString("host", "https://tilecache.rainviewer.com")
                        val past = o.getJSONObject("radar").getJSONArray("past")
                        val path = past.getJSONObject(past.length() - 1).getString("path")
                        "$host$path/256/{z}/{x}/{y}/2/1_1.png"
                    } catch (e: Exception) { null }
                }
                if (url != null) map.setRadar(url)
                else if (!warned) { warned = true; toast("Rain radar isn't available right now (needs signal)", 3500) }
                delay(if (url != null) 600_000L else 120_000L)
            }
        }
    }

    /**
     * Predictive destinations: where you are probably heading now (a place you often drive to at this time, or work in the
     * morning and home later on), with the live journey time. Checked at most every 5 minutes, and only while idle.
     */
    fun refreshPrediction(force: Boolean = false) {
        if (!prefs.b(PreferenceKeys.PREDICT, true)) { prediction = null; return }
        if (route != null || previewing) return
        val now = System.currentTimeMillis()
        if (!force && now - predictionAt < 300_000L) return
        val here = fix?.pos ?: return
        predictionAt = now
        val cal = java.util.Calendar.getInstance()
        val hm = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val weekend = dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY
        val home = places.slot("home"); val work = places.slot("work")
        var cand: Place? = habits.suggest(now)
        if (cand == null && !weekend && work != null && hm in 360..630 && Geo.dist(here, work.pos) > 1500) cand = work
        if (cand == null && home != null && hm >= (if (weekend) 1020 else 930) && Geo.dist(here, home.pos) > 1500) cand = home
        val pl = cand
        if (pl == null || Geo.dist(here, pl.pos) < 800) { prediction = null; return }
        val title = when {
            home != null && Geo.dist(home.pos, pl.pos) < 150 -> "Home"
            work != null && Geo.dist(work.pos, pl.pos) < 150 -> "Work"
            else -> pl.name
        }
        scope.launch {
            var live = false
            val r = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                withContext(Dispatchers.IO) {
                    try {
                        if (hasOnline && online()) { live = true; netRoutes(here, pl.pos, false, 0L, emptyList(), -1.0, true).firstOrNull() }
                        // (no offline routing for this: it loads large files into memory, too heavy for a suggestion in the background)
                        else null
                    } catch (e: Exception) { null }
                }
            }
            if (route != null) return@launch
            val mins = r?.let { Math.round(it.time / 60.0).toInt() } ?: -1
            val dmin = if (live && r != null) Math.round(r.delay / 60.0).toInt() else 0
            val note = when { r == null || !live -> ""; dmin >= 10 -> "heavy traffic"; dmin >= 3 -> "some traffic"; else -> "light traffic" }
            prediction = Prediction(pl, title, mins, dmin, note)
        }
    }

    /**
     * Drive times in minutes to Home and Work, for their tiles on the map page (0 = you're there; missing or -1 = not known, and the
     * tile shows the distance instead). Online only, so it never runs the offline router on the head unit in the background: at most
     * once every 10 minutes, sooner if you've moved 3 km or Home / Work changed. Two small route requests at a time.
     */
    val slotMins = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    private var slotMinsAt = 0L
    private var slotMinsFrom: LL? = null
    private var slotMinsKey = ""
    private var slotMinsJob: Job? = null
    fun refreshSlotTimes() {
        if (route != null || previewing || !hasOnline) return
        val here = fix?.pos ?: return
        val home = places.slot("home"); val work = places.slot("work")
        val key = "${home?.pos?.lat},${home?.pos?.lon}|${work?.pos?.lat},${work?.pos?.lon}"
        val now = System.currentTimeMillis()
        val moved = slotMinsFrom?.let { Geo.dist(it, here) > 3000.0 } ?: true
        if (key == slotMinsKey && !moved && now - slotMinsAt < 600_000L) return
        if (slotMinsJob?.isActive == true) return
        if (key != slotMinsKey) slotMins.clear()
        slotMinsAt = now; slotMinsFrom = here; slotMinsKey = key
        slotMinsJob = scope.launch {
            for ((k, p) in listOf("home" to home, "work" to work)) {
                if (p == null) { slotMins.remove(k); continue }
                if (Geo.dist(here, p.pos) < 800.0) { slotMins[k] = 0; continue }
                val r = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    withContext(Dispatchers.IO) {
                        try { if (online()) netRoutes(here, p.pos, false, 0L, emptyList(), -1.0, true).firstOrNull() else null } catch (e: Exception) { null }
                    }
                }
                if (route != null) return@launch
                slotMins[k] = r?.let { Math.round(it.time / 60.0).toInt() } ?: -1
            }
        }
    }

    // ---- turn arrow on the route ----
    private var turnArrowKey = -1

    /** Draws a white arrow on the route through the next junction once it's within ~700 m (not for "carry straight on"). */
    private fun updateTurnArrow(r: Route, k: Int, cur: Step, d: Double, arriving: Boolean) {
        val m = cur.maneuver.uppercase()
        val wanted = prefs.b(PreferenceKeys.TURNARROWS, true) && !previewing && !arriving && d < 700.0 &&
            !(m == "STRAIGHT" || m == "FOLLOW" || m.startsWith("DEPART") || m == "ROUNDABOUT_STRAIGHT" || m == "ROUNDABOUT_CROSS")
        // (redrawn when the lane offset changes, so the arrow always carries on from the route line in your lane)
        val key = if (wanted) (System.identityHashCode(r) * 31 + k) * 31 + System.identityHashCode(laneLatAt) else -1
        if (key == turnArrowKey) return
        turnArrowKey = key
        if (!wanted) { map.setTurnArrow(emptyList()); return }
        // a roundabout needs a longer arrow (round to the exit); a plain turn a short one
        val back = 38.0
        val fwd = if (m.startsWith("ROUNDABOUT")) 70.0 else 30.0
        val pts = ArrayList<LL>()
        var o = max(0.0, cur.off - back)
        val end = min(r.len, cur.off + fwd)
        val lat = laneLatAt
        fun at(off: Double): LL {
            val (p, brg) = pointAt(r, off)
            val l = lat?.invoke(off) ?: 0.0
            if (Math.abs(l) < 0.01) return p
            val rad = Math.toRadians(brg + 90.0)
            return LL(p.lat + l * Math.cos(rad) / 111320.0, p.lon + l * Math.sin(rad) / (111320.0 * Math.cos(Math.toRadians(p.lat))))
        }
        while (o < end) { pts += at(o); o += 4.0 }
        pts += at(end)
        map.setTurnArrow(pts)
    }

    /** The hazard radar: everything in the next two miles, nearest first. Only republished when what you would see changes. */
    private fun updateRadar(r: Route, progress: Double, pl: Placed) {
        if (bendsFor !== r) {
            bendsFor = r; bends = emptyList()
            scope.launch { val b = withContext(Dispatchers.Default) { try { routeBends(r) } catch (e: Exception) { emptyList() } }; if (bendsFor === r) bends = b }
        }
        val reach = 3219.0
        val out = ArrayList<RadarItem>()
        fun add(kind: String, off: Double, label: String) { val d = off - progress; if (d in -5.0..reach) out += RadarItem(kind, max(0.0, d), label) }
        if (camerasOn()) for ((_, off) in routeCams) add("camera", off, "Speed camera")
        for ((inc, off, _) in incMatches) add(if (inc.cat.contains("work", true)) "works" else "incident", off, incidentLabel(inc))
        if (prefs.b(PreferenceKeys.AVGZONES, true)) for (a in pl.avg) add("avg", a.start, "Average speed check")
        val school = RouteExtras.schoolTime(System.currentTimeMillis())
        for (h in pl.hz) when (h.poi.kind) {
            "bump" -> if (prefs.b(PreferenceKeys.BUMPS, true)) add("bump", h.off, "Speed bump")
            "school" -> if (prefs.b(PreferenceKeys.SCHOOLS, true)) add("school", h.off, if (school) "School · school run" else "School")
            "crossing" -> add("crossing", h.off, "Crossing")
            "rail" -> if (prefs.b(PreferenceKeys.RAILFORD, true)) add("rail", h.off, "Level crossing")
            "ford" -> if (prefs.b(PreferenceKeys.RAILFORD, true)) add("ford", h.off, "Ford")
        }
        for ((o, dir) in bends) add("bend", o, if (dir < 0) "Bend left" else "Bend right")
        out.sortBy { it.dist }
        val shown = out.take(14)
        var sig = shown.size
        for (e in shown) sig = sig * 31 + e.kind.hashCode() + (e.dist / 40.0).toInt()
        if (sig != radarSig) { radarSig = sig; radar = shown }
    }

    private val refRx = Regex("\\b([MAB][0-9]{1,4}(?:\\(M\\))?)\\b")

    /** Road numbers on a sign ("A34", "M3"). */
    private fun signRefs(sign: String): List<String> = refRx.findAll(sign).map { it.groupValues[1] }.distinct().toList()

    /** Place names on a sign, without the road numbers ("Winchester", "Basingstoke"). */
    private fun signDests(sign: String): List<String> =
        sign.split(',', '/', ';', '|').map { refRx.replace(it, "").replace(Regex("[()]"), "").trim() }.filter { it.length >= 3 && it.any { ch -> ch.isLetter() } }.distinct()

    /** "take junction 9 towards Winchester, then keep left for the A34", from the route's step and sign data. */
    private fun smartPhrase(cur: Step, nxt: Step?, word: String, tail: String): String {
        if (!prefs.b(PreferenceKeys.SMARTVOICE, true)) return word + tail
        val sayRoads = prefs.b(PreferenceKeys.VOICE_ROADS, true)
        val m = cur.maneuver.uppercase()
        val exitLike = m.contains("EXIT") || m.contains("KEEP") || m.contains("BEAR") || m.contains("RAMP")
        // "junction 9" is how it's said in the UK and Ireland; everywhere else it's "exit 9"
        val jWord = if (detectedCountry.ifBlank { "GB" }.uppercase() in setOf("GB", "IE", "IM", "JE", "GG")) "junction" else "exit"
        var main = if (cur.exitNo.isNotBlank() && exitLike) "take $jWord ${cur.exitNo}" else word
        val dests = signDests(cur.sign)
        val refs = signRefs(cur.sign)
        main += when {
            !sayRoads -> tail
            dests.isNotEmpty() -> (if (refs.isNotEmpty() && cur.exitNo.isNotBlank()) " for the ${refs[0]}" else "") + " towards " + dests.take(2).joinToString(" and ")
            refs.isNotEmpty() && exitLike -> " for the ${refs[0]}"
            else -> tail
        }
        if (nxt != null && nxt.off - cur.off in 1.0..450.0 && !nxt.maneuver.uppercase().startsWith("ARRIVE")) {
            val target = signRefs(nxt.sign).firstOrNull() ?: nxt.street
            val forWhat = when {
                target.isBlank() || !sayRoads -> ""
                refRx.matches(target) -> " for the $target"
                else -> " onto $target"
            }
            main += ", then " + Geo.maneuverText(nxt.maneuver, nxt.roundExit) + forWhat
        }
        return main
    }

    /** Counts the roads driven on this trip into the familiar-roads list (kept to the 300 most driven). */
    private fun flushRoads() {
        if (tripRoads.isEmpty()) return
        if (simMode != 0) { tripRoads.clear(); return }   // the demo drive doesn't count
        for (r in tripRoads) roadFam[r] = (roadFam[r] ?: 0) + 1
        tripRoads.clear()
        val top = roadFam.entries.sortedByDescending { it.value }.take(300)
        val o = org.json.JSONObject()
        for ((k, v) in top) o.put(k, v)
        prefs.put(PreferenceKeys.ROADFAM, o.toString())
    }

    /** The arrival moment: the camera swoops down to the destination, and the nearest car park is looked up. */
    private fun arrivalMoment(a: ArrivalInfo) {
        arrivalParking = null
        val d = a.place?.pos ?: return
        if (prefs.b(PreferenceKeys.ARRIVALFX, true)) {
            following = false
            recentreToken++
            map.cameraTo(d, fix?.heading ?: map.cameraNow()?.get(2), 58.0, 17.4, 2600, 0.25)
        }
        if (simMode != 0) return
        scope.launch {
            val list = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                withContext(Dispatchers.IO) {
                    try { if (hasOnline && online()) netPoiNearby("parking", d, 700) else Overpass.nearby("parking", d, 700) }
                    catch (e: Exception) { try { Overpass.nearby("parking", d, 700) } catch (e2: Exception) { emptyList() } }
                }
            }
            if (arrival === a) arrivalParking = list?.minByOrNull { Geo.dist(d, it.place.pos) }?.let { it.copy(dist = Geo.dist(d, it.place.pos)) }
        }
    }

    /** Closes the arrival card and goes back to following the car. */
    fun dismissArrival() {
        arrival = null; arrivalParking = null
        if (!following && route == null) recenter()
    }

    /** "left" or "right" for the side of the road the destination is on, from the route's own instruction or from where the pin is. */
    private fun arrivalSide(r: Route, maneuver: String): String {
        val m = maneuver.uppercase()
        if (m.endsWith("LEFT")) return "left"
        if (m.endsWith("RIGHT")) return "right"
        val d = dest?.pos ?: return ""
        if (r.pts.size < 2) return ""
        val a = r.pts[r.pts.size - 2]; val b = r.pts.last()
        if (Geo.dist(a, b) < 2.0 || Geo.dist(b, d) < 8.0) return ""
        val kx = cos(Geo.rad(b.lat)) * 111320.0
        val cross = ((b.lon - a.lon) * kx) * ((d.lat - a.lat) * 110574.0) - ((b.lat - a.lat) * 110574.0) * ((d.lon - a.lon) * kx)
        return if (cross > 0) "left" else "right"
    }

    // ---- arrive by ----
    /** Works out when to leave the chosen destination's route to arrive at [arriveMs], and reminds you when it is time. */
    fun planArriveBy(arriveMs: Long) {
        val d = dest ?: return
        val from = fix?.pos ?: map.center() ?: return
        val r = route
        val viaPts = via.map { it.pos }
        scope.launch {
            val leave = withContext(Dispatchers.IO) {
                try { if (hasKey && online()) TomTomRest.departureFor(apiKey, from, d.pos, viaPts, arriveMs) else null } catch (e: Exception) { null }
            } ?: (arriveMs - ((r?.time ?: 1800.0) * 1000).toLong() - 5 * 60_000L)
            val p = PlannedTrip(d, arriveMs, leave)
            planned = p
            savePlanned()
            // back to the map, where the planned card shows the leave time
            if (previewing) endRoute()
            val f = SimpleDateFormat("HH:mm", Locale.UK)
            toast(if (leave <= System.currentTimeMillis()) "Leave now to arrive by ${f.format(Date(arriveMs))}" else "Leave by ${f.format(Date(leave))} to arrive by ${f.format(Date(arriveMs))}. I'll remind you.", 5000)
            watchPlanned()
        }
    }

    fun cancelPlanned() { planned = null; savePlanned(); plannedJob?.cancel() }

    /** Starts the planned trip now. */
    fun startPlanned() { val p = planned ?: return; cancelPlanned(); startRoute(p.dest) }

    private fun savePlanned() {
        val p = planned
        prefs.put(PreferenceKeys.PLANNED, if (p == null) "{}" else org.json.JSONObject().put("n", p.dest.name).put("s", p.dest.sub).put("la", p.dest.pos.lat).put("lo", p.dest.pos.lon).put("a", p.arriveBy).put("l", p.leaveBy).toString())
    }

    /** Checks the planned trip twice a minute: refreshes the leave time with live traffic half an hour before, and gives the reminders. */
    private fun watchPlanned() {
        plannedJob?.cancel()
        plannedJob = scope.launch {
            var warned10 = false; var warned0 = false; var refreshed = false
            while (true) {
                val p = planned ?: break
                val now = System.currentTimeMillis()
                if (now > p.arriveBy) { cancelPlanned(); break }
                if (!refreshed && now > p.leaveBy - 35 * 60_000L && hasKey && online()) {
                    refreshed = true
                    val from = fix?.pos
                    if (from != null) {
                        val l = withContext(Dispatchers.IO) { try { TomTomRest.departureFor(apiKey, from, p.dest.pos, emptyList(), p.arriveBy) } catch (e: Exception) { null } }
                        if (l != null && planned === p) { planned = PlannedTrip(p.dest, p.arriveBy, l); savePlanned() }
                    }
                }
                val cur = planned ?: break
                val busyDriving = route != null && !previewing
                if (!busyDriving && !warned10 && now >= cur.leaveBy - 10 * 60_000L && now < cur.leaveBy) {
                    warned10 = true
                    raiseAlert("plan", "Leave in 10 minutes for ${cur.dest.name}", 7000)
                    speak("Leave in about 10 minutes for ${cur.dest.name}", true)
                }
                if (!busyDriving && !warned0 && now >= cur.leaveBy) {
                    warned0 = true
                    chime(android.media.ToneGenerator.TONE_PROP_PROMPT)
                    raiseAlert("plan", "Time to leave for ${cur.dest.name}", 8000)
                    speak("It's time to leave for ${cur.dest.name}", true)
                }
                delay(30_000)
            }
        }
    }

    // ---- smart fuel stop ----
    /** On a long trip, finds a good place to fill up around the middle of the route: the cheapest (with Fuel Finder) or the quickest detour. */
    // ---- electric cars: battery estimate ----
    val isEv: Boolean get() = prefs.s(PreferenceKeys.FUEL_TYPE, "E10") == "EV"
    /** Usable battery (kWh), from Settings > Fuel (60 if not set). */
    private fun evBatteryKwh(): Double = (prefs.s(PreferenceKeys.EV_BATTERY_KWH, "").toDoubleOrNull() ?: 60.0).coerceIn(10.0, 200.0)
    /** kWh used per km: from the efficiency in Settings > Fuel (miles per kWh), or a typical 0.18. */
    private fun evKwhPerKm(): Double = prefs.s(PreferenceKeys.EVMPK, "").toDoubleOrNull()?.takeIf { it in 1.0..8.0 }?.let { 1.0 / (it * 1.609344) } ?: 0.18
    /** Roughly how much battery (%) you'll arrive with: the charge you set before the trip, less what's been driven and what's left. */
    fun evArrivalPct(): Int? {
        if (!isEv) return null
        val e = eta ?: return null
        val start = prefs.i(PreferenceKeys.EV_CHARGE_PCT, 80).coerceIn(1, 100)
        val driven = if (navStartMs > 0) max(0.0, tripDist - navStartDist) else 0.0
        val usedPct = (driven + e.dist) / 1000.0 * evKwhPerKm() / evBatteryKwh() * 100.0
        return Math.round(start - usedPct).toInt().coerceIn(-50, 100)
    }

    /** The battery level or the charge to arrive with changed on the route options: look again for a charging stop. */
    fun refreshEvPlan() { val r = route ?: return; fuelSuggestion = null; suggestFuel(r) }

    private fun suggestFuel(r: Route) {
        val fuel = prefs.s(PreferenceKeys.FUEL_TYPE, "E10")
        if (fuel != "EV" && r.len < 150_000) return
        var a = r.len * 0.35; var b = r.len * 0.75
        // electric: a charger before the battery gets down to the charge you want to keep (Settings > Fuel), with the shortest
        // detour, and how much you'll have when you get there. Only when the trip needs one.
        if (fuel == "EV") {
            val cons = evKwhPerKm(); val batt = evBatteryKwh()
            val startPct = prefs.i(PreferenceKeys.EV_CHARGE_PCT, 80).coerceIn(1, 100); val minPct = prefs.i(PreferenceKeys.EV_MIN_ARRIVE, 20).coerceIn(0, 60)
            val rangeM = max(0.0, (startPct - minPct) / 100.0 * batt / cons * 1000.0)
            if (r.len <= rangeM) { fuelSuggestion = null; return }
            a = max(5_000.0, rangeM * 0.5); b = max(a + 5_000.0, rangeM * 0.92)
            scope.launch {
                val best = withContext(Dispatchers.IO) {
                    try {
                        val out = ArrayList<Nearby>()
                        if (hasKey && online()) {
                            val line = RouteExtras.line(r, a, b, 800.0, 140)
                            try {
                                for ((n, secs) in TomTomRest.poiAlong(apiKey, "charging", line, 600)) {
                                    val pr = RouteExtras.project(r, n.place.pos, 3000.0) ?: continue
                                    out += n.copy(dist = pr.first, detourMin = if (secs >= 0) Math.round(secs / 60.0).toInt() else detour(pr.second), ahead = true)
                                }
                            } catch (e: Exception) { }
                        }
                        if (out.isEmpty() && online()) {
                            val line = RouteExtras.line(r, a, b, 1500.0, 45)
                            for (n in Overpass.nearbyAlong("charging", line, 900)) {
                                val pr = RouteExtras.project(r, n.place.pos, 1200.0) ?: continue
                                if (pr.first in a..b) out += n.copy(dist = pr.first, detourMin = detour(pr.second), ahead = true)
                            }
                        }
                        out.filter { it.detourMin >= 0 }.minByOrNull { it.detourMin }?.let { n ->
                            val arr = Math.round(startPct - n.dist / 1000.0 * cons / batt * 100.0).toInt().coerceAtLeast(0)
                            val mins = Math.round((80 - arr).coerceAtLeast(10) / 100.0 * batt / 50.0 * 60.0).toInt()
                            n.copy(extra = "arrive about $arr% · charge to 80%, about $mins min")
                        }
                    } catch (e: Exception) { null }
                }
                if (route === r) fuelSuggestion = best
            }
            return
        }
        scope.launch {
            val best = withContext(Dispatchers.IO) {
                try {
                    val out = ArrayList<Nearby>()
                    if (FuelFinder.configured(prefs)) {
                        try { FuelFinder.refresh(ctx, prefs) } catch (e: Exception) { }
                        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
                        for (i in r.pts.indices) if (r.cum[i] in a..b) { val q = r.pts[i]; minLat = min(minLat, q.lat); maxLat = max(maxLat, q.lat); minLon = min(minLon, q.lon); maxLon = max(maxLon, q.lon) }
                        for (s0 in FuelFinder.stations(ctx)) {
                            if (s0.pos.lat < minLat - 0.015 || s0.pos.lat > maxLat + 0.015 || s0.pos.lon < minLon - 0.025 || s0.pos.lon > maxLon + 0.025) continue
                            val pr = RouteExtras.project(r, s0.pos, 1200.0) ?: continue
                            if (pr.first !in a..b) continue
                            val price = FuelFinder.priceOf(ctx, s0.id, fuel) ?: continue
                            out += Nearby(Place(s0.name, s0.brand, s0.pos), pr.first, if (s0.motorway) "Motorway services" else "", price.pence, detour(pr.second), true)
                        }
                    }
                    // outside the UK: the country's own price service, at a few points along the middle of the trip
                    val cc = detectedCountry.uppercase()
                    if (out.isEmpty() && cc != "GB" && EuFuel.supported(cc, prefs) && EuFuel.hasFuel(cc, fuel) && online()) {
                        for (s0 in EuFuel.along(cc, prefs, RouteExtras.line(r, a, b, 1500.0, 45), fuel, maxAsks = 5)) {
                            val pr = RouteExtras.project(r, s0.pos, 1200.0) ?: continue
                            if (pr.first !in a..b) continue
                            out += Nearby(Place(s0.name, s0.sub, s0.pos), pr.first, "", s0.cents, detour(pr.second), true, unit = "€")
                        }
                    }
                    if (out.isEmpty() && hasKey && online() && fuel != "E85") {
                        val line = RouteExtras.line(r, a, b, 800.0, 140)
                        for ((n, secs) in TomTomRest.poiAlong(apiKey, "fuel", line, 600)) {
                            val pr = RouteExtras.project(r, n.place.pos, 3000.0) ?: continue
                            out += n.copy(dist = pr.first, detourMin = if (secs >= 0) Math.round(secs / 60.0).toInt() else detour(pr.second), ahead = true)
                        }
                    }
                    // price matters most, but a long detour costs time (each minute counts as a penny a litre)
                    if (out.any { it.price != null }) out.filter { it.detourMin in 0..8 }.minByOrNull { (it.price ?: 999.0) + it.detourMin }
                    else out.filter { it.detourMin >= 0 }.minByOrNull { it.detourMin }
                } catch (e: Exception) { null }
            }
            if (route === r) fuelSuggestion = best
        }
    }

    // ---- wrong way ----
    private fun checkWrongWay(p: Fix) {
        if (simMode != 0 || !prefs.b(PreferenceKeys.WRONGWAY, true) || p.speed < 4.0 || (p.acc > 0 && p.acc > 20.0)) { wrongCount = 0; return }
        // the check runs through every nearby road, so it's done in the background (it used to stall the screen once a second)
        if (wrongBusy) return
        wrongBusy = true
        scope.launch {
            val wrong = withContext(Dispatchers.Default) { try { Overpass.wrongWay(p.pos, p.heading) } catch (e: Exception) { false } }
            wrongBusy = false
            if (wrong) wrongCount++ else wrongCount = 0
            wrongResult()
        }
    }
    private var wrongBusy = false

    private fun wrongResult() {
        val now = System.currentTimeMillis()
        if (wrongCount >= 3 && now - wrongAt > 60_000L) {
            wrongAt = now
            chime(android.media.ToneGenerator.TONE_PROP_BEEP2)
            raiseAlert("wrongway", "Wrong way! This is a one-way road", 7000)
            speak("Warning. You may be going the wrong way on a one-way road.", true)
        }
    }

    // ---- voice commands ----
    /**
     * Understands simple spoken commands. Returns null when it isn't a command (the words are then searched for),
     * "share" when the ETA should be shared, "search:<place>" to search for a place, or "" when it was handled.
     */
    fun handleVoice(raw: String): String? {
        val t = raw.lowercase(Locale.UK).trim().removePrefix("hey horizon").trim(' ', ',', '.')
        fun has(vararg w: String) = w.any { t.contains(it) }
        val f = SimpleDateFormat("HH:mm", Locale.UK)
        fun say(s: String) { toast(s, 3500); speak(s, true) }
        // places you have saved
        if (Regex("^(go|take me|navigate|drive|directions|head)( back)?( to)? ?home$").matches(t) || t == "home") {
            val h = places.slot("home"); if (h != null) { say("Going home"); startRoute(h) } else say("Home isn't set yet"); return ""
        }
        if (Regex("^(go|take me|navigate|drive|directions|head)( to)? ?work$").matches(t) || t == "work") {
            val w = places.slot("work"); if (w != null) { say("Going to work"); startRoute(w) } else say("Work isn't set yet"); return ""
        }
        // reports
        if (t.startsWith("report")) {
            val k = when { has("police", "cop") -> "police"; has("accident", "crash") -> "accident"; has("breakdown", "broken") -> "breakdown"; has("camera") -> "mobilecam"; else -> "hazard" }
            report(k); return ""
        }
        // finding things
        val kind = when {
            has("fuel", "petrol", "diesel", "filling station", "gas station") -> "fuel"
            has("charg") -> "charging"; has("toilet", "loo", "bathroom") -> "toilets"; has("coffee", "cafe", "café") -> "coffee"
            has("food", "eat", "restaurant", "hungry") -> "food"; has("services", "rest stop", "rest area") -> "services"
            has("parking", "car park", "somewhere to park") -> "parking"; else -> null
        }
        if (kind != null && has("find", "nearest", "look for", "search for", "where", "any ", "need", "show", "cheap", "stop for", "add a stop", "add stop", "near")) {
            nearbySort = if (has("cheap")) "cheap" else "near"
            if (has("stop") && route != null) stopMode = true
            loadNearby(kind, has("destination", "when i get there"))
            say("Looking for " + Overpass.kindLabel(kind).lowercase() + if (route != null) " along your route" else " nearby")
            return ""
        }
        // route preferences
        if (has("avoid toll")) { prefs.put(PreferenceKeys.AVOID_TOLL, true); say("Avoiding toll roads"); dest?.let { startRoute(it, departMin, true, startNow = route != null && !previewing) }; return "" }
        if (has("avoid motorway")) { prefs.put(PreferenceKeys.AVOID_MOTORWAY, true); say("Avoiding motorways"); dest?.let { startRoute(it, departMin, true, startNow = route != null && !previewing) }; return "" }
        if (has("avoid this road", "avoid the road")) { if (route != null) { say("Looking for a way round"); avoidRoadAhead() } else say("There's no route to change"); return "" }
        // trip info
        if (has("eta", "arrive", "how long", "how far", "time left", "when will i")) {
            val e = eta
            if (e == null) say("There's no route at the moment")
            else say("You'll arrive at ${e.arrive}, in ${Geo.fmtDur(e.secs)}. ${Geo.spoken(e.dist, imperial)} to go." + if (e.delayMin >= 1) " ${e.delayMin} minutes of traffic." else "")
            return ""
        }
        if (has("share")) return "share"
        // sound
        if (has("unmute", "sound on", "turn the voice on", "voice on")) { prefs.put(PreferenceKeys.SOUND_MODE, 0); say("Voice guidance on"); return "" }
        if (has("alerts only")) { prefs.put(PreferenceKeys.SOUND_MODE, 1); say("Alerts only"); return "" }
        if (has("mute", "be quiet", "quiet", "stop talking", "silence", "voice off")) { say("Voice off"); prefs.put(PreferenceKeys.SOUND_MODE, 2); return "" }
        // the map
        if (has("stop navigation", "end route", "end navigation", "cancel route", "cancel navigation", "stop the route", "stop route")) { endRoute(); say("Route ended"); return "" }
        if (has("overview", "show the route", "show route", "whole route")) { overview(); return "" }
        if (has("zoom in")) { zoom(1.0); return "" }
        if (has("zoom out")) { zoom(-1.0); return "" }
        if (has("north up")) { if (!northUp) toggleNorthUp(); return "" }
        if (t == "3d" || t == "2d" || has("three d", "two d")) { if ((t.contains("3") || t.contains("three")) != tilt3d) toggleTilt(); return "" }
        if (has("what time")) { say("It's ${f.format(Date())}"); return "" }
        // "take me to …", "navigate to …"
        Regex("^(?:go to|take me to|navigate to|drive to|directions to|head to|find|search for|show me)\\s+(.+)$").find(t)?.let { return "search:" + it.groupValues[1] }
        return null
    }

    /** The route's line from [a] to [b] metres along it. */
    private fun slice(r: Route, a: Double, b: Double): List<LL> {
        val out = ArrayList<LL>()
        out += pointAt(r, a).first
        for (i in r.pts.indices) { val c = r.cum[i]; if (c > a && c < b) out += r.pts[i] }
        out += pointAt(r, b).first
        return out
    }

    /** Draws the average speed zones on the route on the map: the road between the cameras, and the cameras at each end. */
    private fun pushAvgZones(r: Route, zones: List<AvgOnRoute>) {
        val lines = ArrayList<List<LL>>()
        val cams = ArrayList<Pair<LL, Double>>()
        for (z in zones) {
            val pts = ArrayList<LL>()
            val step = if (z.end - z.start > 20_000.0) 60.0 else 25.0
            var o = z.start
            while (o < z.end) { pts += pointAt(r, o).first; o += step }
            pts += pointAt(r, z.end).first
            lines += pts
            cams += pointAt(r, z.start); cams += pointAt(r, z.end)
        }
        map.setAvgZones(lines, cams)
    }

    /** Once a second while navigating: fetches and places services, bumps, schools and average speed checks, and raises their alerts. */
    private fun routeExtrasTick(r: Route, progress: Double) {
        if (previewing) return
        // your destination's building lights up for the last 400 m (and goes back to normal when you get there)
        run {
            val dp = dest?.pos
            val left = r.len - progress
            map.highlightDestination(if (dp != null && left in 15.0..400.0) dp else null)
        }
        val now = System.currentTimeMillis()
        barProgress = progress
        val d = dest
        // motorway services: once per destination, for longer trips
        if (d != null && servicesFor !== d && !servicesBusy && r.len > 15_000 && now > servicesRetryAt && online()) {
            servicesBusy = true
            val line = RouteExtras.line(r, progress, progress + 400_000.0, 2000.0, 220)
            scope.launch {
                try { rawServices = withContext(Dispatchers.IO) { RouteExtras.services(line) }; servicesFor = d }
                catch (e: Exception) { servicesRetryAt = System.currentTimeMillis() + 120_000L }
                finally { servicesBusy = false }
            }
        }
        // speed bumps, schools and average speed checks: a window of the next 20 km, fetched again before you reach its end
        val wantHz = prefs.b(PreferenceKeys.BUMPS, true) || prefs.b(PreferenceKeys.SCHOOLS, true) || prefs.b(PreferenceKeys.AVGZONES, true)
        // (a new route after a reroute is looked up again, but not more than once every 45 seconds)
        if (wantHz && !hzBusy && (progress + 6000.0 > hzWinEnd || (hzWinRoute !== r && now - hzLastAt > 45_000L)) && now > hzRetryAt && online()) {
            hzBusy = true; hzLastAt = now
            val to = progress + 20_000.0
            val line = RouteExtras.line(r, progress - 300.0, to, 250.0, 90)
            scope.launch {
                try {
                    val (p, a) = withContext(Dispatchers.IO) { RouteExtras.hazards(line) }
                    rawHz = (rawHz + p).distinctBy { "${it.kind}${Math.round(it.pos.lat * 1e5)},${Math.round(it.pos.lon * 1e5)}" }.takeLast(1500)
                    rawAvg = (rawAvg + a).distinctBy { it.id }.takeLast(60)
                    hzWinRoute = r; hzWinEnd = to
                } catch (e: Exception) { hzRetryAt = System.currentTimeMillis() + 60_000L }
                finally { hzBusy = false }
            }
        }
        // place everything on this route (in the background) whenever the route or what was found changes
        val sig = ((System.identityHashCode(r) * 31 + rawServices.size) * 31 + rawHz.size) * 31 + rawAvg.size * 7 + via.size * 13 + zonesVersion * 17
        if (sig != placedSig && !placing) {
            placing = true
            val sv = rawServices; val hz = rawHz; val av = rawAvg; val vs = via
            val zoneWarn = prefs.b(PreferenceKeys.ZONEWARN, true)
            val fuel = prefs.s(PreferenceKeys.FUEL_TYPE, "E10")
            val fuelOn = FuelFinder.configured(prefs) && fuel != "EV"
            scope.launch {
                try {
                    val pl = withContext(Dispatchers.Default) {
                        val svc = sv.mapNotNull { poi -> RouteExtras.project(r, poi.pos, 400.0)?.let { (o, side) -> PoiOnRoute(poi, o, side) } }.sortedBy { it.off }
                        val fuelText = HashMap<RoutePoi, String>()
                        if (fuelOn) {
                            val st = FuelFinder.stations(ctx)
                            for (s in svc) {
                                val near = st.filter { Geo.dist(it.pos, s.poi.pos) < 700.0 }.minByOrNull { Geo.dist(it.pos, s.poi.pos) } ?: continue
                                FuelFinder.priceOf(ctx, near.id, fuel)?.let { fuelText[s.poi] = String.format(java.util.Locale.UK, "%s %.1fp", if (fuel == "B7") "Diesel" else fuel, it.pence) }
                            }
                        }
                        val hzOn = hz.mapNotNull { poi -> RouteExtras.project(r, poi.pos, if (poi.kind == "school") 90.0 else if (poi.kind == "crossing") 12.0 else 15.0)?.let { (o, side) -> PoiOnRoute(poi, o, side) } }
                        val avg = av.mapNotNull { RouteExtras.placeAvg(r, it) }.sortedBy { it.start }
                        val stops = vs.mapNotNull { RouteExtras.project(r, it.pos, 300.0)?.first }
                        val zs = if (zoneWarn) Zones.entries(r, Zones.all(ctx)).map { it.first.name to it.second } else emptyList()
                        Placed(r, svc, fuelText, hzOn, avg, stops, zs)
                    }
                    placed = pl; placedSig = sig
                    pushAvgZones(r, if (prefs.b(PreferenceKeys.AVGZONES, true)) pl.avg else emptyList())
                    // zebra and other marked crossings on the route, painted on the road
                    map.setCrossings(if (prefs.b(PreferenceKeys.ZEBRAS, true)) pl.hz.filter { it.poi.kind == "crossing" && it.poi.name in setOf("zebra", "marked", "uncontrolled") }
                        .take(300).map { Triple(it.poi.pos, pointAt(r, it.off).second, it.poi.name == "zebra") } else emptyList())
                    // stop signs on the route
                    map.setStopSigns(if (prefs.b(PreferenceKeys.STOP_SIGNS, true)) pl.hz.filter { it.poi.kind == "stop" }.take(300).map { it.poi.pos } else emptyList())
                } catch (e: Exception) { placedSig = sig } finally { placing = false }
            }
        }
        val pl = placed?.takeIf { it.route === r }
        if (pl == null) { if (avgInfo != null) avgInfo = null; return }
        val sp = fix?.speed ?: 0.0

        // average speed check
        if (prefs.b(PreferenceKeys.AVGZONES, true)) {
            val cur = pl.avg.firstOrNull { progress >= it.start && progress < it.end }
            if (cur != null) {
                if (avgIn !== cur) {
                    avgIn = cur; avgEnterAt = now; avgEnterProg = progress; avgOverWarned = false
                    if (avgAlerted.add(cur.def.id)) {
                        chime(android.media.ToneGenerator.TONE_PROP_PROMPT)
                        raiseAlert("avg", "Average speed check for ${Geo.fmtDist(cur.end - progress, imperial)}", 6000)
                        speak("Average speed check for ${Geo.spoken(cur.end - progress, imperial)}", true)
                    }
                }
                val secs = (now - avgEnterAt) / 1000.0
                val avg = if (secs > 4.0) (progress - avgEnterProg) / secs else sp
                val lim = cur.def.limit ?: speedLimit
                val over = lim != null && secs > 15.0 && avg > lim + 0.45
                if (over && !avgOverWarned) {
                    avgOverWarned = true
                    chime(android.media.ToneGenerator.TONE_PROP_BEEP2)
                    speak("Your average speed is over the limit", true)
                } else if (!over && lim != null && avg < lim - 0.5) avgOverWarned = false
                // the speed you can do for the rest of the zone and still average under the limit: the time the whole zone may take
                // at the limit, less the time used so far, spread over what's left
                val left = cur.end - progress
                val safe = if (lim != null && secs > 4.0) {
                    val allowed = (cur.end - avgEnterProg) / lim - secs
                    if (allowed > 1.0) left / allowed else 0.0
                } else null
                avgInfo = AvgInfo(avg, lim, left, over, false, 0.0, safe)
            } else {
                if (avgIn != null) { avgIn = null; toast("End of average speed check", 2500) }
                val next = pl.avg.filter { it.start > progress && it.start - progress < 1200.0 }.minByOrNull { it.start }
                avgInfo = next?.let { AvgInfo(0.0, it.def.limit ?: speedLimit, it.end - it.start, false, true, it.start - progress) }
                if (next != null && next.start - progress < 500.0 && avgAlerted.add(next.def.id)) {
                    chime(android.media.ToneGenerator.TONE_PROP_PROMPT)
                    raiseAlert("avg", "Average speed check ahead, ${Geo.fmtDist(next.end - next.start, imperial)} long", 6000)
                    speak("Average speed check ahead", true)
                }
            }
        } else if (avgInfo != null) avgInfo = null

        if (prefs.b(PreferenceKeys.HAZARDRADAR, false)) updateRadar(r, progress, pl)

        // weather and night advice: a quiet note (no sound) in heavy rain, snow, fog or ice when you're near the limit, once per 10 minutes
        run {
            val code = weather?.code
            val lim = speedLimit
            val what = when (code) {
                45, 48 -> "Fog"
                63, 65, 81, 82 -> "Heavy rain"
                in 95..99 -> "Thunderstorms"
                56, 57, 66, 67 -> "Freezing rain"
                in 71..77, 85, 86 -> "Snow"
                else -> null
            }
            val nowMs = System.currentTimeMillis()
            if (prefs.b(PreferenceKeys.WXADVICE, true) && what != null && lim != null && sp > lim * 0.85 && nowMs - adviceAt > 600_000L) {
                adviceAt = nowMs
                val k = if (imperial) 2.23694 else 3.6
                advice = "$what · take it steady, ${Math.round(lim * k / 5.0) * 5} limit"
                scope.launch { delay(9000); advice = null }
            }
        }

        // stop signs on the route just ahead: a short chime and a pill (Settings > Junctions and lanes > Stop signs)
        if (prefs.b(PreferenceKeys.STOP_SIGNS, true)) for (h in pl.hz) {
            if (h.poi.kind != "stop") continue
            val ahead = h.off - progress
            if (ahead < 0.0 || ahead > max(80.0, sp * 6.0)) continue
            val key = "stop${Math.round(h.poi.pos.lat * 1e5)},${Math.round(h.poi.pos.lon * 1e5)}"
            if (!hzAlerted.add(key)) continue
            chime(android.media.ToneGenerator.TONE_PROP_ACK)
            raiseAlert("stop", "Stop sign ahead", 4000)
        }

        // level crossings and fords just ahead: a sign and a spoken warning
        if (prefs.b(PreferenceKeys.RAILFORD, true)) for (h in pl.hz) {
            if (h.poi.kind != "rail" && h.poi.kind != "ford") continue
            val ahead = h.off - progress
            if (ahead < 0.0 || ahead > max(220.0, sp * 9.0)) continue
            val key = "${h.poi.kind}${Math.round(h.poi.pos.lat * 1e5)},${Math.round(h.poi.pos.lon * 1e5)}"
            if (!hzAlerted.add(key)) continue
            if (h.poi.kind == "rail") { raiseAlert("rail", "Level crossing ahead", 6000); speak("Level crossing ahead", true) }
            else { raiseAlert("ford", "Ford ahead: the road may be flooded", 7000); speak("Ford ahead. The road may be flooded", true) }
        }

        // speed bumps and schools just ahead
        for (h in pl.hz) {
            val ahead = h.off - progress
            if (ahead < 0.0 || ahead > 320.0 || h.poi.kind == "crossing" || h.poi.kind == "rail" || h.poi.kind == "ford" || h.poi.kind == "stop") continue
            val key = "${h.poi.kind}${Math.round(h.poi.pos.lat * 1e5)},${Math.round(h.poi.pos.lon * 1e5)}"
            if (h.poi.kind == "bump") {
                if (prefs.b(PreferenceKeys.BUMPS, true) && sp > 6.7 && ahead < max(70.0, sp * 6.0) && now - lastBumpAt > 25_000L && hzAlerted.add(key)) {
                    lastBumpAt = now
                    chime(android.media.ToneGenerator.TONE_PROP_ACK)
                    raiseAlert("bump", "Speed bump ahead", 3500)
                }
            } else if (prefs.b(PreferenceKeys.SCHOOLS, true) && ahead < 300.0 && RouteExtras.schoolTime(now) && hzAlerted.add(key)) {
                raiseAlert("school", "School ahead: ${h.poi.name}", 5000)
                speak("School ahead", true)
            }
        }

        // clean air / low emission zones
        if (prefs.b(PreferenceKeys.ZONEWARN, true)) for ((name, off) in pl.zones) {
            val ahead = off - progress
            if (ahead in -50.0..1500.0 && zoneAlerted.add(name)) {
                raiseAlert("zone", if (ahead <= 0) "You are in $name" else "Entering $name in ${Geo.fmtDist(ahead, imperial)}", 7000)
                speak(if (ahead <= 0) "You are in $name. A charge may apply." else "You are about to enter $name. A charge may apply.", true)
            }
        }

        // next services (only republished when what a driver would see changes)
        // (one entry per services: the map data often has the same services twice, as the services and as its fuel station or car park,
        // or once per side of the motorway, which showed the same name twice)
        val nextSvc = ArrayList<PoiOnRoute>()
        for (sv in pl.services.filter { it.off > progress + 150.0 && it.off - progress < 130_000.0 && it.side < 400.0 }.sortedBy { it.off }) {
            val nm = sv.poi.name.lowercase().replace(Regex("\\b(services|service area|motorway services|moto|welcome break|roadchef|extra)\\b"), "").replace(Regex("[^a-z0-9]"), "")
            if (nextSvc.any { o -> Math.abs(o.off - sv.off) < 2500.0 || (nm.isNotEmpty() && o.poi.name.lowercase().replace(Regex("\\b(services|service area|motorway services|moto|welcome break|roadchef|extra)\\b"), "").replace(Regex("[^a-z0-9]"), "") == nm) }) continue
            nextSvc += sv
            if (nextSvc.size >= 1) break
        }
        var ss = nextSvc.size
        for (s in nextSvc) ss = ss * 31 + s.poi.name.hashCode() + ((s.off - progress) / 160.0).toInt()
        if (ss != servicesSig) {
            servicesSig = ss
            servicesAhead = nextSvc.map { ServiceAhead(it.poi.name, it.off - progress, pl.serviceFuel[it.poi].orEmpty()) }
        }

        // route bar markers
        if (prefs.b(PreferenceKeys.ROUTEBAR, true)) {
            val span = r.len
            val ev = ArrayList<BarEvent>()
            if (camerasOn()) for ((_, off) in routeCams) ev += BarEvent(off, "camera")
            for ((inc, off, _) in incMatches) ev += BarEvent(off, if (inc.cat.contains("work", true)) "works" else "incident")
            for (s in pl.services) if (s.side < 400.0) ev += BarEvent(s.off, "services")
            for (a in pl.avg) ev += BarEvent(a.start, "avg")
            for (o in pl.stops) ev += BarEvent(o, "stop")
            for ((_, o) in pl.zones) ev += BarEvent(o, "zone")
            val shown = ev.filter { it.off >= progress - 30.0 && it.off <= span }
            var bs = shown.size
            for (e in shown) bs = bs * 31 + e.off.toInt() / 50 + e.kind.hashCode()
            if (bs != barSig) { barSig = bs; barEvents = shown }
        }
    }

    fun speakForce(text: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "read")
    }

    /** Turn-by-turn guidance is spoken only in "all" mode; alerts are also spoken in "alerts only". */
    /** True for the engine running the Android Auto screen. */
    @Volatile var isCar = false

    /** The phone keeps quiet while the car screen is connected: the car says the directions and alerts. */
    private fun quiet() = !isCar && RouteSync.carConnected()

    /** The phone's app is showing its own map (set by the screen; with Android Auto connected it normally shows the trip in brief). */
    @Volatile var showingMap = true

    /**
     * The phone while the car screen runs the trip and the phone isn't showing its map: it keeps following the route (so it can
     * carry on by itself when the phone is unplugged) but leaves the lookups to the car: speed limits, cameras, traffic and faster
     * routes aren't fetched twice, which halves the requests on your TomTom key and saves the phone's battery.
     */
    private fun shadow() = quiet() && !showingMap

    /** Joins a trip started on the other screen (the phone or the car): the same destination and stops, straight into guidance. */
    fun followRoute(to: Place, stops: List<Place>) {
        val d = dest
        if (d != null && Geo.dist(d.pos, to.pos) < 30.0 && route != null) {
            // already showing this route's choice (a place shared to both screens): set off with it
            if (previewing) beginNavigation()
            return
        }
        scope.launch {
            // a screen that has only just started may not know where it is yet: give the GPS a moment first
            var n = 0
            while (fix == null && n < 40) { delay(500); n++ }
            via = stops
            startRoute(to, keepVia = true, startNow = true)
        }
    }

    fun speak(text: String, alert: Boolean = false) {
        if (quiet()) return
        val m = soundMode
        if (m == 2 || (m == 1 && !alert) || !ttsReady) return
        val ch = prefs.s(PreferenceKeys.VOICE_CHANNEL, "nav")
        if (ch != voiceChan) { voiceChan = ch; try { tts?.setAudioAttributes(speechAttrs) } catch (e: Exception) { } }
        duck()
        // a direction cuts off whatever is being said; an alert waits its turn, so a speed limit or camera call can no longer
        // cut a direction off half way (with limits changing often in town, some directions were never heard)
        tts?.speak(text, if (alert) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    // ---- demo drive ----
    fun cycleSim() {
        val r = route
        if (r == null) { toast("Pick a destination first, then start the demo drive"); return }
        when (simMode) {
            0 -> { simMode = 1; startSim(r) }
            1 -> simMode = 4
            else -> stopSim()
        }
    }

    private fun startSim(r: Route) {
        simJob?.cancel()
        simDist = fix?.let { snap(r, it.pos).progress } ?: 0.0
        following = true
        simJob = scope.launch {
            while (true) {
                val cur = route ?: break
                // the position moves by exactly the speed it reports, so the arrow glide never sees a gap it has to jump to close
                // (the old demo moved 16 m a second while claiming 7 m/s near a turn, which the glide "corrected" with a jump halfway round the roundabout)
                val td = turn?.dist ?: 1000.0
                val demoSpeed = (if (td < 150) 7.0 else if (td < 400) 13.0 else if (td < 1200) 20.0 else 27.0) * simMode
                simDist = min(cur.len, simDist + demoSpeed)
                val (pt, hd) = pointAt(cur, simDist)
                onPosition(Fix(pt, hd, demoSpeed, 0.0), false)
                if (simDist >= cur.len) break
                delay(1000)
            }
            simMode = 0
        }
    }

    fun stopSim() { simJob?.cancel(); simJob = null; simMode = 0 }

    var onRoad by mutableStateOf("")
    private var limitRoad = ""
    private var roadShown = ""

    private var limitRoadAt = 0L
    private var limitRoadHead = -1.0
    private var roadCandidate = ""

    /**
     * A road name from the lookup round you. A different name only shows once a second lookup agrees, or straight away if you've
     * clearly turned (40 degrees or more), so it no longer jumps to a side road for a moment at junctions. An empty name (no road
     * found, like a car park) clears it.
     */
    private fun noteRoad(name: String, heading: Double) {
        val now = System.currentTimeMillis()
        val turned = heading >= 0 && limitRoadHead >= 0 && abs(((heading - limitRoadHead + 540.0) % 360.0) - 180.0) >= 40.0
        if (name == limitRoad || name.isBlank() || limitRoad.isBlank() || turned || name == roadCandidate) {
            limitRoad = name; roadCandidate = ""
            limitRoadAt = now; if (heading >= 0) limitRoadHead = heading
        } else roadCandidate = name
        refreshRoad()
    }

    /** Name of the road you are on: from the route while navigating, otherwise from the map data around you. */
    private fun refreshRoad() {
        // the map-data name is dropped once it's 45 seconds old (lookups that kept failing used to leave the last road up)
        val mapRoad = if (System.currentTimeMillis() - limitRoadAt < 45_000L) limitRoad else ""
        val n = Geo.shortRoad(if (route != null && !previewing && onRoad.isNotBlank()) onRoad else mapRoad)
        if (n != roadShown) { roadShown = n; map.setRoadName(n) }
        if (roadNow != n) roadNow = n
    }
    private var laneKey: Pair<Double, LaneSec>? = null

    private fun laneHit(dir: String, maneuver: String): Boolean {
        val m = maneuver.uppercase()
        return when {
            m.contains("U_TURN") -> dir.endsWith("U_TURN")
            m.contains("LEFT") -> dir.contains("LEFT")
            m.contains("RIGHT") -> dir.contains("RIGHT")
            else -> dir == "STRAIGHT"
        }
    }

    /** Paints the lane arrows on the road ahead of the junction (approximate: placed across the route line). */
    /** Set when you're on a motorway or dual carriageway: lane guidance then starts further out. */
    private var laneFast = false
    private var earlyLaneFor = -1.0

    private fun updateLaneMarks(r: Route, cur: Step, sec: LaneSec?, d: Double) {
        val key = if (sec != null && d < (if (laneFast) 1300.0 else 400.0) && prefs.b(PreferenceKeys.LANEMAP, true)) Pair(cur.off, sec) else null
        // the route line stays in its lane until you're past the end of its ease back after the junction (clearing it as soon as
        // the lane guidance ended made the line just ahead of you jump sideways)
        if (laneShiftEnd > 0.0 && key == null && curProgress >= laneShiftEnd) { map.setRouteShift(emptyList()); laneLatAt = null; laneShiftEnd = 0.0; turnArrowKey = -2 }
        // where Mapbox has HD roads (the lanes it paints on motorways), the real lanes are read off the map just before the junction,
        // so the lanes drawn and the route line sit exactly on the painted ones (asked every 3 s until found, up to 8 times)
        if (key != null && hdLanesFor != cur.off) {
            if (hdTriesFor != cur.off) { hdTriesFor = cur.off; hdTries = 0 }
            val now = android.os.SystemClock.uptimeMillis()
            if (hdTries < 8 && now - hdAskAt > 3000L && d < 700.0) {
                hdAskAt = now; hdTries++
                val askOff = cur.off
                val nLanes = sec?.lanes?.size ?: 0
                // several places across the approach: close to the junction the exit lanes have often already split off behind
                // the hatched area, so the lanes are looked at further back too, and followed from one place to the next
                val backs = listOf(150.0, 120.0, 90.0, 60.0, 35.0, 15.0).filter { cur.off - it > 0.0 }
                map.laneCentresAlong(backs.map { pointAt(r, cur.off - it) }) { res, lines ->
                    if (hdTriesFor != askOff) return@laneCentresAlong
                    val prof = ArrayList<Pair<Double, List<Double>>>()
                    res?.forEachIndexed { k, offs -> pickLaneWindow(offs, nLanes)?.let { prof += (askOff - backs[k]) to it } }
                    laneDiag = when {
                        res == null -> "Lane data: the junction isn't on screen yet"
                        lines == 0 -> "Lane data: no HD lanes from Mapbox here (lanes laid out from the road's line)"
                        prof.isEmpty() -> "Lane data: HD lanes found ($lines lines) but they didn't match the $nLanes lanes listed (found " + res.joinToString(", ") { it.size.toString() } + " across the road)"
                        else -> "Lane data: HD lanes lined up at ${prof.size} of ${backs.size} points along the approach"
                    }
                    if (res != null && (prof.size >= 3 || hdTries >= 8) && laneDiagFor != askOff && prefs.b(PreferenceKeys.DEMO, false)) { laneDiagFor = askOff; toast(laneDiag, 5000) }
                    // a better read (more places matched) replaces the last; it keeps looking until at least 3 places match
                    if (prof.isNotEmpty() && (hdProfileFor != askOff || prof.size > hdProfile.size)) { hdProfile = prof.sortedBy { it.first }; hdProfileFor = askOff; laneKey = null }
                    if (prof.size >= 3) hdLanesFor = askOff
                }
            }
        }
        // the lane sign goes once you're up to it
        if (laneSignFor >= 0.0 && curProgress > laneSignFor - 15.0) { map.setLaneSign(null, emptyList()); laneSignFor = -1.0 }
        // the lane bubble rides just ahead of the arrow until you reach the junction
        bubbleLanes?.let { bl ->
            if (key != null && curProgress < cur.off - 20.0) map.setLaneBubble(pointAt(r, min(r.len, curProgress + 24.0)).first, bl) else map.setLaneBubble(null, emptyList())
        }
        if (key == laneKey) return
        laneKey = key
        if (key == null || sec == null) {
            map.setLaneSign(null, emptyList()); laneSignFor = -1.0
            bubbleLanes = null; map.setLaneBubble(null, emptyList())
            map.setLanes(emptyList())
            if (laneShiftEnd <= 0.0 || curProgress >= laneShiftEnd) { map.setRouteShift(emptyList()); laneLatAt = null; laneShiftEnd = 0.0 }
            return
        }
        val n = sec.lanes.size
        val anyFollow = sec.lanes.any { it.follow != null }
        val activeIdx = sec.lanes.indices.filter { i -> val ln = sec.lanes[i]; if (anyFollow) ln.follow != null else ln.dirs.any { laneHit(it, cur.maneuver) } }
        // Lined up with the real lanes (the ones Mapbox's map paints where it has them): the route line runs down the middle of the
        // road as mapped, so the lanes are laid out from there, at real lane widths. On a one-way road or a dual carriageway the
        // lanes are centred on the line; on a two-way road they're all on your side of it (the left in the UK).
        val (approach, approachB) = pointAt(r, max(0.0, cur.off - 45.0))
        val road = map.roadAt(approach, approachB, maxDistance = 10.0, headingTolerance = 25.0)
        // Require a close, direction-compatible road match here: at exits and junctions a nearby ramp or cross-road can
        // otherwise supply the wrong one-way/road-centre metadata and offset the highlighted lane away from the route.
        // If the match is ambiguous, use the lane-relative fallback below so the route remains in the selected lane.
        // (with no road data from the map the old way is used: the lanes laid out with the lane to be in under the route line)
        // Only lined up with the road on one-way motorway and trunk carriageways (not slip roads), where the mapped line really
        // runs down the middle of the lanes. On ordinary A roads and town junctions the lanes often widen or gain a turning lane
        // that the mapped line doesn't follow, which put the lanes (and the route line moved into them) off the road; there the
        // lanes are laid out with the lane to be in under the route line, and the route line stays where it is.
        val big = road?.cls?.let { (it == "motorway" || it == "trunk") } == true
        val aligned = road != null && road.oneway == true && big
        val laneW = if (!aligned) 3.6 else if (big) 3.65 else 3.5
        // Lined up: before a junction the mapped line runs down the middle of the main carriageway, which is the lanes that carry
        // straight on. The lanes peeling off to an exit are extra lanes beside it (at the junction Mapbox lists them all together),
        // so the straight-on lanes are centred on the line and the exit lanes sit off to their side. Centring all of them on the
        // line put every lane a lane or two out towards the exit, over the verge. A true split (no straight-on lane) stays centred.
        val throughIdx = sec.lanes.indices.filter { i -> sec.lanes[i].dirs.any { it == "STRAIGHT" } }
        val centre = if (!aligned) (if (activeIdx.isEmpty()) (n - 1) / 2.0 else activeIdx.average())
            else if (throughIdx.isNotEmpty() && throughIdx.size < n && isContiguous(throughIdx)) throughIdx.average()
            else (n - 1) / 2.0
        // each lane's middle and edges, sideways from the route line (metres, + to the right): the real ones read off the map when
        // they match the lane list, else laid out at standard widths as described above
        // Lanes are only painted on the road (and the route line moved into them) once the real lanes have been read off the
        // map and matched at 3 or more places along the approach. Everywhere else a lane sign stands over the road at the junction
        // instead, like a motorway gantry, so nothing is drawn that might not sit on the painted lanes.
        val prof = if (hdLanesFor == cur.off && hdProfileFor == cur.off) hdProfile.filter { it.second.size == n } else emptyList()
        val lined = prof.isNotEmpty()
        if (!lined && curProgress < cur.off - 15.0) {
            map.setLaneSign(pointAt(r, max(0.0, cur.off - 10.0)).first, sec.lanes.mapIndexed { i, lane ->
                val active = i in activeIdx
                lane.dirs.map { dir -> dir to (active && (if (anyFollow) dir == lane.follow else laneHit(dir, cur.maneuver))) } to active
            })
            laneSignFor = cur.off
            bubbleLanes = null
        } else {
            map.setLaneSign(null, emptyList()); laneSignFor = -1.0
            bubbleLanes = if (lined) sec.lanes.mapIndexed { i, lane ->
                val active = i in activeIdx
                lane.dirs.map { dir -> dir to (active && (if (anyFollow) dir == lane.follow else laneHit(dir, cur.maneuver))) } to active
            } else null
        }
        // each lane's middle at [o] metres along the route: from the real lanes (in between the places they were read, and held
        // at the first / last beyond them), else the standard layout
        fun mids(o: Double): DoubleArray {
            if (prof.isEmpty()) return DoubleArray(n) { i -> (i - centre) * laneW }
            if (o <= prof.first().first) return prof.first().second.toDoubleArray()
            if (o >= prof.last().first) return prof.last().second.toDoubleArray()
            val k = prof.indexOfLast { it.first <= o }
            val (oa, a) = prof[k]; val (ob, b) = prof[k + 1]
            val t = if (ob > oa) (o - oa) / (ob - oa) else 0.0
            return DoubleArray(n) { i -> a[i] + (b[i] - a[i]) * t }
        }
        fun leftE(m: DoubleArray, i: Int) = if (prof.isEmpty()) m[i] - laneW / 2 else if (i > 0) (m[i - 1] + m[i]) / 2 else m[0] - (m[1] - m[0]) / 2
        fun rightE(m: DoubleArray, i: Int) = if (prof.isEmpty()) m[i] + laneW / 2 else if (i < n - 1) (m[i] + m[i + 1]) / 2 else m[n - 1] + (m[n - 1] - m[n - 2]) / 2
        fun shifted(p: LL, brg: Double, lateral: Double): LL {
            val rad = Math.toRadians(brg + 90.0)
            return LL(p.lat + lateral * Math.cos(rad) / 111320.0, p.lon + lateral * Math.sin(rad) / (111320.0 * Math.cos(Math.toRadians(p.lat))))
        }
        val marks = ArrayList<MapBinding.LaneMark>()
        for (back in if (lined) listOf(28.0, 65.0, 105.0) else emptyList()) {
            val off = cur.off - back
            if (off < 0) continue
            val (p, brg) = pointAt(r, off)
            val m = mids(off)
            sec.lanes.forEachIndexed { i, lane ->
                val at = shifted(p, brg, m[i])
                val active = i in activeIdx
                lane.dirs.forEach { dir ->
                    val hit = active && (if (anyFollow) dir == lane.follow else laneHit(dir, cur.maneuver))
                    marks += MapBinding.LaneMark(at, brg, dir, hit)
                }
            }
        }
        // lane strips from up to 130 m before the junction to just short of it
        // TomTom's lane section can be short, but the lanes carry on back along the approach, so the strips do not stop where the section starts
        // (only the last stretch before the junction, where the lanes really are: about 110 m on motorways, 60 m elsewhere,
        // so lanes aren't painted far back along a road that only widens at the junction)
        val from = max(0.0, cur.off - (if (lined) 150.0 else 60.0))
        val to = cur.off - 6.0
        val bands = ArrayList<MapBinding.LaneBand>()
        val divs = ArrayList<MapBinding.LaneEdge>()
        if (lined && to - from > 20.0) {
            val samples = ArrayList<Pair<LL, Double>>()
            val sampleMids = ArrayList<DoubleArray>()
            var o = from
            while (o < to) { samples += pointAt(r, o); sampleMids += mids(o); o += 10.0 }
            samples += pointAt(r, to); sampleMids += mids(to)
            val last = samples.size - 1
            for (i in 0 until n) {
                // cut into short pieces so the ribbon can fade in softly at its start
                for (k in 0 until last) {
                    val l = ArrayList<LL>(); val rt = ArrayList<LL>()
                    for (m in k..k + 1) { val (p, brg) = samples[m]; l += shifted(p, brg, leftE(sampleMids[m], i)); rt += shifted(p, brg, rightE(sampleMids[m], i)) }
                    val a = 0.5 + 0.5 * min(1.0, k / 3.0)
                    bands += MapBinding.LaneBand(l + rt.asReversed(), i in activeIdx, a)
                }
                val edge = ArrayList<LL>(); for ((j, sp) in samples.withIndex()) edge += shifted(sp.first, sp.second, rightE(sampleMids[j], i))
                divs += MapBinding.LaneEdge(edge, i == n - 1)
            }
            val outer = ArrayList<LL>(); for ((j, sp) in samples.withIndex()) outer += shifted(sp.first, sp.second, leftE(sampleMids[j], 0))
            divs += MapBinding.LaneEdge(outer, true)
        }
        map.setLaneBands(bands, divs)
        // the route line moved into the lane you need (when the lanes are lined up with the road). One sideways offset along the
        // route, used for both the route line and the turn arrow so they always meet with no step: it eases across (over up to
        // 60 m), stays in the middle of the lit-up lanes right up to the junction, then eases back onto the road's line over the first
        // 25 m after it (where the route carries on along the slip road or the main road). Before, the line only came back in the
        // last 8 m while the turn arrow, drawn on the road's own line from 38 m out, left a gap and a step where they met.
        val shift = ArrayList<LL>()
        laneLatAt = null; laneShiftEnd = 0.0
        if (lined && activeIdx.isNotEmpty()) {
            // the middle of the lit-up lanes, wherever they are along the approach
            fun midAt(o: Double): Double { val m = mids(o); return activeIdx.map { m[it] }.average() }
            val mid = midAt(cur.off - 40.0)
            val s0 = max(0.0, max(sec.off, cur.off - 250.0))
            val s1 = min(r.len, cur.off + 25.0)
            // (fully in the lane by the time the lane strips start, 110 m out, when there's room; else over the first half)
            val stripStart = cur.off - 150.0
            val ramp = if (stripStart - s0 >= 20.0) min(60.0, stripStart - s0) else max(10.0, (cur.off - s0) * 0.5)
            if (maxOf(Math.abs(mid), Math.abs(midAt(cur.off - 100.0)), Math.abs(midAt(cur.off - 10.0))) > 0.3 && cur.off - s0 > 30.0) {
                val fn: (Double) -> Double = { o ->
                    val inF = ((o - s0) / ramp).coerceIn(0.0, 1.0)
                    val outF = ((s1 - o) / 35.0).coerceIn(0.0, 1.0)     // from 10 m before the junction to 25 m after it
                    val f = minOf(inF, outF)
                    midAt(min(o, cur.off)) * f * f * (3 - 2 * f)
                }
                laneLatAt = fn; laneShiftEnd = s1
                var o = s0
                while (true) {
                    val (p, brg) = pointAt(r, o)
                    shift += shifted(p, brg, fn(o))
                    if (o >= s1) break
                    o = minOf(s1, o + 5.0)
                }
            }
        }
        map.setRouteShift(shift)
        map.setLanes(marks)
    }

    /** The sideways offset (metres, + to the right) of the route line in the lane you need, by distance along the route; null when none. */
    private var laneLatAt: ((Double) -> Double)? = null
    /** The real lanes' middles (sideways from the route line, left to right) read off the map for the junction at [hdLanesFor]. */
    private var hdProfile: List<Pair<Double, List<Double>>> = emptyList()
    /** What the last look for the real lanes found (for Demo mode and the feedback report). */
    var laneDiag = ""
        private set
    private var laneDiagFor = -1.0
    private var hdProfileFor = -1.0
    /** The junction the lane sign is standing at (−1 for none). */
    private var laneSignFor = -1.0
    /** The lanes shown in the bubble just ahead of the arrow (null when none). */
    private var bubbleLanes: List<Pair<List<Pair<String, Boolean>>, Boolean>>? = null
    private var hdLanesFor = -1.0
    private var hdTriesFor = -1.0
    private var hdTries = 0
    private var hdAskAt = 0L

    /**
     * The [n] real lanes that make up your carriageway, out of [offs] (all the lanes found across the road there, left to right):
     * the block of [n] side by side (no gap wider than a lane and a half between them) nearest the route line. Null when they
     * don't match the lane list, so the standard layout is used instead.
     */
    private fun pickLaneWindow(offs: List<Double>, n: Int): List<Double>? {
        if (n < 2 || offs.size < n) return null
        var best: List<Double>? = null; var bestD = Double.MAX_VALUE
        for (s in 0..offs.size - n) {
            val w = offs.subList(s, s + n)
            if ((1 until n).any { w[it] - w[it - 1] !in 2.4..5.5 }) continue
            val mid = w.average()
            if (Math.abs(mid) < bestD) { bestD = Math.abs(mid); best = w }
        }
        // the route line has to be somewhere on that carriageway, or it's another road
        return best?.takeIf { it.first() - 3.0 < 0.0 && it.last() + 3.0 > 0.0 }
    }

    /** Where along the route that offset has eased back to nothing (0 when there's none). */
    private var laneShiftEnd = 0.0

    /** True when the lane numbers follow on from each other (2, 3, 4), so they form one block of lanes. */
    private fun isContiguous(idx: List<Int>): Boolean = idx.isNotEmpty() && idx.last() - idx.first() == idx.size - 1

    /** Index of the last route point at or before [d] metres along the route. */
    private fun idxAt(r: Route, d: Double): Int {
        val c = r.cum
        var lo = 0; var hi = c.size - 1
        while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (c[m] <= d) lo = m else hi = m - 1 }
        return lo
    }

    private var gDoneAt = 0L
    private var doneShown = false

    private fun updateDone(r: Route, prog: Double) {
        // grey right back to where the blue line starts (it's drawn from a little behind the car), so no blue shows behind you;
        // with the whole route on screen (map moved by hand), the whole driven part is grey
        val start = if (!following) 0.0 else max(0.0, min(prog - 800.0, lastDrawProg - 950.0))
        if (prog - start < 2.0) { if (doneShown) { map.setDone(emptyList()); doneShown = false }; return }
        val pts = ArrayList<LL>()
        pts += pointAt(r, start).first
        var i = idxAt(r, start) + 1
        while (i < r.pts.size && r.cum[i] < prog) { pts += r.pts[i]; i++ }
        pts += pointAt(r, prog).first
        map.setDone(pts); doneShown = true
    }

    private fun pointAt(r: Route, d: Double): Pair<LL, Double> {
        val c = r.cum
        var lo = 0; var hi = c.size - 1
        while (lo < hi - 1) { val m = (lo + hi) ushr 1; if (c[m] <= d) lo = m else hi = m }
        val seg = (c[hi] - c[lo]).takeIf { it > 0 } ?: 1.0
        val t = ((d - c[lo]) / seg).coerceIn(0.0, 1.0)
        val a = r.pts[lo]; val b = r.pts[hi]
        return Pair(LL(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t), Geo.bearing(a, b))
    }
}
