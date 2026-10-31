package com.headunit.launcher

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** Stable SharedPreferences names. Keep these values unchanged to preserve existing user settings. */
internal object PreferenceKeys {
    const val API_KEY = "apikey"
    const val MAP_STYLE = "mapstyle"
    const val MAP_DETAIL = "mapdetail"
    const val MAP_LITE = "maplite"
    const val GOOGLE_API_KEY = "gkey"
    const val MAPBOX_PROMPT = "mbprompt"
    const val MAPBOX_PROMPTED = "mbprompted"
    const val MAPBOX_MIGRATED = "mbmigrated"
    const val LOCATOR = "locator"
    const val LOCATOR_IMAGE = "locimg"
    const val LOCATOR_IMAGE_TURNS = "locimgturn"
    const val LOCATOR_IMAGE_CIRCLE = "locimgcircle"
    const val MODEL_SHAPE = "modelshape"
    const val MODEL_GLOW = "modelglow"
    const val CAR_COLOUR = "carcolour"
    const val ARROW_COLOUR = "arrowcolour"
    const val TRAFFIC = "traffic"
    const val SATELLITE = "satellite"
    const val BUILDINGS_3D = "buildings3d"
    const val SIGNALS = "signals"
    const val HAZARD_ICONS = "hazardicons"
    const val LAMPS = "lamps"
    const val TREES = "trees"
    const val PATHS = "paths"
    const val RAIN_RADAR = "radar"
    const val RAIN_EFFECTS = "rainfx"
    const val MAP_FPS = "mapfps"
    const val UI_SCALE = "uiscale"
    const val UI_AUTO_SCALE = "uiauto"
    const val SOUND_MODE = "soundmode"
    const val FUEL_TYPE = "fueltype"
    const val PACK_REGION = "packregion"
    const val ETA_NUMBER = "etanum"
    const val ETA_NAME = "etaname"
    const val LANE_STYLE = "lanestyle"
    const val LANE_STRIP = "lanestrip"
    const val THEN_MODE = "thenmode"
    const val THEN_STYLE = "thenstyle"
    const val MAIN_CARD_SIZE = "maincardsize"
    const val MAIN_TEXT_SIZE = "maintextsize"
    const val MAIN_SPEED_SIZE = "mainspeedsize"
    const val MAIN_BUTTON_SIZE = "mainbuttonsize"
    const val MAIN_ARROW_SIZE = "mainarrowsize"
    const val CAR_CARD_SIZE = "carcardsize"
    const val CAR_TEXT_SIZE = "cartextsize"
    const val CAR_SPEED_SIZE = "carspeedsize"
    const val CAR_BUTTON_SIZE = "carbuttonsize"
    const val CAR_ARROW_SIZE = "cararrowsize"
    const val MAP_THEME = "maptheme"
    const val MAP_COLOURS = "mapcolours"
    const val MAP_RESOLUTION = "mapres"
    const val MAPBOX_TOKEN = "mbtoken"
    const val MAPBOX_AUTO_NETWORK = "mbautonet"
    const val MAPBOX_LOGO = "mblogo"
    const val ARROW_PERCENT = "arrowpct"
    const val SPEED_SIZE = "speedsize"
    const val THEME = "theme"
    const val NIGHT_FROM = "nightfrom"
    const val DAY_FROM = "dayfrom"
    const val SETUP_DONE = "setupdone"
    const val OFF_MAP = "offmap"
    const val LIVE_ALTERNATIVES = "livealts"
    const val ALT_ROUTE_COLOUR = "altroutecolour"
    const val FASTER_ROUTE = "fasterroute"
    const val CAMERA_WARNINGS = "cameras"
    const val VOICE = "voice"
    const val TRIPS = "trips"
    const val PLACES_CUSTOM = "places_custom"
    const val PLACES_FAVOURITES = "places_fav"
    const val PLACES_RECENT = "places_recent"
    const val PLACES_SEEN = "places_seen"
    const val PLACE_PARKED = "place_parked"
    const val DRIVE = "drive"
    const val DISTANCE_UNIT = "distunit"
    const val TEMPERATURE_UNIT = "tempunit"
    const val TRAFFIC_WARNING = "warntol"
    const val RIBBON = "ribbon"
    const val LANE_VIEW = "laneview"
    const val JUNCTION_VIEW = "junctionview"
    const val PERFORMANCE_METER = "perfmeter"
    const val UPDATE_LAST_CHECK = "updlast"
    const val WHATS_NEW_SEEN_VERSION = "whatsnew_seen_version"
    const val AUTO_START_ROUTE = "autostartroute"
    const val AUTO_START_SECONDS = "autostartseconds"
    // Additional legacy keys centralised without changing their persisted names.
    const val AMBMAP = "ambmap"
    const val AMBREACT = "ambreact"
    const val ARRIVALFX = "arrivalfx"
    const val AVGZONES = "avgzones"
    const val AVOID_FERRY = "avoid_ferry"
    const val AVOID_MOTORWAY = "avoid_motorway"
    const val AVOID_TOLL = "avoid_toll"
    const val AVOID_UNPAVED = "avoid_unpaved"
    const val AVOIDZONES = "avoidzones"
    const val BREAKREMIND = "breakremind"
    const val BRIGHT = "bright"
    const val BUMPS = "bumps"
    const val CAMBANOBEY = "cambanobey"
    const val CARSCALE = "carscale"
    const val CARTHEME = "cartheme"
    const val CINEMATIC = "cinematic"
    const val CLEARVIEW = "clearview"
    const val COUNTRY = "country"
    const val COUNTRYDETECTED = "countrydetected"
    const val CRUISE = "cruise"
    const val DATAPROV = "dataprov"
    const val DATASNOOZE = "datasnooze"
    const val DEMO = "demo"
    const val DIMNIGHT = "dimnight"
    const val DRIVELOCK = "drivelock"
    const val DRIVESIMPLE = "drivesimple"
    const val EVMPK = "evmpk"
    const val EVPPK = "evppk"
    const val FACADES = "facades"
    const val FFID = "ffid"
    const val FFSECRET = "ffsecret"
    const val FLYOVER = "flyover"
    const val FORCESETUP = "forcesetup"
    const val FUELMPG = "fuelmpg"
    const val FUELPPL = "fuelppl"
    const val FURNITURE3D = "furniture3d"
    const val GLASS = "glass"
    const val HABITS = "habits"
    const val HAZARDRADAR = "hazardradar"
    const val INCIDENTS = "incidents"
    const val KEEPON = "keepon"
    const val LAMPGAP = "lampgap"
    const val LANDMARKS3D = "landmarks3d"
    const val LANEMAP = "lanemap"
    const val LANES_MAPBOX = "lanesmb"
    const val LASTLAT = "lastlat"
    const val LASTLON = "lastlon"
    const val LIMITCHANGE = "limitchange"
    const val LIMITEST = "limitest"
    const val LIVECARD = "livecard"
    const val MAPFAST = "mapfast"
    const val MAPSPEED = "mapspeed"
    const val MBAUTOROUTE = "mbautoroute"
    const val MBHOMEKM = "mbhomekm"
    const val MBLOOK = "mblook"
    const val MBREGIONS = "mbregions"
    const val NIGHTSKY = "nightsky"
    const val PACKINSTALLED = "packinstalled"
    const val PACKNAG = "packnag"
    const val PARKPROMPT = "parkprompt"
    const val PLACE_HOME = "place_home"
    const val PLANNED = "planned"
    const val POLICEALERTS = "policealerts"
    const val POLICEREP = "policerep"
    const val PREDICT = "predict"
    const val QUICKLIST = "quicklist"
    /** The map in 3D (true) or flat 2D: remembered between drives, and shared by the phone / head unit and Android Auto. */
    const val VIEW_3D = "view3d"
    /** The donate card has been shown (it's shown once, a day or more after install). */
    const val DONATE_SHOWN = "donateshown"
    /** Your own normal zoom when not on a route, in zoom levels from the standard one (-2 to +2; 0 = standard). */
    const val DEFAULT_ZOOM = "defzoom"
    /** Stopped for 30 s: the map pulls back to a top-down view of the traffic round you. */
    const val STOPPED_OVERVIEW = "stopview"
    /** Where you left the car, "lat,lon,time" (saved on the phone when Android Auto disconnects). */
    const val PARKED_SPOT = "parkedspot"
    /** Phone-to-car link: the pairing code, this device's part ("car" receives, "phone" sends), and the last message taken. */
    const val LINK_CODE = "linkcode"
    const val LINK_ROLE = "linkrole"
    const val LINK_LAST = "linklast"
    const val RAILFORD = "railford"
    const val REPORTS = "reports"
    const val RESUME = "resume"
    const val ROADFAM = "roadfam"
    const val ROUTE_ECO = "route_eco"
    const val ROUTEBACK = "routeback"
    const val ROUTEBAR = "routebar"
    const val ROUTECHEVRONS = "routechevrons"
    const val ROUTEFLOW = "routeflow"
    const val ROUTETRAFFICONLY = "routetrafficonly"
    const val ROUTEWX = "routewx"
    const val SCHOOLS = "schools"
    const val SEARCHWITH = "searchwith"
    const val SERVICESCARD = "servicescard"
    const val SKYWEATHER = "skyweather"
    const val SMARTFUEL = "smartfuel"
    const val SMARTVOICE = "smartvoice"
    const val SNAPROAD = "snaproad"
    const val SPEEDRING = "speedring"
    const val SPEEDTILT = "speedtilt"
    const val SPEEDWARN = "speedwarn"
    const val STARTPOS = "startpos"
    const val SYSDARKSEEN = "sysdarkseen"
    const val TAPSELECT = "tapselect"
    const val TERRAIN = "terrain"
    const val TUNNELDIM = "tunneldim"
    const val TURN2D = "turn2d"
    const val TURNARROWS = "turnarrows"
    const val UKSIGNS = "uksigns"
    const val UPDCHECK = "updcheck"
    const val UPDSNOOZE = "updsnooze"
    const val WRONGWAY = "wrongway"
    const val WXADVICE = "wxadvice"
    const val ZEBRAS = "zebras"
    const val ZONEWARN = "zonewarn"
    const val TT_MAP_FIX = "ttmapfix"
    const val VOICE_CHANNEL = "voicechan"
    const val TANKERKOENIG_KEY = "tkkey"
    const val VOICE_ROADS = "voiceroads"
    const val AVOID_TOLL_COUNTRIES = "avoid_toll_cc"
    const val ROAD_NAME_SIZE = "roadnamesize"
    const val CAR_ROAD_NAME_SIZE = "carroadnamesize"
    const val CAR_MODEL = "carmodel"
    const val TRAFFIC_GREEN = "trafficgreen"
    const val STOP_SIGNS = "stopsigns"
    const val EV_BATTERY_KWH = "evbatt"
    const val EV_CHARGE_PCT = "evcharge"
    const val EV_MIN_ARRIVE = "evminarr"
}

