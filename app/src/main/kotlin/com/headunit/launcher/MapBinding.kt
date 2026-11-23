package com.headunit.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.Locale
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.mapbox.bindgen.Value
import com.mapbox.common.Cancelable
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiLineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapLoadingErrorType
import com.mapbox.maps.MapOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.maps.applyDefaultParams
import com.mapbox.maps.toCameraOptions
import com.mapbox.maps.toMapboxImage
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.Layer
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.generated.CircleLayer
import com.mapbox.maps.extension.style.layers.generated.FillExtrusionLayer
import com.mapbox.maps.extension.style.layers.generated.FillLayer
import com.mapbox.maps.extension.style.layers.generated.HillshadeLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.generated.ModelLayer
import com.mapbox.maps.interactions.standard.generated.standardBuildings
import com.mapbox.maps.interactions.standard.generated.standardPoi
import com.mapbox.maps.extension.style.layers.properties.generated.ModelType
import com.mapbox.maps.extension.style.layers.properties.generated.ModelScaleMode
import com.mapbox.maps.extension.style.layers.generated.RasterLayer
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.CirclePitchAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.CirclePitchScale
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.IconPitchAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.IconRotationAlignment
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.SymbolPlacement
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.RasterSource
import com.mapbox.maps.extension.style.sources.generated.RasterDemSource
import com.mapbox.maps.extension.style.sources.generated.VectorSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnRotateListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.OnShoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.RotateGestureDetector
import com.mapbox.android.gestures.ShoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector

/**
 * Owns the single map view (Mapbox Maps SDK, Standard style) so it can move between the Home preview and the full Navigation page.
 * Everything the app draws on the map goes through here; the rest of the app never talks to Mapbox directly.
 *
 * Mapbox edition notes:
 * - The base map is Mapbox Standard (or Standard Satellite). Its look is set with Standard's own settings (light preset for
 *   dawn / day / dusk / night, 3D objects, labels), never by recolouring its layers.
 * - The app's own layers sit in Standard's "slots": route, lanes and lamps in "middle" (above roads, below 3D buildings), icons
 *   on top. They are lit as "emissive", so Standard's night lighting doesn't darken the route or the icons.
 * - Asking the map what is on screen is asynchronous in Mapbox and never blocks the app; each question also has a time limit,
 *   so a map that isn't drawing (hidden, in the background) can't leave anything waiting.
 */
class MapBinding(private val ctx: Context, fast: Boolean = false, res: Float = 1f, ratio: Float = 0f) : DefaultLifecycleObserver {
    val mapView: MapView
    private val mbMap: MapboxMap get() = mapView.mapboxMap
    private val prefs = Prefs(ctx)
    private var style: Style? = null
    private var wantedStyle: String? = null
    private var loadedStyle: String? = null
    private val main0 = android.os.Handler(android.os.Looper.getMainLooper())

    private var main: List<LL> = emptyList()
    private var alts: List<List<LL>> = emptyList()
    private var dest: LL? = null
    private var me: LL? = null
    private var meHeading = 0.0

    var onLongPress: ((LL) -> Unit)? = null
    var onUserGesture: (() -> Unit)? = null
    var onStyleLoaded: (() -> Unit)? = null

    /** True once a map style is showing (so a failed switch can keep it on screen). */
    fun hasStyle(): Boolean = style != null
    /** Tapped one of the grey alternative routes (or its time bubble) while driving. */
    var onAltTap: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    /** Mapbox's own map couldn't get a piece of map (with no signal: an area it hasn't stored). */
    var onTileMissing: (() -> Unit)? = null
    private var satellite = false
    private var buildings3d = true
    private var lite = false
    private var hideTraffic = false
    private var trafficOn = true
    private var routing = false
    var tiltNow = 0.0
        private set
    private val subs = ArrayList<Cancelable>()
    // (declared before init: the logo is placed while the map is being set up, before later properties exist)
    private var brandInset: android.graphics.Rect? = null
    /** Where Mapbox's logo goes: "tl", "tr", "bl" or "br" (Settings > Map). */
    private var logoCorner = "tr"
    /** The scale the map is drawn at (screen pixels per map pixel). */
    private val pixelRatioUsed: Float

    /** Standard's import id when the style is loaded as a Mapbox style address. */
    private val BASE = "basemap"

    init {
        // a placeholder until a real token is entered: the Mapbox map refuses to be created with no token at all
        if (MapboxOptions.accessToken.isBlank()) MapboxOptions.accessToken = NO_TOKEN
        val dens = ctx.resources.displayMetrics.density
        // a lower render resolution is the biggest single saving on a slow GPU (the car screen asks for a set scale: see CarSurface)
        val pr = when {
            ratio > 0f -> ratio
            res < 1f -> max(0.6f, dens * res)
            else -> dens
        }
        pixelRatioUsed = pr
        val opts = MapInitOptions(
            context = ctx,
            mapOptions = MapOptions.Builder().applyDefaultParams(ctx).pixelRatio(pr).build(),
            cameraOptions = CameraOptions.Builder().center(Point.fromLngLat(-0.1276, 51.5072)).zoom(14.0).pitch(55.0).build(),
            // the phone moves the map between pages (texture view, can be clipped to round corners); the car draws straight to a surface
            textureView = !fast,
            // no style yet: it's chosen by the engine (and needs a token first)
            styleUri = null,
        )
        mapView = MapView(ctx, opts)
        try { mapView.scalebar.enabled = false } catch (e: Throwable) { }
        try { mapView.compass.enabled = false } catch (e: Throwable) { }
        // Mapbox's logo and credits must stay visible: top right, clear of the app's own cards
        placeBranding(null)
        try { mbMap.setBounds(CameraBoundsOptions.Builder().maxPitch(75.0).build()) } catch (e: Throwable) { }
        try {
            mapView.gestures.rotateEnabled = true
            mapView.gestures.pitchEnabled = true
        } catch (e: Throwable) { }
        // counted all the time: the automatic frame rate compares frames drawn with camera moves asked for
        subs += mbMap.subscribeRenderFrameStarted { PerfStats.renders++ }
        subs += mbMap.subscribeMapLoadingError { e ->
            // only the style itself failing is reported (a missing tile now and then is normal)
            if (e.type == MapLoadingErrorType.STYLE) onError?.invoke(e.message)
            // the 3D model couldn't be loaded: the picture of the same shape instead (until the app is restarted)
            else if (modelOn && (e.sourceId ?: "").startsWith("dl-me-m")) main0.post {
                // your own model: only it is given up (until another file is picked), and the car picture shows; a built-in one: the
                // picture of the same shape for the rest of this run
                if (modelShape == "own") { ownFailed = true; ownFailedStamp = ownStamp; ownFailReason = (e.message ?: "").take(120); onOwnModelFailed?.invoke(ownFailReason) } else modelBroken = true
                modelWanted = false; reinstall()
            }
            // a piece of Mapbox's map itself (not one of the app's extras, like traffic) couldn't be loaded
            else if ((e.type == MapLoadingErrorType.TILE || e.type == MapLoadingErrorType.SOURCE) && !(e.sourceId ?: "").startsWith("dl-")) {
                lastTileError = "${e.sourceId ?: "?"}: ${e.message}".take(140)
                onTileMissing?.invoke()
            }
        }
        mapView.gestures.addOnMapLongClickListener { p -> onLongPress?.invoke(LL(p.latitude(), p.longitude())); true }
        mapView.gestures.addOnMapClickListener { p ->
            main0.postDelayed({ tapClears() }, 150L)
            try { val s = mbMap.pixelForCoordinate(p); tapAt(android.graphics.PointF(s.x.toFloat(), s.y.toFloat())) } catch (e: Throwable) { false }
        }
        // any touch that moves the map (drag, pinch, twist, two-finger tilt) stops the follow camera
        mapView.gestures.addOnMoveListener(object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) { onUserGesture?.invoke() }
            override fun onMove(detector: MoveGestureDetector): Boolean = false
            override fun onMoveEnd(detector: MoveGestureDetector) { }
        })
        mapView.gestures.addOnScaleListener(object : OnScaleListener {
            override fun onScaleBegin(detector: StandardScaleGestureDetector) { onUserGesture?.invoke() }
            override fun onScale(detector: StandardScaleGestureDetector) { }
            override fun onScaleEnd(detector: StandardScaleGestureDetector) { }
        })
        mapView.gestures.addOnRotateListener(object : OnRotateListener {
            override fun onRotateBegin(detector: RotateGestureDetector) { onUserGesture?.invoke() }
            override fun onRotate(detector: RotateGestureDetector) { }
            override fun onRotateEnd(detector: RotateGestureDetector) { }
        })
        mapView.gestures.addOnShoveListener(object : OnShoveListener {
            override fun onShoveBegin(detector: ShoveGestureDetector) { onUserGesture?.invoke() }
            override fun onShove(detector: ShoveGestureDetector) { }
            override fun onShoveEnd(detector: ShoveGestureDetector) { }
        })
        subs += mbMap.subscribeCameraChanged {
            // zooming across the level where the turn arrow appears or goes: put the route back together (or cut it for the arrow)
            if (turnCut != null && cutZoomOk() != cutActive) try { setLine(main) } catch (e: Exception) { }
            placeAltCards(false)
            refreshLamps(); refreshSignals(); sizeFurniture()
            // (while you drive the map never stops moving, so the roads on screen are read on the move too, every few seconds)
            refreshRoads()
            // the 3D locator is sized from the zoom: rebuild it when you zoom, even if you are standing still
            if (locatorStyle != "flat") pushModel(false) else placeLocator()
        }
        subs += mbMap.subscribeMapIdle { placeAltCards(true); refreshLamps(); refreshSignals(); refreshRoads() }
        installTapSelect()
    }

    /** This map is the car screen's (Android Auto): no app top bar there to keep the logo clear of. */
    @Volatile var onCar = false

    /** Puts Mapbox's logo and the (i) credits button top right, inside the visible part of the screen ([inset], null = whole view). */
    fun placeBranding(inset: android.graphics.Rect?) {
        brandInset = inset
        val d = ctx.resources.displayMetrics.density
        val top = logoCorner.startsWith("t")
        val left = logoCorner.endsWith("l")
        val v = if (top) android.view.Gravity.TOP else android.view.Gravity.BOTTOM
        val h = if (left) android.view.Gravity.START else android.view.Gravity.END
        // a little in from the edge (further down at the top, clear of the app's top bar), inside the visible part of the screen
        // (on the car screen there's no top bar to clear, and its high-density display made that gap a big one: the logo ended
        // up well down the screen instead of in the corner)
        val vEdge = (if (top) (inset?.top ?: 0) else (inset?.bottom ?: 0)) + (if (top && !onCar) 64f else 10f) * d
        val hEdge = (if (left) (inset?.left ?: 0) else (inset?.right ?: 0)) + 10f * d
        try {
            mapView.logo.position = v or h
            mapView.logo.marginTop = if (top) vEdge else 0f; mapView.logo.marginBottom = if (top) 0f else vEdge
            // the logo sits beside the (i) button, on the side away from the edge
            mapView.logo.marginLeft = if (left) hEdge + 30f * d else 0f; mapView.logo.marginRight = if (left) 0f else hEdge + 30f * d
        } catch (e: Throwable) { }
        try {
            mapView.attribution.position = v or h
            mapView.attribution.marginTop = if (top) vEdge else 0f; mapView.attribution.marginBottom = if (top) 0f else vEdge
            mapView.attribution.marginLeft = if (left) hEdge else 0f; mapView.attribution.marginRight = if (left) 0f else hEdge
        } catch (e: Throwable) { }
    }


    fun setLogoCorner(c: String) {
        if (c == logoCorner || c !in setOf("tl", "tr", "bl", "br")) return
        logoCorner = c
        placeBranding(brandInset)
    }

    // ---- lifecycle ----
    /**
     * Whether the map can be asked which features are on screen right now. In Mapbox the answer arrives later (it waits for a drawn
     * frame) and never blocks the app, but there's no point asking while the map isn't drawing (the app in the background, the
     * map hidden behind a settings page), so it's only asked while the map is started and actually on screen.
     */
    @Volatile private var started = true
    @Volatile private var destroyed = false
    private fun canQuery(): Boolean = started && mapView.isAttachedToWindow && mapView.isShown && mapView.windowVisibility == android.view.View.VISIBLE &&
        mapView.width > 50 && mapView.height > 50

    // (the Mapbox map also follows the screen it's on by itself; these calls are safe to repeat)
    override fun onStart(owner: LifecycleOwner) { started = true; try { mapView.onStart() } catch (e: Throwable) { } }
    override fun onResume(owner: LifecycleOwner) { started = true; try { flowAnim?.resume() } catch (e: Exception) { } }
    override fun onPause(owner: LifecycleOwner) { try { flowAnim?.pause() } catch (e: Exception) { } }
    override fun onStop(owner: LifecycleOwner) { started = false; try { mapView.onStop() } catch (e: Throwable) { } }

    /** The car screen went away for a while (another app full screen) or came back: stop or restart drawing. */
    fun carPause() { if (!started) return; started = false; try { flowAnim?.pause() } catch (e: Exception) { }; try { mapView.onStop() } catch (e: Throwable) { } }
    fun carResume() { if (started) return; try { mapView.onStart() } catch (e: Throwable) { }; started = true; try { flowAnim?.resume() } catch (e: Exception) { } }
    override fun onDestroy(owner: LifecycleOwner) = destroy()

    /** Stops everything and frees the map (the car screen calls this when it goes away). */
    @Synchronized
    fun destroy() {
        if (destroyed) return
        destroyed = true
        started = false
        try { lightHandler.removeCallbacksAndMessages(null) } catch (e: Throwable) { }
        try { lampExec.shutdownNow() } catch (e: Throwable) { }
        try { signalExec.shutdownNow() } catch (e: Throwable) { }
        for (c in subs) try { c.cancel() } catch (e: Throwable) { }
        subs.clear()
        for (c in pendingQueries) try { c.cancel() } catch (e: Throwable) { }
        pendingQueries.clear()
        try { meAnim?.cancel(); lanePulse?.cancel(); pulseAnim?.cancel(); flowAnim?.cancel() } catch (e: Throwable) { }
        try { mapView.onStop(); mapView.onDestroy() } catch (e: Throwable) { }
    }

    // ---- token and style ----
    /** The Mapbox public token (pk.…) from Settings. A new one is used from the next style load. */
    fun setToken(token: String) {
        val t = token.trim()
        val want = if (t.isEmpty()) NO_TOKEN else t
        if (MapboxOptions.accessToken == want) return
        MapboxOptions.accessToken = want
        // the token changed: load the style again with it
        if (t.isNotEmpty()) { loadedStyle = null; wantedStyle?.let { loadStyle(it) } }
    }

    /** Applies the app's selected language to the loaded TomTom vector style's translated labels. */
    fun setTomTomLabelLanguage(locale: Locale) {
        if (wantedStyle?.contains("/assets/styles/") != true) return
        style?.let { tomTomLabels(it, locale) }
    }

    /**
     * TomTom's map in the app's language (GitHub #243, #278: still in Arabic in the UAE). Mapbox's own label translation only
     * touches Mapbox's map data, so it did nothing to TomTom's. TomTom's tiles carry each name as "name" (the local language)
     * and "name_<language>" ("name_en", "name_zh-Hant"), so every label that shows "name" is pointed at the app's language
     * first, then the local name where there's no translation. The original text is remembered, so changing language again works.
     */
    private val ttFieldOrig = HashMap<String, String>()
    private fun tomTomLabels(st: Style, locale: Locale) {
        try {
            val lang = locale.language.ifBlank { "en" }
            val tag = locale.toLanguageTag()
            val script = locale.script
            val keys = LinkedHashSet<String>().apply {
                if (script.isNotBlank()) add("name_$lang-$script")
                if (tag.contains('-') && tag != lang) add("name_$tag")
                if (lang == "zh") add(if (locale.country in setOf("TW", "HK", "MO")) "name_zh-Hant" else "name_zh-Hans")
                add("name_$lang")
                if (lang == "nb" || lang == "nn") add("name_no")
            }
            val coalesce = "[\"coalesce\"," + keys.joinToString(",") { "[\"get\",\"$it\"]" } + ",[\"get\",\"name\"]]"
            for (info in st.styleLayers) {
                if (info.type != "symbol") continue
                val id = info.id
                if (id.startsWith("dl-")) continue
                val orig = ttFieldOrig[id] ?: try { st.getStyleLayerProperty(id, "text-field").value.toJson() } catch (e: Throwable) { null }?.also { ttFieldOrig[id] = it } ?: continue
                if (!orig.contains("\"name\"")) continue
                val json = orig.replace(Regex("\\[\\s*\"get\"\\s*,\\s*\"name\"\\s*\\]"), coalesce)
                if (json == orig) continue
                val v = com.mapbox.bindgen.Value.fromJson(json).value ?: continue
                st.setStyleLayerProperty(id, "text-field", v)
            }
        } catch (e: Throwable) { android.util.Log.w("MapBinding", "TomTom label language", e) }
    }

    /** Asks for a map style (a Mapbox style address such as [Style.STANDARD]). Loading the same one again does nothing. */
    fun setStyleUrl(url: String) {
        wantedStyle = url
        if (url != loadedStyle) loadStyle(url)
        // the same style asked for again after it failed to load: load it again
        else if (style == null) loadStyle(url)
    }

    /** "classic" or "immersive": the app always uses "immersive" now (kept so the screens can ask). */
    var theme = "immersive"
        private set

    fun setTheme(t: String) { theme = t }

    /** "full" (Standard in 3D), "lite" (Standard, flat, fewer labels) or "lightest" (Mapbox's classic 2D map). Settings > Map. */
    var mapDetail = "full"
        private set
    /** "mapbox", "horizon" (the app's own soft colours, slate blue at night), "twilight" (the same, with the original purple night), "faded" or "grey". Settings > Map. */
    private var colours = "mapbox"
    /** Whether the style has Standard's slots (classic styles don't: the app's layers then simply go on top). */
    private var slotsOn = true
    /** Mapbox's own map is showing, whose light the app can set (the brightness slider is only offered then). */
    @Volatile var lightsAdjustable = true
        private set

    /** Puts a layer in one of Standard's slots (only when the style has them). */
    private fun <T : Layer> T.inSlot(s: String): T { if (slotsOn) slot(s); return this }

    fun setMapDetail(d: String) {
        val v = if (d in setOf("full", "lite", "lightest")) d else "full"
        if (v == mapDetail) return
        val wasLightest = mapDetail == "lightest"
        mapDetail = v
        lite = v != "full"
        try { glowLayer?.visibility(vis(!lite)) } catch (e: Exception) { }
        // to or from the classic map: a different style; between full and lite: just Standard's settings
        if (wasLightest || v == "lightest") { if (isMapboxStyle(wantedStyle)) setStyleUrl(baseStyle()) }
        else {
            setBase("show3dObjects", Value.valueOf(buildings3d && v == "full"))
            setBase("showPointOfInterestLabels", Value.valueOf(!lite && !poiMoving))
            setBase("showTransitLabels", Value.valueOf(!lite))
            setBase("showLandmarkIcons", Value.valueOf(!lite))
            setBase("showHdRoads", Value.valueOf(v == "full" && hdRoadsOn))
            // the real 3D arrow only shows on the full map
            if (style != null) reinstall()
        }
    }

    /** The map's lettering (Mapbox Standard's "font" setting): "" leaves Mapbox's own. Changes in place, no reload. */
    private var mapFont = ""
    fun setFont(f: String) {
        val v = if (f in MAP_FONTS) f else ""
        if (v == mapFont) return
        mapFont = v
        setBase("font", Value.valueOf(if (v.isEmpty()) "DIN Pro" else v))
    }

    fun setColours(c: String) {
        val v = if (c in setOf("mapbox", "horizon", "twilight", "faded", "grey") || c in ThemeLut.ids) c else if (c == "moonlight") "horizon" else "mapbox"
        if (v == colours) return
        colours = v
        // the colours are part of how the map loads (and Horizon is a different base map), so it's loaded again
        if (isMapboxStyle(wantedStyle)) { loadedStyle = null; setStyleUrl(baseStyle()) }
        else if (wantedStyle != null && !slotsOn) { loadedStyle = null; setStyleUrl(wantedStyle!!) }
    }

    /** Asked to load the map style again (satellite switched): the engine knows which map source is chosen. */
    var onRestyle: (() -> Unit)? = null
    val isSatellite: Boolean get() = satellite
    /** For the feedback report: the last piece of Mapbox's map that wouldn't load, and which map is really on screen. */
    @Volatile var lastTileError = ""
    /** Why the map wouldn't load your own 3D model (Mapbox's words), and a hook to say so on screen. */
    @Volatile var ownFailReason = ""
    var onOwnModelFailed: ((String) -> Unit)? = null
    val shownMap: String get() = when (loadedStyle) {
        null -> "none"; Style.STANDARD_SATELLITE -> "satellite"; Style.STANDARD -> "standard"; Style.MAPBOX_STREETS, Style.DARK -> "classic"
        else -> if ((loadedStyle ?: "").startsWith("{")) "own style" else "other"
    }
    /** For the feedback report: what's drawing your arrow. */
    val arrowNote: String get() = when {
        modelShape == "own" && ownFailed -> "your 3D model wouldn't load (car picture instead)" + (if (ownFailReason.isNotBlank()) ": $ownFailReason" else "")
        modelOn -> "3D model ($modelShape)"
        modelWanted -> "3D model ($modelShape) waiting"
        else -> "picture ($locatorStyle)"
    }

    private fun isMapboxStyle(u: String?) = u == Style.STANDARD || u == Style.STANDARD_SATELLITE || u == Style.MAPBOX_STREETS || u == Style.DARK || u == NAV_DAY || u == NAV_NIGHT

    /** Standard's light: "day", "dawn", "dusk" or "night". Changing it fades smoothly, without reloading the map. */
    private var lightPreset = "night"

    /** The light the app wants now, and one forced for a while by the settings' map preview (Day / Night) to show the look. */
    private var realLight = "night"
    private var forcedLight: String? = null

    fun setLightPreset(p: String) {
        realLight = p
        if (forcedLight == null) applyLight(p)
    }

    /** The settings' map preview: show it in "day" or "night"; null goes back to the real light. */
    fun forceLight(p: String?) {
        if (p == forcedLight) return
        forcedLight = p
        applyLight(p ?: realLight)
    }

    private fun applyLight(p: String) {
        if (p == lightPreset) return
        lightPreset = p
        setBase("lightPreset", Value.valueOf(satLight(p)))
        // Horizon at night: the buildings their own colour (below). Back to day, Mapbox's own building colours return; a colour once
        // given can't be taken back while the map is showing, so the map is loaded again (only at that switch, once or twice a day)
        if (horizonNightBuildings()) { setBase("colorBuildings", Value.valueOf(hzBuildings())); setBase("colorLand", Value.valueOf(hzLand())); buildingsTinted = true }
        else if (buildingsTinted) { buildingsTinted = false; if (isMapboxStyle(wantedStyle)) { loadedStyle = null; setStyleUrl(baseStyle()) } }
        applyLights()
        applyCustom()
        // the hill shading has its own day and night shades
        if (hillsOn) style?.let { installHills(it) }
    }

    // ---- the Custom theme (Settings > Map style > Colours > Custom) ----
    // The theme it starts from stays the map's colours ([colours]); only the parts changed are set on top, so an untouched Custom
    // looks exactly like that theme. A colour set can be changed live; one taken away (Reset, or a part set for day but not night)
    // can't be handed back while the map shows, so the map is loaded again. The route line's colour and width are part of the map's
    // layers, so changing those loads it again too.
    private var customOn = false
    private var customDay = CustomLook.Spec()
    private var customNight = CustomLook.Spec()
    private var customRouteK = 1f
    private var customApplied: Set<String> = emptySet()

    private fun customSpec(): CustomLook.Spec? = if (!customOn || satellite) null else if (lightPreset == "night" || lightPreset == "dusk") customNight else customDay
    private fun customRouteCol(): String? = customSpec()?.route

    fun setCustom(on: Boolean, dayJson: String?, nightJson: String?, widthPct: Int) {
        val d = CustomLook.parse(dayJson); val n = CustomLook.parse(nightJson)
        val k = (if (on) widthPct.coerceIn(60, 180) else 100) / 100f
        if (on == customOn && d == customDay && n == customNight && k == customRouteK) return
        val reload = on != customOn || k != customRouteK || (on && (d.route != customDay.route || n.route != customNight.route))
        customOn = on; customDay = d; customNight = n; customRouteK = k
        if (reload) { if (isMapboxStyle(wantedStyle) && style != null) { loadedStyle = null; setStyleUrl(baseStyle()) } }
        else applyCustom()
    }

    private fun applyCustom() {
        if (!slotsOn) return
        val st = style ?: return
        val sp = customSpec()
        val want = HashMap<String, String>()
        if (sp != null) for (part in CustomLook.parts) sp.colours[part.key]?.let { c -> for (k in part.config) want[k] = c }
        // a part that had its own colour and now doesn't (or the route colour changing between day and night): load again
        val routeNow = sp?.route
        if (customApplied.any { it !in want.keys } || routeNow != appliedRouteCol) {
            if (loadedStyle != null && isMapboxStyle(wantedStyle)) { loadedStyle = null; setStyleUrl(baseStyle()) }
            return
        }
        for ((k, v) in want) setBase(k, Value.valueOf(v))
        customApplied = want.keys.toSet()
    }
    /** The route colour the map's layers were made with (checked against the one wanted now). */
    private var appliedRouteCol: String? = null

    // ---- "Match my car": the car's interior lighting colour, and how strong ----
    private var carTint = "#8A5AD0"
    private var carTintStr = 40

    fun setCarTint(hex: String, pct: Int) {
        if (hex == carTint && pct == carTintStr) return
        carTint = hex; carTintStr = pct.coerceIn(0, 100)
        if (colours == "car" && !satellite) ThemeLut.lut("car", carTint, carTintStr)?.let { setBase("theme-data", Value.valueOf(it)) }
    }

    // ---- Light and weather (Settings > Map look > Light and weather) ----
    // Mapbox's own light for each time of day, unless a setting here asks for something else; then the app sets its own two lights
    // (one all round, one from a direction). Pitched to Mapbox's own brightness: by night measured from screenshots, by day from
    // Mapbox's published daylight (expect a tuning round). Mapbox's light can't be handed back while the map is showing, so when
    // nothing calls for the app's own any more, the map is loaded again.
    class LightOpts(
        val nightBright: Int = 100, val moon: Boolean = false, val moonStr: Int = 100, val shadows: Boolean = true,
        val sunReal: Boolean = false, val overcast: Boolean = false, val weatherLight: Boolean = false, val dayBright: Int = 100,
    )
    private var lightOpts = LightOpts()
    private var lightsApplied: String? = null
    /** The weather here now (Open-Meteo's code), for Weather light; -1 when not known. */
    private var wxCode = -1
    private val lightHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sunTick = object : Runnable {
        override fun run() { if (destroyed) return; applyLights(); lightHandler.postDelayed(this, 5 * 60_000L) }
    }

    fun setLightOpts(o: LightOpts) {
        // (the map may only be changed on the main thread)
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { lightHandler.post { setLightOpts(o) }; return }
        if (destroyed) return
        lightOpts = o
        lightHandler.removeCallbacks(sunTick)
        if (o.sunReal) lightHandler.postDelayed(sunTick, 5 * 60_000L)
        applyLights()
    }

    fun setWeatherCode(code: Int?) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { lightHandler.post { setWeatherCode(code) }; return }
        if (destroyed) return
        val c = code ?: -1
        if (c == wxCode) return
        wxCode = c
        applyLights()
    }

    private fun raining() = wxCode in 51..67 || wxCode in 80..82 || wxCode in 95..99
    private fun snowing() = wxCode in 71..77 || wxCode in 85..86

    private fun f3(x: Double) = String.format(java.util.Locale.US, "%.3f", x)
    private fun lightJson(ambCol: String, amb: Double, dirCol: String, dir: Double, az: Double, polar: Double, shadows: Boolean, shadowInt: Double) =
        "[{\"id\":\"hz-amb\",\"type\":\"ambient\",\"properties\":{\"color\":\"$ambCol\",\"intensity\":${f3(amb)}}}," +
            "{\"id\":\"hz-dir\",\"type\":\"directional\",\"properties\":{\"color\":\"$dirCol\",\"intensity\":${f3(dir)}," +
            "\"direction\":[${f3(az)},${f3(polar)}],\"cast-shadows\":$shadows,\"shadow-intensity\":${f3(shadowInt)}}}]"

    /** The app's own light for now, or null for Mapbox's own. */
    private fun wantedLights(): String? {
        if (!slotsOn || satellite) return null
        val o = lightOpts
        return when (lightPreset) {
            "night" -> {
                val k = o.nightBright / 100.0
                // the moon in its real place (with "Sun in its real place"): its light and shadows from where it is, as bright as
                // it is full; once it has set, an even light with no shadows (dubrava4ever, #178)
                if (o.sunReal) {
                    val m = try { val c = mbMap.cameraState.center; Sun.moon(c.latitude(), c.longitude(), System.currentTimeMillis()) } catch (e: Throwable) { null }
                    if (m != null) {
                        val (az, alt, lit) = m
                        return if (alt > 2.0) {
                            val str = (if (o.moon) o.moonStr else 60) / 100.0 * (0.35 + 0.65 * lit)
                            lightJson("#8C9CE0", 0.085 * k, "#C8D8FF", 0.22 * k * str, az, (90.0 - alt).coerceIn(15.0, 80.0), o.shadows, 0.7)
                        } else lightJson("#B4BCE6", 0.12 * k, "#D2DCFF", 0.04 * k, 200.0, 20.0, false, 0.0)
                    }
                }
                if (!o.moon && o.nightBright == 100 && o.shadows) return null
                // the moon: Dusk's low light from one side and long shadows, from a low, pale blue moon in the south-west
                if (o.moon) lightJson("#8C9CE0", 0.085 * k, "#C8D8FF", 0.22 * k * o.moonStr / 100.0, 215.0, 68.0, o.shadows, 0.7)
                // otherwise an even light like Mapbox's night one, brighter or darker
                else lightJson("#B4BCE6", 0.11 * k, "#D2DCFF", 0.06 * k, 200.0, 40.0, o.shadows, 0.6)
            }
            "day" -> {
                val grey = o.overcast || (o.weatherLight && (raining() || snowing()))
                val kd = o.dayBright / 100.0
                if (!grey && !o.sunReal && o.shadows && o.dayBright == 100) return null
                if (grey) {
                    // overcast: soft light from all round, no hard shadows (greyer still when it's raining)
                    val wet = o.weatherLight && (raining() || snowing())
                    lightJson(if (wet) "#DCE0E6" else "#F2F4F7", (if (wet) 0.86 else 0.92) * kd, "#FFFFFF", 0.08 * kd, 180.0, 30.0, false, 0.0)
                } else {
                    var az = 180.0; var polar = 30.0
                    if (o.sunReal) try {
                        val c = mbMap.cameraState.center
                        val (a, el) = Sun.position(c.latitude(), c.longitude(), System.currentTimeMillis())
                        az = a; polar = (90.0 - el).coerceIn(15.0, 75.0)
                    } catch (e: Throwable) { }
                    lightJson("#FFFFFF", 0.8 * kd, "#FFFFFF", 0.2 * kd, az, polar, o.shadows, 0.3)
                }
            }
            else -> null
        }
    }

    private fun applyLights() {
        val want = wantedLights()
        if (want == null) {
            if (lightsApplied != null) {
                lightsApplied = null
                // (unless the map is already being loaded again, for the Horizon colours)
                if (loadedStyle != null && isMapboxStyle(wantedStyle)) { loadedStyle = null; setStyleUrl(baseStyle()); return }
            }
        } else if (want != lightsApplied) {
            val st = style ?: return
            try { Value.fromJson(want).value?.let { st.setStyleLights(it); lightsApplied = want } } catch (e: Throwable) { }
        }
    }


    /**
     * The Horizon theme at night and dusk: buildings a soft lavender-blue, a step lighter than the ground, rather than the same dark
     * shade as it (walls and roofs still take the map's light and shadow). Day keeps Mapbox's own building colours.
     */
    // Horizon at night (chosen by eye on the head unit): Mapbox's night light, the ground at 60% of the way from a deep blue-grey to
    // near white, and the buildings as far towards white as they go, so they stand out lighter than the ground
    private val hzLight = "night"
    private val hzBright = 60
    private val hzBld = 100

    private fun mix(a: Int, b: Int, t: Double): String {
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun m(s: Int) = Math.round(ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", m(16), m(8), m(0))
    }
    /** Horizon or Twilight: the two sets of the app's own colours (they differ only in the night tint). */
    private fun hzFamily() = colours == "horizon" || colours == "twilight"
    private fun twilight() = colours == "twilight"
    /** The ground's two ends: Twilight from a deep violet-blue to a pale lavender; Horizon the same brightness in slate blue with a touch of purple. */
    private fun landDark() = if (twilight()) 0x3F3A66 else 0x37475F
    private fun landLight() = if (twilight()) 0xEAE4FF else 0xE4EDFB
    /** The ground: from deep (0%) to pale (100%), before the night light dims it. */
    private fun hzLand(): String = mix(landDark(), landLight(), hzBright / 100.0)
    /** The buildings: the ground's colour taken towards white (Twilight) or a very pale slate blue (Horizon) by the Buildings level. */
    private fun hzBuildings(): String {
        val land = Integer.parseInt(hzLand().substring(1), 16)
        return mix(land, if (twilight()) 0xF7F2FF else 0xEEF4FF, 0.15 + 0.85 * hzBld / 100.0)
    }
    private var buildingsTinted = false

    private fun horizonNightBuildings(): Boolean = hzFamily() && !satellite && (lightPreset == "night" || lightPreset == "dusk")

    /** Satellite at "night" darkens the photos until there's next to nothing to see, and even dusk left them so dark that with
     *  Night chosen it still looked as if satellite wasn't working (Matattack99, #186): satellite's photos are shown in daylight at
     *  night (the screen's own night dimming still applies). */
    private fun satLight(p: String): String = if (satellite && p == "night") "day"
        else if (p == "night" && hzFamily() && hzLight == "dusk") "dusk" else p

    /** One of Standard's settings (only once a style is showing; they're also written into the style when it loads). */
    private fun setBase(key: String, v: Value) {
        val st = style ?: return
        try { st.setStyleImportConfigProperty(BASE, key, v) } catch (e: Throwable) { }
    }

    /** Standard's settings as they should be now, written into the style before it loads (so night never flashes up as day). */
    private fun baseConfig(): org.json.JSONObject {
        val sat = (wantedStyle ?: "") == Style.STANDARD_SATELLITE
        val c = org.json.JSONObject()
        c.put("lightPreset", if (sat && lightPreset == "night") "day" else if (!sat && lightPreset == "night" && hzFamily() && hzLight == "dusk") "dusk" else lightPreset)
        // shop and place labels rest on the light map and (on the full map) while you're on the move: placing them is the costly part
        c.put("showPointOfInterestLabels", !lite && !poiMoving)
        c.put("showTransitLabels", !lite)
        if (mapFont.isNotEmpty()) c.put("font", mapFont)
        if (!sat) c.put("show3dObjects", buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail))
        // (colour themes are only for the drawn map: Standard Satellite has none, so nothing is sent for it)
        if (!sat) when (colours) {
            "faded" -> c.put("theme", "faded")
            "grey" -> c.put("theme", "monochrome")
            // Mapbox's own colours with a gentle purple-blue hint (mostly at night), from a colour look-up table
            "horizon" -> if (!sat) { c.put("theme", "custom"); c.put("theme-data", HorizonLut.BASE64) }
            // the same with the original deeper purple-blue night
            "twilight" -> if (!sat) { c.put("theme", "custom"); c.put("theme-data", TwilightLut.BASE64) }
            // the other themes (Ember, Mono Night, Forest...): tables made on the device
            else -> if (colours in ThemeLut.ids) ThemeLut.lut(colours, carTint, carTintStr)?.let { c.put("theme", "custom"); c.put("theme-data", it) }
        }
        buildingsTinted = !sat && horizonNightBuildings()
        if (buildingsTinted) { c.put("colorBuildings", hzBuildings()); c.put("colorLand", hzLand()) }
        return c
    }

    /**
     * The style as JSON: Mapbox Standard (or Standard Satellite) imported as "basemap" with its settings, and nothing else; the
     * app's own sources and layers are added once it has loaded.
     */
    private fun wrapperJson(url: String): String {
        val imp = org.json.JSONObject().put("id", BASE).put("url", url).put("config", baseConfig())
        return org.json.JSONObject()
            .put("version", 8)
            .put("imports", org.json.JSONArray().put(imp))
            .put("sources", org.json.JSONObject())
            .put("layers", org.json.JSONArray())
            .toString()
    }

    private var styleToken = 0

    /** A map style has finished loading and is on screen. */
    val styleShown: Boolean get() = style != null

    private fun loadStyle(url: String) {
        // no real token yet: nothing to load (the engine shows a note asking for one)
        if (MapboxOptions.accessToken == NO_TOKEN) return
        loadedStyle = url
        style = null
        val token = ++styleToken
        // Standard (and Standard Satellite) come wrapped with their settings; Mapbox's classic 2D styles load as they are
        val standard = url == Style.STANDARD || url == Style.STANDARD_SATELLITE
        slotsOn = standard
        lightsAdjustable = standard
        try {
            mbMap.loadStyle(if (standard) wrapperJson(url) else url) { st ->
                if (token != styleToken) return@loadStyle
                style = st
                if (url.contains("/assets/styles/")) { ttFieldOrig.clear(); tomTomLabels(st, L.locale) }
                try { install(st) } catch (e: Throwable) { android.util.Log.w("MapBinding", "install failed", e) }
                onStyleLoaded?.invoke()
            }
        } catch (e: Throwable) { onError?.invoke(e.message ?: "Map failed to load") }
    }

    /** Adds a picture to the map at the size MapLibre gave it (its pixels at the bitmap's own density). */
    private fun addImg(st: Style, id: String, b: Bitmap) {
        try {
            val scale = (if (b.density > 0) b.density else android.util.DisplayMetrics.DENSITY_DEFAULT) / android.util.DisplayMetrics.DENSITY_DEFAULT.toFloat()
            st.addStyleImage(id, scale, b.toMapboxImage(), false, emptyList(), emptyList(), null)
        } catch (e: Throwable) { }
    }

    // ---- small helpers for the map's expressions ----
    private fun lit(v: Number): Expression = Expression.literal(v.toDouble())
    private fun col(hex: String): Expression = Expression.color(android.graphics.Color.parseColor(hex))
    /** A value that changes with the zoom: [base] 1.0 = linear, otherwise exponential; pairs of (zoom, value). */
    private fun zoomed(base: Double, vararg zv: Pair<Number, Number>): Expression {
        val args = ArrayList<Expression>()
        args += if (base == 1.0) Expression.linear() else Expression.exponential(base)
        args += Expression.zoom()
        for ((z, v) in zv) { args += lit(z); args += lit(v) }
        return Expression.interpolate(*args.toTypedArray())
    }
    private fun zoomedExpr(base: Double, vararg zv: Pair<Number, Expression>): Expression {
        val args = ArrayList<Expression>()
        args += if (base == 1.0) Expression.linear() else Expression.exponential(base)
        args += Expression.zoom()
        for ((z, v) in zv) { args += lit(z); args += v }
        return Expression.interpolate(*args.toTypedArray())
    }
    /** Adds a layer; one that fails is skipped (and logged) instead of stopping the rest of the map's layers being added. */
    private fun addL(st: Style, l: Layer, below: String? = null) {
        try { if (below != null && st.styleLayerExists(below)) st.addLayerBelow(l, below) else st.addLayer(l) }
        catch (e: Throwable) { android.util.Log.w("MapBinding", "layer ${l.layerId}: ${e.message}") }
    }
    private fun addS(st: Style, s: com.mapbox.maps.extension.style.StyleContract.StyleSourceExtension) {
        try { st.addSource(s) } catch (e: Throwable) { android.util.Log.w("MapBinding", "source: ${e.message}") }
    }
    private fun vis(on: Boolean) = if (on) Visibility.VISIBLE else Visibility.NONE
    private fun layer(id: String): Layer? = try { style?.getLayer(id) } catch (e: Throwable) { null }

    // ---- asking the map what's on screen (asynchronous, with a time limit) ----
    private val pendingQueries = ArrayList<Cancelable>()

    /**
     * Asks which features of [layers] are drawn inside [box] (screen pixels). [done] runs on the main thread with the features, or
     * with null when the map didn't answer within [timeoutMs] (it's then cancelled), so callers can always clear their busy flags.
     */
    private fun query(box: RectF, layers: List<String>, timeoutMs: Long = 3000L, done: (List<Feature>?) -> Unit) {
        var finished = false
        fun finish(r: List<Feature>?) { if (finished) return; finished = true; try { done(r) } catch (e: Throwable) { } }
        val c = try {
            mbMap.queryRenderedFeatures(
                RenderedQueryGeometry(ScreenBox(ScreenCoordinate(box.left.toDouble(), box.top.toDouble()), ScreenCoordinate(box.right.toDouble(), box.bottom.toDouble()))),
                RenderedQueryOptions(layers, null),
            ) { res ->
                val list = try { res.value?.map { it.queriedFeature.feature } } catch (e: Throwable) { null }
                main0.post { finish(list ?: emptyList()) }
            }
        } catch (e: Throwable) { finish(null); return }
        pendingQueries += c
        main0.postDelayed({
            pendingQueries.remove(c)
            if (!finished) { try { c.cancel() } catch (e: Throwable) { }; finish(null) }
        }, timeoutMs)
    }

    // ---- your favourites as stars on the map (Settings > On the map > Favourites on the map; Xundey, #249) ----
    @Volatile private var favs: List<Pair<LL, String>> = emptyList()
    @Volatile private var favsSmall = false
    private val favImgs = HashSet<String>()
    /** A star tapped: which one, by its place in the list given to [setFavourites]. */
    var onFavTap: ((Int) -> Unit)? = null

    /**
     * Shows [list] (each place and its name) as stars, with the name under each; [small] (while navigating) shows smaller stars
     * with no names, so they don't crowd the route. An empty list hides them. Only sent again when something changed.
     */
    /**
     * Your saved places on the map: [list] is each place, its name and its kind ("home", "work" or "fav", a star), [scale] the
     * size set in Settings › On the map › Saved places icons (1 = normal). Smaller and without names while navigating ([small]).
     */
    fun setFavourites(list: List<Pair<LL, String>>, small: Boolean, kinds: List<String> = emptyList(), scale: Float = 1f) {
        if (list == favs && small == favsSmall && kinds == favKinds && scale == favScale) return
        favs = list; favsSmall = small; favKinds = kinds; favScale = scale
        main0.post { pushFavs() }
    }
    private var favKinds: List<String> = emptyList()
    private var favScale = 1f

    private fun pushFavs() {
        val st = style ?: return
        try {
            val fs = ArrayList<Feature>()
            for ((i, f) in favs.withIndex()) {
                val (pos, name) = f
                val kind = favKinds.getOrNull(i) ?: "fav"
                val id = "dl-fav-" + kind + "-" + (if (favsSmall) "s" else name.hashCode().toString()) + "-" + (if (nightStyle) "n" else "d") + "-" + Math.round(favScale * 100)
                if (favImgs.add(id)) addImg(st, id, favBitmap(if (favsSmall) "" else name, kind))
                fs += Feature.fromGeometry(Point.fromLngLat(pos.lon, pos.lat)).also { it.addStringProperty("img", id); it.addNumberProperty("i", i) }
            }
            src("dl-favs")?.featureCollection(FeatureCollection.fromFeatures(fs))
        } catch (e: Throwable) { }
    }

    /** A yellow star in a white-edged circle, with [name] in a small tag under it (none when blank). The star is the middle. */
    private fun favBitmap(name: String, kind: String = "fav"): Bitmap {
        val dm = mapView.resources.displayMetrics
        // (Settings › On the map › Saved places icons: everything in the picture grows or shrinks together)
        val d = dm.density * favScale.coerceIn(0.5f, 2.5f)
        val r = (if (name.isEmpty()) 9f else 12f) * d
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f * d; typeface = android.graphics.Typeface.DEFAULT_BOLD; color = 0xFF3B2A00.toInt() }
        val label = if (name.length > 22) name.take(21) + "…" else name
        val tw = if (label.isEmpty()) 0f else text.measureText(label)
        val tagH = if (label.isEmpty()) 0f else 18f * d
        val w = maxOf(2 * r + 6 * d, tw + 14 * d)
        val half = r + 3 * d + (if (label.isEmpty()) 0f else 3 * d + tagH)
        val b = Bitmap.createBitmap(w.toInt() + 2, (half * 2).toInt() + 2, Bitmap.Config.ARGB_8888)
        b.density = dm.densityDpi
        val cv = Canvas(b)
        val cx = b.width / 2f; val cy = b.height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x55000000; cv.drawCircle(cx, cy + 1.5f * d, r + 1.5f * d, paint)
        paint.color = 0xFFFFFFFF.toInt(); cv.drawCircle(cx, cy, r + 1.5f * d, paint)
        // Home and Work: a house on blue and a briefcase on amber, like their tiles under the search bar, with a white name tag
        if (kind == "home" || kind == "work") {
            val home = kind == "home"
            paint.color = if (home) 0xFF3D7BF0.toInt() else 0xFFE39B1B.toInt(); cv.drawCircle(cx, cy, r, paint)
            paint.color = 0xFFFFFFFF.toInt()
            val k = r * 0.5f
            if (home) {
                cv.drawPath(Path().apply { moveTo(cx, cy - k * 1.05f); lineTo(cx + k * 1.05f, cy - k * 0.05f); lineTo(cx - k * 1.05f, cy - k * 0.05f); close() }, paint)
                cv.drawRect(cx - k * 0.72f, cy - k * 0.1f, cx + k * 0.72f, cy + k * 0.85f, paint)
                paint.color = if (home) 0xFF3D7BF0.toInt() else 0xFFE39B1B.toInt()
                cv.drawRect(cx - k * 0.2f, cy + k * 0.25f, cx + k * 0.2f, cy + k * 0.85f, paint)
            } else {
                cv.drawRoundRect(RectF(cx - k * 0.95f, cy - k * 0.45f, cx + k * 0.95f, cy + k * 0.8f), k * 0.18f, k * 0.18f, paint)
                val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = k * 0.2f }
                cv.drawRoundRect(RectF(cx - k * 0.4f, cy - k * 0.85f, cx + k * 0.4f, cy - k * 0.35f), k * 0.12f, k * 0.12f, hp)
            }
            if (label.isNotEmpty()) {
                val top = cy + r + 3 * d
                val rect = RectF(cx - tw / 2 - 7 * d, top, cx + tw / 2 + 7 * d, top + tagH)
                paint.color = 0xF2FFFFFF.toInt(); cv.drawRoundRect(rect, 6 * d, 6 * d, paint)
                text.color = if (home) 0xFF0B2A63.toInt() else 0xFF5A3A00.toInt()
                cv.drawText(label, cx - tw / 2, top + tagH / 2 - (text.ascent() + text.descent()) / 2, text)
            }
            return b
        }
        paint.color = 0xFFFFC83D.toInt(); cv.drawCircle(cx, cy, r, paint)
        // the five-pointed star
        val star = Path()
        val ro = r * 0.62f; val ri = ro * 0.45f
        for (k in 0 until 10) {
            val a = Math.toRadians(-90.0 + k * 36.0)
            val rr = if (k % 2 == 0) ro else ri
            val x = cx + (rr * Math.cos(a)).toFloat(); val y = cy + (rr * Math.sin(a)).toFloat()
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        paint.color = 0xFF6B4A00.toInt(); cv.drawPath(star, paint)
        if (label.isNotEmpty()) {
            val top = cy + r + 3 * d
            val rect = RectF(cx - tw / 2 - 7 * d, top, cx + tw / 2 + 7 * d, top + tagH)
            paint.color = 0xF2FFFFFF.toInt(); cv.drawRoundRect(rect, 6 * d, 6 * d, paint)
            cv.drawText(label, cx - tw / 2, top + tagH / 2 - (text.ascent() + text.descent()) / 2, text)
        }
        return b
    }

    private fun favAt(sp: android.graphics.PointF): Int? {
        val list = favs
        if (list.isEmpty()) return null
        // (the stars only show from zoom 10)
        if ((try { mbMap.cameraState.zoom } catch (e: Throwable) { 15.0 }) < 10.0) return null
        val reach = 26f * mapView.resources.displayMetrics.density * favScale.coerceIn(0.8f, 2.5f)
        var best: Int? = null
        var bd = reach * reach
        for ((i, f) in list.withIndex()) {
            val px = try { mbMap.pixelForCoordinate(Point.fromLngLat(f.first.lon, f.first.lat)) } catch (e: Throwable) { continue }
            val dx = px.x.toFloat() - sp.x; val dy = px.y.toFloat() - sp.y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = i }
        }
        return best
    }

    /** Google-style destination pin: red teardrop with a white centre. */
    /** Where you parked, shown on the map ("Your car"), or null for none. */
    @Volatile private var parkedAt: LL? = null
    fun setParked(p: LL?) {
        if (p == parkedAt || (p != null && parkedAt != null && Geo.dist(p, parkedAt!!) < 1.0)) return
        parkedAt = p
        main0.post {
            try { src("dl-parked")?.featureCollection(FeatureCollection.fromFeatures(p?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyList())) } catch (e: Throwable) { }
        }
    }

    // ---- the lane sign: an overhead sign standing at the junction, like a motorway gantry, with the lanes to use lit up ----
    @Volatile private var signAt: LL? = null
    @Volatile private var signBmp: Bitmap? = null
    private var signKey = ""

    /**
     * Shows the lane sign standing on the road at [at] (null hides it). [lanes] is each lane, left to right: its arrows (each
     * with whether it's the way to go) and whether it's a lane to use. It stays upright and faces you as the map tilts.
     */
    fun setLaneSign(at: LL?, lanes: List<Pair<List<Pair<String, Boolean>>, Boolean>>) {
        val key = if (at == null) "" else "${at.lat},${at.lon}|" + lanes.joinToString(";") { (d, on) -> d.joinToString(",") { "${it.first}${if (it.second) 1 else 0}" } + (if (on) "*" else "") }
        if (key == signKey) return
        signKey = key
        main0.post {
            val st = style ?: return@post
            try {
                if (at != null && lanes.isNotEmpty()) {
                    val b = laneSignBitmap(lanes)
                    signBmp = b; signAt = at
                    addImg(st, "dl-lanesign", b)
                    src("dl-lanesign")?.featureCollection(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(at.lon, at.lat)))))
                } else {
                    signAt = null
                    src("dl-lanesign")?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
                }
            } catch (e: Throwable) { }
        }
    }

    /** The sign itself: a panel with a white edge, one cell per lane (the ones to use lit), on two posts. */
    private fun laneSignBitmap(lanes: List<Pair<List<Pair<String, Boolean>>, Boolean>>): Bitmap {
        val cw = 64; val ch = 80; val pad = 10; val gap = 4; val post = 44
        val w = pad * 2 + cw * lanes.size + gap * (lanes.size - 1)
        val ph = ch + pad * 2
        val bmp = Bitmap.createBitmap(w, ph + post, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // posts
        p.color = 0xFF8A949C.toInt()
        c.drawRect(pad + 6f, ph - 4f, pad + 14f, (ph + post).toFloat(), p)
        c.drawRect(w - pad - 14f, ph - 4f, w - pad - 6f, (ph + post).toFloat(), p)
        // panel: dark teal (the turn card's colour) with a white edge
        val panel = android.graphics.RectF(1.5f, 1.5f, w - 1.5f, ph - 1.5f)
        p.color = 0xFF1F5E52.toInt(); c.drawRoundRect(panel, 14f, 14f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(panel, 14f, 14f, p)
        p.style = Paint.Style.FILL
        for ((i, lane) in lanes.withIndex()) {
            val x = pad + i * (cw + gap)
            val cell = android.graphics.RectF(x.toFloat(), pad.toFloat(), (x + cw).toFloat(), (pad + ch).toFloat())
            p.color = if (lane.second) 0xFF3FB596.toInt() else 0xFF173F37.toInt()
            c.drawRoundRect(cell, 9f, 9f, p)
            for ((d, hit) in lane.first.sortedBy { it.second }) c.drawBitmap(laneBitmap(d, hit && lane.second), x.toFloat(), pad.toFloat(), null)
        }
        return bmp
    }

    /** A blue round badge with a white car, for where you parked. */
    private fun parkedBitmap(): Bitmap {
        val n = 112
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.argb(70, 0, 0, 0); c.drawCircle(n / 2f, n / 2f + 3f, n / 2f - 4f, p)
        p.color = android.graphics.Color.WHITE; c.drawCircle(n / 2f, n / 2f, n / 2f - 6f, p)
        p.color = android.graphics.Color.parseColor("#2F6BDF"); c.drawCircle(n / 2f, n / 2f, n / 2f - 13f, p)
        // a simple side-on car: body, cabin and two wheels
        p.color = android.graphics.Color.WHITE
        c.drawRoundRect(android.graphics.RectF(28f, 52f, 84f, 70f), 7f, 7f, p)
        c.drawRoundRect(android.graphics.RectF(38f, 39f, 72f, 58f), 8f, 8f, p)
        p.color = android.graphics.Color.parseColor("#2F6BDF")
        c.drawRoundRect(android.graphics.RectF(43f, 44f, 54f, 53f), 3f, 3f, p); c.drawRoundRect(android.graphics.RectF(57f, 44f, 67f, 53f), 3f, 3f, p)
        p.color = android.graphics.Color.WHITE
        c.drawCircle(40f, 72f, 7f, p); c.drawCircle(72f, 72f, 7f, p)
        p.color = android.graphics.Color.parseColor("#2F6BDF")
        c.drawCircle(40f, 72f, 3f, p); c.drawCircle(72f, 72f, 3f, p)
        return bmp
    }

    private fun pinBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(96, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path = Path().apply {
            addCircle(48f, 44f, 34f, Path.Direction.CW)
            moveTo(22f, 66f); lineTo(48f, 120f); lineTo(74f, 66f); close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); setShadowLayer(8f, 0f, 4f, 0x66000000) })
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEA4335.toInt(); style = Paint.Style.FILL })
        c.drawCircle(48f, 44f, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8C1D18.toInt() })
        c.drawCircle(48f, 44f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        return bmp
    }

    private var locatorCache: Pair<String, Bitmap>? = null

    /** The same locator picture the map uses (for the current map colours), for drawing it small elsewhere. Points up. */
    fun locatorBitmap(): Bitmap {
        locatorCache?.let { if (it.first == theme) return it.second }
        val b = if (theme == "immersive") arrowBitmapImmersive() else arrowBitmap()
        locatorCache = theme to b
        return b
    }

    /** Google Maps locator: a chunky white chevron with a visible edge, lying flat on the map. Points up; the badge is rotated by heading. */
    private fun arrowBitmapImmersive(): Bitmap {
        // the whole locator in one picture (translucent ring plus a soft 3D slab arrow), laid flat on the map, so the ring and the arrow always keep the
        // same proportions and squash together as the view tilts. Drawn 4x larger than it is shown (the layer's icon size scales it back down).
        val n = 384
        val cx = 192f; val cy = 192f
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ringR = 160f
        c.drawCircle(cx, cy, ringR - 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB02F78AE.toInt() })
        c.drawCircle(cx, cy, ringR - 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; color = 0xB39ED0E2.toInt() })
        val top = Path().apply {
            moveTo(cx, cy - 125f)
            lineTo(cx + 91f, cy + 33f)
            lineTo(cx, cy + 11f)
            lineTo(cx - 91f, cy + 33f)
            close()
        }
        val round = android.graphics.CornerPathEffect(26f)
        val h = 34
        c.drawPath(Path(top).apply { offset(0f, h + 22f) }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66102040; pathEffect = round; maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
        // the slab's wall: lighter at the top edge, darker towards the ground, so it reads as a solid block
        for (d in h downTo 1) {
            val t = d / h.toFloat()
            val col = android.graphics.Color.rgb((0xA4 - 0x3A * t).toInt(), (0xA5 - 0x3A * t).toInt(), (0xC8 - 0x38 * t).toInt())
            c.drawPath(Path(top).apply { offset(0f, d.toFloat()) }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; pathEffect = round })
        }
        c.drawPath(top, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            pathEffect = round
            shader = android.graphics.LinearGradient(0f, cy - 125f, 0f, cy + 33f, 0xFFF2F2F6.toInt(), 0xFFC4C5D0.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        // a thin bright edge round the top face
        c.drawPath(top, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x99FFFFFF.toInt(); pathEffect = round })
        return bmp
    }







    /**
     * Set by the engine: true when the night look is showing. Standard's light changes by itself (see setLightPreset); the app's
     * own layers have day and night colours (and lamps only at night), so they're put back with the new colours.
     */
    var nightStyle = true
        set(v) {
            if (field == v) return
            field = v
            main0.post { reinstall() }
        }

    /**
     * Set by the engine: true when the night look has been picked (Always night) but the sun is actually up. The map stays dark,
     * but the street lamps aren't lit and the headlight beam is off, as they would be outside (bijindas, #225).
     */
    var daylightOutside = false
        set(v) {
            if (field == v) return
            field = v
            // (the lamps' lit pools are only added at night, so they're put back as on a day / night switch: twice a day at most)
            main0.post { reinstall(); try { placeBeam() } catch (e: Throwable) { } }
        }

    /** Night as far as the lights go: dark, and really after dark. */
    private val litNight get() = nightStyle && !daylightOutside


    // ---- extra 3D features (Settings > Map), see MapExtras.kt ----
    private var facadesOn = true
    private var landmarksOn = true
    private var pathsOn = true
    private var terrainOn = false
    private var tapSelectOn = true
    private var furnitureOn = true
    private var clearOn = true
    private var weatherCode: Int? = null
    /** The map is drawing the rain or snow itself (so the screen overlay only adds fog). */
    @Volatile var drawsWeather = false
    /** Which way the route runs at each traffic light (for turning its 3D model to face you). */
    private val signalHeading = HashMap<LL, Double>()
    @Volatile private var signalModels: FeatureCollection = FeatureCollection.fromFeatures(ArrayList<Feature>())
    /** The traffic lights shown on the route (the first one at each junction). */
    @Volatile private var signalKept: List<LL> = emptyList()
    /** Told the traffic lights on the route each time they change (the engine zooms in as you come up to one). */
    var onRouteSignals: ((List<LL>) -> Unit)? = null
    /** A place on Mapbox's map you tapped (its name and spot): holding on it then routes there by name. */
    var onPoiTap: ((String, LL) -> Unit)? = null

    /**
     * Mapbox's HD roads (lane markings, turn arrows, 3D overpasses). Where Mapbox thinks it has them it hides its ordinary roads,
     * and in some countries that left only the main roads on the map, with every street gone (GitHub #78, Reddit, Bulgaria and
     * Croatia): so it's a setting, on by itself only in the UK, where it's known to work.
     */
    private var hdRoadsOn = true
    fun setHdRoads(on: Boolean) {
        if (on == hdRoadsOn) return
        hdRoadsOn = on
        main0.post { if (!satellite) setBase("showHdRoads", Value.valueOf(hdRoadsOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail))) }
    }

    fun setExtras(facades: Boolean, landmarks: Boolean, paths: Boolean, terrain: Boolean, tapSelect: Boolean, furniture: Boolean, clear: Boolean) {
        val rebuild = terrain != terrainOn || furniture != furnitureOn || clear != clearOn
        facadesOn = facades; landmarksOn = landmarks; pathsOn = paths; terrainOn = terrain; furnitureOn = furniture; clearOn = clear
        if (tapSelect != tapSelectOn) { tapSelectOn = tapSelect; if (!tapSelect) clearSelection() }
        main0.post {
            setBase("show3dFacades", Value.valueOf(facadesOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("show3dLandmarks", Value.valueOf(landmarksOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("showPedestrianRoads", Value.valueOf(pathsOn))
            if (rebuild && style != null) reinstall()
        }
    }

    private var skyCode: Int? = null
    /** The weather for the haze towards the horizon (Settings > Fog and sky); null leaves Mapbox's own sky. */
    fun setSky(code: Int?) {
        if (code == skyCode && style != null) return
        skyCode = code
        main0.post { style?.let { Sky.atmosphere(it, skyCode, nightStyle, daylightOutside) } }
    }

    /** The weather where you are now (Open-Meteo code), for rain or snow on the map; null for none. */
    fun setWeather(code: Int?) {
        if (code == weatherCode && style != null) return
        weatherCode = code
        main0.post { style?.let { drawsWeather = Sky.weather(it, if (noEffects) null else weatherCode) } }
    }

    /** No falling rain or snow on the map (Android Auto's cool running: the phone is hot). */
    @Volatile private var noEffects = false
    fun setNoEffects(on: Boolean) {
        if (on == noEffects) return
        noEffects = on
        main0.post { style?.let { drawsWeather = Sky.weather(it, if (noEffects) null else weatherCode) } }
    }

    /** Puts the extras on a freshly loaded (or rebuilt) map. */
    private fun installExtras(st: Style) {
        // hills and valleys (not on the classic 2D map)
        Sky.terrain(st, terrainOn && mapDetail != "lightest")
        drawsWeather = Sky.weather(st, if (noEffects) null else weatherCode)
        Sky.atmosphere(st, skyCode, nightStyle, daylightOutside)
        if (furnitureOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail)) {
            Furniture.addModels(st)
            addS(st, GeoJsonSource.Builder("dl-signals-m").featureCollection(signalModels).build())
            // sized by the zoom so they stay easy to see (see Furniture.SIGNAL_SIZE)
            addL(st, Furniture.layer("dl-sig-model", "dl-signals-m", Furniture.SIGNAL, 0.35, 14.0, sigSize()).inSlot(TOP))
            addL(st, Furniture.layer("dl-sig-lens", "dl-signals-m", Furniture.SIGNAL_LENS, 1.0, 14.0, sigSize()).inSlot(TOP))
            // the traffic lights round you that aren't on a route (or when there's no route): from the offline pack's list
            addS(st, GeoJsonSource.Builder("dl-signals-near").featureCollection(empty()).build())
            addL(st, Furniture.layer("dl-sign-model", "dl-signals-near", Furniture.SIGNAL, 0.35, 14.0, sigSize()).inSlot(TOP))
            addL(st, Furniture.layer("dl-sign-lens", "dl-signals-near", Furniture.SIGNAL_LENS, 1.0, 14.0, sigSize()).inSlot(TOP))
            main0.postDelayed({ refreshLamps(true) }, 400L)
            // (the flat traffic light badges on your route stay at every zoom)
            furnZoom = -1.0
            applyNearLook()
            addS(st, GeoJsonSource.Builder("dl-cams-m").featureCollection(empty()).build())
            addL(st, Furniture.layer("dl-cam-model", "dl-cams-m", Furniture.CAMERA, 0.5, 14.0, sigSize()).inSlot(TOP))
            // the average speed check cameras at each end of a zone on your route
            addS(st, GeoJsonSource.Builder("dl-avg-cams").featureCollection(FeatureCollection.fromFeatures(avgCamFeats())).build())
            addL(st, Furniture.layer("dl-avgcam-model", "dl-avg-cams", Furniture.CAMERA, 0.5, 13.0, sigSize()).inSlot(TOP))
            // (the flat traffic light and camera badges stay on at every zoom: the 3D models are extra detail when you're close in)
        }
        // clear view: Mapbox's 3D trees and landmarks kept out of the way round your arrow and just ahead (Full 3D only)
        if (clearOn && slotsOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail) && !satellite) {
            addS(st, ClearView.source())
            addL(st, ClearView.layer())
            clearAt = null; updateClear(true)
        }
    }

    private fun sigSize() = Furniture.sizeAt(Furniture.SIGNAL_SIZE, try { mbMap.cameraState.zoom } catch (e: Throwable) { 16.0 })
    private fun lampSize() = Furniture.sizeAt(Furniture.LAMP_SIZE, try { mbMap.cameraState.zoom } catch (e: Throwable) { 16.0 })
    private var furnZoom = -1.0

    /** Resizes the 3D street furniture to the zoom, smoothly (only when the zoom has changed a little, so not on every frame). */
    private fun sizeFurniture() {
        if (style == null) return
        val z = try { mbMap.cameraState.zoom } catch (e: Throwable) { return }
        if (Math.abs(z - furnZoom) < 0.03) return
        furnZoom = z
        // (written straight to the style by id: looking layers up first costs far more, and this runs as you zoom)
        val st = style ?: return
        val furnSigIds = arrayOf("dl-sig-model", "dl-sig-lens", "dl-sign-model", "dl-sign-lens", "dl-cam-model", "dl-avgcam-model")
        val furnLampIds = arrayOf("dl-lamp-post", "dl-lamp-light")
        val s = Furniture.sizeAt(Furniture.SIGNAL_SIZE, z); val sv = Value(listOf(Value(s), Value(s), Value(s)))
        val l = Furniture.sizeAt(Furniture.LAMP_SIZE, z); val lv = Value(listOf(Value(l), Value(l), Value(l)))
        try {
            for (id in furnSigIds) if (st.styleLayerExists(id)) st.setStyleLayerProperty(id, "model-scale", sv)
            for (id in furnLampIds) if (st.styleLayerExists(id)) st.setStyleLayerProperty(id, "model-scale", lv)
        } catch (e: Throwable) { }
    }

    /** On a route, the traffic lights that aren't on it are greyed out (faded and dimmed), so the ones that matter stand out. */
    private fun applyNearLook() {
        try {
            // on a route every 3D traffic light is greyed out (the flat badges on the route are what to look at); lit when there's no route
            for ((b, l) in listOf("dl-sign-model" to "dl-sign-lens", "dl-sig-model" to "dl-sig-lens")) {
                val body = layer(b) as? ModelLayer
                val lens = layer(l) as? ModelLayer
                if (routing) {
                    body?.modelOpacity(0.45); body?.modelColor("#9A9EA6"); body?.modelColorMixIntensity(0.7)
                    lens?.modelOpacity(0.45); lens?.modelColor("#7C8088"); lens?.modelColorMixIntensity(1.0); lens?.modelEmissiveStrength(0.0)
                } else {
                    body?.modelOpacity(1.0); body?.modelColorMixIntensity(0.0)
                    lens?.modelOpacity(1.0); lens?.modelColorMixIntensity(0.0); lens?.modelEmissiveStrength(1.0)
                }
            }
        } catch (e: Throwable) { }
    }

    // ---- zebra crossings on the route ----
    /** Each crossing: where, the road's direction there, and whether it's a zebra (with Belisha beacons). */
    private var crossings: List<Triple<LL, Double, Boolean>> = emptyList()

    private fun zebraFeats(): List<Feature> = crossings.map { (p, b, _) ->
        Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addNumberProperty("r", b) }
    }

    private fun beaconFeats(): List<Feature> = crossings.filter { it.third }.flatMap { (p, b, _) ->
        listOf(-1.0, 1.0).map { side -> val at = Furniture.offset(p, b + 90.0 * side, 5.0); Feature.fromGeometry(Point.fromLngLat(at.lon, at.lat)) }
    }

    /** Stop signs on the route (from OpenStreetMap), drawn as small red octagon badges standing at the junction. */
    private var stopSigns: List<LL> = emptyList()
    private fun stopFeats(): List<Feature> = stopSigns.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
    fun setStopSigns(list: List<LL>) {
        main0.post {
            stopSigns = list
            try { src("dl-stops")?.featureCollection(FeatureCollection.fromFeatures(stopFeats())) } catch (e: Throwable) { }
        }
    }

    /** A stop sign: a red octagon with a white rim and STOP across it (drawn at 2x, shown at half). */
    private fun stopBitmap(): Bitmap {
        val s = 72
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun octagon(r: Float): Path = Path().apply {
            for (i in 0 until 8) {
                val a = Math.toRadians(22.5 + 45.0 * i)
                val x = s / 2f + r * Math.cos(a).toFloat(); val y = s / 2f + r * Math.sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        c.drawPath(octagon(35f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawPath(octagon(30f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8102E.toInt() })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText("STOP", s / 2f, s / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return bmp
    }

    fun setCrossings(list: List<Triple<LL, Double, Boolean>>) {
        main0.post {
            crossings = list
            try {
                src("dl-zebras")?.featureCollection(FeatureCollection.fromFeatures(zebraFeats()))
                src("dl-beacons")?.featureCollection(FeatureCollection.fromFeatures(beaconFeats()))
            } catch (e: Throwable) { }
        }
    }

    /** White crossing stripes across the road (the picture's top is along the road; drawn wide, shown to scale by the zoom). */
    private fun zebraBitmap(): Bitmap {
        val w = 200; val h = 80
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        var x = 6f
        while (x < w - 10) { c.drawRoundRect(android.graphics.RectF(x, 4f, x + 14f, h - 4f), 2f, 2f, p); x += 26f }
        return bmp
    }

    /** A Belisha beacon: a black and white pole with an amber globe on top, its foot at the bottom of the picture. */
    private fun beaconBitmap(): Bitmap {
        val w = 40; val h = 120
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = w / 2f
        for (k in 0 until 6) {
            val y0 = 34f + k * 14f
            c.drawRect(cx - 3.5f, y0, cx + 3.5f, y0 + 14f, Paint().apply { color = if (k % 2 == 0) 0xFF111111.toInt() else 0xFFFFFFFF.toInt() })
        }
        c.drawCircle(cx, 20f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(cx - 5f, 14f, 20f, intArrayOf(0xFFFFE08A.toInt(), 0xFFFFA000.toInt(), 0xFFE07B00.toInt()), floatArrayOf(0f, 0.6f, 1f), android.graphics.Shader.TileMode.CLAMP)
        })
        return bmp
    }

    // ---- route ribbon and tunnels ----
    /** Settings > Route ribbon: the route's soft shadow and the see-through copy over 3D buildings. */
    private var ribbonOn = true
    fun setRibbon(on: Boolean) { if (on == ribbonOn) return; ribbonOn = on; main0.post { if (style != null) reinstall() } }
    private var tunnelLines: List<List<LL>> = emptyList()

    /** The parts of the route that go through tunnels. */
    fun setTunnels(lines: List<List<LL>>) {
        main0.post {
            tunnelLines = lines
            try { src("dl-tunnels")?.featureCollection(FeatureCollection.fromFeatures(lines.filter { it.size >= 2 }.map { line(it) })) } catch (e: Throwable) { }
        }
    }

    // ---- sharp bends on the route: yellow with black chevrons, like the warning sign ----
    private var bendLines: List<List<LL>> = emptyList()
    fun setBends(lines: List<List<LL>>) {
        main0.post {
            bendLines = lines
            try { src("dl-bends")?.featureCollection(FeatureCollection.fromFeatures(lines.filter { it.size >= 2 }.map { line(it) })) } catch (e: Throwable) { }
        }
    }

    /** The bend pattern: yellow with a black chevron pointing along the road (the line repeats it in the way you're going). */
    private fun bendChevronBitmap(): Bitmap {
        val w = 48; val h = 32
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(android.graphics.Color.parseColor("#FACC15"))
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE; strokeWidth = 6f
            strokeJoin = android.graphics.Paint.Join.MITER; strokeCap = android.graphics.Paint.Cap.BUTT
        }
        val path = android.graphics.Path().apply { moveTo(16f, 6f); lineTo(30f, 16f); lineTo(16f, 26f) }
        c.drawPath(path, p)
        return b
    }

    // ---- the headlight beam: a soft cone of light ahead of the arrow at night, while moving ----
    @Volatile private var beamOn = false
    @Volatile private var beamSpeed = 0.0
    fun setBeam(on: Boolean, mps: Double) {
        val was = beamOn
        beamOn = on; beamSpeed = mps
        if (was != on) main0.post { try { pushBeam() } catch (e: Throwable) { } }
    }

    // The beam is a "location indicator" layer: its place and direction are set straight on the layer each time the arrow moves,
    // the way Mapbox moves its own location puck. (It used to be a GeoJSON symbol, re-sent 15 times a second: each update rebuilt
    // the symbol, so it flickered, sometimes vanished for a moment, and the extra work slowed the map.)
    private var beamShown = false
    private var beamLat = 0.0; private var beamLon = 0.0; private var beamHead = -999.0
    private var beamScale = -1.0

    private fun pushBeam() {
        // the old map-layer beam is kept hidden: the map engine placed its picture a cone-length behind the car, so the light
        // seemed to come from nowhere and shine back over the arrow or car (GitHub). The beam is now drawn by beamView instead.
        val st = style
        if (beamShown && st != null && st.styleLayerExists("dl-beam-l")) { st.setStyleLayerProperty("dl-beam-l", "visibility", Value.valueOf("none")); beamShown = false }
        placeBeam()
    }

    /**
     * The headlight beam, drawn just above the map and under the arrow / car, from the same spot on screen the arrow or car is
     * drawn at. Its corners are real places on the road ahead turned into screen points, so it lies flat on the road with the
     * map's tilt and turn, and moves with every camera move (placeLocator calls this).
     */
    val beamView: BeamView by lazy { BeamView(ctx) }

    class BeamView(c: android.content.Context) : android.view.View(c) {
        /** Screen points: nose, outer left, outer tip, outer right, inner left, inner tip, inner right, nose left, nose right, far middle. */
        val pts = FloatArray(20)
        var on = false
        /** The 3D car's outline on screen, left out of the light (null: nothing cut out). */
        var mask: android.graphics.Path? = null
        private val path = android.graphics.Path()
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var gx0 = 0f; private var gy0 = 0f; private var gx1 = 0f; private var gy1 = 0f
        init { isClickable = false; isFocusable = false; setWillNotDraw(false) }
        fun clear() { if (on) { on = false; invalidate() } }
        fun show() { on = true; invalidate() }
        override fun onDraw(canvas: android.graphics.Canvas) {
            if (!on) return
            val p = pts
            // the warm light fades from the nose to the far end (only rebuilt when the beam has moved on screen). One cone, about half
            // as bright as before: the second, wider cone for soft edges read as two beams on top of each other (liamstears)
            if (p[0] != gx0 || p[1] != gy0 || p[18] != gx1 || p[19] != gy1) {
                gx0 = p[0]; gy0 = p[1]; gx1 = p[18]; gy1 = p[19]
                paint.shader = android.graphics.LinearGradient(gx0, gy0, gx1, gy1,
                    intArrayOf(0x8CFFF4D6.toInt(), 0x40FFF4D6, 0x00FFF4D6), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
            }
            path.reset()
            path.moveTo(p[14], p[15]); path.lineTo(p[8], p[9]); path.quadTo(p[10], p[11], p[12], p[13]); path.lineTo(p[16], p[17]); path.close()
            val m = mask
            if (m != null) { canvas.save(); canvas.clipOutPath(m) }
            canvas.drawPath(path, paint)
            if (m != null) canvas.restore()
        }
    }

    private fun placeBeam() {
        val bv = beamView
        val p = me
        val night = (lightPreset == "night" || lightPreset == "dusk") && !daylightOutside
        if (p == null || !beamOn || !night || satellite || bv.parent == null) { bv.clear(); return }
        try {
            val g = beamGeom(p) ?: run { bv.clear(); return }
            val (ahead, len) = g
            val h = meHeading
            // The car pictures and the 3D arrow are drawn on top of the beam: there the beam starts under the car, near its middle, so
            // the car itself hides where it begins and the light seems to come out of the headlights whatever the car's shape (it
            // used to start at the windscreen, liamstears). The flat arrow and the real 3D model are part of the map, under the beam,
            // so there it still starts at the nose.
            val carOnTop = locatorStyle != "flat" && !modelOn && locatorView.visibility == android.view.View.VISIBLE
            // the 3D car: the beam starts under it too, and the car's own shape on screen (its outline, roof included) is cut out
            // of the light, so the car looks drawn over it (the beam covered its roof, liamstears). The beam used to be a picture in
            // the map for this, re-placed many times a second, and it flickered
            // (only the car: the 3D arrow is low and flat, so its beam starts at its tip as before. Cutting the arrow's outline out
            // left a gap, and the light seemed to hover in front of it)
            // (not your own model: its shape is unknown, so a car-shaped box cut out of the light didn't match it and left an odd
            // dark patch round it, GitHub #152. Its beam starts at its nose instead, like the flat arrow's)
            val carModel = modelOn && carLike && modelShape != "own"
            val start = if (carOnTop) ahead * 0.15 else if (carModel) ahead * 0.45 else ahead
            bv.mask = if (carModel) try { modelMask(p, h) } catch (e: Throwable) { null } else null
            val nose = if (start > 0.0) Furniture.offset(p, h, start) else p
            // a little longer the faster you go (up to about 70 mph); measured from the nose, so starting further back doesn't shorten it
            val sc = 0.8 + (beamSpeed.coerceIn(0.0, 31.0) / 31.0) * 0.5
            val l = len * sc + (ahead - start)
            val far = Furniture.offset(nose, h, l * 0.88)
            val tipO = Furniture.offset(nose, h, l * 1.05); val tipI = Furniture.offset(nose, h, l)
            // (about half as wide as it was: the wide cone lit up the buildings either side, GitHub #152)
            val wO = l * 0.24; val wI = l * 0.19
            val places = arrayOf(nose, Furniture.offset(far, h - 90.0, wO), tipO, Furniture.offset(far, h + 90.0, wO),
                Furniture.offset(far, h - 90.0, wI), tipI, Furniture.offset(far, h + 90.0, wI),
                Furniture.offset(nose, h - 90.0, l * 0.025), Furniture.offset(nose, h + 90.0, l * 0.025), Furniture.offset(nose, h, l))
            val out = bv.pts
            for ((i, q) in places.withIndex()) {
                val px = mbMap.pixelForCoordinate(Point.fromLngLat(q.lon, q.lat))
                out[i * 2] = px.x.toFloat(); out[i * 2 + 1] = px.y.toFloat()
            }
            bv.show()
        } catch (e: Throwable) { bv.clear() }
    }

    /**
     * The 3D car's (or 3D arrow's) outline on screen: its footprint on the road, and the same lifted by its height (the camera looks
     * down at an angle, so the roof sits higher on screen), joined into one shape. A little generous, so no light shows on the body.
     */
    private fun modelMask(p: LL, h: Double): android.graphics.Path? {
        val tilt = try { mbMap.cameraState.pitch } catch (e: Throwable) { tiltNow }
        val t = Math.toRadians(tilt)
        val k = modelPxPerCarUnit()
        val a = mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat))
        val f = Furniture.offset(p, h, 20.0)
        val b = mbMap.pixelForCoordinate(Point.fromLngLat(f.lon, f.lat))
        val dl = Math.hypot(b.x - a.x, b.y - a.y)
        if (dl < 1e-3 || k <= 0.0) return null
        val ux = (b.x - a.x) / dl; val uy = (b.y - a.y) / dl
        val nx = -uy; val ny = ux
        // model units: about 4.6 long, 2 wide and 1.6 tall for the car; the arrow is flatter
        val hl = k * (if (carLike) 2.45 else 0.95) * Math.cos(t).coerceAtLeast(0.2) * 1.08
        val hw = k * (if (carLike) 1.05 else 0.8) * 1.08
        val up = k * (if (carLike) 1.65 else 0.45) * Math.sin(t)
        val pts = ArrayList<Pair<Double, Double>>(8)
        for (lift in doubleArrayOf(0.0, up)) for (sl in doubleArrayOf(-1.0, 1.0)) for (sw in doubleArrayOf(-1.0, 1.0))
            pts += Pair(a.x + ux * hl * sl + nx * hw * sw, a.y + uy * hl * sl + ny * hw * sw - lift)
        // the outline round all eight corners (monotone chain)
        val q = pts.sortedWith(compareBy({ it.first }, { it.second }))
        fun cross(o: Pair<Double, Double>, a1: Pair<Double, Double>, b1: Pair<Double, Double>) =
            (a1.first - o.first) * (b1.second - o.second) - (a1.second - o.second) * (b1.first - o.first)
        val hull = ArrayList<Pair<Double, Double>>()
        for (pass in 0..1) {
            val start = hull.size
            for (pt in if (pass == 0) q else q.asReversed()) {
                while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) hull.removeAt(hull.size - 1)
                hull += pt
            }
            hull.removeAt(hull.size - 1)
        }
        if (hull.size < 3) return null
        return android.graphics.Path().apply {
            moveTo(hull[0].first.toFloat(), hull[0].second.toFloat())
            for (i in 1 until hull.size) lineTo(hull[i].first.toFloat(), hull[i].second.toFloat())
            close()
        }
    }

    /**
     * Where the beam starts (metres ahead of the car's spot: the arrow's or car's nose) and how long it is (metres, about three and
     * a half times the arrow's or car's length on screen, so it looks the same at any zoom). Null if it can't be worked out.
     */
    private fun beamGeom(p: LL): Pair<Double, Double>? {
        val tilt = try { mbMap.cameraState.pitch } catch (e: Throwable) { tiltNow }
        val cosT = Math.cos(Math.toRadians(tilt)).coerceAtLeast(0.2)
        val bmp = spriteBmp
        val reachPx: Double = when {
            modelOn -> modelPxPerCarUnit() * (if (carLike) 2.3 else 0.85) * cosT
            locatorStyle != "flat" && bmp != null -> (drawnRadius(bmp) * spriteSize() * 0.8).toDouble()
            else -> ((flatArrow?.let { drawnRadius(it) * (if (theme == "immersive") 0.25f else 0.625f) * arrowScale } ?: 0f) * cosT)
        }
        val a = mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat))
        val f = Furniture.offset(p, meHeading, 20.0)
        val b = mbMap.pixelForCoordinate(Point.fromLngLat(f.lon, f.lat))
        val pxPerM = Math.hypot(b.x - a.x, b.y - a.y) / 20.0
        if (pxPerM < 1e-3) return null
        val ahead = if (reachPx > 0.0) (reachPx / pxPerM).coerceIn(0.0, 60.0) else 0.0
        // the cone's length on screen: 3.5 times the arrow's or car's length, at least 120 px (the drawn arrow can be tiny)
        val lenPx = maxOf(120.0 * mapView.resources.displayMetrics.density / 2.0, reachPx * 2.0 * 3.5)
        return ahead to (lenPx / pxPerM).coerceIn(8.0, 400.0)
    }

    /** How far from the centre a picture's drawing really reaches (its empty margin left out), in its own pixels. */
    private val drawnReach = java.util.WeakHashMap<Bitmap, Float>()
    private fun drawnRadius(b: Bitmap): Float = drawnReach.getOrPut(b) {
        val w = b.width; val h = b.height; val cx = w / 2f; val cy = h / 2f
        val row = IntArray(w); var best = 0f
        var y = 0
        while (y < h) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) { if ((row[x] ushr 24) > 40) { val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble()).toFloat(); if (d > best) best = d }; x += 2 }
            y += 2
        }
        best
    }

    /**
     * The beam: warm light fading out from the arrow, with soft edges. Drawn in the top half of a picture twice its height, as the
     * location indicator centres its picture on the car (so the cone starts at the arrow and reaches ahead).
     */
    private fun beamBitmap(): Bitmap {
        val w = 256; val h = 320
        val b = Bitmap.createBitmap(w, h * 2, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(w / 2f, h.toFloat(), h.toFloat(),
                intArrayOf(0xC8FFF4D6.toInt(), 0x66FFF4D6, 0x00FFF4D6), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
            maskFilter = android.graphics.BlurMaskFilter(16f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val cone = android.graphics.Path().apply {
            moveTo(w / 2f - 6f, h.toFloat()); lineTo(22f, 40f); quadTo(w / 2f, 4f, w - 22f, 40f); lineTo(w / 2f + 6f, h.toFloat()); close()
        }
        c.drawPath(cone, p)
        return b
    }

    // ---- arriving: the building lit, its entrance marked, and a dotted walk from the car park ----
    @Volatile private var doorFrom: LL? = null
    private var door: LL? = null
    private var walkFrom: LL? = null

    fun showArrivalPlace(dest: LL?, from: LL?, parking: LL?) {
        main0.post {
            try {
                if (dest == null) { doorFrom = null; door = null; walkFrom = null; highlightDestination(null); pushDoor(); return@post }
                doorFrom = from; walkFrom = parking
                highlightDestination(dest)
                pushDoor()
            } catch (e: Throwable) { }
        }
    }

    private fun pushDoor() {
        val d = door
        src("dl-door")?.let { s -> if (d != null) s.feature(Feature.fromGeometry(Point.fromLngLat(d.lon, d.lat))) else s.featureCollection(empty()) }
        val w = walkFrom; val to = d ?: destLitFor
        src("dl-walk")?.let { s -> if (w != null && to != null && Geo.dist(w, to) > 15.0) s.feature(line(listOf(w, to))) else s.featureCollection(empty()) }
    }

    /** The point on a building's outline nearest [from] (where you pulled up): its likely entrance. */
    private fun nearestOnOutline(g: com.mapbox.geojson.Geometry?, from: LL): LL? {
        val rings: List<List<Point>> = when (g) {
            is com.mapbox.geojson.Polygon -> g.coordinates().take(1)
            is com.mapbox.geojson.MultiPolygon -> g.coordinates().mapNotNull { it.firstOrNull() }
            else -> return null
        }
        var best: LL? = null; var bd = 1e12
        val k = Math.cos(Math.toRadians(from.lat))
        for (ring in rings) for (i in 0 until ring.size - 1) {
            val ax = (ring[i].longitude() - from.lon) * k; val ay = ring[i].latitude() - from.lat
            val bx = (ring[i + 1].longitude() - from.lon) * k; val by = ring[i + 1].latitude() - from.lat
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val t = if (l2 > 0) (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0) else 0.0
            val px = ax + dx * t; val py = ay + dy * t
            val dd = px * px + py * py
            if (dd < bd) { bd = dd; best = LL(from.lat + py, from.lon + px / k) }
        }
        return best
    }

    /** The entrance marker: a blue disc with a white door. */
    private fun doorBitmap(): Bitmap {
        val dn = mapView.resources.displayMetrics.density
        val sz = (30 * dn).toInt().coerceAtLeast(24)
        val b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE; c.drawCircle(sz / 2f, sz / 2f, sz / 2f, p)
        p.color = android.graphics.Color.parseColor("#2E7CF6"); c.drawCircle(sz / 2f, sz / 2f, sz / 2f - 2.5f * dn, p)
        p.color = android.graphics.Color.WHITE
        val dw = sz * 0.30f; val dh = sz * 0.44f
        c.drawRoundRect(sz / 2f - dw / 2, sz / 2f - dh / 2, sz / 2f + dw / 2, sz / 2f + dh / 2, 2f * dn, 2f * dn, p)
        p.color = android.graphics.Color.parseColor("#2E7CF6"); c.drawCircle(sz / 2f + dw * 0.22f, sz / 2f + dh * 0.05f, 1.6f * dn, p)
        return b
    }

    // ---- average speed zones on the route ----
    private var avgZoneLines: List<List<LL>> = emptyList()
    private var avgCams: List<Pair<LL, Double>> = emptyList()

    private fun avgCamFeats(): List<Feature> = avgCams.map { (p, b) -> val at = Furniture.offset(p, b - 90.0, 5.0); Furniture.point(at.lat, at.lon, b) }

    /** The average speed zones on the route: each as the route's line between its cameras, and the cameras (spot and road direction). */
    fun setAvgZones(lines: List<List<LL>>, cams: List<Pair<LL, Double>>) {
        main0.post {
            avgZoneLines = lines; avgCams = cams
            try {
                src("dl-avg")?.featureCollection(FeatureCollection.fromFeatures(lines.filter { it.size >= 2 }.map { line(it) }))
                src("dl-avg-cams")?.featureCollection(FeatureCollection.fromFeatures(avgCamFeats()))
            } catch (e: Throwable) { }
        }
    }

    // ---- your destination's building, lit up as you get close ----
    private var destLit: com.mapbox.maps.interactions.standard.generated.StandardBuildingsFeature? = null
    private var destLitFor: LL? = null
    private var destTryAt = 0L

    /**
     * Lights up the building at [p] (your destination, from about 400 m away) in Mapbox's building highlight colour; null puts it
     * back to normal. It keeps trying every few seconds until the building is on screen.
     */
    fun highlightDestination(p: LL?) {
        if (p == null) {
            val f = destLit
            destLit = null; destLitFor = null
            if (f != null) try { mbMap.setFeatureState(f, com.mapbox.maps.interactions.standard.generated.StandardBuildingsState { highlight(false) }) } catch (e: Throwable) { }
            if (door != null) { door = null; try { pushDoor() } catch (e: Throwable) { } }
            return
        }
        if (!slotsOn || satellite) return
        val prev = destLitFor
        if (prev != null && Geo.dist(prev, p) < 5.0 && destLit != null) return
        if (prev == null || Geo.dist(prev, p) >= 5.0) { highlightDestination(null); destLitFor = p }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - destTryAt < 3000L || !canQuery()) return
        destTryAt = now
        try {
            val sc = mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat))
            if (sc.x < 0 || sc.y < 0 || sc.x > mapView.width || sc.y > mapView.height) return
            val r = 14.0 * mapView.resources.displayMetrics.density
            val box = RenderedQueryGeometry(ScreenBox(ScreenCoordinate(sc.x - r, sc.y - r), ScreenCoordinate(sc.x + r, sc.y + r)))
            mbMap.queryRenderedFeatures(com.mapbox.maps.interactions.standard.generated.StandardBuildings(), box) { list ->
                val f = list.firstOrNull() ?: return@queryRenderedFeatures
                main0.post {
                    if (destLitFor == null || Geo.dist(destLitFor!!, p) >= 5.0) return@post
                    destLit = f
                    try { mbMap.setFeatureState(f, com.mapbox.maps.interactions.standard.generated.StandardBuildingsState { highlight(true) }) } catch (e: Throwable) { }
                    // arriving: the entrance is the side of the building nearest where you pulled up
                    val from = doorFrom
                    if (from != null) try { door = nearestOnOutline(f.geometry, from); pushDoor() } catch (e: Throwable) { }
                }
            }
        } catch (e: Throwable) { }
    }

    private var clearAt: LL? = null
    private var clearTime = 0L

    /** Moves the clear-view area along with you (at most about twice a second, and only once you've moved a few metres). */
    private fun updateClear(force: Boolean) {
        if (!clearOn) return
        val p = me ?: return
        val now = android.os.SystemClock.uptimeMillis()
        val last = clearAt
        if (!force && (now - clearTime < 500L || (last != null && Geo.dist(last, p) < 4.0))) return
        val s = src("dl-clip") ?: return
        clearAt = p; clearTime = now
        try { s.featureCollection(ClearView.shapes(p, meHeading, main)) } catch (e: Throwable) { }
    }

    // ---- tapping buildings and places on Mapbox's map: they light up (Settings > Map > Tap to highlight) ----
    private var selectAt = 0L

    /** A tap that didn't land on a building: whatever was lit up goes back to normal. */
    private fun tapClears() { if (tapSelectOn && android.os.SystemClock.uptimeMillis() - selectAt > 400L) clearSelection() }

    private fun clearSelection() {
        try {
            mbMap.resetFeatureStates(com.mapbox.maps.interactions.standard.generated.StandardBuildings())
            mbMap.resetFeatureStates(com.mapbox.maps.interactions.standard.generated.StandardPoi())
        } catch (e: Throwable) { }
    }

    private fun installTapSelect() {
        try {
            mbMap.addInteraction(com.mapbox.maps.ClickInteraction.standardBuildings { f, _ ->
                if (!tapSelectOn) return@standardBuildings false
                // (these run straight from the map's own tap handling, outside any try above: anything that went wrong in here
                // would close the app)
                try {
                    clearSelection()
                    selectAt = android.os.SystemClock.uptimeMillis()
                    mbMap.setFeatureState(f, com.mapbox.maps.interactions.standard.generated.StandardBuildingsState { select(true) })
                } catch (e: Throwable) { }
                // (not "handled": the app's own tap handling, like the route cards, still gets the tap)
                false
            })
            mbMap.addInteraction(com.mapbox.maps.ClickInteraction.standardPoi { f, _ ->
                if (!tapSelectOn) return@standardPoi false
                // a place with no name in Mapbox's data has a "null" name, which Mapbox's own name lookup can't read and throws on
                // (it closed the app when tapped): read safely, and a nameless place is just ignored
                try {
                    val g = f.geometry
                    val n = try { f.name } catch (e: Throwable) { null }
                    if (!n.isNullOrBlank()) onPoiTap?.invoke(n, LL(g.latitude(), g.longitude()))
                } catch (e: Throwable) { android.util.Log.w("MapBinding", "place tap: ${e.message}") }
                false
            })
        } catch (e: Throwable) { android.util.Log.w("MapBinding", "tap highlight: ${e.message}") }
    }

    /** Takes the app's own layers off the map and puts them back (after a day / night change), leaving Standard as it is. */
    private fun reinstall() {
        val st = style ?: return
        try {
            Sky.clearTerrain(st)
            for (l in st.styleLayers.map { it.id }.filter { it.startsWith("dl-") }.reversed()) try { st.removeStyleLayer(l) } catch (e: Throwable) { }
            for (src in st.styleSources.map { it.id }.filter { it.startsWith("dl-") }) try { st.removeStyleSource(src) } catch (e: Throwable) { }
            install(st)
        } catch (e: Throwable) { android.util.Log.w("MapBinding", "reinstall failed", e) }
    }

    /** Kept so the engine's settings still apply (the Mapbox edition always uses Mapbox's own map look). */
    @Volatile var keepBaseLook = false
    @Volatile var recolourBase = false
    /** (Standard Satellite has its own imagery, so this is no longer used.) */
    @Volatile var satTiles: String? = null


    /**
     * The roads on screen, for snapping your position to the road while off-route (any time) and placing guessed street lamps
     * (at night): Mapbox Streets' road lines, close in only. They're drawn almost invisibly (1% opacity): a fully transparent layer
     * isn't drawn at all, so the map couldn't be asked which roads are on screen.
     */
    private fun installRoadData(st: Style) {
        addS(st, VectorSource.Builder("dl-streets").url("mapbox://mapbox.mapbox-streets-v8").build())
        addL(st, LineLayer("dl-roadq", "dl-streets").sourceLayer("road").inSlot("bottom")
            .filter(Expression.fromRaw("[\"match\",[\"get\",\"class\"],[\"motorway\",\"motorway_link\",\"trunk\",\"trunk_link\",\"primary\",\"secondary\",\"tertiary\",\"street\",\"street_limited\",\"primary_link\",\"secondary_link\",\"tertiary_link\",\"service\"],true,false]"))
            .lineColor("#808080").lineOpacity(0.01).lineWidth(1.0).lineEmissiveStrength(1.0).minZoom(15.0))
        // Mapbox's HD roads (the lanes it paints on motorways, where it has them): the middle line of every lane, for lining the lane
        // guidance up with the painted lanes. Drawn almost invisibly, close in only, so the map can be asked where they are
        try {
            addS(st, VectorSource.Builder("dl-hd").url("mapbox://mapbox.hd-road-v1").build())
            addL(st, LineLayer("dl-hdq", "dl-hd").sourceLayer("hd_road_centerlines").inSlot("bottom")
                .lineColor("#808080").lineOpacity(0.01).lineWidth(1.0).lineEmissiveStrength(1.0).minZoom(15.5))
        } catch (e: Throwable) { }
    }

    /**
     * The real lanes across the road at [p] (going the way [heading] points), from Mapbox's HD roads: each lane's middle, as metres
     * sideways from [p] (+ to the right), left to right. [done] gets null when it isn't on screen, there's no HD road there, or the
     * map didn't answer. On the main thread.
     */
    /**
     * Where the real lanes are (Mapbox's HD lane lines) across the road at each of [samples] (a place and the road's heading
     * there), in metres from the route line, left to right. One map read covers them all. [done] gets one list per sample (empty
     * where nothing was found or that place is off screen) and how many lane lines were read, or null when none of the places is
     * on screen or the map can't be read yet.
     */
    fun laneCentresAlong(samples: List<Pair<LL, Double>>, done: (List<List<Double>>?, Int) -> Unit) {
        main0.post {
            val st = style
            if (st == null || samples.isEmpty() || !canQuery() || !st.styleLayerExists("dl-hdq")) { done(null, 0); return@post }
            val w = mapView.width.toFloat(); val h = mapView.height.toFloat()
            val onScreen = samples.map { (p, _) ->
                val sc = try { mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat)) } catch (e: Throwable) { null }
                sc != null && sc.x >= 0 && sc.y >= 0 && sc.x <= w && sc.y <= h && !sc.x.isNaN()
            }
            val pts = samples.indices.filter { onScreen[it] }.mapNotNull { i ->
                try { mbMap.pixelForCoordinate(Point.fromLngLat(samples[i].first.lon, samples[i].first.lat)) } catch (e: Throwable) { null }
            }
            if (pts.isEmpty()) { done(null, 0); return@post }
            val m = 90f * mapView.resources.displayMetrics.density
            val box = RectF((pts.minOf { it.x }.toFloat() - m).coerceAtLeast(0f), (pts.minOf { it.y }.toFloat() - m).coerceAtLeast(0f),
                (pts.maxOf { it.x }.toFloat() + m).coerceAtMost(w), (pts.maxOf { it.y }.toFloat() + m).coerceAtMost(h))
            query(box, listOf("dl-hdq"), 3000L) { feats ->
                if (feats == null) { done(null, 0); return@query }
                val lines = ArrayList<List<LL>>()
                for (f in feats) when (val g = f.geometry()) {
                    is LineString -> lines += g.coordinates().map { LL(it.latitude(), it.longitude()) }
                    is MultiLineString -> for (c in g.coordinates()) lines += c.map { LL(it.latitude(), it.longitude()) }
                    else -> { }
                }
                done(samples.mapIndexed { i, (p, hd) -> if (onScreen[i]) MapRouteGeometry.laneOffsets(lines, p, hd) else emptyList() }, lines.size)
            }
        }
    }

    private var roadsAt = 0L
    private var roadsQuerying = false

    /** Reads the roads on screen (every few seconds at most, in the background), for snapping your position to the road. */
    private fun refreshRoads() {
        val st = style ?: return
        if (roadsQuerying || !canQuery() || !st.styleLayerExists("dl-roadq")) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - roadsAt < 4000L) return
        if ((try { mbMap.cameraState.zoom } catch (e: Throwable) { 0.0 }) < 15.0) return
        roadsAt = now; roadsQuerying = true
        query(RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()), listOf("dl-roadq")) { feats ->
            roadsQuerying = false
            if (feats != null) rememberRoads(feats)
        }
    }

    // ---- street lamps: real 3D lamp posts (the lamp model in assets/models), their lamps lit at night ----
    //
    // Where they stand: the lamps surveyed in OpenStreetMap (the offline pack's lamps.tsv). Where nobody has mapped any (most of
    // the UK), lamps are placed along the streets on screen instead, a set distance apart at the kerb, arm over the road. Shown by
    // day and night (only lit at night), from zoom 15. Settings > On the map > Street lamps shows what they're doing.

    /** Settings > On the map > Street lamps at night. */
    var lampsOn = true
        private set
    /** How far apart lamps are placed along streets with no mapped lamps, in metres (Settings > Street lamp spacing). */
    var lampGap = 40.0
        private set
    /** What the lamps are doing now, for Settings. */
    @Volatile var lampStage = "waiting for the map"
    /** Real lamps and lit / unlit roads from OpenStreetMap online (never in offline mode: OsmLamps checks). */
    @Volatile var lampsOsmOn = true
    fun lampInfo(): String = if (!lampsOn) "off" else lampStage

    /** Streets lamps stand along (not motorways or tunnels), from the last look at the roads on screen. */
    @Volatile private var lampRoads: List<List<Point>> = emptyList()
    @Volatile private var lampRoadsNew = false
    private var lampsCentre: LL? = null
    private var lampsAt = 0L
    @Volatile private var lampsBusy = false
    private val lampExec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "lamps").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    fun lampsEnabled(on: Boolean) {
        if (on == lampsOn) return
        lampsOn = on; lampsCentre = null
        main0.post { if (style != null) reinstall() }
    }

    fun applyLampGap(m: Double) {
        if (m == lampGap) return
        lampGap = m.coerceIn(10.0, 200.0); lampsCentre = null
        main0.post { refreshLamps(true) }
    }

    private fun installLamps(st: Style) {
        lampsCentre = null
        if (!lampsOn) { lampStage = "off"; return }
        Furniture.addModels(st)
        val night = litNight
        addS(st, GeoJsonSource.Builder("dl-lamps").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-lamp-pools").featureCollection(empty()).build())
        // at night, a soft warm pool of light on the road under each lamp (about 9 m across, growing with the map as you zoom)
        if (night) addL(st, CircleLayer("dl-lamp-pool", "dl-lamp-pools").inSlot(MID)
            .circleRadius(zoomed(2.0, 15 to 5.5, 22 to 5.5 * 128)).circleBlur(1.0).circleColor("#FFD9A0").circleOpacity(0.38)
            .circlePitchAlignment(CirclePitchAlignment.MAP).circlePitchScale(CirclePitchScale.MAP).circleEmissiveStrength(1.0).minZoom(15.0))
        // the lamp post itself, and its lamp (lit up at night)
        addL(st, Furniture.layer("dl-lamp-post", "dl-lamps", Furniture.LAMP, if (night) 0.3 else 0.0, 15.0, lampSize()).inSlot(MID))
        addL(st, Furniture.layer("dl-lamp-light", "dl-lamps", Furniture.LAMP_LENS, if (night) 1.0 else 0.15, 15.0, lampSize()).inSlot(MID))
        lampStage = "waiting for the map"
        main0.postDelayed({ refreshLamps(true) }, 300L)
    }

    /** Looks for the lamps round the middle of the screen again once you've moved on a bit (the work is done in the background). */
    private fun refreshLamps(force: Boolean = false) {
        if (lampsBusy) return
        val st = style ?: return
        val wantLamps = lampsOn && st.styleSourceExists("dl-lamps")
        val wantLights = signalsOn && st.styleSourceExists("dl-signals-near")
        if (!wantLamps && !wantLights) return
        val cam = try { mbMap.cameraState } catch (e: Throwable) { return }
        if (cam.zoom < 14.0) { if (wantLamps) lampStage = "zoom in closer to see them"; return }
        val c = LL(cam.center.latitude(), cam.center.longitude())
        val now = android.os.SystemClock.uptimeMillis()
        val last = lampsCentre
        val again = force || lampRoadsNew || last == null || Geo.dist(last, c) > 120.0
        if (!again || now - lampsAt < 1200L) return
        lampsBusy = true; lampsAt = now; lampsCentre = c; lampRoadsNew = false
        val roads = lampRoads; val gap = lampGap; val night = litNight
        try {
            lampExec.execute {
                try {
                    val (posts, pools, note) = if (wantLamps) placeLamps(c, roads, gap) else Triple(emptyList(), emptyList(), "off")
                    if (wantLamps) lampStage = note
                    val lights = if (wantLights) nearLights(c) else emptyList()
                    main0.post {
                        lampsBusy = false
                        if (destroyed) return@post
                        if (wantLamps) {
                            src("dl-lamps")?.featureCollection(FeatureCollection.fromFeatures(posts))
                            if (night) src("dl-lamp-pools")?.featureCollection(FeatureCollection.fromFeatures(pools))
                        }
                        if (wantLights) src("dl-signals-near")?.featureCollection(FeatureCollection.fromFeatures(lights))
                    }
                } catch (e: Throwable) {
                    lampStage = "error: ${e.javaClass.simpleName} ${e.message ?: ""}".take(120)
                    main0.post { lampsBusy = false }
                }
            }
        } catch (e: Throwable) { lampsBusy = false }
    }

    /**
     * Where the lamps go round [c] (about 900 m each way): the mapped ones, each with its arm turned over the nearest street; and,
     * if the area has hardly any mapped, lamps every [gap] metres along the streets on screen, at the left kerb, arm over the road.
     * The nearest 90 are kept. Returns the posts, the light pools under their lamps, and a line for Settings.
     */
    private fun placeLamps(c: LL, roads: List<List<Point>>, gap: Double): Triple<List<Feature>, List<Feature>, String> {
        val dLat = 0.008; val dLon = dLat / Math.cos(Math.toRadians(c.lat))
        val packed = if (OfflineRouter.hasLamps(ctx)) OfflineRouter.lamps(ctx, c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon, maxTiles = 64) else emptyList()
        // OpenStreetMap online for the same area (without an offline pack too): its lamps, and the roads it says are lit or unlit.
        // Not there yet: fetched in the background and the lamps placed again when it arrives
        val areas = if (lampsOsmOn) OsmLamps.around(ctx, c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon) {
            main0.post { lampsCentre = null; refreshLamps(true) }
        } else emptyList()
        val kx = 111320.0 * Math.cos(Math.toRadians(c.lat)); val ky = 111320.0
        // every mapped lamp once (the pack and the online list overlap), within the area
        val mapped = ArrayList<DoubleArray>()
        run {
            val seen = HashSet<Long>()
            fun add(q: DoubleArray) {
                if (Math.abs(q[0] - c.lat) > dLat || Math.abs(q[1] - c.lon) > dLon) return
                if (seen.add(Math.floor(q[0] * ky / 3.0).toLong() * 1000003L + Math.floor(q[1] * kx / 3.0).toLong())) mapped += q
            }
            packed.forEach { add(it) }
            for (a in areas) a.lamps.forEach { add(it) }
        }
        // spots: lat, lon, bearing of the arm
        val spots = ArrayList<DoubleArray>()
        val near = mapped.sortedBy { Geo.dist(LL(it[0], it[1]), c) }.take(90)
        for (q in near) spots += doubleArrayOf(q[0], q[1], armTowardsRoad(q[0], q[1], roads))
        // where lamps are mapped thoroughly (a city that's had its lamp list added), guessed ones only go on roads marked lit that
        // have no mapped lamp near; elsewhere on any road not marked unlit, away from the mapped ones
        val thorough = mapped.size >= 40
        val clearOf = if (thorough) 45.0 else 28.0
        // the mapped lamps and the lit / unlit roads in a grid of 30 m squares, to look up quickly
        val cellM = 30.0
        fun cell(lat: Double, lon: Double) = Math.floor(lat * ky / cellM).toLong() * 1000003L + Math.floor(lon * kx / cellM).toLong()
        val lampGrid = HashMap<Long, MutableList<DoubleArray>>()
        for (q in mapped) lampGrid.getOrPut(cell(q[0], q[1])) { ArrayList() } += q
        // each road piece as lit flag, then the two ends (x, y in metres)
        val litGrid = HashMap<Long, MutableList<DoubleArray>>()
        var litWays = 0
        for (a in areas) for ((lit, g) in a.ways) {
            litWays++
            var i = 0
            while (i + 3 < g.size) {
                val ax = g[i + 1] * kx; val ay = g[i] * ky; val bx = g[i + 3] * kx; val by = g[i + 2] * ky
                val piece = doubleArrayOf(if (lit) 1.0 else 0.0, ax, ay, bx, by)
                // filed under every square the piece passes through (pieces are short; long ones are sampled)
                val len = Math.hypot(bx - ax, by - ay)
                val n = Math.max(1, Math.ceil(len / cellM).toInt())
                val keys = HashSet<Long>()
                for (t in 0..n) keys += cell((g[i] + (g[i + 2] - g[i]) * t / n), (g[i + 1] + (g[i + 3] - g[i + 1]) * t / n))
                for (kk in keys) litGrid.getOrPut(kk) { ArrayList() } += piece
                i += 2
            }
        }
        fun cellsNear(grid: HashMap<Long, MutableList<DoubleArray>>, lat: Double, lon: Double): List<DoubleArray> {
            val ci = Math.floor(lat * ky / cellM).toLong(); val cj = Math.floor(lon * kx / cellM).toLong()
            val out = ArrayList<DoubleArray>()
            for (di in -1L..1L) for (dj in -1L..1L) grid[(ci + di) * 1000003L + (cj + dj)]?.let { out += it }
            return out
        }
        /** OpenStreetMap's word on the road at this point: true lit, false unlit, null not said (nearest marked road within 12 m). */
        fun litAt(lat: Double, lon: Double): Boolean? {
            val px = lon * kx; val py = lat * ky
            var best = 12.0; var res: Boolean? = null
            for (q in cellsNear(litGrid, lat, lon)) {
                val dx = q[3] - q[1]; val dy = q[4] - q[2]
                val l2 = dx * dx + dy * dy
                val t = if (l2 < 1e-6) 0.0 else (((px - q[1]) * dx + (py - q[2]) * dy) / l2).coerceIn(0.0, 1.0)
                val d = Math.hypot(px - (q[1] + dx * t), py - (q[2] + dy * t))
                if (d < best) { best = d; res = q[0] > 0.5 }
            }
            return res
        }
        fun mappedNear(lat: Double, lon: Double): Boolean = cellsNear(lampGrid, lat, lon).any { Geo.dist(LL(it[0], it[1]), LL(lat, lon)) < clearOf }
        // every street piece on screen in the same 30 m squares, so a placed lamp can be kept off the carriageway: at the left kerb
        // of one road it could stand on another (the other half of a dual carriageway, a side road at a junction), which showed as
        // lamps in the middle of the road (ChrisSantosGarage, #296)
        val roadGrid = HashMap<Long, MutableList<DoubleArray>>()
        for (line in roads) for (i in 0 until line.size - 1) {
            val a = line[i]; val b = line[i + 1]
            val piece = doubleArrayOf(a.longitude() * kx, a.latitude() * ky, b.longitude() * kx, b.latitude() * ky)
            val len = Math.hypot(piece[2] - piece[0], piece[3] - piece[1])
            val n = Math.max(1, Math.ceil(len / cellM).toInt())
            val keys = HashSet<Long>()
            for (t in 0..n) keys += cell(a.latitude() + (b.latitude() - a.latitude()) * t / n, a.longitude() + (b.longitude() - a.longitude()) * t / n)
            for (kk in keys) roadGrid.getOrPut(kk) { ArrayList() } += piece
        }
        /** How far [lat], [lon] is from the middle of the nearest street on screen (up to 30 m). */
        fun roadGap(lat: Double, lon: Double): Double {
            val px = lon * kx; val py = lat * ky
            var best = 30.0
            for (q in cellsNear(roadGrid, lat, lon)) {
                val dx = q[2] - q[0]; val dy = q[3] - q[1]
                val l2 = dx * dx + dy * dy
                val t = if (l2 < 1e-6) 0.0 else (((px - q[0]) * dx + (py - q[1]) * dy) / l2).coerceIn(0.0, 1.0)
                best = minOf(best, Math.hypot(px - (q[0] + dx * t), py - (q[1] + dy * t)))
            }
            return best
        }
        var skippedUnlit = 0
        if (roads.isNotEmpty()) {
            val taken = HashSet<Long>()
            val cell = Math.max(8.0, gap * 0.6)
            fun key(lat: Double, lon: Double) = Math.floor(lat * 111320.0 / cell).toLong() * 1000003L + Math.floor(lon * 111320.0 * Math.cos(Math.toRadians(lat)) / cell).toLong()
            for (q in spots) taken += key(q[0], q[1])
            for (line in roads) {
                if (line.size < 2) continue
                var carry = gap / 2
                for (i in 0 until line.size - 1) {
                    val a = line[i]; val b = line[i + 1]
                    val ax = a.longitude() * kx; val ay = a.latitude() * ky
                    val bx = b.longitude() * kx; val by = b.latitude() * ky
                    val len = Math.hypot(bx - ax, by - ay)
                    if (len < 0.5) continue
                    var d = carry
                    while (d <= len) {
                        val t = d / len
                        val lat = a.latitude() + (b.latitude() - a.latitude()) * t
                        val lon = a.longitude() + (b.longitude() - a.longitude()) * t
                        d += gap
                        val lit = litAt(lat, lon)
                        if (lit == false || (thorough && lit != true)) { skippedUnlit++; continue }
                        if (mappedNear(lat, lon)) continue
                        val dir = Math.toDegrees(Math.atan2(bx - ax, by - ay))
                        // at the left kerb, about 5.5 m from the middle of the road, arm reaching back over it
                        val at = Furniture.offset(LL(lat, lon), dir - 90.0, 5.5)
                        // (not where it would stand in another road: within 4.5 m of any street's middle, its own included)
                        if (roadGap(at.lat, at.lon) < 4.5) continue
                        if (taken.add(key(at.lat, at.lon)) && Geo.dist(at, c) < 900.0) spots += doubleArrayOf(at.lat, at.lon, dir + 90.0)
                    }
                    carry = d - len
                }
            }
        }
        val kept = spots.sortedBy { Geo.dist(LL(it[0], it[1]), c) }.take(90)
        val posts = kept.map { Furniture.point(it[0], it[1], it[2]) }
        // the lamp hangs about 2.5 m out along the arm
        val pools = kept.map { val h = Furniture.offset(LL(it[0], it[1]), it[2], 2.5); Feature.fromGeometry(Point.fromLngLat(h.lon, h.lat)) }
        val realShown = minOf(kept.size, near.size)
        val placedOnRoads = kept.size - realShown
        val litNote = if (litWays > 0) ", $litWays roads marked lit or unlit" + (if (skippedUnlit > 0) " (dark ones left dark)" else "") else ""
        val note = when {
            kept.isEmpty() && roads.isEmpty() -> "no streets read from the map here yet"
            kept.isEmpty() -> "none here" + litNote
            mapped.isNotEmpty() -> "${kept.size} shown: $realShown mapped in OpenStreetMap, $placedOnRoads placed along the streets$litNote"
            else -> "${kept.size} placed along the streets (none mapped here)$litNote"
        }
        return Triple(posts, pools, note)
    }

    /**
     * The traffic lights round [c] from the offline pack's list (the ones not already shown on your route): each at the left kerb of
     * the road it stands on, facing the traffic coming up to it. The nearest 60.
     */
    private fun nearLights(c: LL): List<Feature> {
        if (!OfflineRouter.hasSignals(ctx)) return emptyList()
        val dLat = 0.008; val dLon = dLat / Math.cos(Math.toRadians(c.lat))
        val pts = OfflineRouter.lamps(ctx, c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon, file = "signals.tsv", maxTiles = 64)
        val onRoute = signalKept
        val out = ArrayList<Feature>()
        for (q in pts.sortedBy { Geo.dist(LL(it[0], it[1]), c) }) {
            val p = LL(q[0], q[1])
            if (onRoute.any { Geo.dist(it, p) < 30.0 }) continue
            // (the road isn't known when the map hasn't shown it close up yet: the light then stands where it's mapped, facing north)
            val dir = roadDirAt(p)
            val at = if (dir != null) Furniture.offset(p, dir - 90.0, 4.5) else p
            out += Furniture.point(at.lat, at.lon, dir ?: 0.0)
            if (out.size >= 60) break
        }
        return out
    }

    /** The direction of the road at [p] (within 15 m), from the roads last read off the map, or null. */
    private fun roadDirAt(p: LL): Double? {
        val k = Math.cos(Math.toRadians(p.lat)) * 111320.0; val k2 = 110574.0
        var bd = 15.0; var dir: Double? = null
        for (rp in roadInfo) {
            val pts = rp.pts
            for (i in 0 until pts.size - 1) {
                val ax = (pts[i].longitude() - p.lon) * k; val ay = (pts[i].latitude() - p.lat) * k2
                val bx = (pts[i + 1].longitude() - p.lon) * k; val by = (pts[i + 1].latitude() - p.lat) * k2
                if ((ax > bd && bx > bd) || (ax < -bd && bx < -bd) || (ay > bd && by > bd) || (ay < -bd && by < -bd)) continue
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                if (l2 < 0.01) continue
                val t = (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
                val d = Math.hypot(ax + dx * t, ay + dy * t)
                if (d < bd) { bd = d; dir = Math.toDegrees(Math.atan2(dx, dy)) }
            }
        }
        return dir
    }

    /** The way a mapped lamp's arm points: towards the nearest street within 25 m (else north). */
    private fun armTowardsRoad(lat: Double, lon: Double, roads: List<List<Point>>): Double {
        val k = Math.cos(Math.toRadians(lat)) * 111320.0; val k2 = 111320.0
        var bd = 25.0; var bx = 0.0; var by = 0.0; var found = false
        for (pts in roads) for (i in 0 until pts.size - 1) {
            val ax = (pts[i].longitude() - lon) * k; val ay = (pts[i].latitude() - lat) * k2
            val cx = (pts[i + 1].longitude() - lon) * k; val cy = (pts[i + 1].latitude() - lat) * k2
            if ((ax > bd && cx > bd) || (ax < -bd && cx < -bd) || (ay > bd && cy > bd) || (ay < -bd && cy < -bd)) continue
            val dx = cx - ax; val dy = cy - ay
            val l2 = dx * dx + dy * dy
            if (l2 < 0.01) continue
            val t = (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
            val px = ax + dx * t; val py = ay + dy * t
            val d = Math.hypot(px, py)
            if (d < bd && d > 0.3) { bd = d; bx = px; by = py; found = true }
        }
        return if (found) Math.toDegrees(Math.atan2(bx, by)) else 0.0
    }






    // ---- traffic lights (OpenStreetMap), fetched along the whole route (ahead first) so every light on it shows at any zoom ----
    var signalsOn = true
    @Volatile private var signalPts: List<LL> = emptyList()
    @Volatile private var signalErr = ""
    @Volatile private var signalStage = "not started"
    @Volatile private var signalToken = 0
    @Volatile private var signalBusy = false
    private var sigRoute: List<LL>? = null
    /** Every light found so far (kept across reroutes, so a new route only fetches the roads not already covered). */
    private val sigAll = LinkedHashMap<Long, LL>()
    /** Small grid cells (about 20 m) of road that have already been searched for lights. */
    private val sigCovered = HashSet<Long>()
    private val sigLock = Any()
    /** Speed cameras found along the route by the same search (the engine turns them into alerts and map badges). */
    private val camAll = LinkedHashMap<Long, LL>()
    /** Gets every speed camera found along routes so far, each time more arrive. */
    var onRouteCameras: ((List<LL>) -> Unit)? = null

    /** One line for the settings page: how many lights are known and whether the last fetch worked. */
    fun signalInfo(): String {
        if (!signalsOn) return "off"
        if (main.size < 2) return "no route (lights only show on your route)"
        val onR = signalShown
        return "$onR on the drawn route, ${signalPts.size} found along it" + (if (signalErr.isNotEmpty()) ", last error: $signalErr" else "") + ", status: $signalStage"
    }

    /** A short version for the performance meter. */
    fun signalShort(): String {
        if (!signalsOn) return "off"
        if (main.size < 2) return "no route"
        val onR = signalShown
        return "$onR shown/${signalPts.size} found" + (if (signalErr.isNotEmpty()) " ($signalErr)" else "") + " · $signalStage"
    }

    fun signalsEnabled(on: Boolean) {
        signalsOn = on
        // (the search along the route carries on either way: it also finds the speed cameras)
        pushSignals()
        if (!on) try { src("dl-signals-near")?.featureCollection(empty()) } catch (e: Throwable) { }
        else main0.post { lampsCentre = null; refreshLamps(true) }
    }

    /**
     * A traffic light the way Google Maps draws them on your route: a small, upright, dark-grey light with a crisp white outline,
     * a soft shadow and three little lamps (red, amber, green), sitting right on the road. Drawn at 3x and shown small.
     */
    private fun signalBitmap(): Bitmap {
        val s = 3f
        val bmp = Bitmap.createBitmap((30 * s).toInt(), (52 * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        val outer = RectF(4f, 3f, 26f, 47f)
        // shadow just below, then the white outline, then the dark body
        c.drawRoundRect(RectF(outer.left, outer.top + 2f, outer.right, outer.bottom + 2f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 })
        c.drawRoundRect(outer, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        val inner = RectF(outer.left + 2.2f, outer.top + 2.2f, outer.right - 2.2f, outer.bottom - 2.2f)
        c.drawRoundRect(inner, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3C4043.toInt() })
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = (outer.left + outer.right) / 2f
        val r = 4.2f
        dot.color = 0xFFEA4335.toInt(); c.drawCircle(cx, 13.5f, r, dot)
        dot.color = 0xFFFBBC04.toInt(); c.drawCircle(cx, 25f, r, dot)
        dot.color = 0xFF34A853.toInt(); c.drawCircle(cx, 36.5f, r, dot)
        return bmp
    }

    // only the lights on the road you are following are drawn: the route is turned into a grid of small cells, each knowing which
    // bits of the route pass through it, so finding the route near a light is a quick look at its own and neighbouring cells
    private var cellsFor: List<LL>? = null
    private val routeCells = HashMap<Long, ArrayList<Int>>()
    private fun cellKey(lat: Double, lon: Double) = Math.floor(lat / 0.0002).toLong() * 1000003L + Math.floor(lon / 0.0003).toLong()

    private fun rebuildCells(r: List<LL>) {
        routeCells.clear()
        for (i in 0 until r.size - 1) {
            val a = r[i]; val b = r[i + 1]
            val steps = (Geo.dist(a, b) / 15.0).toInt()
            var lastK = Long.MIN_VALUE
            for (k in 0..steps + 1) {
                val t = k / (steps + 1.0)
                val key = cellKey(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                if (key == lastK) continue
                lastK = key
                val l = routeCells.getOrPut(key) { ArrayList(2) }
                if (l.isEmpty() || l[l.size - 1] != i) l.add(i)
            }
        }
        cellsFor = r
    }

    private fun onRoute(p: LL): Boolean {
        val k = cellKey(p.lat, p.lon)
        for (dy in -1..1) for (dx in -1..1) if (routeCells.containsKey(k + dy * 1000003L + dx)) return true
        return false
    }

    private fun signalFeatures(): FeatureCollection {
        val r = main
        signalModels = empty(); signalKept = emptyList()
        if (r.size < 2) return empty()
        if (cellsFor !== r) rebuildCells(r)
        // only lights that stand on the route's own road (within ~10 m of the line), and at each junction only the first one you reach:
        // a junction usually has a light on every arm, but only the one on your approach matters
        val near = signalPts.filter { onRoute(it) }
        if (near.isEmpty()) return empty()
        val cum = DoubleArray(r.size)
        for (i in 1 until r.size) cum[i] = cum[i - 1] + Geo.dist(r[i - 1], r[i])
        val placed = ArrayList<Pair<Double, LL>>()
        val segs = HashSet<Int>()
        signalHeading.clear()
        for (p in near) {
            segs.clear()
            val k = cellKey(p.lat, p.lon)
            for (dy in -1..1) for (dx in -1..1) routeCells[k + dy * 1000003L + dx]?.let { segs.addAll(it) }
            val kx = Math.cos(Math.toRadians(p.lat)) * 111320.0; val ky = 110574.0
            var bd = 10.0; var bo = -1.0; var bi = -1
            for (i in segs) {
                val ax = (r[i].lon - p.lon) * kx; val ay = (r[i].lat - p.lat) * ky
                val bx = (r[i + 1].lon - p.lon) * kx; val by = (r[i + 1].lat - p.lat) * ky
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                val t = if (l2 > 0) (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0) else 0.0
                val d = Math.hypot(ax + dx * t, ay + dy * t)
                if (d < bd) { bd = d; bo = cum[i] + (cum[i + 1] - cum[i]) * t; bi = i }
            }
            if (bo >= 0) { placed += bo to p; signalHeading[p] = Geo.bearing(r[bi], r[bi + 1]) }
        }
        placed.sortBy { it.first }
        val keep = ArrayList<Feature>()
        val keepPts = ArrayList<LL>()
        val models = ArrayList<Feature>()
        var lastOff = -1e9
        for ((off, p) in placed) {
            if (off - lastOff < 70.0) { lastOff = off; continue }   // same junction: you have already passed its first light
            lastOff = off
            keep += Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))
            keepPts += p
            // the 3D light: at the left kerb, facing you as you come up to it
            val hd = signalHeading[p]
            if (hd != null && models.size < 400) { val at = Furniture.offset(p, hd - 90.0, 4.5); models += Furniture.point(at.lat, at.lon, hd) }
            if (keep.size >= 3000) break
        }
        signalModels = FeatureCollection.fromFeatures(models)
        signalKept = keepPts
        return FeatureCollection.fromFeatures(keep)
    }

    /** How many lights the last update put on the map (for the settings page and the performance meter, without working it out again). */
    @Volatile private var signalShown = 0

    /** Working out which lights sit on the route is done on its own thread (a long route can have thousands to check). */
    private val signalExec = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "lights").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    @Volatile private var signalJob = false
    @Volatile private var signalDirty = false

    private fun pushSignals() {
        if (destroyed) return
        // one at a time; changes that arrive meanwhile are folded into one more pass when it finishes
        if (signalJob) { signalDirty = true; return }
        signalJob = true; signalDirty = false
        val on = signalsOn
        try {
            signalExec.execute {
                val fc = try { if (on) signalFeatures() else empty() } catch (e: Throwable) { empty() }
                main0.post {
                    if (destroyed) { signalJob = false; return@post }
                    try {
                        signalShown = fc.features()?.size ?: 0
                        src("dl-signals")?.featureCollection(fc)
                        src("dl-signals-m")?.featureCollection(if (on) signalModels else empty())
                        try { onRouteSignals?.invoke(signalKept) } catch (e: Throwable) { }
                    } catch (e: Throwable) { }
                    signalJob = false
                    if (signalDirty) pushSignals()
                }
            }
        } catch (e: Throwable) { signalJob = false }
    }

    /** Called as the map moves: only restarts the search along the route if an earlier one gave up (no signal). */
    private fun refreshSignals() {
        if (signalBusy) return
        val r = sigRoute ?: return
        if (signalErr.isNotEmpty() && android.os.SystemClock.uptimeMillis() - sigFailAt > 30_000L) { sigRoute = null; loadSignalsAlong(r) }
    }
    @Volatile private var sigFailAt = 0L

    /**
     * Finds every traffic light along [route] in the background: the route is cut into stretches of about 20 km (the first one
     * starting where you are), and each is asked for in one go as a corridor 30 m either side of the road. Roads already searched
     * (from an earlier route) are skipped, so a reroute usually needs little or nothing new.
     */
    private fun loadSignalsAlong(route: List<LL>) {
        if (route.size < 2 || route === sigRoute) return
        sigRoute = route
        val token = ++signalToken
        signalBusy = true; signalErr = ""; signalStage = "finding lights along the route…"
        Thread {
            try {
                // points every ~50 m along the route
                val pts = ArrayList<LL>()
                pts += route[0]
                var acc = 0.0
                for (i in 1 until route.size) {
                    acc += Geo.dist(route[i - 1], route[i])
                    if (acc >= 50.0 || i == route.size - 1) { pts += route[i]; acc = 0.0 }
                }
                fun cellsAlong(a: LL, b: LL, into: MutableCollection<Long>) {
                    val steps = (Geo.dist(a, b) / 15.0).toInt()
                    for (k in 0..steps + 1) { val t = k / (steps + 1.0); into += cellKey(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) }
                }
                // stretches of route not searched yet, at most 400 points (~20 km) each
                val chunks = ArrayList<List<LL>>()
                var cur = ArrayList<LL>()
                for (i in pts.indices) {
                    val covered = synchronized(sigLock) { sigCovered.contains(cellKey(pts[i].lat, pts[i].lon)) }
                    if (covered) { if (cur.size >= 2) chunks += cur; cur = ArrayList(); continue }
                    if (cur.isEmpty() && i > 0) cur += pts[i - 1]
                    cur += pts[i]
                    // about 7.5 km per request: long enough to be few requests, short enough for a busy server to manage
                    if (cur.size >= 150) { chunks += cur; cur = arrayListOf(pts[i]) }
                }
                if (cur.size >= 2) chunks += cur
                var usedPack = false
                var n = 0
                // (a stretch that couldn't be searched is tried again later; a later stretch that worked used to clear the error, so
                // the failed one, and its cameras, were never looked for again)
                var anyFailed = false
                for (ch in chunks) {
                    if (token != signalToken) return@Thread
                    n++
                    signalStage = "finding lights along the route ($n of ${chunks.size})…"
                    val cells = HashSet<Long>()
                    for (i in 0 until ch.size - 1) cellsAlong(ch[i], ch[i + 1], cells)
                    var found: List<LL>? = null
                    var foundCams: List<LL> = emptyList()
                    var camsMissed = false
                    // a downloaded area covering this stretch: its traffic lights and cameras, straight from the files
                    val area = OfflineRouter.areaFor(ctx, ch.first(), ch.last()) ?: OfflineRouter.areaFor(ctx, ch[ch.size / 2])
                    val pd = area?.let { OfflineRouter.packDir(ctx, it) }
                    var s0 = 90.0; var n0 = -90.0; var w0 = 180.0; var e0 = -180.0
                    for (p in ch) { s0 = minOf(s0, p.lat); n0 = maxOf(n0, p.lat); w0 = minOf(w0, p.lon); e0 = maxOf(e0, p.lon) }
                    fun fromPack(name: String): List<LL>? {
                        if (pd == null || !java.io.File(pd, name).let { it.exists() && it.length() > 8 }) return null
                        return OfflineRouter.lamps(ctx, s0 - 0.0005, w0 - 0.0005, n0 + 0.0005, e0 + 0.0005, name, pd, 5000).map { a ->
                            LL(a[0], a[1]).also { c -> if (a.size > 2) Overpass.camDir[Overpass.camKey(c)] = a[2] }
                        }
                    }
                    val packLights = fromPack("signals.tsv")
                    val packCams = fromPack("cameras.tsv")
                    if (packLights != null || packCams != null) usedPack = true
                    if (packLights != null && packCams != null) { found = packLights; foundCams = packCams }
                    else for (attempt in 0 until 5) {
                        if (token != signalToken) return@Thread
                        try {
                            val online = Overpass.routeFeatures(ch, 30)
                            found = packLights ?: online.first
                            foundCams = packCams ?: online.second
                            break
                        } catch (e: Throwable) {
                            signalErr = (e.message ?: e.javaClass.simpleName).take(80)
                            // with the lights from the pack, only the cameras were missing: carry on without them (searched again later)
                            if (packLights != null) { found = packLights; camsMissed = true; break }
                            // a busy server: back off for longer each time (5, 10, 20, 30 s)
                            Thread.sleep(longArrayOf(5000L, 10000L, 20000L, 30000L, 30000L)[attempt])
                        }
                    }
                    if (found == null) { anyFailed = true; sigFailAt = android.os.SystemClock.uptimeMillis(); continue }
                    if (camsMissed) { anyFailed = true; sigFailAt = android.os.SystemClock.uptimeMillis() }
                    // keep only lights near this stretch (the offline search is a box, and the corridor search is a little generous)
                    val keep = found.filter { p ->
                        val k = cellKey(p.lat, p.lon)
                        var hit = false
                        for (dy in -1..1) for (dx in -1..1) if (cells.contains(k + dy * 1000003L + dx)) hit = true
                        hit
                    }
                    val snap: List<LL>
                    val camSnap: List<LL>
                    synchronized(sigLock) {
                        for (p in keep) sigAll[Math.round(p.lat * 1e6) * 1000003L + Math.round(p.lon * 1e6)] = p
                        for (p in foundCams) camAll[Math.round(p.lat * 1e6) * 1000003L + Math.round(p.lon * 1e6)] = p
                        while (camAll.size > 5000) camAll.remove(camAll.keys.first())
                        camSnap = ArrayList(camAll.values)
                        while (sigAll.size > 20000) sigAll.remove(sigAll.keys.first())
                        // (when the cameras couldn't be fetched, the stretch is searched again next time)
                        if (!camsMissed) sigCovered.addAll(cells)
                        if (sigCovered.size > 400_000) sigCovered.clear()
                        snap = ArrayList(sigAll.values)
                    }
                    if (token != signalToken) return@Thread
                    if (!anyFailed) signalErr = ""
                    main0.post {
                        if (token == signalToken) { signalPts = snap; pushSignals() }
                        try { onRouteCameras?.invoke(camSnap) } catch (e: Throwable) { }
                    }
                }
                if (anyFailed && signalErr.isEmpty()) signalErr = "part of the route couldn't be searched"
                val snap = synchronized(sigLock) { ArrayList(sigAll.values) }
                main0.post {
                    if (token == signalToken) {
                        signalPts = snap; pushSignals(); signalBusy = false
                        signalStage = if (signalErr.isEmpty()) "route searched (${if (usedPack) "downloaded area" else Overpass.lastNote})" else "some of the route couldn't be searched, will retry"
                    }
                }
            } catch (e: Throwable) {
                signalErr = "internal: " + (e.message ?: e.javaClass.simpleName).take(60); sigFailAt = android.os.SystemClock.uptimeMillis()
                main0.post { if (token == signalToken) signalBusy = false }
            }
        }.apply { priority = Thread.MIN_PRIORITY; isDaemon = true }.start()
    }








    /** Waze-style arrow: fat rounded chevron, blue fill, thick white outline and a soft shadow. Points up; rotated by heading. */
    private fun arrowBitmap(): Bitmap {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path = Path().apply {
            moveTo(64f, 14f)
            quadTo(70f, 14f, 74f, 22f)
            lineTo(100f, 92f)
            quadTo(104f, 104f, 92f, 100f)
            lineTo(64f, 84f)
            lineTo(36f, 100f)
            quadTo(24f, 104f, 28f, 92f)
            lineTo(54f, 22f)
            quadTo(58f, 14f, 64f, 14f)
            close()
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x00000000; style = Paint.Style.FILL; setShadowLayer(10f, 0f, 5f, 0x80000000.toInt())
        }
        c.drawPath(path, shadow)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 12f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        c.drawPath(path, edge)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = android.graphics.LinearGradient(0f, 14f, 0f, 104f, 0xFF3AA0FF.toInt(), 0xFF1263E0.toInt(), android.graphics.Shader.TileMode.CLAMP)
        }
        c.drawPath(path, fill)
        return bmp
    }

    private fun empty() = FeatureCollection.fromFeatures(ArrayList<Feature>())

    // ---- the app's own layers ----
    /** Middle of Standard: above roads, below 3D buildings and labels. Top: above shop labels, below place names. */
    private val MID = "middle"
    private val TOP = "top"

    // layers changed often (every frame or on a timer) are kept, so they don't have to be looked up each time
    private var flowLayer: LineLayer? = null
    private var glowLayer: LineLayer? = null
    private var lanesOnLayer: SymbolLayer? = null

    private fun install(st: Style) {
        val im = theme == "immersive"
        val dayLook = !nightStyle
        bubImgs.clear(); laneImgs.clear()
        // the classic map in the Horizon colours
        if (!slotsOn && (hzFamily() || recolourBase)) try { recolourClassic(st) } catch (e: Throwable) { }
        // Standard's newer settings are set once it's loaded (so a style that doesn't know one can't stop the map loading)
        if (!satellite) {
            setBase("show3dTrees", Value.valueOf(treesOn)); setBase("showLandmarkIcons", Value.valueOf(!lite))
            // lane markings, turn arrows, stop lines and 3D overpasses where Mapbox has them (Full 3D only)
            setBase("showHdRoads", Value.valueOf(hdRoadsOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            // detailed building fronts, landmark buildings, footpaths (Settings > Map)
            setBase("show3dFacades", Value.valueOf(facadesOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("show3dLandmarks", Value.valueOf(landmarksOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("showPedestrianRoads", Value.valueOf(pathsOn))
            // your destination's building is lit up in the app's blue as you get close
            setBase("colorBuildingHighlight", Value.valueOf("#7FB3FF"))
        }
        // live traffic on the other roads (Mapbox Traffic): slow, heavy and stopped traffic coloured, and free-flowing roads in
        // green too unless that's switched off (Settings > Map), under the route
        addS(st, VectorSource.Builder("dl-traffic-src").url("mapbox://mapbox.mapbox-traffic-v1").build())
        addL(st, LineLayer("dl-traffic", "dl-traffic-src").sourceLayer("traffic").inSlot(MID)
            .filter(trafficFilter())
            .lineColor(Expression.fromRaw("[\"match\",[\"get\",\"congestion\"],\"low\",\"#1EA362\",\"moderate\",\"#F9AB00\",\"heavy\",\"#E8453C\",\"severe\",\"#A51D1D\",\"#F9AB00\"]"))
            .lineWidth(zoomed(1.5, 8 to 1.0, 13 to 2.5, 16 to 4.5, 19 to 9.0))
            .lineOffset(zoomed(1.5, 10 to 0.5, 16 to 2.5, 19 to 5.0))
            .lineOpacity(0.85).lineEmissiveStrength(1.0)
            .lineCap(LineCap.ROUND).lineJoin(LineJoin.ROUND)
            .minZoom(8.0)
            .visibility(vis(trafficOn && !(routing && hideTraffic))))
        addS(st, GeoJsonSource.Builder("dl-alts").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-live-alts").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-live-bub").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-route").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-flow").featureCollection(empty()).lineMetrics(true).build())
        addS(st, GeoJsonSource.Builder("dl-done").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-dest").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-flags").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-chain").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-chain-line").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-tap").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-jam").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-hazards").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-me").featureCollection(empty()).build())
        // the arrow at the next turn, drawn on the route; no simplification, so small shapes keep their corners
        addS(st, GeoJsonSource.Builder("dl-turn").featureCollection(empty()).tolerance(0.1).build())
        addS(st, GeoJsonSource.Builder("dl-turn-head").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-lanes").featureCollection(empty()).build())
        // every lane arrow added now in one go (they're small), so nothing new is added to the map as you come up to a junction
        try {
            for (d in listOf("STRAIGHT", "SLIGHT_LEFT", "LEFT", "SHARP_LEFT", "LEFT_U_TURN", "SLIGHT_RIGHT", "RIGHT", "SHARP_RIGHT", "RIGHT_U_TURN"))
                for (on in listOf(true, false)) { val n = "ln-$d-${if (on) 1 else 0}"; addImg(st, n, laneBitmap(d, on)); laneImgs.add(n) }
        } catch (e: Throwable) { laneImgs.clear() }
        // lane bands: each lane drawn as a strip along the road before a junction, the lane(s) you need lit, the others dimmed, with dashed dividers
        addS(st, GeoJsonSource.Builder("dl-lane-bands").featureCollection(empty()).build())
        addS(st, GeoJsonSource.Builder("dl-lane-divs").featureCollection(empty()).build())
        fun ln(id: String, src: String) = LineLayer(id, src).inSlot(MID).lineEmissiveStrength(1.0).lineCap(LineCap.ROUND).lineJoin(LineJoin.ROUND)
        // average speed zones on the route: the stretch between the cameras shaded yellow, with dashed yellow edges (under the route)
        addS(st, GeoJsonSource.Builder("dl-avg").featureCollection(FeatureCollection.fromFeatures(avgZoneLines.filter { it.size >= 2 }.map { line(it) })).build())
        val bandW = zoomed(2.0, 12 to 8.0, 16 to 30.0, 20 to 480.0)
        addL(st, LineLayer("dl-avg-band", "dl-avg").inSlot(MID).lineEmissiveStrength(1.0).lineCap(LineCap.BUTT).lineJoin(LineJoin.ROUND)
            .lineColor("#FFCC00").lineOpacity(if (nightStyle) 0.30 else 0.38).lineWidth(bandW))
        for ((id, side) in listOf("dl-avg-edge-l" to -1.0, "dl-avg-edge-r" to 1.0)) addL(st, LineLayer(id, "dl-avg").inSlot(MID).lineEmissiveStrength(1.0)
            .lineCap(LineCap.BUTT).lineJoin(LineJoin.ROUND).lineColor("#FFCC00").lineOpacity(0.95)
            .lineWidth(zoomed(2.0, 12 to 1.2, 16 to 2.6, 20 to 8.0)).lineDasharray(listOf(3.0, 2.2))
            .lineOffset(zoomed(2.0, 12 to 4.0 * side, 16 to 15.0 * side, 20 to 240.0 * side)))
        // zebra crossings on the route: white stripes painted across the road, with Belisha beacons at each side (close in only)
        addImg(st, "dl-zebra", zebraBitmap()); addImg(st, "dl-beacon", beaconBitmap())
        addS(st, GeoJsonSource.Builder("dl-zebras").featureCollection(FeatureCollection.fromFeatures(zebraFeats())).build())
        addS(st, GeoJsonSource.Builder("dl-beacons").featureCollection(FeatureCollection.fromFeatures(beaconFeats())).build())
        addL(st, SymbolLayer("dl-zebra", "dl-zebras").inSlot(MID).iconImage("dl-zebra").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.MAP).iconRotationAlignment(IconRotationAlignment.MAP).iconRotate(Expression.get("r"))
            // (sized to about 9 m across the road: the picture is 200 px wide, shown at the screen's density)
            .iconSize(run { val d = mapView.resources.displayMetrics.density.toDouble(); zoomed(2.0, 16 to 0.06 * d, 20 to 0.96 * d) })
            .iconOpacity(0.92).iconEmissiveStrength(0.8).minZoom(16.0))
        // stop signs on the route: a small badge that stands up at the junction (from zoom 14)
        addImg(st, "dl-stop", stopBitmap())
        addS(st, GeoJsonSource.Builder("dl-stops").featureCollection(FeatureCollection.fromFeatures(stopFeats())).build())
        addL(st, SymbolLayer("dl-stop", "dl-stops").inSlot(TOP).iconImage("dl-stop").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconAnchor(IconAnchor.BOTTOM).iconPitchAlignment(IconPitchAlignment.VIEWPORT)
            .iconSize(zoomed(1.5, 14 to 0.35, 17 to 0.55, 20 to 0.8)).iconEmissiveStrength(1.0).minZoom(14.0))
        addL(st, SymbolLayer("dl-beacon", "dl-beacons").inSlot(TOP).iconImage("dl-beacon").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconAnchor(IconAnchor.BOTTOM).iconPitchAlignment(IconPitchAlignment.VIEWPORT)
            .iconSize(zoomed(1.6, 16 to 0.32, 20 to 0.9)).iconEmissiveStrength(1.0).minZoom(16.0))
        // a GPX track you're looking at or driving (GitHub #153): pink, under the routes, with its start (green) and end (red)
        addS(st, GeoJsonSource.Builder("dl-track").featureCollection(trackFeatures()).build())
        addL(st, ln("dl-track-line", "dl-track").filter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
            .lineColor("#D4537E").lineWidth(zoomed(1.5, 8 to 3.0, 14 to 6.0, 18 to 12.0)).lineOpacity(if (trackFaint) 0.45 else 0.9))
        addL(st, CircleLayer("dl-track-ends", "dl-track").inSlot(MID).filter(Expression.eq(Expression.geometryType(), Expression.literal("Point")))
            .circleRadius(7.0).circleStrokeWidth(2.0).circleStrokeColor("#FFFFFF").circleEmissiveStrength(1.0)
            .circleColor(Expression.fromRaw("[\"case\",[\"==\",[\"get\",\"end\"],1],\"#E24B4A\",\"#1D9E75\"]")))
        addL(st, ln("dl-alts-line", "dl-alts").lineColor(prefs.s(PreferenceKeys.ALT_ROUTE_COLOUR, "#C4D9E6")).lineWidth(if (im) 8.0 else 7.0).lineOpacity(0.92))
        // other ways to go while driving: grey, under the route
        addL(st, ln("dl-live-alts-line", "dl-live-alts").lineColor(prefs.s(PreferenceKeys.ALT_ROUTE_COLOUR, "#C4D9E6")).lineWidth(routeW(0.75f)).lineOpacity(0.92))
        // route ribbon: a soft shadow just below the line, so it looks lifted off the road (Full 3D, Settings > Route ribbon)
        if (ribbonOn && !lite) addL(st, ln("dl-route-shadow", "dl-route").lineColor("#000000").lineOpacity(if (dayLook) 0.22 else 0.35)
            .lineWidth(routeW(if (im) 1.2f else 1.1f)).lineBlur(5.0).lineTranslate(listOf(0.0, 5.0))
            .lineTranslateAnchor(com.mapbox.maps.extension.style.layers.properties.generated.LineTranslateAnchor.VIEWPORT))
        // the wide blurred glow under the route is one of the most expensive things to draw; the light map leaves it out
        val glow = ln("dl-route-glow", "dl-route").lineColor(sevColor(im, dayLook)).lineWidth(routeW(if (im) 1.45f else 1.3f)).lineBlur(4.0)
            .lineOpacity(if (im) 0.62 else 0.4).visibility(vis(!lite))
        addL(st, glow); glowLayer = glow
        // by day a light blue edge, but deep enough to show against the light map
        addL(st, ln("dl-route-casing", "dl-route").lineColor(if (dayLook) "#8AB6F0" else if (im) "#D4F4FF" else "#eaf2ff")
            .lineWidth(routeW(if (im) 1.5f else 1.25f)).lineOpacity(if (dayLook) 1.0 else if (im) 0.95 else 0.92))
        addL(st, ln("dl-route-line", "dl-route").lineColor(sevColor(im, dayLook)).lineWidth(routeW(if (im) 1.0f else 0.85f)))
        // ...and the route showing faintly through 3D buildings that stand in front of it: a see-through copy drawn above them
        if (ribbonOn && slotsOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail)) addL(st, LineLayer("dl-route-xray", "dl-route").inSlot(TOP).lineEmissiveStrength(1.0)
            .lineCap(LineCap.ROUND).lineJoin(LineJoin.ROUND).lineColor(sevColor(im, dayLook)).lineWidth(routeW(0.7f)).lineOpacity(0.28))
        // tunnels on the route: the line turns dashed and see-through there (pale dashes over it)
        addS(st, GeoJsonSource.Builder("dl-tunnels").featureCollection(FeatureCollection.fromFeatures(tunnelLines.filter { it.size >= 2 }.map { line(it) })).build())
        addL(st, ln("dl-tunnel-dash", "dl-tunnels").lineCap(LineCap.BUTT).lineColor(if (dayLook) "#EEF2F7" else "#2B3150")
            .lineWidth(routeW(if (im) 1.55f else 1.3f)).lineOpacity(0.6).lineDasharray(listOf(0.9, 1.1)))
        // sharp bends: the route goes yellow with black chevrons through them, like the warning sign (from zoom 12)
        addImg(st, "dl-bend-chev", bendChevronBitmap())
        addS(st, GeoJsonSource.Builder("dl-bends").featureCollection(FeatureCollection.fromFeatures(bendLines.filter { it.size >= 2 }.map { line(it) })).build())
        addL(st, ln("dl-bend-line", "dl-bends").lineCap(LineCap.BUTT).linePattern("dl-bend-chev").lineWidth(routeW(if (im) 1.0f else 0.85f)).minZoom(12.0))
        // a band of light that runs along the route towards the destination
        val flow = ln("dl-flow-line", "dl-flow").lineWidth(routeW(if (im) 0.6f else 0.5f)).lineBlur(3.0)
            .lineGradient(flowGradient(0.5f)).visibility(vis(flowOn))
        addL(st, flow); flowLayer = flow
        // small direction chevrons along the route line (only close in, where they help)
        addImg(st, "dl-chev", chevronBitmap())
        addL(st, SymbolLayer("dl-route-chev", "dl-route").inSlot(MID)
            .symbolPlacement(SymbolPlacement.LINE).iconImage("dl-chev").symbolSpacing(110.0)
            .iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconRotationAlignment(IconRotationAlignment.MAP).iconPitchAlignment(IconPitchAlignment.MAP)
            .iconOpacity(0.75).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 15 to 0.28, 18 to 0.5, 20 to 0.7))
            .minZoom(15.0).visibility(vis(chevOn)))
        // the turn arrow: (by day) a soft dark halo so it stands out on the light map, a dark edge, a white shaft and a white head
        if (dayLook) addL(st, ln("dl-turn-soft", "dl-turn").lineColor("#16467A").lineWidth(routeW(1.62f)).lineBlur(routeW(0.3f)).lineOpacity(0.35).minZoom(13.0))
        addL(st, ln("dl-turn-case", "dl-turn").lineColor("#16467A").lineWidth(routeW(1.28f)).minZoom(13.0))
        addL(st, ln("dl-turn-line", "dl-turn").lineColor("#FFFFFF").lineWidth(routeW(1.08f)).minZoom(13.0))
        addImg(st, "dl-turn-head-img", turnHeadBitmap(dayLook))
        addL(st, SymbolLayer("dl-turn-head", "dl-turn-head").inSlot(MID)
            .iconImage("dl-turn-head-img").iconRotate(Expression.get("b"))
            .iconRotationAlignment(IconRotationAlignment.MAP).iconPitchAlignment(IconPitchAlignment.MAP)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            // the head's base sits exactly on the end of the shaft, and grows with the zoom the same way the line does
            .iconAnchor(IconAnchor.BOTTOM)
            .iconSize(zoomed(1.7, 10 to headSize(3f), 13 to headSize(6.5f), 15 to headSize(10f), 17 to headSize(16f), 19 to headSize(24f)))
            .minZoom(13.0))
        // the part of the route you have already driven turns grey, drawn over the blue
        addL(st, ln("dl-done-casing", "dl-done").lineColor(if (im) "#B4B9CE" else "#C3C8D8").lineWidth(routeW(if (im) 1.5f else 1.25f)).lineOpacity(0.9).lineCap(LineCap.BUTT))
        addL(st, ln("dl-done-line", "dl-done").lineColor(if (im) "#7E849F" else "#8C92AC").lineWidth(routeW(if (im) 1.0f else 0.85f)).lineCap(LineCap.BUTT))
        // lane ribbons: the lane you need is a solid teal ribbon that fades away into the distance, the others are quiet road-coloured strips
        val night = nightStyle
        val onCol = if (night) "#2FC9B8" else "#1FB5A8"
        // the lanes not to use: a darker road-surface strip with clear white lines, so they stand out from the map's road under them
        // (they used to be close to the road's own colour and faded into it)
        val offCol = if (night) "#262C38" else "#AEB6C4"
        val isOn = Expression.eq(Expression.get("on"), lit(1))
        addL(st, FillLayer("dl-lane-fill", "dl-lane-bands").inSlot(MID)
            .fillColor(Expression.switchCase(isOn, col(onCol), col(offCol)))
            .fillOpacity(Expression.product(Expression.toNumber(Expression.get("a")), Expression.switchCase(isOn, lit(0.95), lit(0.9))))
            .fillEmissiveStrength(1.0).minZoom(15.5))
        val lineCol = "#FFFFFF"
        addL(st, LineLayer("dl-lane-edge", "dl-lane-divs").inSlot(MID).filter(Expression.eq(Expression.get("s"), lit(1)))
            .lineColor(lineCol).lineOpacity(0.95).lineEmissiveStrength(1.0)
            .lineWidth(zoomed(2.0, 15 to 0.25, 20 to 3.5)).minZoom(15.5))
        addL(st, LineLayer("dl-lane-div", "dl-lane-divs").inSlot(MID).filter(Expression.eq(Expression.get("s"), lit(0)))
            .lineColor(lineCol).lineOpacity(0.9).lineEmissiveStrength(1.0).lineDasharray(listOf(3.0, 4.0))
            .lineWidth(zoomed(2.0, 15 to 0.2, 20 to 2.8)).minZoom(15.5))
        for ((id, on) in listOf("dl-lanes-off" to 0, "dl-lanes-on" to 1)) {
            val l = SymbolLayer(id, "dl-lanes").inSlot(MID).filter(Expression.eq(Expression.get("on"), lit(on)))
                .iconImage(Expression.get("img")).iconRotate(Expression.get("rot"))
                .iconRotationAlignment(IconRotationAlignment.MAP).iconPitchAlignment(IconPitchAlignment.MAP)
                .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
                .iconSize(zoomed(2.0, 15 to 0.1, 20 to 2.6))
            addL(st, l)
            if (on == 1) lanesOnLayer = l
        }
        // street lamps (3D models, see StreetLamps below); and the road data used for lamps and road snapping
        installLamps(st)
        installRoadData(st)
        installHills(st)
        // (the route line was just made with the colour wanted now)
        appliedRouteCol = customRouteCol(); customApplied = emptySet(); applyCustom()
        lightsApplied = null; applyLights()
        // traffic lights (icons stand on the road, above shop labels)
        addImg(st, "dl-signal", signalBitmap())
        addS(st, GeoJsonSource.Builder("dl-signals").featureCollection(empty()).build())
        addL(st, SymbolLayer("dl-signals", "dl-signals").inSlot(TOP)
            .iconImage("dl-signal").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 8 to 0.28, 13 to 0.34, 15 to 0.38, 17 to 0.5, 19 to 0.64, 21 to 0.76))
            .iconAnchor(IconAnchor.BOTTOM))
        // speed cameras, roadworks and closures on the route
        for ((kind, nm) in hazardNames.withIndex()) addImg(st, nm, hazardBitmap(kind))
        // every speed camera around you, route or not: a small camera badge on the road (the route's own ones sit on top, bigger)
        // number-plate (ALPR) cameras such as Flock's, when switched on: a purple eye badge, so it's never taken for a speed camera
        addImg(st, "dl-alpr", alprBitmap())
        addS(st, GeoJsonSource.Builder("dl-alpr").featureCollection(empty()).build())
        addL(st, SymbolLayer("dl-alpr", "dl-alpr")
            .iconImage("dl-alpr").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 8 to 0.3, 12 to 0.4, 15 to 0.6, 18 to 0.8)))
        pushAlpr()
        // a lorry's height, weight and width limits ahead: a road sign with the limit, red when the lorry doesn't fit
        limImgs.clear()
        addS(st, GeoJsonSource.Builder("dl-lim").featureCollection(empty()).build())
        // (always shown, and the map's own road names and labels keep out of their way instead of being written over them; the
        // speed limit sign has its own size, the lorry signs as before)
        addL(st, SymbolLayer("dl-lim", "dl-lim")
            .iconImage(Expression.get("img")).iconAllowOverlap(true).iconIgnorePlacement(false)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            // (the zoom has to be the outermost part: Mapbox refuses a zoom curve inside a case, and then the whole layer was
            // never added, so neither the speed limit sign on the route nor the lorry signs ever showed)
            .iconSize(run {
                val isSpeed = Expression.eq(Expression.get("k"), lit(0))
                fun both(speed: Double, lorry: Double) = Expression.switchCase(isSpeed, lit(speed), lit(lorry))
                zoomedExpr(1.0, 8 to both(0.75, 0.3), 10 to both(0.75, 0.35), 12 to both(0.825, 0.4), 14 to both(0.9, 0.533),
                    15 to both(0.933, 0.6), 17 to both(1.0, 0.733), 18 to both(1.0, 0.8))
            }))
        pushLimits()
        addS(st, GeoJsonSource.Builder("dl-cams").featureCollection(empty()).build())
        addL(st, SymbolLayer("dl-cams", "dl-cams")
            .iconImage("dl-hz-cam").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            .iconSize(Expression.fromRaw(camsSize())))
        addL(st, SymbolLayer("dl-hazards", "dl-hazards")
            .iconImage(Expression.get("img")).iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconSize(Expression.fromRaw(HAZ_SIZE)))
        // where you parked (on the phone, after Android Auto disconnects)
        addImg(st, "dl-car-pin", parkedBitmap())
        addS(st, GeoJsonSource.Builder("dl-parked").featureCollection(FeatureCollection.fromFeatures(
            parkedAt?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyList())).build())
        signBmp?.let { addImg(st, "dl-lanesign", it) }
        addS(st, GeoJsonSource.Builder("dl-lanesign").featureCollection(FeatureCollection.fromFeatures(
            signAt?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyList())).build())
        addL(st, SymbolLayer("dl-lanesign-l", "dl-lanesign").iconImage("dl-lanesign").iconAnchor(IconAnchor.BOTTOM)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 13 to 0.45, 16 to 0.7, 18 to 0.9)))
        addL(st, SymbolLayer("dl-parked-l", "dl-parked").iconImage("dl-car-pin").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0).iconSize(zoomed(1.0, 10 to 0.4, 15 to 0.55, 18 to 0.7)))
        // your favourites as stars (from zoom 10, so a zoomed-out map isn't covered in them)
        favImgs.clear()
        addS(st, GeoJsonSource.Builder("dl-favs").featureCollection(empty()).build())
        addL(st, SymbolLayer("dl-favs-l", "dl-favs").iconImage(Expression.get("img")).iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0).minZoom(10.0).iconSize(zoomed(1.0, 10 to 0.75, 14 to 0.9, 17 to 1.0)))
        pushFavs()
        if (im) {
            addImg(st, "dl-pin", pinBitmap())
            addL(st, SymbolLayer("dl-dest-dot", "dl-dest")
                .iconImage("dl-pin").iconAnchor(IconAnchor.BOTTOM).iconEmissiveStrength(1.0)
                .iconAllowOverlap(true).iconIgnorePlacement(true).iconSize(0.6))
        } else addL(st, CircleLayer("dl-dest-dot", "dl-dest")
            .circleRadius(10.0).circleColor("#ff3b30").circleStrokeColor("#ffffff").circleStrokeWidth(3.0).circleEmissiveStrength(1.0))
        // the 3D flags (destination red, stops amber), lit from within so they stand out day and night
        if (!satellite) try {
            addL(st, FillExtrusionLayer("dl-flags-3d", "dl-flags").inSlot(MID).minZoom(12.0)
                .fillExtrusionColor(Expression.fromRaw("[\"case\",[\"==\",[\"get\",\"part\"],\"pole\"],\"#F2F4F8\",[\"==\",[\"get\",\"kind\"],\"stop\"],\"#FFB020\",\"#FF3B30\"]"))
                .fillExtrusionHeight(Expression.get("top")).fillExtrusionBase(Expression.get("base"))
                .fillExtrusionEmissiveStrength(0.85).fillExtrusionOpacity(1.0))
        } catch (e: Throwable) { }
        // the headlight beam (under the arrow; night only, while moving)
        addImg(st, "dl-beam", beamBitmap())
        beamShown = false; beamScale = -1.0; beamHead = -999.0
        try {
            // (no movement animation: it's moved with the arrow, which is already smoothed)
            val json = "{\"id\":\"dl-beam-l\",\"type\":\"location-indicator\",\"layout\":{\"bearing-image\":\"dl-beam\",\"visibility\":\"none\"}," +
                "\"paint\":{\"location\":[0,0,0],\"bearing\":0,\"bearing-image-size\":0.8,\"perspective-compensation\":0," +
                "\"accuracy-radius\":0,\"emphasis-circle-radius\":0,\"location-transition\":{\"duration\":0,\"delay\":0},\"bearing-transition\":{\"duration\":0,\"delay\":0}}}"
            Value.fromJson(json).value?.let { st.addStyleLayer(it, null) }
        } catch (e: Throwable) { android.util.Log.w("MapBinding", "beam: ${e.message}") }
        // arriving: a dotted walk from the car park, and the entrance marked
        addS(st, GeoJsonSource.Builder("dl-walk").featureCollection(empty()).build())
        addL(st, LineLayer("dl-walk-line", "dl-walk").lineColor("#2E7CF6").lineWidth(5.0).lineOpacity(0.95)
            .lineCap(LineCap.ROUND).lineDasharray(listOf(0.1, 2.0)).lineEmissiveStrength(1.0))
        addImg(st, "dl-door", doorBitmap())
        addS(st, GeoJsonSource.Builder("dl-door").featureCollection(empty()).build())
        addL(st, SymbolLayer("dl-door-l", "dl-door").iconImage("dl-door").iconAnchor(IconAnchor.CENTER)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0).iconPitchAlignment(IconPitchAlignment.VIEWPORT))
        pushDoor()
        if (!im) {
            addL(st, CircleLayer("dl-me-halo", "dl-me")
                .circleRadius(15.0 * 0.625 * arrowScale).circleColor("#2e8bff").circleOpacity(0.25).circleEmissiveStrength(1.0)
                .circlePitchAlignment(CirclePitchAlignment.MAP).circlePitchScale(CirclePitchScale.MAP))
        }
        // the "+4 min" / "−2 min" bubbles on the alternatives (on top of everything, so they're always readable)
        addL(st, SymbolLayer("dl-live-bub", "dl-live-bub")
            .iconImage(Expression.get("img")).iconAnchor(IconAnchor.BOTTOM)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT)
            .iconSize(zoomed(1.0, 9 to 0.6, 14 to 0.68, 18 to 0.76)))
        // "See all locations": a dotted line from you to the one picked, and every branch as its logo with the drive time
        addL(st, LineLayer("dl-chain-line", "dl-chain-line").lineColor("#4C8DFF").lineWidth(5.0).lineOpacity(0.9)
            .lineCap(LineCap.ROUND).lineDasharray(listOf(0.1, 2.2)).lineEmissiveStrength(1.0))
        addL(st, SymbolLayer("dl-chain-pins", "dl-chain")
            .iconImage(Expression.get("img")).iconAnchor(IconAnchor.CENTER)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT)
            .symbolSortKey(Expression.get("z")))
        pushChain()
        // how long each queue on the route holds you up ("+8 min"), at its start, in its own colour; other routes' ones dimmed
        addL(st, SymbolLayer("dl-jam-l", "dl-jam").iconImage(Expression.get("img")).iconAnchor(IconAnchor.BOTTOM)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).symbolSortKey(Expression.get("z"))
            .iconSize(zoomed(1.0, 9 to 0.75, 13 to 0.9, 16 to 1.0)))
        pushJams()
        // the spot you tapped or held on the map (while its card is open): the destination's red pin, a little smaller
        addImg(st, "dl-tap-pin", pinBitmap())
        addL(st, SymbolLayer("dl-tap-l", "dl-tap").iconImage("dl-tap-pin").iconAnchor(IconAnchor.BOTTOM)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconSize(0.48))
        pushTap()
        // (a style change, day to night say, drops the searched place's building highlight: put it back)
        hlStyled = false; pushHl()
        flatArrow = if (im) arrowBitmapImmersive() else arrowBitmap()
        addImg(st, "dl-arrow", flatArrow!!)
        addL(st, SymbolLayer("dl-me-arrow", "dl-me")
            .iconImage("dl-arrow").iconRotate(Expression.get("h"))
            .iconRotationAlignment(IconRotationAlignment.MAP).iconPitchAlignment(IconPitchAlignment.MAP)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconSize((if (im) 0.25 else 0.625) * arrowScale)
            .visibility(vis(locatorStyle == "flat")))
        // the real 3D model (Full 3D only); otherwise the 3D locator is drawn by locatorView over the map, see pushModel
        modelOn = false; modelAt = null
        if (modelWanted && !modelBroken && MapStylePolicy.supportsFullDetailFeatures(mapDetail)) installModel(st)
        // (the road-name pill is roadView, see placeLocator)
        spriteName = null
        placeLocator()
        pushAll()
        installExtras(st)
        // the rain radar, if it's on, goes back under the route
        pushRadar()
    }

    /** Just the 3D buildings setting, keeping the map as it is otherwise. */
    fun setBuildings3d(b3d: Boolean) = setView(satellite, b3d)

    fun setView(sat: Boolean, b3d: Boolean) {
        val satChanged = sat != satellite
        satellite = sat
        buildings3d = b3d
        // satellite is Mapbox's Standard Satellite style (a style of its own, so the map reloads); 3D buildings is one of Standard's settings
        if (satChanged && wantedStyle != null) { val r = onRestyle; if (r != null) r() else setStyleUrl(baseStyle()) }
        else if (!sat && slotsOn) {
            setBase("show3dObjects", Value.valueOf(buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            // (landmarks and building fronts are buildings too: with 3D buildings off they went on showing, GitHub)
            setBase("show3dLandmarks", Value.valueOf(landmarksOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("show3dFacades", Value.valueOf(facadesOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
        }
        else if (!sat) style?.let { st ->
            // the Horizon colours' own 3D buildings
            try {
                if (st.styleLayerExists("dl-building-3d")) st.setStyleLayerProperty("dl-building-3d", "visibility", Value.valueOf(if (b3d) "visible" else "none"))
                else if (b3d && hzFamily()) recolourClassic(st)
            } catch (e: Throwable) { }
        }
    }

    /** The style the map should show: Standard, or Standard Satellite when satellite is on. */
    fun baseStyle(): String = when (MapStylePolicy.choose(satellite, mapDetail, nightStyle)) {
        BaseMapStyle.STANDARD_SATELLITE -> Style.STANDARD_SATELLITE
        BaseMapStyle.DARK_STREETS -> Style.DARK
        BaseMapStyle.STREETS -> Style.MAPBOX_STREETS
        BaseMapStyle.STANDARD -> Style.STANDARD
    }

    private val NAV_DAY = "mapbox://styles/mapbox/navigation-day-v1"
    private val NAV_NIGHT = "mapbox://styles/mapbox/navigation-night-v1"

    /** While a route is on the map, traffic colours on every other road can be hidden so the route line stands out (the route itself shows its own traffic). */
    fun setHideOtherTraffic(b: Boolean) {
        if (hideTraffic == b) return
        hideTraffic = b
        applyTraffic()
    }

    /** Live traffic colours on the roads on or off (Settings > Map > Live traffic). */
    fun setTraffic(on: Boolean) {
        if (trafficOn == on) return
        trafficOn = on
        applyTraffic()
    }

    /** Free-flowing roads drawn green as well as the slow ones (Settings > Map > Light traffic in green). */
    private var trafficGreen = false
    // (green only on the main roads (motorways, A roads and the bigger B roads): on every side street it painted the whole map green)
    private fun trafficFilter(): Expression = Expression.fromRaw(
        if (trafficGreen) "[\"any\",[\"match\",[\"get\",\"congestion\"],[\"moderate\",\"heavy\",\"severe\"],true,false]," +
            "[\"all\",[\"==\",[\"get\",\"congestion\"],\"low\"],[\"match\",[\"get\",\"class\"],[\"motorway\",\"trunk\",\"primary\",\"secondary\"],true,false]]]"
        else "[\"match\",[\"get\",\"congestion\"],[\"moderate\",\"heavy\",\"severe\"],true,false]")
    fun setTrafficGreen(on: Boolean) {
        if (on == trafficGreen) return
        trafficGreen = on
        main0.post { try { (layer("dl-traffic") as? LineLayer)?.filter(trafficFilter()) } catch (e: Throwable) { } }
    }

    private fun applyTraffic() {
        try { layer("dl-traffic")?.visibility(vis(trafficOn && !(routing && hideTraffic))) } catch (e: Throwable) { }
    }

    /** Light map: no points of interest and no route glow, so there is less to draw and place every frame. */
    private var poiMoving = false

    /** Set by the engine as you start and stop moving, so the full map can rest its shop labels while driving. */
    fun setMoving(m: Boolean) {
        if (m == poiMoving) return
        poiMoving = m
        if (!lite) setBase("showPointOfInterestLabels", Value.valueOf(!poiMoving))
    }

    /** (Kept for the old "Light map" switch; Map detail sets this now.) */
    fun setLite(b: Boolean) {
        if (lite == b || mapDetail != "full") return
        lite = b
        try { glowLayer?.visibility(vis(!lite)) } catch (e: Exception) { }
        setBase("showPointOfInterestLabels", Value.valueOf(!lite && !poiMoving))
        setBase("showTransitLabels", Value.valueOf(!lite))
        if (!satellite) {
            setBase("show3dObjects", Value.valueOf(buildings3d && !lite))
            setBase("showLandmarkIcons", Value.valueOf(!lite))
        }
    }


    /**
     * The Horizon colours, exactly as the original app painted its map: soft, light tones by day and a deep blue-grey at night, matched
     * to layers by name (land, water, parks, roads, buildings), green only for the shapes whose class is green, buildings shaded by
     * height, road fills a touch darker by day so they separate from the land, and the original label colours. Used for the Horizon
     * colours on Mapbox's map and for TomTom's map. Also adds 3D buildings when the map has none (as the original did).
     */
    private fun recolourClassic(st: Style) {
        val dark = nightStyle
        val land = if (dark) "#2D3358" else "#F4F2EE"
        val water = if (dark) "#2A6682" else "#AADAF5"
        val park = if (dark) "#2E5C5E" else "#CDEBC4"
        val bld = if (dark) "#454C78" else "#E7E5E1"
        // (by day the original made road fills 20% darker after painting them white: #CCCCCC)
        val roadFill = if (dark) "#464E7C" else "#CCCCCC"
        val roadCase = if (dark) "#262B4B" else "#DCDAD5"
        val hwyFill = if (dark) "#5E67A0" else "#CCCCCC"
        val hwyCase = if (dark) "#2B3052" else "#CFCCC6"
        val rail = if (dark) "#454C70" else "#D2D0CB"
        val text = if (dark) "#C9CEE8" else "#5F6368"
        val halo = if (dark) "#2D3358" else "#FFFFFF"
        val roadText = if (dark) "#D3D8F0" else "#3E4459"
        val roadHalo = if (dark) "#252A48" else "#F4F6FB"
        val parkWords = listOf("park", "green", "forest", "wood", "grass", "vegetation", "nature", "garden", "golf", "cemetery", "recreation", "pitch", "meadow", "scrub", "farm")
        val roadWords = listOf("road", "street", "motorway", "trunk", "highway", "primary", "secondary", "tertiary", "residential", "service", "link", "ramp", "track", "bridge", "tunnel", "path", "pedestrian", "transportation")
        val hwyWords = listOf("motorway", "trunk", "highway", "freeway", "expressway")
        val greenKinds = "[\"grass\",\"wood\",\"forest\",\"park\",\"garden\",\"meadow\",\"scrub\",\"heath\",\"farmland\",\"farm\",\"orchard\",\"vineyard\",\"recreation_ground\",\"recreation\",\"pitch\",\"golf_course\",\"golf\",\"cemetery\",\"nature_reserve\",\"village_green\",\"allotments\",\"common\",\"grassland\",\"tree\",\"trees\",\"wetland\",\"playground\",\"sports_centre\",\"green\"]"
        val greenByClass = Expression.fromRaw("[\"match\",[\"downcase\",[\"to-string\",[\"coalesce\",[\"get\",\"class\"],[\"get\",\"subclass\"],[\"get\",\"kind\"],[\"get\",\"type\"],\"\"]]],$greenKinds,\"$park\",\"$land\"]")
        val cols = if (dark) listOf("#3C4370", "#454C7E", "#51598F", "#5F68A0") else listOf("#F1EFEB", "#E9E7E3", "#E0DED9", "#D6D4CF")
        val extColour = Expression.fromRaw("[\"interpolate\",[\"linear\"],[\"case\",[\"has\",\"render_height\"],[\"to-number\",[\"get\",\"render_height\"]],[\"has\",\"height\"],[\"to-number\",[\"get\",\"height\"]],10],0,\"${cols[0]}\",20,\"${cols[1]}\",60,\"${cols[2]}\",150,\"${cols[3]}\"]")
        fun set(id: String, prop: String, v: Value) { try { st.setStyleLayerProperty(id, prop, v) } catch (e: Throwable) { } }
        fun set(id: String, prop: String, hex: String) = set(id, prop, Value.valueOf(hex))
        var has3d = false
        var buildingSrc: String? = null
        var firstSymbol: String? = null
        for (info in st.styleLayers) {
            val id = info.id
            if (id.startsWith("dl-")) continue
            val srcLayer = try { st.getStyleLayerProperty(id, "source-layer").value.contents as? String ?: "" } catch (e: Throwable) { "" }
            val key = (id + " " + srcLayer).lowercase()
            // the map's own traffic lines are hidden: the app draws live traffic itself (and hides it round your route)
            if (key.contains("traffic")) { set(id, "visibility", "none"); continue }
            if (key.contains("flow") || key.contains("incident")) continue
            when (info.type) {
                "background" -> set(id, "background-color", land)
                "fill" -> when {
                    key.contains("water") -> set(id, "fill-color", water)
                    parkWords.any { key.contains(it) } -> set(id, "fill-color", park)
                    key.contains("building") -> {
                        set(id, "fill-color", bld)
                        if (srcLayer == "building") buildingSrc = try { st.getStyleLayerProperty(id, "source").value.contents as? String } catch (e: Throwable) { null }
                    }
                    // generic land-cover / land-use layers hold grass, woods and parks together with plain ground: tint by each shape's class
                    listOf("landcover", "land cover", "landuse", "land use", "vegetation", "natural", "leisure", "green").any { key.contains(it) } -> set(id, "fill-color", greenByClass)
                    listOf("land", "background", "urban", "residential", "commercial", "industrial").any { key.contains(it) } -> set(id, "fill-color", land)
                }
                "fill-extrusion" -> {
                    has3d = true
                    set(id, "fill-extrusion-color", extColour)
                    set(id, "fill-extrusion-opacity", Value.valueOf(if (dark) 0.9 else 0.96))
                    set(id, "fill-extrusion-vertical-gradient", Value.valueOf(true))
                }
                "line" -> {
                    val casing = key.contains("casing") || key.contains("case") || key.contains("outline") || key.contains("stroke")
                    val hwy = hwyWords.any { key.contains(it) }
                    when {
                        key.contains("water") || key.contains("river") || key.contains("stream") || key.contains("canal") -> set(id, "line-color", water)
                        key.contains("rail") || key.contains("subway") || key.contains("transit") -> set(id, "line-color", rail)
                        roadWords.any { key.contains(it) } -> set(id, "line-color", if (casing) (if (hwy) hwyCase else roadCase) else (if (hwy) hwyFill else roadFill))
                    }
                }
                "symbol" -> {
                    if (firstSymbol == null) firstSymbol = id
                    val roadLabel = roadWords.any { key.contains(it) }
                    set(id, "text-color", if (roadLabel) roadText else text)
                    set(id, "text-halo-color", if (roadLabel) roadHalo else halo)
                    set(id, "text-halo-width", Value.valueOf(1.4))
                }
            }
        }
        // 3D buildings, as the original added to Mapbox's map (when 3D buildings are on), under the labels
        val src = buildingSrc ?: (if (st.styleSourceExists("composite")) "composite" else null)
        if (!has3d && src != null && buildings3d && !st.styleLayerExists("dl-building-3d")) {
            val l = FillExtrusionLayer("dl-building-3d", src).sourceLayer("building").minZoom(15.0)
                .filter(Expression.fromRaw("[\"all\",[\"!=\",[\"get\",\"extrude\"],\"false\"],[\"!=\",[\"get\",\"underground\"],\"true\"]]"))
                .fillExtrusionColor(extColour)
                .fillExtrusionHeight(Expression.fromRaw("[\"interpolate\",[\"linear\"],[\"zoom\"],15,0,15.6,[\"to-number\",[\"get\",\"height\"],8]]"))
                .fillExtrusionBase(Expression.fromRaw("[\"interpolate\",[\"linear\"],[\"zoom\"],15,0,15.6,[\"to-number\",[\"get\",\"min_height\"],0]]"))
                .fillExtrusionOpacity(if (dark) 0.9 else 0.95).fillExtrusionVerticalGradient(true)
            addL(st, l, firstSymbol)
        }
    }

    // ---- trees: Standard draws its own 3D trees ----
    private var treesOn = true

    fun treesEnabled(on: Boolean) {
        if (treesOn == on) return
        treesOn = on
        if (!satellite) setBase("show3dTrees", Value.valueOf(on))
    }


    /** Line width that grows with zoom the way road widths do: clearly wider than the road under it, but always well under the locator arrow's width. */
    private fun routeW(scale: Float): Expression {
        // everything route-related (line, casing, glow, driven part, turn arrow) is sized from this, so they all stay in step
        val k = scale * ROUTE_K
        return zoomed(1.7, 10 to 3f * k, 13 to 6.5f * k, 15 to 10f * k, 17 to 16f * k, 19 to 24f * k)
    }

    /** How thick the route is drawn overall (1 = the standard width). */
    /** Makes the route line (and everything sized with it) thicker: the Android Auto map sets this a little above 1, as the
     *  line looked thin there against the buildings. Set straight after creating the map, before its style is built. */
    var routeBoost = 1f
    private val ROUTE_K: Float get() = routeBoost * customRouteK

    // ---- the GPX track (GitHub #153) ----
    private var trackPts: List<LL> = emptyList()
    /** Fainter while you drive it, so your route line on top reads first. */
    private var trackFaint = false

    private fun trackFeatures(): FeatureCollection {
        val p = trackPts
        if (p.size < 2) return empty()
        val a = Feature.fromGeometry(Point.fromLngLat(p.first().lon, p.first().lat)).apply { addNumberProperty("end", 0) }
        val b = Feature.fromGeometry(Point.fromLngLat(p.last().lon, p.last().lat)).apply { addNumberProperty("end", 1) }
        return FeatureCollection.fromFeatures(listOf(line(p), a, b))
    }

    /** Shows a GPX track on the map (empty to take it away); [faint] while it's being driven. */
    fun setTrack(pts: List<LL>, faint: Boolean = false) {
        main0.post {
            try {
                trackPts = pts; trackFaint = faint
                src("dl-track")?.featureCollection(trackFeatures())
                style?.let { st -> if (st.styleLayerExists("dl-track-line")) st.setStyleLayerProperty("dl-track-line", "line-opacity", Value.valueOf(if (faint) 0.45 else 0.9)) }
            } catch (e: Throwable) { }
        }
    }

    private fun line(p: List<LL>): Feature =
        Feature.fromGeometry(LineString.fromLngLats(p.map { Point.fromLngLat(it.lon, it.lat) }))

    private fun lineSev(p: List<LL>, level: Number): Feature {
        val f = line(p)
        f.addNumberProperty("sev", level)
        return f
    }

    /**
     * [seg] as lines coloured by traffic, where each point's level is [level] of its index from [firstIdx]. With blending on, the
     * colour runs from one level to the next over about 150 m each side of where it changes, in short pieces of in-between levels
     * (about 25 m each), instead of changing in one step (#334).
     */
    private fun trafficRuns(seg: List<LL>, firstIdx: Int, level: (Int) -> Int, out: ArrayList<Feature>) {
        if (seg.size < 2) return
        // the plain runs, as before
        val runs = ArrayList<Triple<Int, Int, Int>>()   // start index, end index (inclusive), level
        var start = 0; var cur = level(firstIdx)
        for (j in 1 until seg.size - 1) {
            val l = level(firstIdx + j)
            if (l != cur) { runs += Triple(start, j, cur); start = j; cur = l }
        }
        runs += Triple(start, seg.size - 1, cur)
        if (!blendTraffic || runs.size == 1) { for ((a, b, l) in runs) out += lineSev(seg.subList(a, b + 1), l); return }
        val cum = DoubleArray(seg.size); for (j in 1 until seg.size) cum[j] = cum[j - 1] + Geo.dist(seg[j - 1], seg[j])
        // where each change is, and the levels either side
        val changes = (1 until runs.size).map { k -> Triple(cum[runs[k].first], runs[k - 1].third, runs[k].third) }
        fun at(d: Double): LL {
            var i = java.util.Arrays.binarySearch(cum, d).let { if (it >= 0) it else -it - 2 }.coerceIn(0, seg.size - 2)
            val span = cum[i + 1] - cum[i]; val t = if (span > 0) ((d - cum[i]) / span).coerceIn(0.0, 1.0) else 0.0
            val a = seg[i]; val b = seg[i + 1]
            return LL(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        }
        // the cuts: every route point, plus every 25 m inside a blend; each piece gets the level at its middle
        val total = cum.last()
        val cuts = java.util.TreeSet<Double>(); for (c0 in cum) cuts += c0
        for ((d, _, _) in changes) { var x = maxOf(0.0, d - 150.0); while (x < minOf(total, d + 150.0)) { cuts += x; x += 25.0 } }
        fun sevAt(d: Double): Double {
            var best = Double.MAX_VALUE; var v = 0.0; var near = false
            for ((cd, a, b) in changes) { val off = d - cd; if (kotlin.math.abs(off) < 150.0 && kotlin.math.abs(off) < best) { best = kotlin.math.abs(off); near = true
                val t = ((off + 150.0) / 300.0).coerceIn(0.0, 1.0); val sm = t * t * (3 - 2 * t); v = a + (b - a) * sm } }
            if (near) return v
            val k = runs.indexOfLast { cum[it.first] <= d }.coerceAtLeast(0); return runs[k].third.toDouble()
        }
        val list = cuts.toList()
        var pts = arrayListOf(at(list[0])); var lvl = Math.round(sevAt((list[0] + list.getOrElse(1) { list[0] }) / 2) * 10) / 10.0
        for (i in 1 until list.size) {
            val mid = (list[i - 1] + list[i]) / 2
            val l = Math.round(sevAt(mid) * 10) / 10.0
            val p = at(list[i])
            if (l != lvl && pts.size >= 2) { out += lineSev(pts, lvl); pts = arrayListOf(pts.last()) }
            lvl = l; pts += p
        }
        if (pts.size >= 2) out += lineSev(pts, lvl)
    }

    private var mainSev: IntArray? = null
    private var mainOff = 0

    /** Route colour: blue (a deeper blue by day, like the progress bar), amber where traffic is slow, red where it's heavy. */
    // (the same colours as before, now blended: "sev" is 0 for clear, 1 slow and 2 heavy, and between those where one turns into the
    // next along the route, #334. Whole numbers come out exactly as they did)
    private fun sevColor(im: Boolean = false, day: Boolean = false): Expression {
        fun rgba(hex: String): String { val c = android.graphics.Color.parseColor(hex)
            return "\"rgba(${android.graphics.Color.red(c)},${android.graphics.Color.green(c)},${android.graphics.Color.blue(c)},${String.format(java.util.Locale.US, "%.3f", android.graphics.Color.alpha(c) / 255.0)})\"" }
        val base = rgba(customRouteCol() ?: if (day) "#2A7BE4" else if (im) "#2FC4F5" else "#4CC9FF")
        return Expression.fromRaw("[\"interpolate\",[\"linear\"],[\"to-number\",[\"get\",\"sev\"],0],0,$base,1,${rgba(if (im) "#F9AB00" else "#FFB020")},2,${rgba(if (im) "#D93025" else "#FF4D4D")}]")
    }

    /** Settings › On the map › Blend traffic colours (#334). */
    @Volatile private var blendTraffic = true
    fun setBlendTraffic(on: Boolean) { if (on == blendTraffic) return; blendTraffic = on; main0.post { try { setLine(main) } catch (e: Exception) { } } }

    private fun pushAll() {
        pushLive(); pushRadar(); pushSatFallback(); pushTurn()
        pushRoute(); pushDest(); pushFlags(); pushMe(); pushHazards(); pushCams(); pushSignals()
    }

    private var cams: List<LL> = emptyList()

    /** The speed cameras around you, shown on the map whether or not you're on a route. */
    // ---- number-plate (ALPR) cameras: where they are and what they are ("Flock Safety"), from OpenStreetMap ----
    @Volatile private var alprs: List<Pair<LL, String>> = emptyList()
    /** A number-plate camera on the map was tapped: what it is. */
    var onAlprTap: ((String) -> Unit)? = null
    /** One of your own reports (accident, police…) tapped on the map, with where it is. */
    var onReportTap: ((LL) -> Unit)? = null

    /** Your report badge nearest a tap, within about 28 dp of it. */
    private fun reportBadgeAt(sp: android.graphics.PointF): LL? {
        val list = hazards.filter { it.second in 3..7 }
        if (list.isEmpty()) return null
        val reach = 28f * mapView.resources.displayMetrics.density
        var best: LL? = null
        var bd = reach * reach
        for ((p, _) in list) {
            val px = try { mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat)) } catch (e: Throwable) { continue }
            val dx = px.x.toFloat() - sp.x
            val dy = px.y.toFloat() - sp.y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = p }
        }
        return best
    }

    fun setAlpr(list: List<Pair<LL, String>>) {
        main0.post { if (list != alprs) { alprs = list; pushAlpr() } }
    }

    private fun pushAlpr() {
        val s = src("dl-alpr") ?: return
        try { s.featureCollection(FeatureCollection.fromFeatures(alprs.map { Feature.fromGeometry(Point.fromLngLat(it.first.lon, it.first.lat)) })) } catch (e: Exception) { }
    }

    /** The number-plate camera drawn under screen point [sp] (within a finger's width), or null. */
    private fun alprAt(sp: android.graphics.PointF): Pair<LL, String>? {
        val list = alprs
        if (list.isEmpty()) return null
        val reach = 28f * mapView.resources.displayMetrics.density
        var best: Pair<LL, String>? = null
        var bd = reach * reach
        for (a in list) {
            val px = try { mbMap.pixelForCoordinate(Point.fromLngLat(a.first.lon, a.first.lat)) } catch (e: Throwable) { continue }
            val dx = px.x.toFloat() - sp.x
            val dy = px.y.toFloat() - sp.y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = a }
        }
        return best
    }

    /** A lorry's limits on the map: where, the sign's text ("4.1m", "7.5t"), and whether the lorry is over it (red). */
    @Volatile private var limits: List<Triple<LL, String, Boolean>> = emptyList()
    @Volatile private var speedChange: Pair<LL, String>? = null
    /** The sign pictures already added to the current map style (they go when the style changes). */
    private val limImgs = HashSet<String>()

    fun setLimits(list: List<Triple<LL, String, Boolean>>) {
        main0.post { if (list != limits) { limits = list; pushLimits() } }
    }

    /** The next posted speed-limit change, pinned to its position along the route. */
    fun setSpeedChange(pos: LL?, text: String?, board: String = "round") {
        val next = if (pos != null && !text.isNullOrBlank()) pos to text else null
        main0.post { if (next != speedChange || board != limitBoard) { speedChange = next; limitBoard = board; pushLimits() } }
    }
    /** How speed limit signs look here: "round" (the red ring), "us" or "ca" (the white board), like the main sign. */
    @Volatile private var limitBoard = "round"


    private fun pushLimits() {
        val st = style ?: return
        val s = src("dl-lim") ?: return
        try {
            val feats = ArrayList<Feature>()
            for ((p, text, over) in limits.take(60)) {
                val id = "dl-lim-" + (if (over) "r-" else "w-") + text
                if (limImgs.add(id)) addImg(st, id, limitBitmap(text, over))
                feats += Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addStringProperty("img", id); it.addNumberProperty("k", 1) }
            }
            // the next speed limit change on the route: the main sign's own look, larger, with a white halo (it was too small,
            // and covered by the road names)
            speedChange?.let { (p, text) ->
                val id = "dl-lim-sp-$limitBoard-$text"
                if (limImgs.add(id)) addImg(st, id, routeLimitBitmap(text, limitBoard))
                feats += Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addStringProperty("img", id); it.addNumberProperty("k", 0) }
            }
            s.featureCollection(FeatureCollection.fromFeatures(feats))
        } catch (e: Throwable) { }
    }

    /**
     * The next speed limit on the route, drawn like the main sign: the domed roundel with its shaded red ring, or the US / Canada
     * board, on a white halo so it stands out from the map. Sized for the screen's density (the old one was a fixed 104 pixels,
     * tiny on a sharp screen).
     */
    private fun routeLimitBitmap(text: String, board: String): Bitmap {
        val d = mapView.resources.displayMetrics.density
        val tf = if (android.os.Build.VERSION.SDK_INT >= 28) android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 900, false) else android.graphics.Typeface.DEFAULT_BOLD
        val n0 = text.toIntOrNull() ?: 0
        fun p(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        if (board == "us" || board == "ca") {
            val w = (44f * d).toInt(); val h = (54f * d).toInt(); val m = 3f * d
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawRoundRect(RectF(m, m + 1.5f * d, w - m, h - m + 1.5f * d), 7f * d, 7f * d, p(0x55000000))
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h - 1.5f * d), 8f * d, 8f * d, p(0xFFFFFFFF.toInt()))
            c.drawRoundRect(RectF(m, m, w - m, h - m - 1.5f * d), 6f * d, 6f * d, p(0xFF151515.toInt()))
            c.drawRoundRect(RectF(m + 2f * d, m + 2f * d, w - m - 2f * d, h - m - 3.5f * d), 4.5f * d, 4.5f * d, p(0xFFFFFFFF.toInt()))
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF151515.toInt(); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 6.2f * d }
            val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt(); textAlign = Paint.Align.CENTER; typeface = tf; textSize = (if (n0 >= 100) 15f else 19f) * d }
            if (board == "ca") c.drawText("MAXIMUM", w / 2f, 15f * d, small)
            else { c.drawText("SPEED", w / 2f, 13.5f * d, small); c.drawText("LIMIT", w / 2f, 20.5f * d, small) }
            c.drawText(text, w / 2f, 42f * d, big)
            return bmp
        }
        val size = (54f * d).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f; val cy = size / 2f - 1f * d
        val halo = size / 2f - 1.5f * d
        val r = halo - 3f * d
        c.drawCircle(cx, cy + 1.5f * d, halo, p(0x55000000))
        c.drawCircle(cx, cy, halo, p(0xFFFFFFFF.toInt()))
        c.drawCircle(cx, cy, r, p(0xFF7A0A14.toInt()))
        c.drawCircle(cx, cy, r * 0.965f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, cy - r, 0f, cy + r, intArrayOf(0xFFFF5A5F.toInt(), 0xFFE0142B.toInt(), 0xFFB30C1F.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        })
        val face = r * 0.74f
        c.drawCircle(cx, cy, face + r * 0.03f, p(0x33000000))
        c.drawCircle(cx, cy, face, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(cx - face * 0.25f, cy - face * 0.35f, face * 1.5f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1F1F3.toInt(), 0xFFE1E2E6.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111111.toInt(); textAlign = Paint.Align.CENTER; typeface = tf; letterSpacing = -0.03f
            textSize = 2f * r * (if (n0 >= 100) 0.31f else 0.39f)
        }
        val b = android.graphics.Rect(); tp.getTextBounds(text, 0, text.length, b)
        c.drawText(text, cx, cy - (b.top + b.bottom) / 2f, tp)
        return bmp
    }

    /** A round limit sign: white with a red ring and the limit in black, or filled red when the lorry is over the limit. */
    private fun limitBitmap(text: String, over: Boolean): Bitmap {
        val n = 104
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = n / 2f
        fun p(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        c.drawCircle(cx, cx + 2f, 46f, p(0x55000000))
        c.drawCircle(cx, cx, 46f, p(0xFFD93025.toInt()))
        c.drawCircle(cx, cx, 36f, p(if (over) 0xFFFFE3E0.toInt() else 0xFFFFFFFF.toInt()))
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF202124.toInt(); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = if (text.length <= 4) 24f else if (text.length <= 5) 20f else 17f
        }
        c.drawText(text, cx, cx + tp.textSize * 0.36f, tp)
        return bmp
    }

    /** The purple eye badge for a number-plate camera. */
    private fun alprBitmap(): Bitmap {
        val n = 96
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = n / 2f
        fun p(col: Int, st: Paint.Style = Paint.Style.FILL, w: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = st; strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        c.drawCircle(cx, cx + 2f, 38f, p(0x55000000))
        c.drawCircle(cx, cx, 38f, p(0xFFFFFFFF.toInt()))
        c.drawCircle(cx, cx, 32f, p(0xFF6A3FD1.toInt()))
        // an eye: the almond outline and the pupil
        val eye = android.graphics.Path().apply {
            moveTo(cx - 22f, cx)
            quadTo(cx, cx - 22f, cx + 22f, cx)
            quadTo(cx, cx + 22f, cx - 22f, cx)
            close()
        }
        c.drawPath(eye, p(0xFFFFFFFF.toInt(), Paint.Style.STROKE, 5f))
        c.drawCircle(cx, cx, 7f, p(0xFFFFFFFF.toInt()))
        return bmp
    }

    fun setCameras(list: List<LL>) {
        main0.post { if (list != cams) { cams = list; pushCams() } }
    }

    private fun pushCams() {
        val s = src("dl-cams") ?: return
        try { s.featureCollection(FeatureCollection.fromFeatures(cams.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })) } catch (e: Exception) { }
        // the 3D cameras face you (turned towards where you are now), standing at the left kerb
        val m = src("dl-cams-m") ?: return
        try {
            val here = me
            m.featureCollection(FeatureCollection.fromFeatures(cams.take(200).map { c ->
                val b = if (here != null) Geo.bearing(here, c) else 0.0
                val at = Furniture.offset(c, b - 90.0, 4.0)
                Furniture.point(at.lat, at.lon, b)
            }))
        } catch (e: Exception) { }
    }

    // (never throws: asking a style that's being replaced by a newer one is an error in the map engine, and these are called from
    // posted jobs that can run just as the map reloads; that was a crash in the old map engine)
    private fun src(id: String): GeoJsonSource? = try { style?.getSourceAs<GeoJsonSource>(id) } catch (e: Throwable) { null }

    /** The route line's stretch moved into the lane you need before a junction (from the road's line, back onto it), or empty. */
    private var routeShift: List<LL> = emptyList()

    fun setRouteShift(pts: List<LL>) {
        main0.post {
            if (pts.isEmpty() && routeShift.isEmpty()) return@post
            routeShift = pts
            try { setLine(main) } catch (e: Throwable) { }
        }
    }

    /** [p] moved onto the lane stretch when it's alongside it (within 10 m), else as it is. */
    private fun onShift(p: LL): LL = MapRouteGeometry.shiftPoint(p, routeShift)

    /**
     * [pts] with the lane stretch spliced in (where its ends sit on the line), and for each point the index of the route point it
     * stands for (so the traffic colours stay in the right places). Lane points already behind you are left out.
     */
    private fun withShift(pts: List<LL>): Pair<List<LL>, IntArray?> = MapRouteGeometry.spliceShift(pts, routeShift)

    private fun setLine(pts0: List<LL>) {
        val s = src("dl-route") ?: return
        val (pts, origIdx) = withShift(pts0)
        try { src("dl-flow")?.featureCollection(if (pts.size < 2) FeatureCollection.fromFeatures(ArrayList<Feature>()) else FeatureCollection.fromFeatures(arrayListOf(line(pts)))) } catch (e: Exception) { }
        if (pts.size < 2) { s.featureCollection(empty()); return }
        val sev = mainSev
        fun level(j0: Int): Int { val j = origIdx?.getOrNull(j0) ?: j0; return if (sev != null && mainOff + j in sev.indices) sev[mainOff + j] else 0 }
        // where the turn arrow is drawn, the route line underneath is left out (cut at the exact points where the arrow starts and ends,
        // not at the nearest route point), so none of it shows round the arrow's edges
        // ...but only while the arrow is drawn (zoom 13 and in): zoomed further out the arrow isn't shown, and the cut left a gap
        val cut = if (cutZoomOk()) turnCut else null
        cutActive = cut != null
        val feats = ArrayList<Feature>()
        fun addRun(seg: List<LL>, firstIdx: Int) = trafficRuns(seg, firstIdx, ::level, feats)
        val pa = cut?.firstOrNull()?.let { MapRouteGeometry.projectOn(pts, it) }
        val pb = cut?.lastOrNull()?.let { MapRouteGeometry.projectOn(pts, it) }
        if (pa != null && pb != null && pb.first > pa.first) {
            // before the arrow: up to the arrow's start point
            addRun(pts.subList(0, pa.first + 1) + pa.second, 0)
            // after the arrow: from its end point on
            addRun(listOf(pb.second) + pts.subList(pb.first + 1, pts.size), pb.first)
        } else addRun(pts, 0)
        s.featureCollection(FeatureCollection.fromFeatures(feats))
    }


    private fun pushRoute() {
        setLine(main)
        src("dl-alts")?.featureCollection(FeatureCollection.fromFeatures(ArrayList(alts.filter { it.size >= 2 }.map { line(it) })))
    }

    private var hazards: List<Pair<LL, Int>> = emptyList()
    /** The route's badges' size by zoom, times each one's own scale ("sc": the camera size setting for speed cameras, 1 for the rest). */
    private val HAZ_SIZE = "[\"interpolate\",[\"linear\"],[\"zoom\"]," +
        listOf(11 to 0.45, 15 to 0.75, 18 to 1.0).joinToString(",") { (z, v) -> "$z,[\"*\",$v,[\"coalesce\",[\"get\",\"sc\"],1]]" } + "]"
    /** Map icons by kind: camera, roadworks, closure, police, accident, hazard, breakdown, mobile camera. */
    private val hazardNames = arrayOf("dl-hz-cam", "dl-hz-work", "dl-hz-closed", "dl-hz-police", "dl-hz-accident", "dl-hz-hazard", "dl-hz-breakdown", "dl-hz-mobilecam")

    /** Icons for speed cameras (0), roadworks (1) and closures (2) along the route. */
    fun setHazards(list: List<Pair<LL, Int>>) { hazards = list; pushHazards() }

    // ---- the speed camera badge's size (Settings > Map look > On your route > Speed camera size): 50% to 350% ----
    private var camScale = 1.0
    fun setCameraSize(pct: Int) {
        val v = pct.coerceIn(50, 350) / 100.0
        if (v == camScale) return
        val before = camDetail()
        camScale = v
        val st = style ?: return
        if (camDetail() != before) addImg(st, "dl-hz-cam", cameraBitmap())
        try { if (st.styleLayerExists("dl-cams")) Value.fromJson(camsSize()).value?.let { st.setStyleLayerProperty("dl-cams", "icon-size", it) } } catch (e: Throwable) { }
        pushHazards()
    }
    /** How many times over the camera badge is drawn: twice over (for sharpness) once it's set above 150%. */
    private fun camDetail() = if (camScale > 1.5) 2 else 1
    /** Every camera around you: the badge's size by zoom, times the setting. */
    private fun camsSize(): String {
        fun f(x: Double) = String.format(java.util.Locale.US, "%.3f", x * camScale)
        return "[\"interpolate\",[\"linear\"],[\"zoom\"],8,${f(0.36)},12,${f(0.44)},15,${f(0.68)},18,${f(0.9)}]"
    }

    private fun pushHazards() {
        val s = src("dl-hazards") ?: return
        val fs = hazards.map { (p, k) ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also {
                it.addStringProperty("img", hazardNames[k.coerceIn(0, hazardNames.size - 1)])
                // the speed camera badge follows the size setting; roadworks, closures and reports stay as they are
                it.addNumberProperty("sc", if (k == 0) camScale else 1.0)
            }
        }
        s.featureCollection(FeatureCollection.fromFeatures(fs))
    }
    /** Round badge with a simple glyph: camera on white with a red ring, amber warning triangle, red no-entry. */
    /**
     * The speed camera badge: a solid red disc with a thick white ring, a thin dark rim round the outside and a white camera, so it
     * stands out on any map colour, day or night. Drawn larger than the other badges (it shows bigger on the map).
     */
    private fun cameraBitmap(): Bitmap {
        val n = 136
        // drawn with extra pixels when the size setting makes it big (the same size on the map, but sharp rather than blurry)
        val k = camDetail()
        val bmp = Bitmap.createBitmap(n * k, n * k, Bitmap.Config.ARGB_8888)
        if (k > 1) bmp.density = bmp.density * k
        val c = Canvas(bmp)
        c.scale(k.toFloat(), k.toFloat())
        val cx = n / 2f
        fun p(col: Int, st: Paint.Style = Paint.Style.FILL, w: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = st; strokeWidth = w }
        // a soft shadow under it
        c.drawCircle(cx, cx + 4f, 60f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
        c.drawCircle(cx, cx, 60f, p(0xFF1A1A1A.toInt()))           // dark rim
        c.drawCircle(cx, cx, 57f, p(0xFFFFFFFF.toInt()))           // white ring
        // the red face, a touch lighter at the top
        c.drawCircle(cx, cx, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, cx - 46f, 0f, cx + 46f, 0xFFFF3B3B.toInt(), 0xFFD0101E.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        // white camera: body, viewfinder bump, lens with a red centre
        val white = p(0xFFFFFFFF.toInt())
        c.drawRoundRect(android.graphics.RectF(cx - 27f, cx - 13f, cx + 27f, cx + 21f), 7f, 7f, white)
        c.drawRoundRect(android.graphics.RectF(cx - 11f, cx - 23f, cx + 11f, cx - 11f), 4f, 4f, white)
        c.drawCircle(cx, cx + 4f, 12f, p(0xFFD0101E.toInt()))
        c.drawCircle(cx, cx + 4f, 6.5f, white)
        return bmp
    }

    private fun hazardBitmap(kind: Int): Bitmap {
        if (kind == 0) return cameraBitmap()
        val n = 96
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = n / 2f
        fun p(col: Int, st: Paint.Style = Paint.Style.FILL, w: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = st; strokeWidth = w }
        // a round badge with a bold mark in the middle, for reports
        fun badge(ring: Int, fill: Int, mark: String) {
            c.drawCircle(cx, cx + 2f, 38f, p(0x55000000))
            c.drawCircle(cx, cx, 38f, p(0xFFFFFFFF.toInt()))
            c.drawCircle(cx, cx, 31f, p(fill))
            c.drawCircle(cx, cx, 38f, p(ring, Paint.Style.STROKE, 6f))
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 46f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            c.drawText(mark, cx, cx + 16f, tp)
        }
        when (kind) {
            4 -> badge(0xFFB71C1C.toInt(), 0xFFE53935.toInt(), "!")
            5 -> badge(0xFFE65100.toInt(), 0xFFF9A825.toInt(), "!")
            6 -> {
                badge(0xFFE65100.toInt(), 0xFFFB8C00.toInt(), "")
                // a small car
                c.drawRoundRect(android.graphics.RectF(cx - 18f, cx - 4f, cx + 18f, cx + 9f), 5f, 5f, p(0xFFFFFFFF.toInt()))
                c.drawRoundRect(android.graphics.RectF(cx - 11f, cx - 13f, cx + 11f, cx - 2f), 5f, 5f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx - 10f, cx + 11f, 4.5f, p(0xFF37474F.toInt()))
                c.drawCircle(cx + 10f, cx + 11f, 4.5f, p(0xFF37474F.toInt()))
            }
            7 -> {
                c.drawCircle(cx, cx + 2f, 38f, p(0x55000000))
                c.drawCircle(cx, cx, 38f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx, cx, 38f, p(0xFF1E66F5.toInt(), Paint.Style.STROKE, 7f))
                c.drawRoundRect(android.graphics.RectF(cx - 19f, cx - 9f, cx + 19f, cx + 15f), 5f, 5f, p(0xFF37474F.toInt()))
                c.drawRoundRect(android.graphics.RectF(cx - 8f, cx - 16f, cx + 8f, cx - 8f), 3f, 3f, p(0xFF37474F.toInt()))
                c.drawCircle(cx, cx + 3f, 8f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx, cx + 3f, 4.5f, p(0xFF37474F.toInt()))
            }
            1 -> {
                val tri = Path().apply { moveTo(cx, 10f); lineTo(n - 8f, n - 14f); lineTo(8f, n - 14f); close() }
                val rounded = android.graphics.CornerPathEffect(10f)
                c.save(); c.translate(0f, 3f)
                c.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; pathEffect = rounded })
                c.restore()
                c.drawPath(tri, p(0xFFFFFFFF.toInt()).apply { pathEffect = rounded })
                val inner = Path().apply { moveTo(cx, 22f); lineTo(n - 22f, n - 23f); lineTo(22f, n - 23f); close() }
                c.drawPath(inner, p(0xFFF9A825.toInt()).apply { pathEffect = android.graphics.CornerPathEffect(6f) })
                c.drawRoundRect(android.graphics.RectF(cx - 3.5f, 42f, cx + 3.5f, 62f), 3f, 3f, p(0xFF2B2B2B.toInt()))
                c.drawCircle(cx, 70f, 4.2f, p(0xFF2B2B2B.toInt()))
            }
            2 -> {
                c.drawCircle(cx, cx + 2f, 38f, p(0x55000000))
                c.drawCircle(cx, cx, 38f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx, cx, 30f, p(0xFFE53935.toInt()))
                c.drawRoundRect(android.graphics.RectF(cx - 18f, cx - 5f, cx + 18f, cx + 5f), 4f, 4f, p(0xFFFFFFFF.toInt()))
            }
            3 -> {
                c.drawCircle(cx, cx + 2f, 38f, p(0x55000000))
                c.drawCircle(cx, cx, 38f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx, cx, 38f, p(0xFF1E66F5.toInt(), Paint.Style.STROKE, 7f))
                // shield
                val sh = Path().apply {
                    moveTo(cx - 14f, cx - 15f); lineTo(cx + 14f, cx - 15f); lineTo(cx + 14f, cx + 2f)
                    quadTo(cx + 14f, cx + 14f, cx, cx + 20f); quadTo(cx - 14f, cx + 14f, cx - 14f, cx + 2f); close()
                }
                c.drawPath(sh, p(0xFF1E66F5.toInt()))
                c.drawCircle(cx, cx - 1f, 5f, p(0xFFFFFFFF.toInt()))
            }
            else -> {
                c.drawCircle(cx, cx + 2f, 38f, p(0x55000000))
                c.drawCircle(cx, cx, 38f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx, cx, 38f, p(0xFFE53935.toInt(), Paint.Style.STROKE, 7f))
                // camera glyph: body, viewfinder bump and lens
                c.drawRoundRect(android.graphics.RectF(cx - 19f, cx - 9f, cx + 19f, cx + 15f), 5f, 5f, p(0xFF37474F.toInt()))
                c.drawRoundRect(android.graphics.RectF(cx - 8f, cx - 16f, cx + 8f, cx - 8f), 3f, 3f, p(0xFF37474F.toInt()))
                c.drawCircle(cx, cx + 3f, 8f, p(0xFFFFFFFF.toInt()))
                c.drawCircle(cx, cx + 3f, 4.5f, p(0xFF37474F.toInt()))
            }
        }
        return bmp
    }
    // ---- lane arrows painted on the road ----
    class LaneMark(val pt: LL, val bearing: Double, val dir: String, val on: Boolean)
    private val laneImgs = HashSet<String>()
    private var lanePulse: android.animation.ValueAnimator? = null

    private fun laneBitmap(dir: String, on: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(64, 80, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ang = when (dir) {
            "SLIGHT_LEFT" -> -45f; "LEFT" -> -90f; "SHARP_LEFT" -> -135f; "LEFT_U_TURN" -> -180f
            "SLIGHT_RIGHT" -> 45f; "RIGHT" -> 90f; "SHARP_RIGHT" -> 135f; "RIGHT_U_TURN" -> 180f
            else -> 0f
        }
        fun draw(p: Paint) {
            c.drawLine(32f, 74f, 32f, 44f, p)
            c.save(); c.rotate(ang, 32f, 44f)
            c.drawLine(32f, 44f, 32f, 18f, p)
            c.drawLine(32f, 18f, 22f, 28f, p)
            c.drawLine(32f, 18f, 42f, 28f, p)
            c.restore()
        }
        val out = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 12f; color = if (on) 0x66000000 else 0x99000000.toInt() }
        draw(out)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8f
            color = if (on) 0xFFFFFFFF.toInt() else 0xDDFFFFFF.toInt()
        }
        draw(fill)
        return bmp
    }

    class LaneBand(val ring: List<LL>, val on: Boolean, val a: Double = 1.0)
    class LaneEdge(val pts: List<LL>, val solid: Boolean)

    /** Draws the lane strips and dividers (empty lists clear them). */
    fun setLaneBands(bands: List<LaneBand>, divs: List<LaneEdge>) {
        main0.post {
            val st = style ?: return@post
            try {
                val bf = ArrayList<Feature>()
                for (b in bands) {
                    if (b.ring.size < 4) continue
                    val pts = (b.ring + b.ring[0]).map { Point.fromLngLat(it.lon, it.lat) }
                    val f = Feature.fromGeometry(Polygon.fromLngLats(listOf(pts)))
                    f.addNumberProperty("on", if (b.on) 1 else 0)
                    f.addNumberProperty("a", b.a)
                    bf.add(f)
                }
                src("dl-lane-bands")?.featureCollection(FeatureCollection.fromFeatures(bf))
                val df = ArrayList<Feature>()
                for (d in divs) if (d.pts.size >= 2) df.add(Feature.fromGeometry(LineString.fromLngLats(d.pts.map { Point.fromLngLat(it.lon, it.lat) })).also { it.addNumberProperty("s", if (d.solid) 1 else 0) })
                src("dl-lane-divs")?.featureCollection(FeatureCollection.fromFeatures(df))
            } catch (e: Throwable) { }
        }
    }

    fun setLanes(marks: List<LaneMark>) {
        if (marks.isEmpty()) setLaneBands(emptyList(), emptyList())
        main0.post {
            val st = style ?: return@post
            val feats = ArrayList<Feature>()
            for (m in marks) {
                val name = "ln-${m.dir}-${if (m.on) 1 else 0}"
                if (laneImgs.add(name)) addImg(st, name, laneBitmap(m.dir, m.on))
                val f = Feature.fromGeometry(Point.fromLngLat(m.pt.lon, m.pt.lat))
                f.addStringProperty("img", name)
                f.addNumberProperty("rot", m.bearing)
                f.addNumberProperty("on", if (m.on) 1 else 0)
                feats.add(f)
            }
            src("dl-lanes")?.featureCollection(FeatureCollection.fromFeatures(feats))
            val layer = lanesOnLayer
            lanePulse?.cancel(); lanePulse = null
            if (feats.isEmpty()) { try { layer?.iconOpacity(1.0) } catch (e: Throwable) { }; return@post }
            lanePulse = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
                // only four brightness steps, so the map is not asked to redraw on every screen refresh just for a blink
                var lastStep = -1
                addUpdateListener {
                    val step = Math.round((it.animatedValue as Float) * 3f)
                    if (step != lastStep) { lastStep = step; try { layer?.iconOpacity(0.7 + 0.3 * step / 3.0) } catch (e: Throwable) { } }
                }
                start()
            }
        }
    }

    // ---- data from the engine ----
    fun setRoute(mainPts: List<LL>, altPts: List<List<LL>>, sev: IntArray? = null) {
        main = mainPts; alts = altPts; mainSev = sev; mainOff = 0; pushRoute(); pushSignals()
        if (mainPts.isEmpty()) { setAvgZones(emptyList(), emptyList()); setTunnels(emptyList()); setCrossings(emptyList()); setStopSigns(emptyList()) }
        if (mainPts.size >= 2) loadSignalsAlong(mainPts) else { sigRoute = null; signalToken++; signalBusy = false; signalStage = "no route" }
        val on = mainPts.isNotEmpty()
        if (on != routing) { routing = on; applyTraffic(); applyNearLook() }
        syncFlow()
    }

    // ---- live alternatives while driving ----
    private var liveLines: List<List<LL>> = emptyList()
    private var liveBubs: List<LL> = emptyList()
    private var liveDiffs: List<Int> = emptyList()
    private var liveTraffic: List<Int> = emptyList()
    private val bubImgs = HashSet<String>()

    /** A rounded time bubble with a little tail: green when it is quicker, dark when it is slower. Drawn at 2x and shown at half size. */
    /**
     * The card on another route, like Google Maps: "4 min slower" or "3 min faster" in bold, and under it how its traffic compares
     * with yours. Faster ones are green, the rest dark. A small point underneath sits on the route.
     */
    private fun bubbleBitmap(diff: Int, traffic: Int = 0): Bitmap {
        val d = mapView.resources.displayMetrics.density * 2f
        // (in the app's language: these were always English, so on a Polish unit the card said "1 min slower, Similar traffic", desko1973, #297)
        val top = if (diff < 0) L.t("{0} min faster", -diff) else if (diff > 0) L.t("{0} min slower", diff) else L.t("Same time")
        val sub = when (traffic) { -1 -> L.t("Less traffic"); 1 -> L.t("More traffic"); else -> L.t("Similar traffic") }
        // day: a light card with dark writing, like the rest of the day look; night: the dark card. A faster way is green in both
        val day = !nightStyle
        val green = diff < 0
        val lightCard = day && !green
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lightCard) 0xFF14171C.toInt() else android.graphics.Color.WHITE; textSize = 16f * d; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lightCard) 0xFF59616D.toInt() else 0xE6FFFFFF.toInt(); textSize = 12.5f * d }
        val tw = maxOf(tp.measureText(top), sp.measureText(sub))
        val w = (tw + 26f * d).toInt(); val bh = 50f * d; val tail = 9f * d
        val b = Bitmap.createBitmap(w + (6 * d).toInt(), (bh + tail + 6 * d).toInt(), Bitmap.Config.ARGB_8888)
        // marked with the map's own screen density: left unmarked it took the phone's, so on the car screen (a lower density) the
        // card came out about a third of its size and couldn't be read (desko1973, #240)
        b.density = mapView.resources.displayMetrics.densityDpi
        val c = Canvas(b)
        val ox = 3f * d; val oy = 3f * d
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (green) 0xFF1E9E5A.toInt() else if (day) 0xFFF7F8FB.toInt() else 0xFF2B3142.toInt() }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lightCard) 0x40141A24 else 0xE6FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = (if (lightCard) 1.4f else 2.2f) * d }
        val path = Path().apply {
            addRoundRect(RectF(ox, oy, ox + w, oy + bh), 14f * d, 14f * d, Path.Direction.CW)
            moveTo(ox + w / 2f - tail, oy + bh - 1); lineTo(ox + w / 2f, oy + bh + tail); lineTo(ox + w / 2f + tail, oy + bh - 1); close()
        }
        // a soft shadow just under it
        c.save(); c.translate(0f, 1.5f * d)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (day) 0x30000000 else 0x40000000 })
        c.restore()
        c.drawPath(path, fill); c.drawPath(path, edge)
        c.drawText(top, ox + (w - tp.measureText(top)) / 2f, oy + 22f * d, tp)
        // the traffic line, with a little coloured dot: green less, amber similar, red more
        val dotCol = if (lightCard) when (traffic) { -1 -> 0xFF1E9E5A.toInt(); 1 -> 0xFFE0453A.toInt(); else -> 0xFFE09A00.toInt() }
            else when (traffic) { -1 -> 0xFF5BE38F.toInt(); 1 -> 0xFFFF6B5E.toInt(); else -> 0xFFFFC94D.toInt() }
        val sw = sp.measureText(sub); val dotR = 3.6f * d
        val sx = ox + (w - (sw + dotR * 2 + 5f * d)) / 2f
        c.drawCircle(sx + dotR, oy + 38f * d, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotCol })
        c.drawText(sub, sx + dotR * 2 + 5f * d, oy + 42.5f * d, sp)
        return b
    }


    /** The other ways to go, in grey, with a bubble on each saying how it compares. Empty lists clear them. */
    fun setLiveAlts(lines: List<List<LL>>, bubbles: List<LL>, diffs: List<Int>, traffic: List<Int> = emptyList()) {
        liveLines = lines; liveBubs = bubbles; liveDiffs = diffs; liveTraffic = traffic; cardSpots = emptyList()
        main0.post { pushLive(); placeAltCards(true) }
    }

    // where each alternative's card is shown: moved along its line so it's always on screen (null when its line is off screen)
    private var cardSpots: List<LL?> = emptyList()
    private var cardsAt = 0L

    /** Screen positions of many places at once (one call to the map rather than one each). */
    private fun pixels(pts: List<LL>): List<ScreenCoordinate> =
        try { mbMap.pixelsForCoordinates(pts.map { Point.fromLngLat(it.lon, it.lat) }) } catch (e: Throwable) { emptyList() }

    /**
     * Keeps every alternative's card on screen: the card goes on the first stretch of its line that's in the clear part of the map
     * (not under the cards down the side, the buttons or the edges), as near the turn-off as possible. Checked a few times a second
     * while the map moves and once when it stops.
     */
    private fun placeAltCards(now: Boolean) {
        if (liveLines.isEmpty()) return
        val t = android.os.SystemClock.uptimeMillis()
        if (!now && t - cardsAt < 350L) return
        cardsAt = t
        val w = mapView.width.toFloat(); val h = mapView.height.toFloat()
        if (w < 50 || h < 50) return
        val dp = mapView.resources.displayMetrics.density
        // the card sits above its point, about 190 x 65 dp: keep the whole card on screen, clear of the round buttons down the side
        val halfW = 100f * dp; val cardH = 68f * dp; val btn = 84f * dp
        // first choice: the clear part of the map (not under the cards down the side or the top bar); else anywhere on screen
        val side = w * 0.40f
        val strict = floatArrayOf(
            if (cardsMirrored) btn + halfW else side + halfW, h * 0.14f + cardH,
            if (cardsMirrored) w - side - halfW else w - btn - halfW, h * 0.84f)
        val loose = floatArrayOf(halfW + 12f * dp, cardH + 12f * dp, w - halfW - 12f * dp, h - 24f * dp)
        val spots = liveLines.map { line ->
            if (line.size < 2) return@map null
            // look along the line (about 150 points at most), from the turn-off outwards
            val step = maxOf(1, line.size / 150)
            val sample = ArrayList<LL>()
            var i = minOf(2, line.size - 1)
            while (i < line.size) { sample += line[i]; i += step }
            val px = pixels(sample)
            if (px.size != sample.size) return@map null
            fun first(b: FloatArray): LL? {
                for (k in sample.indices) { val p = px[k]; if (p.x >= b[0] && p.x <= b[2] && p.y >= b[1] && p.y <= b[3]) return sample[k] }
                return null
            }
            first(strict) ?: first(loose)
        }
        val zoom = try { mbMap.cameraState.zoom } catch (e: Throwable) { 15.0 }
        val moved = spots.size != cardSpots.size || spots.indices.any { k ->
            val a = spots[k]; val b = cardSpots.getOrNull(k)
            if (a == null || b == null) a != b else Geo.dist(a, b) > (if (zoom > 14) 25.0 else 150.0)
        }
        if (moved) { cardSpots = spots; pushCards() }
    }

    /** Set by the screen: the cards are on the right (left-hand drive), so the clear part of the map is on the left. */
    var cardsMirrored = false

    private fun pushLive() {
        if (style == null) return
        try {
            val fl = ArrayList<Feature>()
            liveLines.forEachIndexed { i, l -> if (l.size >= 2) fl += line(l).also { it.addNumberProperty("i", i) } }
            src("dl-live-alts")?.featureCollection(FeatureCollection.fromFeatures(fl))
        } catch (e: Exception) { }
        stylePicked()
        pushCards()
    }

    // the other route tapped once (fahadfadi): drawn in yellow on top of the others until it's tapped again to switch to it; -1 none
    private var pickedLive = -1

    /** Shows which other route has been tapped once ([i] its number; -1 for none). */
    fun setPickedAlt(i: Int) { main0.post { if (pickedLive != i) { pickedLive = i; stylePicked() } } }

    private fun stylePicked() {
        val ly = layer("dl-live-alts-line") as? LineLayer ?: return
        val base = prefs.s(PreferenceKeys.ALT_ROUTE_COLOUR, "#C4D9E6")
        try {
            val i = pickedLive
            if (i < 0) { ly.lineColor(base); ly.lineSortKey(0.0) }
            else {
                ly.lineColor(Expression.fromRaw("[\"case\",[\"==\",[\"get\",\"i\"],$i],\"#F0B429\",\"$base\"]"))
                ly.lineSortKey(Expression.fromRaw("[\"case\",[\"==\",[\"get\",\"i\"],$i],1,0]"))
            }
        } catch (e: Exception) { }
    }

    /** Where each card is on the map right now (for taps), by alternative number. */
    private var shownCards: List<Pair<Int, LL>> = emptyList()

    /** Just the cards (moved often as the map moves; the lines themselves are only sent when they change). */
    private fun pushCards() {
        val st = style ?: return
        try {
            val fb = ArrayList<Feature>()
            val shown = ArrayList<Pair<Int, LL>>()
            liveBubs.forEachIndexed { i, p0 ->
                // the on-screen spot if it's been worked out, else the starting one; nothing when the whole line is off screen
                val p = if (i < cardSpots.size) (cardSpots[i] ?: return@forEachIndexed) else p0
                val diff = liveDiffs.getOrElse(i) { 0 }
                val tr = liveTraffic.getOrElse(i) { 0 }
                val nm = "dl-bub-$diff-$tr-${if (nightStyle) "n" else "d"}"
                if (bubImgs.add(nm)) addImg(st, nm, bubbleBitmap(diff, tr))
                fb += Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addNumberProperty("i", i); it.addStringProperty("img", nm) }
                shown += i to p
            }
            shownCards = shown
            src("dl-live-bub")?.featureCollection(FeatureCollection.fromFeatures(fb))
        } catch (e: Exception) { }
    }

    // ---- rain radar ----
    private var radarUrl: String? = null

    /** Shows the live rain radar tiles under the route ([url] with {z}/{x}/{y}), or hides it with null. */
    fun setRadar(url: String?) {
        radarUrl = url
        main0.post { pushRadar() }
    }

    private fun pushRadar() {
        val st = style ?: return
        try {
            if (st.styleLayerExists("dl-radar")) st.removeStyleLayer("dl-radar")
            if (st.styleSourceExists("dl-radar")) st.removeStyleSource("dl-radar")
            val u = radarUrl ?: return
            addS(st, RasterSource.Builder("dl-radar").tiles(listOf(u)).tileSize(256).minzoom(0).maxzoom(7).build())
            addL(st, RasterLayer("dl-radar", "dl-radar").inSlot(MID).rasterOpacity(0.55).rasterFadeDuration(0.0).rasterEmissiveStrength(1.0), "dl-alts-line")
        } catch (e: Exception) { }
    }

    // ---- satellite from TomTom, when Mapbox turns its own away ----
    private var satFallbackUrl: String? = null
    /** True once TomTom's satellite pictures are standing in for Mapbox's (for the rest of this run). */
    val satFallbackOn: Boolean get() = satFallbackUrl != null

    /**
     * Mapbox has been refusing its satellite pictures for many people since late October 2026 ("429 Too Many Requests", even well
     * inside their limits), which left the satellite view a plain background. With a TomTom key, TomTom's satellite pictures
     * ([url] with {z}/{x}/{y}) are laid in under Mapbox's roads and names instead. TomTom's free allowance is 50,000 map
     * pictures a day.
     */
    fun setSatFallback(url: String?) {
        satFallbackUrl = url
        main0.post { pushSatFallback() }
    }

    private fun pushSatFallback() {
        val st = style ?: return
        try {
            if (st.styleLayerExists("dl-ttsat")) st.removeStyleLayer("dl-ttsat")
            if (st.styleSourceExists("dl-ttsat")) st.removeStyleSource("dl-ttsat")
            val u = satFallbackUrl ?: return
            // (only on the satellite view, and only where Standard's slots keep it under the roads, names and the app's own layers)
            if (!satellite || !slotsOn) return
            addS(st, RasterSource.Builder("dl-ttsat").tiles(listOf(u)).tileSize(256).minzoom(0).maxzoom(19).attribution("© TomTom").build())
            addL(st, RasterLayer("dl-ttsat", "dl-ttsat").inSlot("bottom").rasterFadeDuration(0.0).rasterEmissiveStrength(1.0), "dl-roadq")
        } catch (e: Exception) { }
    }

    // ---- cinematic route preview ----
    private var flyToken = 0
    private var flyDone: (() -> Unit)? = null

    /**
     * A short fly-through of a new route: the camera swoops low along it in 3D (about 3 s), then [done] runs (the overview).
     * Touching the map stops it straight away and goes to the overview.
     */
    fun flyRoute(pts: List<LL>, cum: DoubleArray, len: Double, tilt: Double = 60.0, full: Boolean = false, done: () -> Unit) {
        if (pts.size < 2) { done(); return }
        val token = ++flyToken
        flyDone = done
        val zoom = if (full) (if (len < 30_000) 15.2 else if (len < 120_000) 13.8 else 12.6)
            else when { len < 5_000 -> 15.0; len < 30_000 -> 13.0; len < 120_000 -> 11.2; len < 300_000 -> 10.0; else -> 9.0 }
        fun at(f: Double): Pair<LL, Double> {
            val d = len * f
            var i = cum.indexOfFirst { it >= d }; if (i < 1) i = 1
            val a = pts[i - 1]; val b = pts[min(pts.size - 1, i + 3)]
            return pts[i] to Geo.bearing(a, b)
        }
        // the full fly-over ("Fly over route"): about 20 to 40 seconds along the whole drive, closer in than the quick one
        val frames = if (!full) listOf(0.04 to 900, 0.45 to 1100, 0.9 to 1000) else {
            val n = 16
            val total = (len / 1000.0 * 1300.0).coerceIn(18_000.0, 40_000.0)
            (0 until n).map { i -> (0.02 + 0.96 * i / (n - 1)) to (total / n).toInt() }
        }
        fun step(n: Int) {
            if (token != flyToken) return
            if (n >= frames.size) { flyDone = null; done(); return }
            val (f, ms) = frames[n]
            val (c, br) = at(f)
            tiltNow = tilt
            val cam = CameraOptions.Builder().center(Point.fromLngLat(c.lon, c.lat)).zoom(zoom).pitch(tilt).bearing(br)
                .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0)).build()
            var cancelled = false
            try {
                mbMap.easeTo(cam, MapAnimationOptions.mapAnimationOptions { duration(ms.toLong()) }, object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        cancelled = true
                        if (token == flyToken) { flyToken++; flyDone = null; done() }
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) step(n + 1) }
                })
            } catch (e: Throwable) { if (token == flyToken) { flyToken++; flyDone = null; done() } }
        }
        step(0)
    }

    /** Stops a fly-through without running its ending. */
    fun stopFly() { flyToken++; flyDone = null }

    /** Ends a route fly-over now, as a tap on the map does (its finish runs: back to the overview). */
    fun endFly() { flyDone?.let { d -> flyDone = null; flyToken++; try { mapView.camera.cancelAllAnimators() } catch (e: Throwable) { }; d() } }

    /** The part of the map's width the route panel covers down the left (a phone held sideways), so the route fits beside it. */
    @Volatile var previewSide = 0.0

    // ---- flowing light along the route ----
    private var flowOn = false
    private var flowWanted = true
    private var flowAnim: android.animation.ValueAnimator? = null
    private var flowLastPush = 0L

    /** A soft band of white light that brightens towards the middle; [p] is where it is along the line (0..1). */
    private fun flowGradient(p: Float): Expression {
        val c = p.coerceIn(0.07f, 0.93f).toDouble()
        fun w(a: Double) = Expression.rgba(255.0, 255.0, 255.0, a)
        return Expression.interpolate(
            Expression.linear(), Expression.lineProgress(),
            lit(0.0), w(0.0),
            lit(c - 0.06), w(0.0),
            lit(c), w(0.7),
            lit(c + 0.06), w(0.0),
            lit(1.0), w(0.0),
        )
    }

    /** Switches the flowing light on or off (it only runs while there is a route). */
    fun setFlow(on: Boolean) { flowWanted = on; syncFlow() }

    private fun syncFlow() {
        val want = flowWanted && routing
        if (want == flowOn) return
        flowOn = want
        main0.post {
            flowAnim?.cancel(); flowAnim = null
            try { flowLayer?.visibility(vis(flowOn)) } catch (e: Exception) { }
        }
    }

    /**
     * Moves the flowing light on a step. Called by the engine on the same beat as the camera, so the light and the camera change in
     * the same drawn frame (on its own timer the map was redrawn up to twice as often as needed, which a slow head unit can't keep up with).
     */
    fun flowFrame() {
        if (!flowOn) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - flowLastPush < 30) return
        flowLastPush = now
        try { flowLayer?.lineGradient(flowGradient((now % 3200L) / 3200f)) } catch (e: Exception) { }
    }

    private var pulseAnim: android.animation.ValueAnimator? = null

    /** Pulses the glow under the route line (used when a turn is close; the Modern look keeps the glow steady). */
    fun setTurnPulse(on: Boolean) {
        if (theme == "immersive") return
        if (on == (pulseAnim != null)) return
        main0.post {
            pulseAnim?.cancel(); pulseAnim = null
            val layer = glowLayer
            if (!on) {
                try { layer?.lineOpacity(0.4); layer?.lineWidth(routeW(1.7f)) } catch (e: Throwable) { }
            } else {
                pulseAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 650; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
                    addUpdateListener {
                        val v = (it.animatedValue as Float).toDouble()
                        try { layer?.lineOpacity(0.35 + 0.55 * v); layer?.lineWidth(18.0 + 16.0 * v) } catch (e: Throwable) { }
                    }
                    start()
                }
            }
        }
    }

    /** Size of the locator arrow (and its glow) relative to normal: 1.0 is the base size. */
    var arrowScale = 2.4f
        private set

    // ---- 3D locator ----
    /** "arrow" (3D arrow), "car" (3D car) or "flat" (the flat picture). */
    var locatorStyle = "arrow"
        private set
    private var locatorColor = "#F2F2F6"
    private var modelZoom = -1.0
    private var spriteName: String? = null
    private var spriteDrawing: String? = null
    private var spriteYaw = 0.0
    private var spriteRot = 0.0
    private var spriteWanted: String? = null

    fun setLocator(styleName: String, color: String) {
        // the real 3D model: [color] is "<arrow|car>|<colour>"; when it can't be shown (Lite, Lightest, satellite) the picture of the
        // same shape and colour is used
        val wantModel = styleName == "model"
        val oldModel = Triple(modelWanted, modelShape, modelColour + modelGlow + ownStamp)
        val oldTurn = ownTurn
        if (wantModel) {
            val parts = color.split('|')
            modelGlow = parts.getOrNull(2) != "0"
            // (your own model, but its file has gone: the car)
            modelShape = when (parts.getOrNull(0)) { "car" -> "car"; "own" -> if (OwnModel.file(ctx) != null) "own" else "car"; else -> "arrow" }
            modelColour = parts.getOrNull(1)?.takeIf { it.startsWith("#") } ?: (if (modelShape != "arrow") "#F4F5F7" else "#F2F2F6")
            ownTurn = parts.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            ownStamp = parts.getOrNull(4) ?: ""
            if (modelShape == "own") ownSide = OwnModel.side(ctx, Prefs(ctx))
        }
        // (your own model that wouldn't load isn't tried again until a different file is picked)
        if (modelShape == "own" && ownStamp != ownFailedStamp) ownFailed = false
        modelWanted = wantModel && !modelBroken && !(modelShape == "own" && ownFailed)
        // your own picture, but none saved (or it was deleted): the 3D arrow
        val noPic = styleName == "custom" && !java.io.File(ctx.filesDir, "locator.png").exists()
        // (your own model has no picture of its own: where it can't be drawn, the car picture stands in)
        locatorStyle = when { wantModel -> if (modelShape == "own") "car" else modelShape; noPic -> "arrow"; else -> styleName }
        locatorColor = when { wantModel -> modelColour; noPic -> "#F2F2F6"; else -> color }
        if (style == null) return
        // the model's layers are put in (or taken out, or recoloured) with the rest of the app's layers
        if (oldModel != Triple(modelWanted, modelShape, modelColour + modelGlow + ownStamp)) reinstall() else {
            applyLocatorVisibility()
            // turned: only the model's direction changes
            if (oldTurn != ownTurn && modelOn) { modelHead = -999.0; moveModel() }
        }
        spriteName = null
        pushModel(true)
    }

    // ---- the real 3D model locator (Full 3D only) ----
    private var modelWanted = false
    /** The 3D model failed to load once: it isn't tried again this run. */
    private var modelBroken = false
    private var modelShape = "arrow"
    private var modelColour = "#F2F2F6"
    /** Your own model (shape "own"): how far it's turned, which copy of the file, and its longest side in its own units. */
    private var ownTurn = 0.0
    private var ownStamp = ""
    private var ownSide = 4.4
    /** Your own model couldn't be loaded: the car picture is shown instead, and Settings says so. */
    @Volatile var ownFailed = false
        private set
    private var ownFailedStamp = ""
    /** The arrow or a car-sized model (the built-in car or your own). */
    private val carLike: Boolean get() = modelShape != "arrow"
    /** Settings > Your arrow > Glow: a soft blue glow on the ground round the model, and the model fully lit. */
    private var modelGlow = true
    /** The 3D model is on the map now (it's then drawn instead of the picture). */
    private var modelOn = false
    private var modelAt: LL? = null
    private var modelHead = -999.0

    /** Screen pixels per model unit (the model is built in the same units as the picture's shape). */
    // (your own model is brought to the built-in car's length first, whatever scale it was made at)
    private fun modelPxPerUnit(): Double = arrowScale * (if (carLike) 300.0 / 4.4 * 0.6 * 0.28 * (if (modelShape == "own") 4.4 / ownSide.coerceIn(1e-4, 1e6) else 1.0) else 300.0 * 0.42 * 0.31)

    /**
     * Screen pixels per "car unit" (a metre of the built-in car, which is 4.4 long). Your own model is sized by its own units, which
     * can be anything (millimetres, centimetres, or tiny units): the glow, the headlight beam and the car's outline are sized in car
     * units, so they fit round your model the same as round the built-in car (Matattack99: the glow filled nearly the whole screen).
     */
    private fun modelPxPerCarUnit(): Double = modelPxPerUnit() * (if (modelShape == "own") ownSide.coerceIn(1e-4, 1e6) / 4.4 else 1.0)

    /** The glow's radius (map pixels): round the model, in step with its size on screen. */
    private fun modelGlowRadius(): Double = modelPxPerCarUnit() / max(0.1, pixelRatioUsed.toDouble()) * (if (carLike) 2.2 else 1.45)

    /**
     * The model's size: the same size on screen at every zoom, exactly like the picture locator. Mapbox's location-indicator models
     * are sized in screen pixels per model unit ("viewport" scale mode), in the map's own pixels (screen pixels / pixel ratio).
     */
    private fun modelScale(): List<Double> {
        val s = modelPxPerUnit() / max(0.1, pixelRatioUsed.toDouble())
        return listOf(s, s, s)
    }

    private fun modelSourceValue(uri: String, p: LL?, head: Double): Value {
        val m = HashMap<String, Value>()
        m["uri"] = Value(uri)
        m["position"] = Value(listOf(Value(p?.lon ?: 0.0), Value(p?.lat ?: 0.0)))
        m["orientation"] = Value(listOf(Value(0.0), Value(0.0), Value(head + (if (modelShape == "own") ownTurn else 0.0))))
        val models = HashMap<String, Value>()
        models["me"] = Value(m)
        return Value(models)
    }

    /** The real 3D car's files for the chosen car type (Settings > Your arrow > Car type); the saloon is the original model. */
    private fun carUri(part: String): String = if (carModel == "saloon" || carModel !in CAR_MODELS) "asset://models/car_$part.glb" else "asset://models/car_${part}_$carModel.glb"

    /** The model files: the coloured part and the untinted part (your own model is one file, used for both, the second hidden). */
    private fun modelUris(): Pair<String, String> = when (modelShape) {
        "car" -> carUri("body") to carUri("parts")
        "own" -> (OwnModel.uri(ctx) ?: carUri("body")).let { it to it }
        else -> "asset://models/arrow.glb" to "asset://models/disc.glb"
    }

    private fun installModel(st: Style) {
        val car = carLike
        val own = modelShape == "own"
        val p = me
        val (uriA, uriB) = modelUris()
        try {
            for ((id, uri) in listOf("dl-me-ma" to uriA, "dl-me-mb" to uriB)) {
                val props = HashMap<String, Value>()
                props["type"] = Value("model")
                props["models"] = modelSourceValue(uri, p, meHeading)
                st.addStyleSource(id, Value(props))
            }
            val scale = modelScale()
            // the glow: a soft, blurred blue circle lying on the ground round the disc (under the model, same size on screen at every zoom)
            if (modelGlow) {
                addS(st, GeoJsonSource.Builder("dl-me-glow").featureCollection(FeatureCollection.fromFeatures(
                    if (p != null) listOf(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))) else emptyList())).build())
                addL(st, CircleLayer("dl-me-glow-l", "dl-me-glow").inSlot(TOP)
                    .circleRadius(modelGlowRadius()).circleBlur(0.85).circleColor("#4C9BE6").circleOpacity(0.55)
                    .circlePitchAlignment(CirclePitchAlignment.MAP).circleEmissiveStrength(1.0))
            }
            // the untinted part first (the blue disc under the arrow, or the car's glass, lights and tyres), then the coloured part
            // set up the way Mapbox's own location marker is (a "location indicator" model, no shadows): the tested path
            addL(st, ModelLayer("dl-me-model-b", "dl-me-mb").modelType(ModelType.LOCATION_INDICATOR).modelScaleMode(ModelScaleMode.VIEWPORT).modelScale(scale)
                .modelCastShadows(false).modelReceiveShadows(false).modelEmissiveStrength(if (car) (if (modelGlow) 0.85 else 0.6) else 1.0)
                .visibility(vis(p != null && !own)))
            // the arrow / car in your colour. In the default white it keeps the model's own shading (a white top over a grey-blue
            // edge, matched to the picture locator); a chosen colour tints it all. Mostly self-lit, so night doesn't grey it out
            addL(st, ModelLayer("dl-me-model-a", "dl-me-ma").modelType(ModelType.LOCATION_INDICATOR).modelScaleMode(ModelScaleMode.VIEWPORT).modelScale(scale)
                // (your own model keeps its own colours)
                .modelColor(modelColour).modelColorMixIntensity(if (own || modelColour.equals(if (car) "#F4F5F7" else "#F2F2F6", true)) 0.0 else 1.0)
                .modelCastShadows(false).modelReceiveShadows(false).modelEmissiveStrength(if (modelGlow) 1.0 else if (car) 0.75 else 0.9)
                .visibility(vis(p != null)))
            modelOn = st.styleLayerExists("dl-me-model-a")
            modelAt = p; modelHead = meHeading
        } catch (e: Throwable) { modelOn = false; android.util.Log.w("MapBinding", "3D model: ${e.message}") }
    }

    /** Moves the 3D model to where the arrow is now (cheap: only its position and heading change, nothing is re-read). */
    private fun moveModel() {
        val st = style ?: return
        val p = me ?: return
        val prev = modelAt
        if (prev != null && Math.abs(prev.lat - p.lat) < 1e-8 && Math.abs(prev.lon - p.lon) < 1e-8 && Math.abs(modelHead - meHeading) < 0.2) return
        val first = prev == null
        modelAt = p; modelHead = meHeading
        val (uriA, uriB) = modelUris()
        try {
            st.setStyleSourceProperty("dl-me-ma", "models", modelSourceValue(uriA, p, meHeading))
            st.setStyleSourceProperty("dl-me-mb", "models", modelSourceValue(uriB, p, meHeading))
            if (modelGlow && (first || prev == null || Math.abs(prev.lat - p.lat) > 1e-8 || Math.abs(prev.lon - p.lon) > 1e-8))
                src("dl-me-glow")?.featureCollection(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))))
            if (first) { layer("dl-me-model-a")?.visibility(vis(true)); layer("dl-me-model-b")?.visibility(vis(modelShape != "own")) }
        } catch (e: Throwable) { }
    }

    /** Sprites are drawn at 2.4x; this brings them to the same on-screen size the flat arrow had (the car a little longer). */
    private fun spriteSize(): Float = arrowScale * 1.22f * (if (locatorStyle == "car") 0.28f else 0.31f)

    private fun shade(hex: String, f: Float): Int {
        val c = try { android.graphics.Color.parseColor(hex) } catch (e: Exception) { -1 }
        fun ch(v: Int) = (v * f).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(ch(android.graphics.Color.red(c)), ch(android.graphics.Color.green(c)), ch(android.graphics.Color.blue(c)))
    }

    /** A shape with soft corners: every corner of [pts] is replaced by a small curve of radius about [r] (in the same units). */
    private fun soften(pts: List<Pair<Double, Double>>, r: Double, steps: Int = 5): List<Pair<Double, Double>> {
        val n = pts.size
        val out = ArrayList<Pair<Double, Double>>()
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]
            val d1 = Math.hypot(p1.first - p0.first, p1.second - p0.second); val d2 = Math.hypot(p2.first - p1.first, p2.second - p1.second)
            val rr = minOf(r, d1 * 0.45, d2 * 0.45)
            val a = (p1.first + (p0.first - p1.first) * rr / d1) to (p1.second + (p0.second - p1.second) * rr / d1)
            val b = (p1.first + (p2.first - p1.first) * rr / d2) to (p1.second + (p2.second - p1.second) * rr / d2)
            for (k in 0..steps) {
                val t = k / steps.toDouble(); val u = 1 - t
                out += (u * u * a.first + 2 * u * t * p1.first + t * t * b.first) to (u * u * a.second + 2 * u * t * p1.second + t * t * b.second)
            }
        }
        return out
    }

    private fun rrect(x0: Double, x1: Double, y0: Double, y1: Double, r: Double) = soften(listOf(x1 to y1, x0 to y1, x0 to y0, x1 to y0), r, 4)

    /** One solid piece of the model: a footprint pushed up from [z0] to [z1], in one colour. */
    private class Prism(val pts: List<Pair<Double, Double>>, val z0: Double, val z1: Double, val col: Int)

    private fun arrowParts(col: String): List<Prism> {
        // the same soft chevron as the flat arrow, now solid: a dark edge underneath and the arrow on top
        // (listed anticlockwise, so the walls face outwards)
        // very soft corners, like the flat arrow: a round nose, rounded wing tips and a smooth curve at the back
        // sized and shaped to match the flat arrow: it fills a little over half the ring's width, with a deep, smooth curve at the back
        val raw = listOf(0.0 to 0.58, -0.64 to -0.53, 0.0 to -0.28, 0.64 to -0.53)
        val soft = soften(soften(raw, 0.3, 8), 0.06, 2)
        // centre the arrow exactly on the ring: move it so the middle of its area sits on the centre point,
        // and mirror the right half onto the left so it is perfectly symmetrical from every angle
        var a2 = 0.0; var cyA = 0.0
        for (i in soft.indices) {
            val p0 = soft[i]; val p1 = soft[(i + 1) % soft.size]
            val cr = p0.first * p1.second - p1.first * p0.second
            a2 += cr; cyA += (p0.second + p1.second) * cr
        }
        val cy = if (Math.abs(a2) > 1e-9) cyA / (3.0 * a2) else 0.0
        val half = soft.filter { it.first <= 1e-9 }.map { 0.0.coerceAtMost(it.first) to it.second - cy }
        val right = half.asReversed().map { -it.first to it.second }.filter { it.first > 1e-9 }
        val shape = if (half.size >= 3) half + right else soft.map { it.first to it.second - cy }
        return listOf(Prism(shape, 0.0, 0.19, shade(col, 1f)))
    }

    /** Which car the "Car" locator is: saloon, hatch, estate, suv, pickup, van or sports (Settings > Your arrow). */
    @Volatile var carModel = "saloon"
        private set
    fun setCarModel(m: String) {
        val v = if (m in CAR_MODELS) m else "saloon"
        if (v == carModel) return
        carModel = v
        main0.post {
            spriteBmps.clear(); spriteName = null
            // the real 3D car: point it at the new car type's model files (only the two model sources change, nothing else is redrawn)
            try {
                val st = style
                if (st != null && modelOn && modelShape == "car") {
                    st.setStyleSourceProperty("dl-me-ma", "models", modelSourceValue(carUri("body"), me ?: modelAt, meHeading))
                    st.setStyleSourceProperty("dl-me-mb", "models", modelSourceValue(carUri("parts"), me ?: modelAt, meHeading))
                }
            } catch (e: Throwable) { }
            try { pushModel(true) } catch (e: Throwable) { }
        }
    }

    /** A small top-down picture of a car model in a colour, for choosing one in Settings. */
    fun carPreview(model: String, col: String): Bitmap = renderSprite(carParts(col, model), 300.0 / 5.4, 0.0, 0.0, true)

    private fun carParts(col: String, model: String = carModel): List<Prism> {
        val body = shade(col, 1f); val lower = shade(col, 0.9f); val roof = shade(col, 1.05f)
        val glass = 0xFF2C4F8C.toInt(); val dark = 0xFF1B1E26.toInt(); val red = 0xFFE3262F.toInt()
        val l = ArrayList<Prism>()
        fun wheels(front: Double, back: Double, x: Double = 0.98, r: Double = 0.36, h: Double = 0.52) {
            for (sx in listOf(-1.0, 1.0)) for (sy in listOf(back, front)) l += Prism(rrect(sx * x - 0.13, sx * x + 0.13, sy - r, sy + r, 0.12), 0.0, h, 0xFF101114.toInt())
        }
        fun tails(y: Double, x: Double, z0: Double, z1: Double) {
            l += Prism(rrect(-x, -x + 0.32, y, y + 0.25, 0.12), z0, z1, red); l += Prism(rrect(x - 0.32, x, y, y + 0.25, 0.12), z0, z1, red)
        }
        fun mirrors(y: Double, x: Double, z: Double) {
            l += Prism(rrect(-x - 0.18, -x, y, y + 0.26, 0.08), z, z + 0.14, dark); l += Prism(rrect(x, x + 0.18, y, y + 0.26, 0.08), z, z + 0.14, dark)
        }
        when (model) {
            // a short, rounded hatchback
            "hatch" -> {
                wheels(1.2, -1.15)
                l += Prism(rrect(-0.9, 0.9, -1.92, -1.62, 0.28), 0.1, 0.36, dark)
                l += Prism(rrect(-1.0, 1.0, -1.86, 1.95, 0.8), 0.24, 0.6, lower)
                l += Prism(rrect(-1.06, 1.06, -1.82, 1.9, 0.9), 0.6, 0.98, body)
                tails(-1.87, 0.96, 0.5, 1.0)
                mirrors(0.3, 1.04, 0.96)
                l += Prism(rrect(-0.88, 0.88, -1.6, 0.75, 0.62), 0.98, 1.44, glass)
                l += Prism(rrect(-0.8, 0.8, -1.5, 0.48, 0.55), 1.44, 1.6, roof)
            }
            // a long estate with a long flat roof
            "estate" -> {
                wheels(1.42, -1.5)
                l += Prism(rrect(-0.92, 0.92, -2.42, -2.1, 0.28), 0.1, 0.36, dark)
                l += Prism(rrect(-1.02, 1.02, -2.36, 2.2, 0.7), 0.24, 0.6, lower)
                l += Prism(rrect(-1.08, 1.08, -2.32, 2.14, 0.8), 0.6, 0.98, body)
                tails(-2.37, 0.98, 0.46, 0.95)
                mirrors(0.42, 1.04, 0.96)
                l += Prism(rrect(-0.9, 0.9, -2.08, 0.95, 0.55), 0.98, 1.42, glass)
                l += Prism(rrect(-0.82, 0.82, -1.98, 0.66, 0.45), 1.42, 1.58, roof)
            }
            // a tall, square SUV with roof rails
            "suv" -> {
                wheels(1.45, -1.4, 1.02, 0.42, 0.66)
                l += Prism(rrect(-0.98, 0.98, -2.3, -2.0, 0.22), 0.14, 0.46, dark)
                l += Prism(rrect(-1.08, 1.08, -2.24, 2.24, 0.55), 0.3, 0.78, lower)
                l += Prism(rrect(-1.14, 1.14, -2.2, 2.18, 0.62), 0.78, 1.22, body)
                tails(-2.25, 1.04, 0.66, 1.18)
                mirrors(0.5, 1.1, 1.2)
                l += Prism(rrect(-0.98, 0.98, -2.0, 1.0, 0.4), 1.22, 1.74, glass)
                l += Prism(rrect(-0.92, 0.92, -1.92, 0.78, 0.34), 1.74, 1.88, roof)
                l += Prism(rrect(-0.84, -0.74, -1.8, 0.6, 0.04), 1.88, 1.96, dark)
                l += Prism(rrect(0.74, 0.84, -1.8, 0.6, 0.04), 1.88, 1.96, dark)
            }
            // a pickup: a cab at the front and an open load bed behind
            "pickup" -> {
                wheels(1.5, -1.55, 1.04, 0.42, 0.66)
                l += Prism(rrect(-1.0, 1.0, -2.5, -2.22, 0.18), 0.14, 0.46, dark)
                l += Prism(rrect(-1.1, 1.1, -2.44, 2.3, 0.5), 0.3, 0.78, lower)
                l += Prism(rrect(-1.14, 1.14, -0.25, 2.26, 0.6), 0.78, 1.2, body)
                l += Prism(rrect(-1.14, 1.14, -2.42, -0.25, 0.14), 0.78, 0.86, dark)                      // the bed floor
                l += Prism(rrect(-1.14, -0.98, -2.42, -0.25, 0.06), 0.86, 1.24, body)                    // bed sides and tailgate
                l += Prism(rrect(0.98, 1.14, -2.42, -0.25, 0.06), 0.86, 1.24, body)
                l += Prism(rrect(-1.14, 1.14, -2.42, -2.28, 0.06), 0.86, 1.24, body)
                tails(-2.45, 1.06, 0.7, 1.16)
                mirrors(0.95, 1.1, 1.2)
                l += Prism(rrect(-0.98, 0.98, -0.2, 1.3, 0.4), 1.2, 1.72, glass)
                l += Prism(rrect(-0.92, 0.92, -0.12, 1.06, 0.34), 1.72, 1.86, roof)
            }
            // a tall box van
            "van" -> {
                wheels(1.75, -1.6, 1.04, 0.4, 0.62)
                l += Prism(rrect(-1.0, 1.0, -2.55, -2.3, 0.14), 0.12, 0.44, dark)
                l += Prism(rrect(-1.1, 1.1, -2.5, 2.35, 0.45), 0.26, 0.74, lower)
                l += Prism(rrect(-1.14, 1.14, -2.48, 2.3, 0.5), 0.74, 1.1, body)
                tails(-2.52, 1.06, 0.7, 1.4)
                mirrors(1.55, 1.1, 1.15)
                l += Prism(rrect(-1.0, 1.0, 1.25, 2.0, 0.4), 1.1, 1.72, glass)                           // the windscreen
                l += Prism(rrect(-1.14, 1.14, -2.48, 1.3, 0.24), 1.1, 2.15, body)                       // the load box
                l += Prism(rrect(-1.06, 1.06, -2.4, 1.25, 0.2), 2.15, 2.22, roof)
            }
            // a low, wide sports car with a little spoiler
            "sports" -> {
                wheels(1.3, -1.38, 1.04, 0.38, 0.46)
                l += Prism(rrect(-0.95, 0.95, -2.28, -2.0, 0.3), 0.08, 0.3, dark)
                l += Prism(rrect(-1.1, 1.1, -2.22, 2.28, 0.95), 0.18, 0.5, lower)
                l += Prism(rrect(-1.16, 1.16, -2.18, 2.24, 1.0), 0.5, 0.78, body)
                tails(-2.22, 1.02, 0.42, 0.72)
                mirrors(0.25, 1.1, 0.78)
                l += Prism(rrect(-0.84, 0.84, -1.0, 0.85, 0.7), 0.78, 1.12, glass)
                l += Prism(rrect(-0.72, 0.72, -0.78, 0.5, 0.6), 1.12, 1.22, roof)
                l += Prism(rrect(-0.98, 0.98, -2.2, -1.96, 0.08), 0.86, 0.94, dark)                       // spoiler
            }
            else -> {
        // the saloon: a chunky, friendly, cartoon car in the style of the Google Maps one: short and wide, very rounded, a big domed
        // glasshouse, a fat white roof, tall red tail lights in the rear corners, a dark under-bumper and black mirrors. Length 4.4.
        for (sx in listOf(-1.0, 1.0)) for (sy in listOf(-1.38, 1.42)) l += Prism(rrect(sx * 0.98 - 0.13, sx * 0.98 + 0.13, sy - 0.36, sy + 0.36, 0.12), 0.0, 0.52, 0xFF101114.toInt())
        l += Prism(rrect(-0.92, 0.92, -2.24, -1.9, 0.3), 0.1, 0.36, dark)                // under-bumper
        l += Prism(rrect(-1.02, 1.02, -2.16, 2.2, 0.85), 0.24, 0.6, lower)             // lower body
        l += Prism(rrect(-1.08, 1.08, -2.12, 2.14, 0.95), 0.6, 0.98, body)             // big rounded shoulders
        l += Prism(rrect(-0.98, -0.66, -2.17, -1.92, 0.12), 0.46, 0.95, red)           // tall tail lights in the corners
        l += Prism(rrect(0.66, 0.98, -2.17, -1.92, 0.12), 0.46, 0.95, red)
        l += Prism(rrect(-1.22, -1.04, 0.42, 0.68, 0.08), 0.96, 1.1, dark)              // mirrors
        l += Prism(rrect(1.04, 1.22, 0.42, 0.68, 0.08), 0.96, 1.1, dark)
        l += Prism(rrect(-0.9, 0.9, -1.35, 0.95, 0.7), 0.98, 1.42, glass)             // domed glasshouse
        l += Prism(rrect(-0.8, 0.8, -1.05, 0.66, 0.62), 1.42, 1.58, roof)              // fat roof
            }
        }
        return l
    }

    /**
     * Draws the 3D model as seen from the camera: [yaw] is the model's heading relative to the top of the screen, [pitch] the map tilt.
     * Faces are flat-shaded (lit from the top left of the screen) and painted back to front.
     */
    private fun renderSprite(parts: List<Prism>, unitPx: Double, yaw: Double, pitch: Double, car: Boolean): Bitmap {
        val w = 300; val h = 300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ox = w / 2.0; val oy = h / 2.0
        val cy = Math.cos(Math.toRadians(yaw)); val sy = Math.sin(Math.toRadians(yaw))
        val cp = Math.cos(Math.toRadians(pitch)); val sp = Math.sin(Math.toRadians(pitch))
        fun rot(x: Double, y: Double) = (x * cy + y * sy) to (-x * sy + y * cy)
        fun proj(x: Double, y: Double, z: Double): Pair<Float, Float> {
            val (X, Y) = rot(x, y)
            return (ox + X * unitPx).toFloat() to (oy - (Y * cp + z * sp) * unitPx).toFloat()
        }
        fun depth(x: Double, y: Double, z: Double): Double { val (_, Y) = rot(x, y); return -Y * sp + z * cp }
        // soft glow / shadow on the road
        val glowR = if (car) 1.45 else 0.95
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (car) 0x667FC8FF else 0x9E2F78AE.toInt()
            if (car) maskFilter = android.graphics.BlurMaskFilter((unitPx * 0.45).toFloat(), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val ring = Path()
        for (k in 0..40) { val a = 2 * Math.PI * k / 40; val (px, py) = proj(Math.cos(a) * glowR, Math.sin(a) * glowR * (if (car) 2.0 else 1.0), 0.0); if (k == 0) ring.moveTo(px, py) else ring.lineTo(px, py) }
        ring.close()
        c.drawPath(ring, glowPaint)
        if (!car) c.drawPath(ring, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xB39ED0E2.toInt() })
        // light comes from the viewer's upper left
        val lx = -0.35; val ly = 0.45
        // each face: its outline on screen, depth, base colour and how brightly it's lit; walls get a top-to-bottom shade, tops a soft sheen
        class Face(val path: Path, val d: Double, val col: Int, val top: Boolean, val y0: Float, val y1: Float)
        val faces = ArrayList<Face>()
        for (pr in parts) {
            val n = pr.pts.size
            for (i in 0 until n) {
                val a = pr.pts[i]; val b = pr.pts[(i + 1) % n]
                // outward normal of this wall (footprints are anticlockwise), turned to the screen
                var nx = b.second - a.second; var ny = -(b.first - a.first)
                val nl = Math.hypot(nx, ny); if (nl < 1e-9) continue
                nx /= nl; ny /= nl
                val (NX, NY) = rot(nx, ny)
                // walls facing away from the camera can't be seen
                if (-NY * sp < -0.02 && pitch > 2) continue
                val bright = (0.62 + 0.38 * (NX * lx + NY * ly)).coerceIn(0.42, 1.04)
                val p1 = proj(a.first, a.second, pr.z0); val p2 = proj(b.first, b.second, pr.z0)
                val p3 = proj(b.first, b.second, pr.z1); val p4 = proj(a.first, a.second, pr.z1)
                val path = Path().apply { moveTo(p1.first, p1.second); lineTo(p2.first, p2.second); lineTo(p3.first, p3.second); lineTo(p4.first, p4.second); close() }
                val mx = (a.first + b.first) / 2; val my = (a.second + b.second) / 2
                faces += Face(path, depth(mx, my, (pr.z0 + pr.z1) / 2) - 0.001, scaleCol(pr.col, bright), false, minOf(p3.second, p4.second), maxOf(p1.second, p2.second))
            }
            val top = Path()
            var ty0 = Float.MAX_VALUE; var ty1 = -Float.MAX_VALUE
            pr.pts.forEachIndexed { i, q -> val (px, py) = proj(q.first, q.second, pr.z1); ty0 = minOf(ty0, py); ty1 = maxOf(ty1, py); if (i == 0) top.moveTo(px, py) else top.lineTo(px, py) }
            top.close()
            val cxm = pr.pts.sumOf { it.first } / n; val cym = pr.pts.sumOf { it.second } / n
            faces += Face(top, depth(cxm, cym, pr.z1) + 0.002, pr.col, true, ty0, ty1)
        }
        faces.sortBy { it.d }
        // a soft cartoon outline round the whole shape: every face stroked thickly underneath, then filled on top, so only the
        // outside edge of the stroke shows (much cheaper than joining all the faces into one outline)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = if (car) 6.4f else 7.2f; strokeJoin = Paint.Join.ROUND; color = 0x8A0E1A33.toInt() }
        for (f in faces) c.drawPath(f.path, outline)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG)
        val seam = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; strokeJoin = Paint.Join.ROUND }
        for (f in faces) {
            val lightC = if (f.top) scaleCol(f.col, 1.04) else scaleCol(f.col, 1.06)
            val darkC = if (f.top) scaleCol(f.col, 0.86) else scaleCol(f.col, 0.78)
            fp.shader = if (f.y1 - f.y0 > 1f) android.graphics.LinearGradient(0f, f.y0, 0f, f.y1, lightC, darkC, android.graphics.Shader.TileMode.CLAMP) else null
            fp.color = f.col
            c.drawPath(f.path, fp)
            // a hairline in the face colour hides tiny gaps between neighbouring faces
            seam.color = scaleCol(f.col, 0.95); c.drawPath(f.path, seam)
        }
        return bmp
    }

    private fun scaleCol(c: Int, f: Double): Int {
        fun ch(v: Int) = (v * f).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(android.graphics.Color.alpha(c), ch(android.graphics.Color.red(c)), ch(android.graphics.Color.green(c)), ch(android.graphics.Color.blue(c)))
    }

    /**
     * The 3D locator is an Android view laid over the map, not a picture inside it. A new picture is needed every 8 degrees of
     * turn and 5 of tilt, and adding a picture to the map makes it lay out every label and icon on screen again: going round a
     * roundabout (a full turn in a few seconds) did that dozens of times and the map dropped to a few frames a second. Swapping
     * the picture of a view costs nothing, and the view is moved to the car's spot on screen after every camera move.
     */
    val locatorView: android.widget.ImageView by lazy {
        android.widget.ImageView(ctx).apply {
            isClickable = false; isFocusable = false
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = android.view.View.GONE
        }
    }
    /** The road-name pill under the locator, also a view over the map. */
    val roadView: android.widget.ImageView by lazy {
        android.widget.ImageView(ctx).apply {
            isClickable = false; isFocusable = false
            scaleType = android.widget.ImageView.ScaleType.FIT_XY
            visibility = android.view.View.GONE
        }
    }
    private val spriteBmps = LinkedHashMap<String, Bitmap>()
    private var spriteBmp: Bitmap? = null

    /** Picks (and if needed draws, in the background) the picture for the current heading, map bearing and tilt. */
    private fun pushModel(force: Boolean) {
        if (locatorStyle == "flat" || modelOn) { placeLocator(); return }
        val cam = try { mbMap.cameraState } catch (e: Throwable) { return }
        modelZoom = cam.zoom
        val rel = ((meHeading - cam.bearing) % 360.0 + 360.0) % 360.0
        // pictures every 8 degrees of turn; the small difference in between is made up by turning the view itself, so the arrow/car
        // rotates perfectly smoothly instead of clicking from one picture to the next
        // your own picture: "<version>|<turns with you 1/0>|<circle 1/0>"
        val custom = locatorStyle == "custom"
        val cOpts = if (custom) locatorColor.split('|') else emptyList()
        val cTurns = cOpts.getOrNull(1) != "0"
        val cCircle = cOpts.getOrNull(2) == "1"
        val cClear = cOpts.getOrNull(3) != "0"
        val upright = custom && !cTurns
        val yawB = if (upright) 0L else (Math.round(rel / 8.0) * 8) % 360
        val pitB = if (upright) 0L else Math.round(cam.pitch / 5.0) * 5
        val key = "spr-$locatorStyle-${if (locatorStyle == "car") carModel else ""}-${locatorColor.removePrefix("#")}-$yawB-$pitB"
        if (key != spriteName || force) {
            val have = spriteBmps[key]
            if (have != null) {
                spriteBmps.remove(key); spriteBmps[key] = have      // most recently used last
                spriteName = key; spriteYaw = yawB.toDouble()
                if (spriteBmp !== have) { spriteBmp = have; locatorView.setImageBitmap(have) }
            } else if (spriteDrawing == null) {
                spriteDrawing = key
                val car = locatorStyle == "car"; val col = locatorColor
                Thread {
                    val bmp = try {
                        if (custom) customImage(cOpts.getOrNull(0) ?: "", cClear)?.let { renderCustom(it, yawB.toDouble(), pitB.toDouble(), !upright, cCircle) }
                        else if (car) renderSprite(carParts(col), 300.0 / 4.4 * 0.6, yawB.toDouble(), pitB.toDouble(), true)
                        else renderSprite(arrowParts(col), 300.0 * 0.42, yawB.toDouble(), pitB.toDouble(), false)
                    } catch (e: Throwable) { null }
                    main0.post {
                        spriteDrawing = null
                        if (bmp != null) {
                            spriteBmps[key] = bmp
                            // the last 48 pictures are kept (a full turn at one tilt is 45)
                            while (spriteBmps.size > 48) spriteBmps.remove(spriteBmps.keys.first())
                            pushModel(false)
                        } else spriteName = key      // couldn't draw it: don't keep retrying the same picture
                    }
                }.start()
            }
            // (while a picture is being drawn, the current one stays up, turned to the right angle)
        }
        spriteRot = if (upright) 0.0 else ((rel - spriteYaw + 540.0) % 360.0) - 180.0
        placeLocator()
    }

    @Volatile private var customBase: Bitmap? = null
    @Volatile private var customVer: String? = null

    /** Your locator picture (saved small by the settings page), loaded once per version. */
    private fun customImage(ver: String, clearWhite: Boolean = true): Bitmap? {
        val stamp = ver + "|" + (if (clearWhite) "1" else "0")
        if (customVer == stamp) return customBase
        val f = java.io.File(ctx.filesDir, "locator.png")
        var b = try { if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path) else null } catch (e: Throwable) { null }
        if (b != null && clearWhite) b = try { withoutWhiteBackground(b) } catch (e: Throwable) { b }
        customBase = b; customVer = stamp
        return b
    }

    /**
     * A picture saved with a solid white background (no real see-through part) gets that white made see-through, so it doesn't
     * show as a white box on the map. Only the white that touches the edge of the picture is removed, so white inside the
     * picture (a white car, say) stays. A picture that already has see-through pixels is returned as it is.
     */
    private fun withoutWhiteBackground(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        if (w < 2 || h < 2) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (v in px) if ((v ushr 24) < 250) return src
        fun white(v: Int): Boolean = ((v shr 16) and 0xFF) >= 235 && ((v shr 8) and 0xFF) >= 235 && (v and 0xFF) >= 235
        // all four corners must be white, otherwise the picture's edge isn't a white background
        if (!white(px[0]) || !white(px[w - 1]) || !white(px[(h - 1) * w]) || !white(px[h * w - 1])) return src
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        fun push(i: Int) { if (!seen[i] && white(px[i])) { seen[i] = true; stack[top++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (top > 0) {
            val i = stack[--top]
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        for (i in px.indices) if (seen[i]) px[i] = 0
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Your picture as the locator: the same size as the 3D arrow; when it turns with you it is also laid flat on the road
     * (squashed by the tilt, like the arrow), otherwise it stands upright facing you.
     */
    private fun renderCustom(base: Bitmap, yaw: Double, pitch: Double, lie: Boolean, circle: Boolean): Bitmap {
        val w = 300
        val bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val side = 190f
        val src = if (circle) {
            // cut to a circle with a thin white edge, for photos and square logos
            val n = minOf(base.width, base.height)
            val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val oc = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            oc.drawCircle(n / 2f, n / 2f, n / 2f, p)
            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            oc.drawBitmap(base, -(base.width - n) / 2f, -(base.height - n) / 2f, p)
            p.xfermode = null; p.style = Paint.Style.STROKE; p.strokeWidth = n * 0.045f; p.color = 0xFFFFFFFF.toInt()
            oc.drawCircle(n / 2f, n / 2f, n / 2f - p.strokeWidth / 2f, p)
            out
        } else base
        val sc = side / maxOf(src.width, src.height)
        val m = android.graphics.Matrix()
        m.postTranslate(-src.width / 2f, -src.height / 2f)
        m.postScale(sc, sc)
        if (lie) {
            m.postRotate(yaw.toFloat())
            m.postScale(1f, Math.cos(Math.toRadians(pitch)).toFloat().coerceAtLeast(0.35f))
        }
        m.postTranslate(w / 2f, w / 2f)
        // a soft shadow under it so it stands out on any map colour
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
        val shadow = src.extractAlpha()
        c.save(); c.translate(0f, 6f); c.drawBitmap(shadow, m, sh); c.restore()
        c.drawBitmap(src, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        shadow.recycle()
        if (src !== base) src.recycle()
        return bmp
    }

    private val drawnBottom = java.util.WeakHashMap<Bitmap, Float>()
    /** The flat arrow's picture (for spacing the road name under it). */
    private var flatArrow: Bitmap? = null

    /** How far below the picture's centre its drawing reaches (in the picture's pixels), measured once per picture. */
    private fun drawnBelowCentre(b: Bitmap): Float = drawnBottom.getOrPut(b) {
        val w = b.width; val h = b.height
        val row = IntArray(w)
        var bottom = h / 2
        for (y in h - 1 downTo h / 2) {
            b.getPixels(row, 0, w, 0, y, w, 1)
            if (row.any { (it ushr 24) > 40 }) { bottom = y; break }
        }
        (bottom - h / 2f).coerceAtLeast(0f)
    }

    /** Puts the locator view on the car's spot on screen (called after every camera move and every move of the car). */
    fun placeLocator() {
        try { placeBeam() } catch (e: Throwable) { }
        val v = locatorView
        val p = me
        val bmp = spriteBmp
        fun gone(x: android.view.View) { if (x.visibility != android.view.View.GONE) x.visibility = android.view.View.GONE }
        if (p == null || style == null) { gone(v); gone(roadView); return }
        val pt = try { mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat)).let { android.graphics.PointF(it.x.toFloat(), it.y.toFloat()) } } catch (e: Throwable) { return }
        // the road name: drawn at 2x and shown at half, its top a little below the locator (lower when the view is flatter)
        val rb = (roadView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (roadName.isBlank() || rb == null || roadNameHidden) gone(roadView) else {
            // (times the road name size from Settings > Display)
            val rw = (rb.width / 2 * roadNameScale).toInt(); val rh = (rb.height / 2 * roadNameScale).toInt()
            val rlp = roadView.layoutParams
            if (rlp == null || rlp.width != rw || rlp.height != rh) roadView.layoutParams = android.widget.FrameLayout.LayoutParams(rw, rh)
            roadView.translationX = pt.x - rw / 2f
            // just below whichever reaches lower: the blue disc under the arrow (flattened by the tilt) or the arrow / car picture
            val dens = mapView.resources.displayMetrics.density
            val tilt = try { mbMap.cameraState.pitch } catch (e: Throwable) { tiltNow }
            // the Classic look has a flat blue ring on the map under the arrow (the Modern look draws its ring in the picture itself)
            val discHalf = if (theme == "immersive") 0f else 15f * 0.625f * arrowScale * dens * Math.cos(Math.toRadians(tilt)).toFloat()
            // the picture has empty space round the arrow / car: measure where its drawing really ends at the bottom
            val below = if (modelOn) (modelPxPerCarUnit() * (if (carLike) 2.3 else 0.85) * Math.cos(Math.toRadians(tilt))).toFloat()
                        else if (locatorStyle != "flat" && bmp != null) drawnBelowCentre(bmp) * spriteSize()
                        else flatArrow?.let { drawnBelowCentre(it) * (if (theme == "immersive") 0.25f else 0.625f) * arrowScale * Math.cos(Math.toRadians(tilt)).toFloat() } ?: 0f
            roadView.translationY = pt.y + maxOf(discHalf, below) + 3f * dens
            if (roadView.visibility != android.view.View.VISIBLE) roadView.visibility = android.view.View.VISIBLE
        }
        if (modelOn) { gone(v); moveModel(); return }
        if (locatorStyle == "flat" || bmp == null) { gone(v); return }
        // the same size the map gave the picture before: its pixels times the icon size
        val k = spriteSize()
        val w = Math.round(bmp.width * k); val h = Math.round(bmp.height * k)
        val lp = v.layoutParams
        if (lp == null || lp.width != w || lp.height != h) v.layoutParams = android.widget.FrameLayout.LayoutParams(w, h)
        v.translationX = pt.x - w / 2f
        v.translationY = pt.y - h / 2f
        v.rotation = spriteRot.toFloat()
        if (v.visibility != android.view.View.VISIBLE) v.visibility = android.view.View.VISIBLE
    }
    /** A white chevron pointing along the line (to the right in the picture, which is "along the line" for line placement). */
    private fun chevronBitmap(): Bitmap {
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        c.drawPath(Path().apply { moveTo(20f, 12f); lineTo(42f, 32f); lineTo(20f, 52f) }, p)
        return b
    }

    /** Icon size for the arrow head at a zoom where the route line is [w] map pixels wide: about 2.4 times the shaft's width. */
    private fun headSize(w: Float): Float = 2.4f * 1.28f * w * ROUTE_K / 196f

    /**
     * The head of the turn arrow, pointing up, with its flat base along the bottom edge so it joins the shaft as one piece:
     * white with the same dark edge as the shaft, slightly rounded. Drawn at 1:1 map pixels (200 wide).
     */
    private fun turnHeadBitmap(day: Boolean): Bitmap {
        // the triangle is 196 wide inside a 220 wide picture, leaving room round it for the soft halo; its base is the bottom edge
        val b = Bitmap.createBitmap(220, 162, Bitmap.Config.ARGB_8888)
        b.density = android.util.DisplayMetrics.DENSITY_DEFAULT
        val c = Canvas(b)
        val ox = 12f; val top = 12f; val bot = 162f
        // the dark edge matches the shaft's: the shaft's edge is 0.1 of a route width each side, and the head is 3.07 route widths across
        val edgeW = 196f / 3.072f * 0.1f * 1.4f
        val outer = Path().apply { moveTo(ox + 98f, top); lineTo(ox + 196f, bot); lineTo(ox, bot); close() }
        if (day) c.drawPath(outer, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x5916467A; style = Paint.Style.STROKE; strokeWidth = 12f; strokeJoin = Paint.Join.ROUND
            pathEffect = android.graphics.CornerPathEffect(30f); maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        })
        c.drawPath(outer, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16467A.toInt(); pathEffect = android.graphics.CornerPathEffect(30f) })
        val inset = edgeW
        val inner = Path().apply { moveTo(ox + 98f, top + inset * 1.8f); lineTo(ox + 196f - inset * 1.7f, bot); lineTo(ox + inset * 1.7f, bot); close() }
        c.drawPath(inner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); pathEffect = android.graphics.CornerPathEffect(24f) })
        return b
    }

    private var chevOn = true
    /** Set by the engine: whether the turn arrow is switched on (so the route line is only cut when the arrow is really there). */
    var turnArrowsShown = true


    /** Chevrons along the route on or off (the turn arrow has its own setting, checked in the engine). */
    fun setRouteArrows(on: Boolean) {
        chevOn = on
        try { layer("dl-route-chev")?.visibility(vis(on)) } catch (e: Exception) { }
    }

    /** The arrow's size as set, before [pipShrink]. */
    private var baseArrowScale = 2.4f
    /** In the small picture-in-picture window the arrow is shrunk with the window (1 = full screen; #301). */
    @Volatile private var pipShrink = 1f
    fun setPipShrink(k: Float) {
        val v = k.coerceIn(0.2f, 1f)
        if (v == pipShrink) return
        pipShrink = v
        main0.post { setArrowScale(baseArrowScale) }
    }

    fun setArrowScale(want: Float) {
        baseArrowScale = want
        arrowScale = want * pipShrink
        val f = arrowScale
        val im = theme == "immersive"
        try {
            (layer("dl-me-arrow") as? SymbolLayer)?.iconSize((if (im) 0.25 else 0.625) * f)
            (layer("dl-me-halo") as? CircleLayer)?.circleRadius(15.0 * 0.625 * f)
            if (modelOn) { val sc = modelScale(); (layer("dl-me-model-a") as? ModelLayer)?.modelScale(sc); (layer("dl-me-model-b") as? ModelLayer)?.modelScale(sc)
                (layer("dl-me-glow-l") as? CircleLayer)?.circleRadius(modelGlowRadius()) }
        } catch (e: Exception) { }
        placeLocator()
    }

    /** The arrow at the next turn, drawn along the route through the junction; an empty list clears it. */
    private var turnCut: List<LL>? = null
    private var cutActive = false
    private fun cutZoomOk(): Boolean = (try { mbMap.cameraState.zoom } catch (e: Throwable) { 16.0 }) >= 13.0

    private var turnPath: List<LL> = emptyList()

    /** Puts the remembered turn arrow back (after the map style reloads, e.g. day to night, its layers start empty). */
    private fun pushTurn() {
        val path = turnPath
        try {
            if (path.size < 2) { src("dl-turn")?.featureCollection(empty()); src("dl-turn-head")?.featureCollection(empty()); return }
            src("dl-turn")?.feature(line(path))
            val a = path[path.size - 2]; val b = path.last()
            src("dl-turn-head")?.feature(Feature.fromGeometry(Point.fromLngLat(b.lon, b.lat)).also { it.addNumberProperty("b", Geo.bearing(a, b)) })
        } catch (e: Exception) { }
    }

    fun setTurnArrow(path: List<LL>) {
        main0.post {
            turnPath = path
            turnCut = if (path.size >= 2 && turnArrowsShown) path else null
            try { setLine(main) } catch (e: Exception) { }
            try {
                if (path.size < 2) { src("dl-turn")?.featureCollection(empty()); src("dl-turn-head")?.featureCollection(empty()); return@post }
                // the shaft runs right to the end, where the head's flat base sits on it
                src("dl-turn")?.feature(line(path))
                val a = path[path.size - 2]; val b = path.last()
                src("dl-turn-head")?.feature(Feature.fromGeometry(Point.fromLngLat(b.lon, b.lat)).also { it.addNumberProperty("b", Geo.bearing(a, b)) })
            } catch (e: Exception) { }
        }
    }
    private var roadName = ""
    private var roadOffTilt = -99.0

    /** How far below the locator the road pill sits: further down when the view is flat (ring is round), closer when tilted. */
    private fun roadOffset(): Float {
        val r = (if (theme == "immersive") 40f else 9.4f) * arrowScale
        val c = Math.cos(Math.toRadians(tiltNow))
        // (doubled: the offset is scaled by the pill's icon size of 0.5)
        return ((r * (0.2 + 0.22 * c) + 2f) * 2.0).toFloat()
    }

    /** The road you're on, in a Google-style pill: deep blue, fully rounded, bold white text, a soft shadow. Drawn at 2x (shown at half). */
    private fun roadBitmap(name: String): Bitmap {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); color = 0xFFFFFFFF.toInt(); letterSpacing = 0.02f }
        val txt = if (name.isBlank()) " " else name
        val maxText = 640f
        val shown = if (tp.measureText(txt) > maxText) android.text.TextUtils.ellipsize(txt, android.text.TextPaint(tp), maxText, android.text.TextUtils.TruncateAt.END).toString() else txt
        val w = tp.measureText(shown) + 64f
        val hgt = 80f
        val bmp = Bitmap.createBitmap((w + 16f).toInt(), (hgt + 18f).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rect = android.graphics.RectF(8f, 6f, 8f + w, 6f + hgt)
        c.drawRoundRect(android.graphics.RectF(rect).apply { offset(0f, 4f) }, hgt / 2, hgt / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
        c.drawRoundRect(rect, hgt / 2, hgt / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFF3B8BD0.toInt(), 0xFF2A72B5.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(android.graphics.RectF(rect).apply { inset(1.5f, 1.5f) }, hgt / 2, hgt / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = 0x40FFFFFF })
        c.drawText(shown, rect.left + 32f, rect.centerY() - (tp.descent() + tp.ascent()) / 2, tp)
        return bmp
    }

    /** Size of the road name pill under the locator (1 = normal), from Settings > Display. */
    private var roadNameScale = 1f
    fun setRoadNameScale(s: Float) {
        val v = s.coerceIn(0.5f, 2.5f)
        if (v == roadNameScale) return
        roadNameScale = v
        try { roadView.scaleType = android.widget.ImageView.ScaleType.FIT_XY } catch (e: Throwable) { }
        placeLocator()
    }

    /**
     * Hides the road name pill under the locator while the screen shows the road elsewhere (the "road · Heading NW" pill at the
     * bottom when driving with no route): the two used to sit on top of each other.
     */
    @Volatile private var roadNameHidden = false
    fun setRoadNameHidden(h: Boolean) {
        if (h == roadNameHidden) return
        roadNameHidden = h
        main0.post { try { placeLocator() } catch (e: Throwable) { } }
    }

    /** Shows the name of the road you are on in a small pill under the locator (empty hides it). */
    fun setRoadName(name: String) {
        if (name == roadName) return
        roadName = name
        // a view over the map: a new name used to be a new map picture, which made the map lay out all its labels again
        try { if (name.isNotBlank()) roadView.setImageBitmap(roadBitmap(name)) } catch (e: Exception) { }
        placeLocator()
    }

    /** Recolours the other-route lines straight away when the colour is changed in Settings (the style itself is only built once). */
    fun setAltColour(hex: String) {
        val c = try { android.graphics.Color.parseColor(hex); hex } catch (e: Exception) { return }
        main0.post {
            val st = style ?: return@post
            for (id in listOf("dl-alts-line", "dl-live-alts-line")) try {
                if (st.styleLayerExists(id)) st.setStyleLayerProperty(id, "line-color", Value.valueOf(c))
            } catch (e: Throwable) { }
        }
    }

    /**
     * The map only redraws when something on it changes, and while following you that's the camera, which the engine moves on a steady
     * beat set by Settings > Display > Frame rate. The map itself is left free to draw each of those updates the moment it arrives: capping
     * it at the same rate made it drop the odd update (its timer and the screen's never line up exactly), which looked jumpy.
     */
    fun setMaxFps(n: Int) { try { mapView.setMaximumFps(60) } catch (e: Exception) { } } // (deliberately not [n]: see above)

    private var doneEmpty = true

    /** The driven stretch of the route (a short trail behind the car), drawn in grey. */
    fun setDone(pts: List<LL>) {
        val s = src("dl-done") ?: return
        if (pts.size < 2) { if (!doneEmpty) { s.featureCollection(empty()); doneEmpty = true }; return }
        doneEmpty = false
        s.feature(line(pts))
    }

    fun setMainOnly(mainPts: List<LL>, sev: IntArray? = null, firstSeg: Int = 0) {
        main = mainPts; mainSev = sev; mainOff = firstSeg; setLine(main)
        // the lights are picked against the route: as the drawn stretch of route moves on, pick them again (they used to stay as
        // they were for the first stretch, so lights further along never appeared)
        pushSignals()
    }
    fun setDest(p: LL?) { dest = p; pushDest(); pushFlags() }

    // ---- 3D flags: a tall flag at the destination (and smaller ones at your stops) standing above the buildings ----
    private var flagsOn = true
    private var stopPts: List<LL> = emptyList()

    fun setFlags(on: Boolean) { if (on != flagsOn) { flagsOn = on; pushFlags() } }
    fun setStopFlags(stops: List<LL>) { if (stops != stopPts) { stopPts = stops; pushFlags() } }

    /** A flag at [p], [h] metres tall: a thin pole and a flag near its top, as 3D shapes ("part" pole / flag, "kind" dest / stop). */
    private fun flagShapes(p: LL, h: Double, kind: String): List<Feature> {
        val kx = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(p.lat))); val ky = 1.0 / 110_540.0
        fun box(x0: Double, y0: Double, x1: Double, y1: Double): com.mapbox.geojson.Polygon = com.mapbox.geojson.Polygon.fromLngLats(listOf(listOf(
            Point.fromLngLat(p.lon + x0 * kx, p.lat + y0 * ky), Point.fromLngLat(p.lon + x1 * kx, p.lat + y0 * ky),
            Point.fromLngLat(p.lon + x1 * kx, p.lat + y1 * ky), Point.fromLngLat(p.lon + x0 * kx, p.lat + y1 * ky), Point.fromLngLat(p.lon + x0 * kx, p.lat + y0 * ky))))
        val w = h / 45.0   // thicker for a taller flag
        val pole = Feature.fromGeometry(box(-0.5 * w, -0.5 * w, 0.5 * w, 0.5 * w)).also { it.addStringProperty("part", "pole"); it.addStringProperty("kind", kind); it.addNumberProperty("base", 0.0); it.addNumberProperty("top", h) }
        val flag = Feature.fromGeometry(box(0.5 * w, -0.25 * w, h * 0.22, 0.25 * w)).also { it.addStringProperty("part", "flag"); it.addStringProperty("kind", kind); it.addNumberProperty("base", h * 0.74); it.addNumberProperty("top", h * 0.98) }
        return listOf(pole, flag)
    }

    private fun pushFlags() {
        val s = src("dl-flags") ?: return
        val d = dest
        if (!flagsOn || d == null) { s.featureCollection(empty()); return }
        val fs = ArrayList<Feature>(flagShapes(d, 42.0, "dest"))
        for (q in stopPts.take(10)) fs += flagShapes(q, 28.0, "stop")
        s.featureCollection(FeatureCollection.fromFeatures(fs))
    }

    // ---- hill shading: soft shading on hillsides, even on a flat map (Settings > Light & 3D > 3D) ----
    private var hillsOn = false

    fun setHillShading(on: Boolean) {
        if (on == hillsOn) return
        hillsOn = on
        style?.let { installHills(it) }
    }

    private fun installHills(st: Style) {
        try {
            if (!hillsOn || satellite || !slotsOn || mapDetail == "lightest") { if (st.styleLayerExists("dl-hills")) st.removeStyleLayer("dl-hills"); return }
            if (!st.styleSourceExists("dl-dem")) st.addSource(Sky.demSource())
            if (st.styleLayerExists("dl-hills")) st.removeStyleLayer("dl-hills")
            val night = lightPreset == "night" || lightPreset == "dusk"
            addL(st, HillshadeLayer("dl-hills", "dl-dem").inSlot("bottom").maxZoom(17.0)
                .hillshadeExaggeration(if (night) 0.35 else 0.45)
                .hillshadeShadowColor(if (night) "#05070F" else "#3A4A5C")
                .hillshadeHighlightColor(if (night) "#2A3354" else "#FFFFFF")
                .hillshadeAccentColor(if (night) "#0B1020" else "#5A6B7A")
                .hillshadeIlluminationAnchor(com.mapbox.maps.extension.style.layers.properties.generated.HillshadeIlluminationAnchor.MAP))
        } catch (e: Throwable) { }
    }
    private var meAnim: android.animation.ValueAnimator? = null
    private var lastMePush = 0L

    /** Moves the arrow smoothly from where it is now to [p] over [durationMs] (one GPS interval). */
    /**
     * Opens the map where you were last time (saved every 15 s while driving), facing the same way, with your arrow already there,
     * instead of over London until the GPS answers.
     */
    fun startAt(p: LL, bearing: Double?) {
        try {
            val b = CameraOptions.Builder().center(Point.fromLngLat(p.lon, p.lat)).zoom(16.5).pitch(55.0)
            if (bearing != null) b.bearing(bearing)
            mbMap.setCamera(b.build())
        } catch (e: Throwable) { }
        try { setMe(p, bearing ?: 0.0, 0) } catch (e: Throwable) { }
    }

    fun setMe(p0: LL?, heading: Double = 0.0, durationMs: Long = 1000) {
        // in the lane stretch before a junction your arrow rides in the lane with the route line, not on the road's middle line
        val p = p0?.let { onShift(it) }
        meAnim?.cancel()
        if (durationMs <= 0 && p != null) {
            val o = me
            if (o != null && Math.abs(o.lat - p.lat) < 1e-8 && Math.abs(o.lon - p.lon) < 1e-8 && Math.abs(meHeading - heading) < 0.2) return
        }
        val from = me
        if (p == null || from == null || durationMs <= 0 || Geo.dist(from, p) > 300) {
            me = p; meHeading = heading; pushMe(); return
        }
        val fromH = meHeading
        val dH = ((heading - fromH + 540.0) % 360.0) - 180.0
        meAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { a ->
                val t = a.animatedFraction.toDouble()
                me = LL(from.lat + (p.lat - from.lat) * t, from.lon + (p.lon - from.lon) * t)
                meHeading = (fromH + dH * t + 360.0) % 360.0
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastMePush >= 66 || t >= 1.0) { lastMePush = now; pushMe() }
            }
            start()
        }
    }

    private fun pushDest() {
        val s = src("dl-dest") ?: return
        val d = dest
        if (d != null) s.feature(Feature.fromGeometry(Point.fromLngLat(d.lon, d.lat))) else s.featureCollection(empty())
    }

    private var meShownFlat = false

    private fun pushMe() {
        pushModel(false)
        updateClear(false)
        try { pushBeam() } catch (e: Throwable) { }
        // the map's own (flat) arrow only needs moving when it's the one showing; the 3D one is a view over the map
        val flat = locatorStyle == "flat"
        if (!flat && !meShownFlat) return
        val s = src("dl-me") ?: return
        val p = me
        if (p != null && flat) {
            val f = Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))
            f.addNumberProperty("h", meHeading)
            s.feature(f)
            meShownFlat = true
        } else { s.featureCollection(empty()); meShownFlat = false }
    }

    private fun applyLocatorVisibility() {
        try { layer("dl-me-arrow")?.visibility(vis(locatorStyle == "flat")) } catch (e: Exception) { }
        pushMe()
        placeLocator()
    }

    // ---- camera ----
    fun center(): LL? = try { mbMap.cameraState.center.let { LL(it.latitude(), it.longitude()) } } catch (e: Throwable) { null }

    /**
     * The part of the map that can actually be seen, as insets from the view's edges in pixels (on Android Auto its own cards and
     * buttons cover some of the car screen; null means the whole view). The locator and the follow camera are placed within it.
     */
    @Volatile var viewInset: android.graphics.Rect? = null
    /** How far down the locator may sit (as the top padding fraction): kept higher on tall, narrow car screens. */
    @Volatile var maxTopFrac = 1.0
    /** Pixels at the bottom of the visible part covered by the app's own cards (tall car screens), kept clear of the locator. */
    @Volatile var coverTarget = 0.0
    // eased towards coverTarget a little each frame, so the locator glides rather than jumps when a card comes or goes
    private var coverNow = 0.0
    private fun cover(): Double {
        val t = coverTarget
        coverNow += (t - coverNow) * 0.08
        if (abs(t - coverNow) < 1.0) coverNow = t
        return coverNow
    }

    /** Camera padding (left, top, right, bottom) for a locator place given as fractions, within [viewInset]. */
    private fun pads(topF: Double, leftF: Double): DoubleArray {
        val w = mapView.width.toDouble(); val h = mapView.height.toDouble()
        val tf = min(topF, maxTopFrac)
        val i = viewInset ?: return doubleArrayOf(w * max(0.0, leftF), h * tf, w * max(0.0, -leftF), 0.0)
        val bot = i.bottom + min(cover(), (h - i.top - i.bottom) * 0.55)
        val vw = max(1.0, w - i.left - i.right); val vh = max(1.0, h - i.top - bot)
        return doubleArrayOf(i.left + vw * max(0.0, leftF), i.top + vh * tf, i.right + vw * max(0.0, -leftF), bot)
    }

    /** Mapbox's padding is (top, left, bottom, right); the app's is (left, top, right, bottom). Kept inside the view so the map never refuses it. */
    private fun edge(pd: DoubleArray): EdgeInsets {
        val w = max(1.0, mapView.width.toDouble()); val h = max(1.0, mapView.height.toDouble())
        val l = pd[0].coerceIn(0.0, w * 0.95); val r = pd[2].coerceIn(0.0, max(0.0, w * 0.95 - l))
        val t = pd[1].coerceIn(0.0, h * 0.95); val b = pd[3].coerceIn(0.0, max(0.0, h * 0.95 - t))
        return EdgeInsets(t, l, b, r)
    }

    fun cameraTo(c: LL, bearing: Double?, tilt: Double, zoom: Double, ms: Int, topPadFraction: Double, leftPadFraction: Double = 0.0) {
        val pd = pads(topPadFraction, leftPadFraction)
        val b = CameraOptions.Builder().center(Point.fromLngLat(c.lon, c.lat)).pitch(tilt).zoom(zoom).padding(edge(pd))
        if (bearing != null) b.bearing(bearing)
        tiltNow = tilt
        try { mbMap.easeTo(b.build(), MapAnimationOptions.mapAnimationOptions { duration(max(1, ms).toLong()) }) } catch (e: Throwable) { }
    }

    /** Moves the camera instantly (no animation). Called every frame by the glide loop so motion never stalls between GPS fixes. */
    fun cameraSet(c: LL, bearing: Double, tilt: Double, zoom: Double, topPadFraction: Double, leftPadFraction: Double) {
        // a negative side fraction pads from the right instead (the mirrored, left-hand drive layout)
        val pd = pads(topPadFraction, leftPadFraction)
        tiltNow = tilt
        try {
            // an eased move still running would fight this one (the old map stopped it the same way)
            mapView.camera.cancelAllAnimators()
            mbMap.setCamera(CameraOptions.Builder().center(Point.fromLngLat(c.lon, c.lat)).pitch(tilt).zoom(zoom).bearing(bearing).padding(edge(pd)).build())
        } catch (e: Throwable) { }
        placeLocator()
    }

    // roads on screen from the last lamp search (night), for snapping your position while off-route
    @Volatile private var roadSegs: List<List<Point>> = emptyList()

    /** A road line from Mapbox's road data: its points, its kind ("motorway", "primary", "street"...) and whether it's one-way. */
    class RoadPiece(val pts: List<Point>, val cls: String, val oneway: Boolean?, val name: String = "")
    @Volatile private var roadInfo: List<RoadPiece> = emptyList()

    /**
     * The road at [p] running about the way [heading] goes (within 15 m), from the roads last read off the map: its kind and whether
     * it's one-way (null when the map hasn't shown that road yet).
     */
    fun roadAt(p: LL, heading: Double, maxDistance: Double = 15.0, headingTolerance: Double = 40.0): RoadPiece? {
        val list = roadInfo
        if (list.isEmpty() || android.os.SystemClock.uptimeMillis() - roadSegsAt > 30_000L) return null
        val k = Math.cos(Math.toRadians(p.lat)) * 111320.0; val k2 = 110574.0
        var best: RoadPiece? = null; var bd = maxDistance.coerceIn(1.0, 30.0)
        val tolerance = headingTolerance.coerceIn(5.0, 45.0)
        for (rp in list) {
            val pts = rp.pts
            for (i in 0 until pts.size - 1) {
                val ax = (pts[i].longitude() - p.lon) * k; val ay = (pts[i].latitude() - p.lat) * k2
                val bx = (pts[i + 1].longitude() - p.lon) * k; val by = (pts[i + 1].latitude() - p.lat) * k2
                if ((ax > bd && bx > bd) || (ax < -bd && bx < -bd) || (ay > bd && by > bd) || (ay < -bd && by < -bd)) continue
                val dx = bx - ax; val dy = by - ay
                val l2 = dx * dx + dy * dy
                if (l2 < 0.01) continue
                val t = (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
                val d = Math.hypot(ax + dx * t, ay + dy * t)
                if (d >= bd) continue
                // the same direction of travel (either way along the line: a two-way road's line may be drawn against you)
                val segB = Math.toDegrees(Math.atan2(dx, dy))
                val diff = Math.abs(((segB - heading + 540.0) % 360.0) - 180.0)
                if (diff > tolerance && diff < 180.0 - tolerance) continue
                bd = d; best = rp
            }
        }
        return best
    }
    @Volatile private var roadSegsAt = 0L

    private fun rememberRoads(feats: List<Feature>) {
        val out = ArrayList<List<Point>>()
        for (f in feats) when (val g = f.geometry()) {
            is LineString -> out += g.coordinates()
            is MultiLineString -> out.addAll(g.coordinates())
            else -> { }
        }
        roadSegs = out; roadSegsAt = android.os.SystemClock.uptimeMillis()
        // the same roads with what kind they are and whether they're one-way, for lining the painted lane guidance up with the road
        val info = ArrayList<RoadPiece>()
        for (f in feats) {
            val cls = try { f.getStringProperty("class") } catch (e: Throwable) { null } ?: ""
            val ow = try { f.getStringProperty("oneway") } catch (e: Throwable) { null }
            val one = when (ow) { "true" -> true; "false" -> false; else -> null }
            // the road's name as the map shows it (or its number, "M27", when it has no name)
            val nm = try { f.getStringProperty("name") } catch (e: Throwable) { null }?.takeIf { it.isNotBlank() }
                ?: try { f.getStringProperty("ref") } catch (e: Throwable) { null }.orEmpty()
            when (val g = f.geometry()) {
                is LineString -> info += RoadPiece(g.coordinates(), cls, one, nm)
                is MultiLineString -> for (c in g.coordinates()) info += RoadPiece(c, cls, one, nm)
                else -> { }
            }
        }
        roadInfo = info
        // the streets lamps stand along: not motorways or trunk roads, links, service roads or tunnels
        val streets = ArrayList<List<Point>>()
        val lampClasses = setOf("primary", "secondary", "tertiary", "street", "street_limited", "primary_link", "secondary_link", "tertiary_link")
        for (f in feats) {
            val cls = try { f.getStringProperty("class") } catch (e: Throwable) { null } ?: continue
            val tunnel = try { f.getStringProperty("structure") == "tunnel" } catch (e: Throwable) { false }
            if (tunnel || cls !in lampClasses) continue
            when (val g = f.geometry()) {
                is LineString -> streets += g.coordinates()
                is MultiLineString -> streets.addAll(g.coordinates())
                else -> { }
            }
        }
        if (streets.size != lampRoads.size || streets.isNotEmpty() && lampRoads.isEmpty()) lampRoadsNew = true
        lampRoads = streets
        if (lampRoadsNew) main0.post { refreshLamps() }
    }

    /**
     * Moves a GPS position onto the nearest drawn road that runs the way you are heading, or returns null when none is close enough.
     * It never waits on the map: it uses the roads the map last reported (the engine tries the speed-limit roads first anyway).
     * A one-way road (each side of a divided road is drawn as one) only counts going its own way, so on a dual carriageway the arrow
     * stays on your side even when the GPS drifts towards the middle (GitHub #150). As with the speed-limit roads, your side is
     * looked for up to 45 m off when the nearer line is the other side's; with only the other side close, the GPS position is kept.
     */
    fun snapToRoad(p: LL, heading: Double): LL? {
        val list = roadInfo
        if (list.isEmpty() || android.os.SystemClock.uptimeMillis() - roadSegsAt > 20_000L) return null
        return try {
            val k = Math.cos(Math.toRadians(p.lat)) * 111320.0
            val k2 = 110574.0
            var best: LL? = null
            var bd = 45.0
            var againstNear = false
            for (rp in list) {
                val pts = rp.pts
                val oneway = rp.oneway == true
                for (i in 0 until pts.size - 1) {
                    val ax = (pts[i].longitude() - p.lon) * k; val ay = (pts[i].latitude() - p.lat) * k2
                    val bx = (pts[i + 1].longitude() - p.lon) * k; val by = (pts[i + 1].latitude() - p.lat) * k2
                    if ((ax > bd && bx > bd) || (ax < -bd && bx < -bd) || (ay > bd && by > bd) || (ay < -bd && by < -bd)) continue
                    val dx = bx - ax; val dy = by - ay
                    val l2 = dx * dx + dy * dy
                    if (l2 < 0.01) continue
                    val t = (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
                    val d = Math.hypot(ax + dx * t, ay + dy * t)
                    if (d >= bd) continue
                    // only roads that run the way you are heading (a two-way road either way along its line), so a parallel street
                    // or a crossing road is not chosen
                    val segB = (Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0
                    var diff = Math.abs(segB - heading) % 360.0
                    if (diff > 180.0) diff = 360.0 - diff
                    if (diff > 35.0 && diff < 145.0) continue
                    // a one-way line drawn against you is the other carriageway (or a one-way street you shouldn't be on)
                    if (oneway && diff >= 145.0) { if (d < 24.0) againstNear = true; continue }
                    bd = d
                    best = LL(p.lat + (ay + dy * t) / k2, p.lon + (ax + dx * t) / k)
                }
            }
            if (bd > 24.0) {
                if (best == null) return if (againstNear) p else null
                if (!againstNear) return null
            }
            best
        } catch (e: Exception) { null }
    }

    /**
     * A tap on the map at screen point [sp] (also used for taps on the Android Auto screen): skips the route fly-through, or picks an
     * alternative route by its card or line. True when it did something. Worked out from where the cards and lines are on screen,
     * so it never has to wait for the map.
     */
    // ---- "See all locations" ----
    /** One branch on the map: where, its drive time (or distance), its logo if there is one, and whether it's the one picked. */
    class ChainPin(val pos: LL, val label: String, val logo: Bitmap?, val selected: Boolean)
    private var chainPins: List<ChainPin> = emptyList()
    private var chainYou: LL? = null
    var onChainTap: ((Int) -> Unit)? = null

    /** A queue's delay label: where it starts, the minutes it adds, heavy (red) or slow (amber), and dimmed for another route. */
    class JamPin(val pos: LL, val mins: Int, val heavy: Boolean, val dim: Boolean)
    private var jamPins: List<JamPin> = emptyList()

    /** Shows the delay labels on the queues (empty: none). */
    fun showJams(pins: List<JamPin>) { jamPins = pins; pushJams() }

    private fun pushJams() {
        val st = style ?: return
        val s = src("dl-jam") ?: return
        if (jamPins.isEmpty()) { s.featureCollection(empty()); return }
        val fs = ArrayList<Feature>()
        for (p in jamPins) {
            val id = "dl-jam-${p.mins}-" + (if (p.heavy) "h" else "s") + (if (p.dim) "d" else "")
            try { if (!st.hasStyleImage(id)) addImg(st, id, jamBitmap(p)) } catch (e: Throwable) { }
            val f = Feature.fromGeometry(Point.fromLngLat(p.pos.lon, p.pos.lat))
            f.addStringProperty("img", id); f.addNumberProperty("z", if (p.dim) 0 else 1)
            fs += f
        }
        s.featureCollection(FeatureCollection.fromFeatures(fs))
    }

    /** A rounded pill with a white edge, "+N min" in white, red for heavy traffic and amber for slow (dimmer for another route). */
    private fun jamBitmap(p: JamPin): Bitmap {
        val dm = mapView.resources.displayMetrics
        val d = dm.density
        val label = L.t("+{0} min", p.mins)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * d; typeface = android.graphics.Typeface.DEFAULT_BOLD; color = android.graphics.Color.WHITE
        }
        val w = text.measureText(label) + 22f * d
        val h = 27f * d
        val b = Bitmap.createBitmap((w + 6 * d).toInt(), (h + 7 * d).toInt(), Bitmap.Config.ARGB_8888)
        b.density = dm.densityDpi
        val cv = android.graphics.Canvas(b)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val box = android.graphics.RectF(3 * d, 2 * d, 3 * d + w, 2 * d + h)
        val rad = h / 2
        paint.color = android.graphics.Color.argb(if (p.dim) 60 else 110, 0, 0, 0)
        cv.drawRoundRect(android.graphics.RectF(box.left, box.top + 2 * d, box.right, box.bottom + 2 * d), rad, rad, paint)
        paint.color = android.graphics.Color.WHITE
        cv.drawRoundRect(box, rad, rad, paint)
        val fill = if (p.heavy) (if (p.dim) "#B0484B" else "#E5484D") else (if (p.dim) "#9A6A1C" else "#E08A00")
        paint.color = android.graphics.Color.parseColor(fill)
        val inset = 2f * d
        cv.drawRoundRect(android.graphics.RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), rad - inset, rad - inset, paint)
        if (p.dim) text.alpha = 215
        val fm = text.fontMetrics
        cv.drawText(label, box.centerX() - text.measureText(label) / 2, box.centerY() - (fm.ascent + fm.descent) / 2, text)
        return b
    }

    private var tapAt: LL? = null

    /** Shows the red pin where you tapped ([p]), or takes it away (null). */
    fun setTapPin(p: LL?) { if (p == tapAt) return; tapAt = p; pushTap() }

    // ---- a searched place's building, lit up while its card is open ----
    // On Mapbox's own map (Plus) it's the map's "selected" building colour; on the free map, the building's outline drawn over it
    // in blue (lighter at night), and raised in blue when 3D buildings are on
    private var hlFor: LL? = null
    private var hlStd: com.mapbox.maps.interactions.standard.generated.StandardBuildingsFeature? = null
    private var hlFeature: Feature? = null
    private var hlStyled = false
    private var hlToken = 0

    fun highlightPlace(p: LL?) {
        if (p != null && hlFor != null && Geo.dist(hlFor!!, p) < 5.0 && (hlStd != null || hlFeature != null)) return
        val t = ++hlToken
        // the last one back to normal
        hlStd?.let { f -> try { mbMap.setFeatureState(f, com.mapbox.maps.interactions.standard.generated.StandardBuildingsState { select(false) }) } catch (e: Throwable) { } }
        hlStd = null; hlFeature = null; hlFor = p; pushHl()
        if (p == null || satellite) return
        // (after the map has finished moving there, so the building is drawn; once more a little later if it wasn't yet)
        main0.postDelayed({ if (t == hlToken) findHl(p, t, retry = true) }, 1_900L)
    }

    private fun findHl(p: LL, t: Int, retry: Boolean) {
        if (!canQuery()) return
        try {
            val sc = mbMap.pixelForCoordinate(Point.fromLngLat(p.lon, p.lat))
            if (sc.x < 0 || sc.y < 0 || sc.x > mapView.width || sc.y > mapView.height) return
            val r = 10.0 * mapView.resources.displayMetrics.density
            if (slotsOn) {
                val box = RenderedQueryGeometry(ScreenBox(ScreenCoordinate(sc.x - r, sc.y - r), ScreenCoordinate(sc.x + r, sc.y + r)))
                mbMap.queryRenderedFeatures(com.mapbox.maps.interactions.standard.generated.StandardBuildings(), box) { list ->
                    main0.post {
                        if (t != hlToken) return@post
                        val f = list.minByOrNull { distTo(it.geometry, p) }
                        if (f == null) { if (retry) main0.postDelayed({ if (t == hlToken) findHl(p, t, false) }, 1_500L); return@post }
                        hlStd = f
                        try { mbMap.setFeatureState(f, com.mapbox.maps.interactions.standard.generated.StandardBuildingsState { select(true) }) } catch (e: Throwable) { }
                    }
                }
                return
            }
            val st = style ?: return
            val layers = try { st.styleLayers.map { it.id }.filter { it.contains("building") && !it.startsWith("dl-hl") } } catch (e: Throwable) { emptyList() }
            if (layers.isEmpty()) return
            query(RectF((sc.x - r).toFloat(), (sc.y - r).toFloat(), (sc.x + r).toFloat(), (sc.y + r).toFloat()), layers, 2_500L) { list ->
                if (t != hlToken) return@query
                val f = list?.filter { it.geometry() is Polygon || it.geometry() is com.mapbox.geojson.MultiPolygon }?.minByOrNull { distTo(it.geometry(), p) }
                if (f == null) { if (retry) main0.postDelayed({ if (t == hlToken) findHl(p, t, false) }, 1_500L); return@query }
                hlFeature = f; pushHl()
            }
        } catch (e: Throwable) { }
    }

    /** 0 for a point inside the shape, otherwise roughly how far it is from the shape's corners (degrees, only for comparing). */
    private fun distTo(g: com.mapbox.geojson.Geometry?, p: LL): Double {
        val rings: List<List<Point>> = when (g) {
            is Polygon -> g.coordinates().take(1)
            is com.mapbox.geojson.MultiPolygon -> g.coordinates().mapNotNull { it.firstOrNull() }
            else -> return Double.MAX_VALUE
        }
        var best = Double.MAX_VALUE
        for (ring in rings) {
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[j]
                if ((a.latitude() > p.lat) != (b.latitude() > p.lat) &&
                    p.lon < (b.longitude() - a.longitude()) * (p.lat - a.latitude()) / (b.latitude() - a.latitude()) + a.longitude()) inside = !inside
                j = i
                best = minOf(best, Math.hypot(a.longitude() - p.lon, a.latitude() - p.lat))
            }
            if (inside) return 0.0
        }
        return best
    }

    private fun pushHl() {
        val st = style ?: return
        try {
            if (!hlStyled || !st.styleSourceExists("dl-hl")) {
                if (!st.styleSourceExists("dl-hl")) addS(st, GeoJsonSource.Builder("dl-hl").featureCollection(empty()).build())
                // (under the labels, so street and place names stay readable on top of it)
                val below = try { st.styleLayers.firstOrNull { it.type == "symbol" && !it.id.startsWith("dl-") }?.id } catch (e: Throwable) { null }
                val dark = nightStyle
                val fill = if (dark) "#85B7EB" else "#378ADD"; val edge = if (dark) "#85B7EB" else "#185FA5"
                if (!st.styleLayerExists("dl-hl-fill")) addL(st, FillLayer("dl-hl-fill", "dl-hl").fillColor(fill).fillOpacity(0.35), below)
                if (!st.styleLayerExists("dl-hl-3d")) addL(st, FillExtrusionLayer("dl-hl-3d", "dl-hl").fillExtrusionColor(fill).fillExtrusionOpacity(0.95)
                    .fillExtrusionHeight(Expression.fromRaw("[\"+\",[\"to-number\",[\"coalesce\",[\"get\",\"render_height\"],[\"get\",\"height\"]],8],0.6]"))
                    .fillExtrusionBase(Expression.fromRaw("[\"to-number\",[\"coalesce\",[\"get\",\"render_min_height\"],[\"get\",\"min_height\"]],0]")), below)
                if (!st.styleLayerExists("dl-hl-line")) addL(st, LineLayer("dl-hl-line", "dl-hl").lineColor(edge).lineWidth(2.5), below)
                hlStyled = true
            }
            // (day or night colours, as the map is now)
            val dark = nightStyle
            st.setStyleLayerProperty("dl-hl-fill", "fill-color", Value.valueOf(if (dark) "#85B7EB" else "#378ADD"))
            st.setStyleLayerProperty("dl-hl-3d", "fill-extrusion-color", Value.valueOf(if (dark) "#85B7EB" else "#378ADD"))
            st.setStyleLayerProperty("dl-hl-line", "line-color", Value.valueOf(if (dark) "#85B7EB" else "#185FA5"))
            // (raised only when the map's buildings are 3D, so it sits on the building as drawn)
            st.setStyleLayerProperty("dl-hl-3d", "visibility", Value.valueOf(if (buildings3d) "visible" else "none"))
            val f = hlFeature
            src("dl-hl")?.featureCollection(if (f == null) empty() else FeatureCollection.fromFeatures(listOf(f)))
        } catch (e: Throwable) { }
    }

    private fun pushTap() {
        val s = src("dl-tap") ?: return
        val p = tapAt
        s.featureCollection(if (p == null) empty() else FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))))
    }

    fun showChain(pins: List<ChainPin>, you: LL?) { chainPins = pins; chainYou = you; pushChain() }

    fun clearChain() { chainPins = emptyList(); chainYou = null; pushChain() }

    private fun pushChain() {
        val st = style ?: return
        val s = src("dl-chain") ?: return
        val line = src("dl-chain-line")
        if (chainPins.isEmpty()) { s.featureCollection(empty()); line?.featureCollection(empty()); return }
        val fs = ArrayList<Feature>()
        chainPins.forEachIndexed { i, p ->
            val id = "dl-chain-$i-" + (if (p.selected) "s" else "n") + "-" + (p.label.hashCode() and 0xffff) + (if (p.logo != null) "L" else "")
            try { if (!st.hasStyleImage(id)) addImg(st, id, chainPinBitmap(p)) } catch (e: Throwable) { }
            val f = Feature.fromGeometry(Point.fromLngLat(p.pos.lon, p.pos.lat))
            f.addStringProperty("img", id); f.addNumberProperty("i", i); f.addNumberProperty("z", if (p.selected) 1 else 0)
            fs += f
        }
        s.featureCollection(FeatureCollection.fromFeatures(fs))
        val sel = chainPins.firstOrNull { it.selected }
        val you = chainYou
        line?.featureCollection(if (sel != null && you != null)
            FeatureCollection.fromFeature(Feature.fromGeometry(com.mapbox.geojson.LineString.fromLngLats(listOf(Point.fromLngLat(you.lon, you.lat), Point.fromLngLat(sel.pos.lon, sel.pos.lat)))))
        else empty())
    }

    /** A branch's pin: its logo in a white circle (a blue ring and a blue time tag when picked), the drive time underneath. */
    private fun chainPinBitmap(p: ChainPin): Bitmap {
        val dm = mapView.resources.displayMetrics
        val d = dm.density
        val r = (if (p.selected) 29f else 20f) * d
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * d; typeface = android.graphics.Typeface.DEFAULT_BOLD; color = android.graphics.Color.WHITE
        }
        val tw = text.measureText(p.label)
        val tagH = 24f * d
        val w = maxOf(2 * r + 12 * d, tw + 24 * d)
        val h = 2 * r + 8 * d + tagH + 6 * d
        val b = Bitmap.createBitmap(w.toInt() + 2, (h * 2).toInt(), Bitmap.Config.ARGB_8888)
        b.density = dm.densityDpi
        val cv = android.graphics.Canvas(b)
        val cx = b.width / 2f
        // (the circle sits in the middle of the picture, so the pin's middle is the place itself; the label hangs below)
        val cy = b.height / 2f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.argb(110, 0, 0, 0)
        cv.drawCircle(cx, cy + 2 * d, r + 1.5f * d, paint)
        paint.color = if (p.selected) android.graphics.Color.parseColor("#4C8DFF") else android.graphics.Color.WHITE
        cv.drawCircle(cx, cy, r, paint)
        paint.color = android.graphics.Color.WHITE
        cv.drawCircle(cx, cy, r - (if (p.selected) 4f * d else 0f), paint)
        val inner = r - (if (p.selected) 4f * d else 0f)
        val logo = p.logo
        if (logo != null) {
            val s = inner * 1.25f
            val save = cv.save()
            val clip = android.graphics.Path().apply { addCircle(cx, cy, inner - 2 * d, android.graphics.Path.Direction.CW) }
            cv.clipPath(clip)
            cv.drawBitmap(logo, null, android.graphics.RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            cv.restoreToCount(save)
        } else {
            // no logo: a red pin dot, like the usual search pin
            paint.color = android.graphics.Color.parseColor("#EA4335")
            cv.drawCircle(cx, cy, inner * 0.42f, paint)
        }
        // the label under the circle
        val ty = cy + r + 6 * d
        if (p.selected) {
            paint.color = android.graphics.Color.parseColor("#4C8DFF")
            cv.drawRoundRect(android.graphics.RectF(cx - tw / 2 - 10 * d, ty, cx + tw / 2 + 10 * d, ty + tagH), 12 * d, 12 * d, paint)
            cv.drawText(p.label, cx - tw / 2, ty + tagH / 2 + text.textSize * 0.35f, text)
        } else {
            val outline = android.graphics.Paint(text).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f * d; color = android.graphics.Color.argb(230, 10, 14, 22) }
            cv.drawText(p.label, cx - tw / 2, ty + tagH / 2 + text.textSize * 0.35f, outline)
            cv.drawText(p.label, cx - tw / 2, ty + tagH / 2 + text.textSize * 0.35f, text)
        }
        return b
    }

    /** All the branches fitted into the part of the map beside the list (on the driver's side: left, or right when [mirrored]). */
    /** One place, close in (street level), in the middle of the part of the screen left clear: the edges as parts of the screen covered. */
    fun centreIn(pos: LL, left: Double, top: Double, right: Double, bottom: Double) {
        val dLat = 0.0002; val dLon = dLat / Math.max(0.2, Math.cos(Math.toRadians(pos.lat)))
        fitFrac(listOf(LL(pos.lat - dLat, pos.lon - dLon), LL(pos.lat + dLat, pos.lon + dLon)), left + 0.02, top + 0.02, right + 0.02, bottom + 0.02)
    }

    fun fitChain(points: List<LL>, mirrored: Boolean) {
        val side = if (mapView.width > mapView.height) 0.40 else 0.06
        // (upright: the list is along the bottom and the turn card across the top, so the places go in the space between)
        val wide = mapView.width > mapView.height
        fitFrac(points, if (mirrored) 0.06 else side, if (wide) 0.12 else 0.2, if (mirrored) side else 0.06, if (wide) 0.10 else 0.52)
    }

    /** A tap near a branch's pin (within about 30 dp). */
    private fun chainAt(sp: android.graphics.PointF): Int? {
        if (chainPins.isEmpty()) return null
        val px = pixels(chainPins.map { it.pos })
        if (px.size != chainPins.size) return null
        val r = 30.0 * mapView.resources.displayMetrics.density
        var best: Int? = null; var bd = r
        px.forEachIndexed { i, p -> val dd = Math.hypot(p.x - sp.x, p.y - sp.y); if (dd < bd) { bd = dd; best = i } }
        return best
    }

    fun tapAt(sp: android.graphics.PointF): Boolean {
        chainAt(sp)?.let { i -> onChainTap?.invoke(i); return true }
        // a tap during the route fly-through skips straight to the overview
        flyDone?.let { d -> flyDone = null; flyToken++; try { mapView.camera.cancelAllAnimators() } catch (e: Throwable) { }; d(); return true }
        // a number-plate camera: say what it is
        alprAt(sp)?.let { a -> onAlprTap?.invoke(a.second); return true }
        // one of your favourites: its place card, with Go
        if (onFavTap != null) favAt(sp)?.let { i -> onFavTap?.invoke(i); return true }
        // one of your own reports: offer to remove it
        if (onReportTap != null) reportBadgeAt(sp)?.let { p -> onReportTap?.invoke(p); return true }
        if (liveLines.isEmpty()) return false
        return try {
            val dp = mapView.resources.displayMetrics.density
            // the cards first (about 190 x 65 dp, standing on their point): easy to hit while driving
            val cards = shownCards
            val cpx = pixels(cards.map { it.second })
            if (cpx.size == cards.size) for (k in cards.indices) {
                val p = cpx[k]
                if (sp.x >= p.x - 105 * dp && sp.x <= p.x + 105 * dp && sp.y >= p.y - 80 * dp && sp.y <= p.y + 12 * dp) { onAltTap?.invoke(cards[k].first); return true }
            }
            // then the lines: the nearest one within about 30 dp of the tap
            val r = 30.0 * dp
            var best = -1; var bd = r
            liveLines.forEachIndexed { i, line ->
                if (line.size < 2) return@forEachIndexed
                val step = maxOf(1, line.size / 300)
                val sample = ArrayList<LL>()
                var j = 0
                while (j < line.size) { sample += line[j]; j += step }
                if (sample.last() !== line.last()) sample += line.last()
                val px = pixels(sample)
                for (n in 0 until px.size - 1) {
                    val ax = px[n].x - sp.x; val ay = px[n].y - sp.y
                    val bx = px[n + 1].x - sp.x; val by = px[n + 1].y - sp.y
                    val dx = bx - ax; val dy = by - ay
                    val l2 = dx * dx + dy * dy
                    val t = if (l2 > 0) (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0) else 0.0
                    val d = Math.hypot(ax + dx * t, ay + dy * t)
                    if (d < bd) { bd = d; best = i }
                }
            }
            if (best >= 0) { onAltTap?.invoke(best); true } else false
        } catch (e: Exception) { false }
    }

    /** Current camera, as (lat, lon, bearing, tilt, zoom, topPadFraction, leftPadFraction). */
    fun cameraNow(): DoubleArray? {
        val c = try { mbMap.cameraState } catch (e: Throwable) { return null }
        val t = c.center
        val pd = c.padding
        val w = max(1, mapView.width).toDouble(); val h = max(1, mapView.height).toDouble()
        val i = viewInset
        if (i != null) {
            // back from padding within the visible part to the fractions the camera code works in
            val bot = pd.bottom
            val vw = max(1.0, w - i.left - i.right); val vh = max(1.0, h - i.top - bot)
            val l = pd.left - i.left; val r = pd.right - i.right
            return doubleArrayOf(t.latitude(), t.longitude(), c.bearing, c.pitch, c.zoom, (pd.top - i.top) / vh, (max(0.0, l) - max(0.0, r)) / vw)
        }
        return doubleArrayOf(t.latitude(), t.longitude(), c.bearing, c.pitch, c.zoom, pd.top / h, (pd.left - pd.right) / w)
    }

    /** A tall (portrait) screen: taller than it is wide. */
    fun isTall(): Boolean = try { mapView.height > mapView.width } catch (e: Throwable) { false }

    fun setTilt(tilt: Double) {
        tiltNow = tilt
        try { mbMap.easeTo(CameraOptions.Builder().pitch(tilt).build(), MapAnimationOptions.mapAnimationOptions { duration(500L) }) } catch (e: Throwable) { }
    }

    fun zoomBy(delta: Double) {
        try {
            val z = mbMap.cameraState.zoom
            mbMap.easeTo(CameraOptions.Builder().zoom(z + delta).build(), MapAnimationOptions.mapAnimationOptions { duration(250L) })
        } catch (e: Throwable) { }
    }

    fun resetNorth() {
        try { mbMap.easeTo(CameraOptions.Builder().bearing(0.0).build(), MapAnimationOptions.mapAnimationOptions { duration(400L) }) } catch (e: Throwable) { }
    }

    /** Drags the map with the user's finger (the car screen's scroll gesture). Android Auto reports
     * finger displacement, while cameraForDrag expects the inverse camera displacement; negate it so
     * dragging right moves the map content right, and dragging down moves it down. */
    fun panBy(dx: Float, dy: Float) {
        try {
            val w = mapView.width / 2.0; val h = mapView.height / 2.0
            mbMap.setCamera(mbMap.cameraForDrag(ScreenCoordinate(w, h), ScreenCoordinate(w - dx, h - dy)))
        } catch (e: Throwable) { }
    }

    /** Zooms by [dz] levels keeping the point under ([x], [y]) where it is (the car screen's pinch and double tap). */
    fun zoomAround(dz: Double, x: Float, y: Float) {
        try {
            val z = mbMap.cameraState.zoom
            val b = CameraOptions.Builder().zoom((z + dz).coerceIn(1.0, 21.0))
            if (!x.isNaN() && !y.isNaN()) b.anchor(ScreenCoordinate(x.toDouble(), y.toDouble()))
            mbMap.setCamera(b.build())
        } catch (e: Throwable) { }
    }

    /** Smoothly zooms out (flat, north-up) so every point sits in the area above the route-preview panel. */
    /** [panel]: the bottom of the screen is covered by the route preview panel (false: only the turn card and trip card are in the way). */
    fun fit(points: List<LL>, panel: Boolean = true) =
        if (panel && previewSide > 0.0) fitFrac(points, previewSide + 0.05, 0.12, 0.10, 0.12)
        else fitFrac(points, 0.07, if (panel) 0.10 else 0.16, 0.07, if (panel) 0.60 else 0.20)

    /** Fits [points] into the map with these parts of it (fractions of its width and height) kept clear on each side. */
    fun fitFrac(points: List<LL>, plF: Double, ptF: Double, prF: Double, pbF: Double) {
        if (points.size < 2) return
        val d = mapView.resources.displayMetrics.density.toDouble()
        val w = mapView.width / d
        val h = mapView.height / d
        if (w < 50 || h < 50) return
        fun mx(lon: Double) = (lon + 180.0) / 360.0
        fun my(lat: Double): Double {
            val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0
        }
        val xs = points.map { mx(it.lon) }; val ys = points.map { my(it.lat) }
        val x0 = xs.min(); val x1 = xs.max(); val y0 = ys.min(); val y1 = ys.max()
        // visible area (dp): the bottom half is covered by the preview panel
        val pl = w * plF; val pr = w * prF; val pt = h * ptF; val pb = h * pbF
        val aw = w - pl - pr; val ah = h - pt - pb
        val fx = max(x1 - x0, 1e-7); val fy = max(y1 - y0, 1e-7)
        // (zoom in this formula is for a map drawn at the screen's own density; a lower render scale shows more map, so it's corrected)
        val prK = d / max(0.1, mapPixelRatio())
        val z = (min(Math.log(aw * prK / (512.0 * fx)), Math.log(ah * prK / (512.0 * fy))) / Math.log(2.0))
        val zoom = z.coerceIn(2.0, 17.0)
        val world = 512.0 * Math.pow(2.0, zoom) / prK
        // bounds centre should land in the middle of the visible area, not the middle of the map
        val cx = (x0 + x1) / 2 + (w / 2 - (pl + aw / 2)) / world
        val cy = (y0 + y1) / 2 + (h / 2 - (pt + ah / 2)) / world
        val lon = cx * 360.0 - 180.0
        val lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * cy))))
        tiltNow = 0.0
        val cp = CameraOptions.Builder().center(Point.fromLngLat(lon, lat)).zoom(zoom).pitch(0.0).bearing(0.0)
            .padding(EdgeInsets(0.0, 0.0, 0.0, 0.0)).build()
        try { mbMap.easeTo(cp, MapAnimationOptions.mapAnimationOptions { duration(1600L) }) } catch (e: Throwable) { }
    }

    private fun mapPixelRatio(): Double = pixelRatioUsed.toDouble()

    companion object {
        /** Stands in for the access token until one is entered (the map can't even be created without some token). */
        const val NO_TOKEN = "pk.none"
        /** The car shapes for the "Car" locator, in the order they're offered. */
        val CAR_MODELS = listOf("saloon", "hatch", "estate", "suv", "pickup", "van", "sports")
    }
}

/** The fonts offered for the map's lettering: wider and heavier than Mapbox's narrow DIN Pro, all with the weights the map uses. */
internal val MAP_FONTS = listOf("Roboto", "Open Sans", "Inter", "Lato", "Montserrat")
