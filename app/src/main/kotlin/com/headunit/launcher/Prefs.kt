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
    /** Your own 3D model: "<stamp>|<bytes>|<longest side>|<file name>" (the file itself is in the app's storage). */
    const val OWN_MODEL = "ownmodel"
    /** How far your own 3D model is turned, in degrees, so it points the way you drive. */
    const val MODEL_TURN = "modelturn"
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
    /** The map's lettering: "" = Mapbox's own (DIN Pro), or one of Mapbox's fonts (karthikeyansr: easier to read). */
    const val MAP_FONT = "mapfont"
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
    const val OFFLINE_MODE = "offlinemode"
    const val SHORTCUTS = "shortcuts"
    const val LIVE_ALTERNATIVES = "livealts"
    const val ALT_ROUTE_COLOUR = "altroutecolour"
    const val FASTER_ROUTE = "fasterroute"
    const val CAMERA_WARNINGS = "cameras"
    const val VOICE = "voice"
    const val TRIPS = "trips"
    const val PLACES_CUSTOM = "places_custom"
    const val PLACES_FAVOURITES = "places_fav"
    const val PLACES_ROUTES = "places_routes"
    const val CAR_TRIP_BTNS = "car_trip_btns"
    const val CAR_TRIP_BTN_SIZE = "car_trip_btn_size"
    /** Android Auto split screen: the trip card as a "bar" along the bottom or a "column" on the left (GitHub). */
    const val CAR_SPLIT_TRIP = "car_split_trip"
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
    /** The notes the What's new card last showed, so Settings › Version › What's new can bring them back (GitHub #149). */
    const val WHATS_NEW_LAST_NOTES = "whatsnew_last_notes"
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
    /** Countries whose "camera alerts are off here" message has already been shown (comma-separated codes): it's said once, ever. */
    const val CAMBAN_SEEN = "cambanseen"
    const val CARSCALE = "carscale"
    const val CARTHEME = "cartheme"
    const val CINEMATIC = "cinematic"
    const val CLEARVIEW = "clearview"
    const val COUNTRY = "country"
    const val COUNTRYDETECTED = "countrydetected"
    const val CRUISE = "cruise"
    /** "Heading E" after the road name in that pill (liamstears: a switch of its own). */
    const val CRUISE_HEADING = "cruise_heading"
    const val DATAPROV = "dataprov"
    const val DATASNOOZE = "datasnooze"
    const val DEMO = "demo"
    const val DIMNIGHT = "dimnight"
    const val DRIVELOCK = "drivelock"
    /** The media bar on the driving screen (Reddit, mrEnmi). */
    const val MEDIA_BAR = "media_bar"
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
    /** How lanes show on the map: "lit" (the lane to use lit up; on motorways where the real lanes are known, the route line moves
     *  into it), "closeup" (a drawn picture of the junction's lanes in a corner of the map) or "sign" (a sign over the road). */
    const val LANE_MAP_STYLE = "lanemapstyle"
    const val LASTLAT = "lastlat"
    const val LASTLON = "lastlon"
    const val LIMITCHANGE = "limitchange"
    /** How a limit change is told: "both" chime then voice, "voice", "chime" (LIMITCHANGE off: not at all). */
    const val LIMITCHANGE_HOW = "limitchange_how"
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
    /** Off: the zoom stays put instead of following your speed and the junctions (GitHub #154). */
    const val AUTO_ZOOM = "autozoom"
    /** With no route, a round search button in the corner instead of the search bar and its buttons (GitHub #154). */
    const val SEARCH_BUTTON = "search_button"
    /** Clock times: "auto" (the device's own 12 / 24-hour setting), "12" or "24". */
    const val CLOCK = "clock"
    /** The + / - zoom last used when not on a route, kept for next time: on the phone / head unit, and on Android Auto. */
    const val FREE_ZOOM = "freezoom"
    const val FREE_ZOOM_CAR = "freezoomcar"
    /** North up (instead of the way you're heading) chosen last time: on the phone / head unit, and on Android Auto. */
    const val NORTH_UP = "northup"
    /** Number-plate (ALPR) cameras such as Flock's shown on the map (Settings > Map). */
    const val ALPR_CAMS = "alprcams"
    /** The map on the driver's display (Android Auto's instrument cluster), on cars that offer it. */
    const val CLUSTER_MAP = "clustermap"
    /** Card positions: the turn card's side ("auto" follows the steering wheel side, "left", "right"). */
    const val CARDS_SIDE = "cardsside"
    /** Card positions: the trip card ("auto" under the turn card, "left", "centre", "right"). */
    const val TRIP_POS = "trippos"
    /** Card positions: the speedometer's side ("auto" away from the cards, "left", "right"). */
    const val SPEED_SIDE = "speedside"
    /** Card positions on the Android Auto screen: the trip card and the speedometer. */
    const val CAR_TRIP_POS = "cartrippos"
    const val CAR_SPEED_SIDE = "carspeedside"
    /** Mapbox's HD roads: "auto" (on in the UK only), "on" or "off". */
    const val HD_ROADS = "hdroads"
    /** Sharp bends: the yellow chevron stretch on the route and the warning before each. */
    const val BEND_WARN = "bendwarn"
    /** The headlight beam ahead of the arrow at night. */
    const val BEAM = "beam"
    /** On arrival: the building lit, its entrance marked and the walk from the car park. */
    const val ARRIVAL_PLACE = "arrivalplace"
    /** Chimes (speeding, limit change, cameras) as a percentage of full strength, on the voice's channel. */
    const val CHIME_VOL = "chimevol"
    /** On a wide screen, the arrow stays to the side when the cards fold away (instead of moving back to the middle). */
    const val ARROW_SIDE = "arrowside"
    /** The head unit's headlight setting (car_mode) reads the other way round on this unit. */
    const val LAMPS_INVERT = "lampsinvert"
    /** How close the map on the driver's display is: -2 (furthest out) to 1 (closest), 0 normal. */
    const val CLUSTER_ZOOM = "clusterzoom"
    /** The speed camera badge's size on the map, in percent (50 to 350, 100 as it always was). */
    const val CAM_SIZE = "camsize"
    /** Moonlight at night: a low blue light with long shadows in place of Mapbox's flat night light. */
    const val MOONLIT = "moonlit"
    /** Light and weather (Settings > Map look): night brightness and moon strength in percent (100 as Mapbox / as first made). */
    const val NIGHT_BRIGHT = "nightbright"
    const val MOON_STR = "moonstr"
    const val SHADOWS = "shadows"
    const val SUN_REAL = "sunreal"
    const val OVERCAST = "overcast"
    const val WEATHER_LIGHT = "weatherlight"
    /** "Match my car" colours: the car's interior lighting colour, and how strong (percent). */
    const val CAR_TINT = "cartint"
    const val CAR_TINT_STR = "cartintstr"
    /** The Custom theme: the theme it starts from, day and night colours (JSON, see [CustomLook]), route width in percent, saved themes. */
    const val CUSTOM_BASE = "custombase"
    const val CUSTOM_DAY = "customday"
    const val CUSTOM_NIGHT = "customnight"
    const val CUSTOM_ROUTE_W = "customroutew"
    const val CUSTOM_SAVED = "customsaved"
    /** Arrive on your side of the road (Mapbox and offline routes). */
    const val ARRIVE_SIDE = "arriveside"
    /** Mapbox's junction pictures at motorway junctions (only shown when Mapbox has them for the account). */
    const val JUNCTION_PICS = "junctionpics"
    /** Holding on the map shows what's there first (road, limit, area), with Go and Add as a stop. */
    const val WHATS_HERE = "whatshere"
    /** The next speed limit ahead, shown small beside the current one. */
    const val NEXT_LIMIT = "nextlimit"
    /** Tall 3D flags at the destination and your stops. */
    const val FLAGS_3D = "flags3d"
    /** Soft shading on hillsides. */
    const val HILLSHADE = "hillshade"
    /** Where the speed limit and speedometer sit, on the side away from the cards: "bottom" (as it always was), "middle" or "top". */
    const val SPEED_POS = "speedpos"
    /** The same for the Android Auto screen: "bottom" (the bottom corner), "middle" (under the turn card; the name is from when
     *  it was the middle of the right-hand side) or "top" (top right: in the corner itself while navigating, making way while
     *  Android Auto's buttons are showing there; under those buttons otherwise). */
    const val SPEED_POS_CAR = "speedposcar"
    /** The speedometer's look on the Android Auto screen: "app" (the same as the app: the ring or the block) or "compact"
     *  (a small dark pill with the limit sign and your speed side by side, easy to tuck into a corner). */
    const val CAR_SPEED_STYLE = "carspeedstyle"
    /** Set the first time a car passes a tap on its screen to the app (not every car does): only then can the app's own
     *  buttons stand in for Android Auto's (the trip card's red button for Stop). */
    const val CAR_TAPS_SEEN = "cartapsseen"
    /** The Home and Work buttons under the search bar (the app and the Android Auto screen alike): each can be left off. */
    const val HOME_BTN = "homebtn"
    const val WORK_BTN = "workbtn"
    /** Settings > Backup: put the API keys (TomTom, Mapbox) in the backup file too. Off unless switched on: the file is plain text. */
    const val BACKUP_KEYS = "backupkeys"
    /** Electric car: still show the Fuel (petrol station) button on the map page. */
    const val EV_SHOW_FUEL = "evshowfuel"
    /** While navigating, the notification shows the next turn (arrow, distance, road) for launchers, watches and other screens. */
    const val TURN_NOTIF = "turnnotif"
    /** What routes are planned for: "car" or "lorry" (Settings > Routes > Vehicle), and the lorry's measurements:
     *  height, width and length in cm, weight and axle weight in tonnes x 10, and whether it carries hazardous goods. */
    const val VEHICLE = "vehicle"
    /** Top speed for arrival times (GitHub #134): the number, and whether it's in "mph" or "kmh"; 0 = off. */
    const val TOP_SPEED = "top_speed"
    const val TOP_SPEED_UNIT = "top_speed_unit"
    const val LORRY_HEIGHT = "lorryh"
    const val LORRY_WIDTH = "lorryw"
    const val LORRY_LENGTH = "lorryl"
    const val LORRY_WEIGHT = "lorryt"
    const val LORRY_AXLE = "lorryaxle"
    const val LORRY_HAZMAT = "lorryhaz"
    const val NORTH_UP_CAR = "northupcar"
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
    const val PREFER_SIMPLE = "prefer_simple"
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
    const val APP_LANG = "applang"
    const val TANKERKOENIG_KEY = "tkkey"
    /** NSW FuelCheck (Australia: New South Wales and Tasmania): the API key and secret from api.nsw.gov.au. */
    const val NSW_FUEL_KEY = "nswkey"
    /** The 3D view's tilt in degrees on a wide screen, a tall screen and Android Auto (0 = Auto, as the app chooses). */
    const val TILT_WIDE = "tiltwide"
    const val TILT_TALL = "tilttall"
    const val TILT_CAR = "tiltcar"
    /** Full screen: the status and navigation bars hidden (off keeps a head unit's own bars showing). */
    const val FULLSCREEN = "fullscreen"
    const val NSW_FUEL_SECRET = "nswsecret"
    const val VOICE_ROADS = "voiceroads"
    /** Directions that name the traffic lights or stop sign at the turn ("At the traffic lights, turn right"), on the card and in the voice. */
    const val LANDMARK_DIRS = "landmarkdirs"
    const val AVOID_TOLL_COUNTRIES = "avoid_toll_cc"
    const val ROAD_NAME_SIZE = "roadnamesize"
    const val CAR_ROAD_NAME_SIZE = "carroadnamesize"
    const val CAR_MODEL = "carmodel"
    const val TRAFFIC_GREEN = "trafficgreen"
    const val STOP_SIGNS = "stopsigns"
    const val EV_BATTERY_KWH = "evbatt"
    const val EV_PCT_CARD = "evpctcard"
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
        /** The API keys: only in a backup when asked for. */
        val SECRET_KEYS = setOf(PreferenceKeys.API_KEY, PreferenceKeys.MAPBOX_TOKEN, PreferenceKeys.GOOGLE_API_KEY)
        var RAW: HashMap<String, String>? = null
        val STATES = HashMap<String, MutableState<String?>>()
    }

    private fun state(k: String): MutableState<String?> = synchronized(LOCK) { states.getOrPut(k) { mutableStateOf(raw[k]) } }

    fun s(k: String, d: String) = state(k).value ?: d
    fun b(k: String, d: Boolean) = state(k).value?.toBooleanStrictOrNull() ?: d
    fun i(k: String, d: Int) = state(k).value?.toIntOrNull() ?: d
    fun f(k: String, d: Float) = state(k).value?.toFloatOrNull() ?: d

    /**
     * Everything as JSON text (settings, saved places, logs...). The API keys go in only with [includeKeys] (Settings > Backup >
     * Include API keys): the file is plain text, so they're left out unless asked for.
     */
    fun exportJson(includeKeys: Boolean = false): String = synchronized(LOCK) {
        val o = JSONObject()
        raw.forEach { (k, v) -> if (includeKeys || k !in SECRET_KEYS) o.put(k, v) }
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
            val value = o.get(k)
            require(value !is JSONObject && value !is org.json.JSONArray) { "Invalid setting value" }
            val text0 = if (value == JSONObject.NULL) "" else value.toString()
            // API keys are restored when the backup has them (it was made with "Include API keys"); an empty one in the file
            // never wipes a key that's already set here
            if (k in SECRET_KEYS && text0.isBlank()) continue
            pending[k] = text0
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

/**
 * Where the speedometer sits on the Android Auto screen, as one choice: "bl" / "br" (a bottom corner), "middle" (under the turn
 * card) or "top" (the top right corner). It's kept as two settings (SPEED_POS_CAR, and CAR_SPEED_SIDE for which bottom corner),
 * which used to be two separate choices in Settings, one of which did nothing unless the other was on Bottom.
 */
fun Prefs.carSpeedSpot(): String = when (s(PreferenceKeys.SPEED_POS_CAR, "bottom")) {
    "middle" -> "middle"
    "top", "corner" -> "top"
    // ("auto": away from the trip card, which is on the right only when it's been put there)
    else -> when (s(PreferenceKeys.CAR_SPEED_SIDE, "auto")) { "left" -> "bl"; "right" -> "br"; else -> if (s(PreferenceKeys.CAR_TRIP_POS, "auto") == "right") "bl" else "br" }
}

fun Prefs.setCarSpeedSpot(spot: String) {
    when (spot) {
        "bl" -> { put(PreferenceKeys.SPEED_POS_CAR, "bottom"); put(PreferenceKeys.CAR_SPEED_SIDE, "left") }
        "br" -> { put(PreferenceKeys.SPEED_POS_CAR, "bottom"); put(PreferenceKeys.CAR_SPEED_SIDE, "right") }
        else -> put(PreferenceKeys.SPEED_POS_CAR, spot)
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
            // (your own model, but none saved: the arrow)
            val shape = s(PreferenceKeys.MODEL_SHAPE, "arrow").let { if (it == "own" && s(PreferenceKeys.OWN_MODEL, "").isBlank()) "arrow" else it }
            val carLike = shape != "arrow"
            loc to (shape + "|" + s(if (carLike) PreferenceKeys.CAR_COLOUR else PreferenceKeys.ARROW_COLOUR, if (carLike) "#F4F5F7" else "#F2F2F6") +
                "|" + (if (b(PreferenceKeys.MODEL_GLOW, true)) "1" else "0") +
                // your own model: its turn and which copy of the file
                (if (shape == "own") "|" + s(PreferenceKeys.MODEL_TURN, "0") + "|" + s(PreferenceKeys.OWN_MODEL, "").substringBefore('|') else ""))
        }
        "car" -> loc to s(PreferenceKeys.CAR_COLOUR, "#F4F5F7")
        else -> loc to s(PreferenceKeys.ARROW_COLOUR, "#F2F2F6")
    }
}

/**
 * The arrow's size on the Android Auto map: the app's arrow size, normalised for the smaller car surface (so the 240% main-app
 * default doesn't make an enormous arrow there), times the car screen's own "Navigation arrow size".
 */
fun Prefs.carArrowScale(): Float =
    ((s(PreferenceKeys.ARROW_PERCENT, "240").toIntOrNull() ?: 240) / 100f).coerceIn(1f, 5f) * 0.9f * (i(PreferenceKeys.CAR_ARROW_SIZE, 100).coerceIn(30, 180) / 100f)

/** Settings > Map > Map detail: "full", "lite" or "lightest" (the old "Light map" switch counts as lite). */
fun Prefs.mapDetail(): String = s(PreferenceKeys.MAP_DETAIL, if (b(PreferenceKeys.MAP_LITE, false)) "lite" else "full")


/** Whether Mapbox's HD roads are wanted: the setting, or by itself only in the UK (elsewhere they could hide the ordinary streets). */
fun hdRoadsWanted(prefs: Prefs, country: String): Boolean = when (prefs.s(PreferenceKeys.HD_ROADS, "auto")) {
    "on" -> true
    "off" -> false
    else -> country.isBlank() || country.equals("GB", true)
}