/**
 * Tiny observable key/value store (Compose-aware).
 * Every key has its own state, so changing one setting only redraws the screens that actually read that setting
 * (a single shared map would redraw everything that read any setting).
 */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("drive_launcher", Context.MODE_PRIVATE)
    // one set of values for the whole app: the phone's screen, the Android Auto screen and the services all see a change at once
    private val raw: HashMap<String, String> = synchronized(LOCK) {
        RAW ?: HashMap<String, String>().apply { sp.all.forEach { (k, v) -> put(k, v.toString()) } }.also { RAW = it }
    }
    private val states get() = STATES

    private companion object {
        val LOCK = Any()
        var RAW: HashMap<String, String>? = null
        val STATES = HashMap<String, MutableState<String?>>()
    }

    private fun state(k: String): MutableState<String?> = synchronized(LOCK) { states.getOrPut(k) { mutableStateOf(raw[k]) } }

    fun s(k: String, d: String) = state(k).value ?: d
    fun b(k: String, d: Boolean) = state(k).value?.toBooleanStrictOrNull() ?: d
    fun i(k: String, d: Int) = state(k).value?.toIntOrNull() ?: d
    fun f(k: String, d: Float) = state(k).value?.toFloatOrNull() ?: d

    /** Everything except the API key, as JSON text (settings, saved places, logs...). */
    fun exportJson(): String = synchronized(LOCK) {
        val o = JSONObject()
        raw.forEach { (k, v) -> if (k != PreferenceKeys.API_KEY) o.put(k, v) }
        o.toString(2)
    }

    /** Restores values from [exportJson] output; validates the complete file before changing settings. */
    fun importJson(text: String): Int {
        require(text.isNotBlank() && text.length <= 2 * 1024 * 1024) { "Backup is empty or too large" }
        val o = JSONObject(text)
        // Parse and validate everything first, so a malformed backup can never leave half the settings restored.
        val pending = LinkedHashMap<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            require(k.isNotBlank() && k.length <= 200) { "Invalid setting name" }
            if (k == PreferenceKeys.API_KEY) continue // API keys are intentionally never imported from backups.
            val value = o.get(k)
            require(value !is JSONObject && value !is org.json.JSONArray) { "Invalid setting value" }
            pending[k] = if (value == JSONObject.NULL) "" else value.toString()
        }
        pending.forEach { (k, v) -> put(k, v) }
        return pending.size
    }

    fun put(k: String, v: Any) {
        val s = v.toString()
        // nothing changed: no disk write and nothing on screen is redrawn
        synchronized(LOCK) { if (raw[k] == s) return; raw[k] = s }
        state(k).value = s
        sp.edit().putString(k, s).apply()
    }
}

/**
 * The map source: whatever was picked in Settings; until something is picked, TomTom once a TomTom key has been entered
 * (otherwise this build's default).
 */
fun Prefs.mapSource(): String = s(PreferenceKeys.MAP_STYLE, BuildConfig.DEFAULT_MAP).let { if (it == "google") "mapbox" else it }

/**
 * Once, on the first start after updating from a Horizon Nav from before the Mapbox map: switch the map to Mapbox (the old
 * TomTom / OpenStreetMap / Google choice was for the old map engine) and drop the Google key, which nothing uses now. Your places,
 * keys, settings and downloaded packs are all kept.
 */
fun Prefs.migrateToMapbox() {
    // a short prompt for the Mapbox key shows once after this update (new installs get it in setup instead, which clears it)
    if (!b(PreferenceKeys.MAPBOX_PROMPTED, false)) { put(PreferenceKeys.MAPBOX_PROMPT, true); put(PreferenceKeys.MAPBOX_PROMPTED, true) }
    if (!b(PreferenceKeys.MAPBOX_MIGRATED, false)) {
        put(PreferenceKeys.MAP_STYLE, "mapbox")
        put(PreferenceKeys.GOOGLE_API_KEY, "")
        put(PreferenceKeys.MAPBOX_MIGRATED, true)
    }
    // Once: adding a TomTom key used to switch the map to TomTom's flat map by itself, so the 3D buildings, models and lane
    // markings all vanished the moment people added their keys. Put those maps back to Mapbox (TomTom's map can still be picked
    // in Settings > Map; from now on the key is only used for search, routes and traffic).
    if (!b(PreferenceKeys.TT_MAP_FIX, false)) {
        if (s(PreferenceKeys.MAP_STYLE, "") == "tomtom") put(PreferenceKeys.MAP_STYLE, "mapbox")
        put(PreferenceKeys.TT_MAP_FIX, true)
    }
}

/** Saves a TomTom key. (It's used for search, routes and traffic; it no longer switches the map to TomTom's.) */
fun Prefs.setApiKey(k: String) {
    put(PreferenceKeys.API_KEY, k.trim())
}

/** The locator setting as the map takes it: its style and colour ("model" takes "<arrow|car>|<colour>"). */
fun Prefs.locatorSpec(): Pair<String, String> {
    val loc = s(PreferenceKeys.LOCATOR, "arrow")
    return when (loc) {
        "custom" -> loc to (s(PreferenceKeys.LOCATOR_IMAGE, "0") + "|" + (if (b(PreferenceKeys.LOCATOR_IMAGE_TURNS, true)) "1" else "0") + "|" + (if (b(PreferenceKeys.LOCATOR_IMAGE_CIRCLE, false)) "1" else "0"))
        "model" -> {
            val shape = s(PreferenceKeys.MODEL_SHAPE, "arrow")
            loc to (shape + "|" + s(if (shape == "car") PreferenceKeys.CAR_COLOUR else PreferenceKeys.ARROW_COLOUR, if (shape == "car") "#F4F5F7" else "#F2F2F6") +
                "|" + (if (b(PreferenceKeys.MODEL_GLOW, true)) "1" else "0"))
        }
        "car" -> loc to s(PreferenceKeys.CAR_COLOUR, "#F4F5F7")
        else -> loc to s(PreferenceKeys.ARROW_COLOUR, "#F2F2F6")
    }
}

/** Settings > Map > Map detail: "full", "lite" or "lightest" (the old "Light map" switch counts as lite). */
fun Prefs.mapDetail(): String = s(PreferenceKeys.MAP_DETAIL, if (b(PreferenceKeys.MAP_LITE, false)) "lite" else "full")
