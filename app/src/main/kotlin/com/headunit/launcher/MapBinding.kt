package com.headunit.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
            else if (modelOn && (e.sourceId ?: "").startsWith("dl-me-m")) main0.post { modelBroken = true; modelWanted = false; reinstall() }
            // a piece of Mapbox's map itself (not one of the app's extras, like traffic) couldn't be loaded
            else if ((e.type == MapLoadingErrorType.TILE || e.type == MapLoadingErrorType.SOURCE) && !(e.sourceId ?: "").startsWith("dl-")) onTileMissing?.invoke()
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

    /** Puts Mapbox's logo and the (i) credits button top right, inside the visible part of the screen ([inset], null = whole view). */
    fun placeBranding(inset: android.graphics.Rect?) {
        brandInset = inset
        val d = ctx.resources.displayMetrics.density
        val top = logoCorner.startsWith("t")
        val left = logoCorner.endsWith("l")
        val v = if (top) android.view.Gravity.TOP else android.view.Gravity.BOTTOM
        val h = if (left) android.view.Gravity.START else android.view.Gravity.END
        // a little in from the edge (further down at the top, clear of the app's top bar), inside the visible part of the screen
        val vEdge = (if (top) (inset?.top ?: 0) else (inset?.bottom ?: 0)) + (if (top) 64f else 12f) * d
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
    /** "mapbox", "horizon" (the app's own soft colours), "faded" or "grey". Settings > Map. */
    private var colours = "mapbox"
    /** Whether the style has Standard's slots (classic styles don't: the app's layers then simply go on top). */
    private var slotsOn = true

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
            setBase("showHdRoads", Value.valueOf(v == "full"))
            // the real 3D arrow only shows on the full map
            if (style != null) reinstall()
        }
    }

    fun setColours(c: String) {
        val v = if (c in setOf("mapbox", "horizon", "faded", "grey")) c else "mapbox"
        if (v == colours) return
        colours = v
        // the colours are part of how the map loads (and Horizon is a different base map), so it's loaded again
        if (isMapboxStyle(wantedStyle)) { loadedStyle = null; setStyleUrl(baseStyle()) }
        else if (wantedStyle != null && !slotsOn) { loadedStyle = null; setStyleUrl(wantedStyle!!) }
    }

    /** Asked to load the map style again (satellite switched): the engine knows which map source is chosen. */
    var onRestyle: (() -> Unit)? = null
    val isSatellite: Boolean get() = satellite

    private fun isMapboxStyle(u: String?) = u == Style.STANDARD || u == Style.STANDARD_SATELLITE || u == Style.MAPBOX_STREETS || u == Style.DARK || u == NAV_DAY || u == NAV_NIGHT

    /** Standard's light: "day", "dawn", "dusk" or "night". Changing it fades smoothly, without reloading the map. */
    private var lightPreset = "night"

    fun setLightPreset(p: String) {
        if (p == lightPreset) return
        lightPreset = p
        setBase("lightPreset", Value.valueOf(satLight(p)))
    }

    /** Satellite at "night" darkens the photos until there's next to nothing to see (it looked as if satellite wasn't working),
     *  so satellite goes no darker than dusk. */
    private fun satLight(p: String): String = if (satellite && p == "night") "dusk" else p

    /** One of Standard's settings (only once a style is showing; they're also written into the style when it loads). */
    private fun setBase(key: String, v: Value) {
        val st = style ?: return
        try { st.setStyleImportConfigProperty(BASE, key, v) } catch (e: Throwable) { }
    }

    /** Standard's settings as they should be now, written into the style before it loads (so night never flashes up as day). */
    private fun baseConfig(): org.json.JSONObject {
        val sat = (wantedStyle ?: "") == Style.STANDARD_SATELLITE
        val c = org.json.JSONObject()
        c.put("lightPreset", if (sat && lightPreset == "night") "dusk" else lightPreset)
        // shop and place labels rest on the light map and (on the full map) while you're on the move: placing them is the costly part
        c.put("showPointOfInterestLabels", !lite && !poiMoving)
        c.put("showTransitLabels", !lite)
        if (!sat) c.put("show3dObjects", buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail))
        // (colour themes are only for the drawn map: Standard Satellite has none, so nothing is sent for it)
        if (!sat) when (colours) {
            "faded" -> c.put("theme", "faded")
            "grey" -> c.put("theme", "monochrome")
            // Mapbox's own colours with a gentle purple-blue hint (mostly at night), from a colour look-up table
            "horizon" -> if (!sat) { c.put("theme", "custom"); c.put("theme-data", HorizonLut.BASE64) }
        }
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

    private fun loadStyle(url: String) {
        // no real token yet: nothing to load (the engine shows a note asking for one)
        if (MapboxOptions.accessToken == NO_TOKEN) return
        loadedStyle = url
        style = null
        val token = ++styleToken
        // Standard (and Standard Satellite) come wrapped with their settings; Mapbox's classic 2D styles load as they are
        val standard = url == Style.STANDARD || url == Style.STANDARD_SATELLITE
        slotsOn = standard
        try {
            mbMap.loadStyle(if (standard) wrapperJson(url) else url) { st ->
                if (token != styleToken) return@loadStyle
                style = st
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

    // ---- the lane bubble: a small strip of the lanes riding on the road just ahead of your arrow (when the lanes are painted on the road) ----
    @Volatile private var bubbleAt: LL? = null
    @Volatile private var bubbleBmp: Bitmap? = null
    private var bubbleLanesKey = ""
    private var bubbleKey = ""

    /** Shows the lane bubble at [at] (null hides it). Same lane description as [setLaneSign]. */
    fun setLaneBubble(at: LL?, lanes: List<Pair<List<Pair<String, Boolean>>, Boolean>>) {
        val lk = lanes.joinToString(";") { (d, on) -> d.joinToString(",") { "${it.first}${if (it.second) 1 else 0}" } + (if (on) "*" else "") }
        val key = if (at == null) "" else String.format(java.util.Locale.US, "%.5f,%.5f|%s", at.lat, at.lon, lk)
        if (key == bubbleKey) return
        bubbleKey = key
        main0.post {
            val st = style ?: return@post
            try {
                if (at != null && lanes.isNotEmpty()) {
                    if (lk != bubbleLanesKey || bubbleBmp == null) { bubbleLanesKey = lk; bubbleBmp = laneSignBitmap(lanes, false); addImg(st, "dl-lanebubble", bubbleBmp!!) }
                    bubbleAt = at
                    src("dl-lanebubble")?.featureCollection(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(at.lon, at.lat)))))
                } else {
                    bubbleAt = null
                    src("dl-lanebubble")?.featureCollection(FeatureCollection.fromFeatures(emptyList()))
                }
            } catch (e: Throwable) { }
        }
    }

    /** The sign itself: a panel with a white edge, one cell per lane (the ones to use lit), on two posts ([posts] false: just the panel, for the bubble). */
    private fun laneSignBitmap(lanes: List<Pair<List<Pair<String, Boolean>>, Boolean>>, posts: Boolean = true): Bitmap {
        val cw = 64; val ch = 80; val pad = 10; val gap = 4; val post = if (posts) 44 else 0
        val w = pad * 2 + cw * lanes.size + gap * (lanes.size - 1)
        val ph = ch + pad * 2
        val bmp = Bitmap.createBitmap(w, ph + post, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (posts) {
            p.color = 0xFF8A949C.toInt()
            c.drawRect(pad + 6f, ph - 4f, pad + 14f, (ph + post).toFloat(), p)
            c.drawRect(w - pad - 14f, ph - 4f, w - pad - 6f, (ph + post).toFloat(), p)
        }
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

    fun setExtras(facades: Boolean, landmarks: Boolean, paths: Boolean, terrain: Boolean, tapSelect: Boolean, furniture: Boolean, clear: Boolean) {
        val rebuild = terrain != terrainOn || furniture != furnitureOn || clear != clearOn
        facadesOn = facades; landmarksOn = landmarks; pathsOn = paths; terrainOn = terrain; furnitureOn = furniture; clearOn = clear
        if (tapSelect != tapSelectOn) { tapSelectOn = tapSelect; if (!tapSelect) clearSelection() }
        main0.post {
            setBase("show3dFacades", Value.valueOf(facadesOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("show3dLandmarks", Value.valueOf(landmarksOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("showPedestrianRoads", Value.valueOf(pathsOn))
            if (rebuild && style != null) reinstall()
        }
    }

    private var skyCode: Int? = null
    /** The weather for the haze towards the horizon (Settings > Fog and sky); null leaves Mapbox's own sky. */
    fun setSky(code: Int?) {
        if (code == skyCode && style != null) return
        skyCode = code
        main0.post { style?.let { Sky.atmosphere(it, skyCode, nightStyle) } }
    }

    /** The weather where you are now (Open-Meteo code), for rain or snow on the map; null for none. */
    fun setWeather(code: Int?) {
        if (code == weatherCode && style != null) return
        weatherCode = code
        main0.post { style?.let { drawsWeather = Sky.weather(it, weatherCode) } }
    }

    /** Puts the extras on a freshly loaded (or rebuilt) map. */
    private fun installExtras(st: Style) {
        // hills and valleys (not on the classic 2D map)
        Sky.terrain(st, terrainOn && mapDetail != "lightest")
        drawsWeather = Sky.weather(st, weatherCode)
        Sky.atmosphere(st, skyCode, nightStyle)
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
                clearSelection()
                selectAt = android.os.SystemClock.uptimeMillis()
                mbMap.setFeatureState(f, com.mapbox.maps.interactions.standard.generated.StandardBuildingsState { select(true) })
                // (not "handled": the app's own tap handling, like the route cards, still gets the tap)
                false
            })
            mbMap.addInteraction(com.mapbox.maps.ClickInteraction.standardPoi { f, _ ->
                if (!tapSelectOn) return@standardPoi false
                val g = f.geometry
                f.name?.let { n -> onPoiTap?.invoke(n, LL(g.latitude(), g.longitude())) }
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
        val night = nightStyle
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
        val roads = lampRoads; val gap = lampGap; val night = nightStyle
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
        val mapped = if (OfflineRouter.hasLamps(ctx)) OfflineRouter.lamps(ctx, c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon, maxTiles = 64) else emptyList()
        // spots: lat, lon, bearing of the arm
        val spots = ArrayList<DoubleArray>()
        val near = mapped.sortedBy { Geo.dist(LL(it[0], it[1]), c) }.take(90)
        for (q in near) spots += doubleArrayOf(q[0], q[1], armTowardsRoad(q[0], q[1], roads))
        val guessing = mapped.size < 15
        if (guessing && roads.isNotEmpty()) {
            val taken = HashSet<Long>()
            val cell = Math.max(8.0, gap * 0.6)
            fun key(lat: Double, lon: Double) = Math.floor(lat * 111320.0 / cell).toLong() * 1000003L + Math.floor(lon * 111320.0 * Math.cos(Math.toRadians(lat)) / cell).toLong()
            for (q in spots) taken += key(q[0], q[1])
            val kx = 111320.0 * Math.cos(Math.toRadians(c.lat)); val ky = 111320.0
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
                        val dir = Math.toDegrees(Math.atan2(bx - ax, by - ay))
                        // at the left kerb, about 5.5 m from the middle of the road, arm reaching back over it
                        val at = Furniture.offset(LL(lat, lon), dir - 90.0, 5.5)
                        if (taken.add(key(at.lat, at.lon)) && Geo.dist(at, c) < 900.0) spots += doubleArrayOf(at.lat, at.lon, dir + 90.0)
                        d += gap
                    }
                    carry = d - len
                }
            }
        }
        val kept = spots.sortedBy { Geo.dist(LL(it[0], it[1]), c) }.take(90)
        val posts = kept.map { Furniture.point(it[0], it[1], it[2]) }
        // the lamp hangs about 2.5 m out along the arm
        val pools = kept.map { val h = Furniture.offset(LL(it[0], it[1]), it[2], 2.5); Feature.fromGeometry(Point.fromLngLat(h.lon, h.lat)) }
        val placedOnRoads = kept.size - minOf(kept.size, near.size)
        val note = when {
            kept.isEmpty() && roads.isEmpty() -> "no streets read from the map here yet"
            kept.isEmpty() -> "none here"
            mapped.isNotEmpty() && placedOnRoads > 0 -> "${kept.size} shown (${near.size} mapped, $placedOnRoads placed along the streets)"
            mapped.isNotEmpty() -> "${kept.size} shown, from your offline pack"
            !OfflineRouter.hasLamps(ctx) -> "${kept.size} placed along the streets (your offline pack has no lamp list)"
            else -> "${kept.size} placed along the streets (none mapped here)"
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
                    if (found == null) { sigFailAt = android.os.SystemClock.uptimeMillis(); continue }
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
                    signalErr = ""
                    main0.post {
                        if (token == signalToken) { signalPts = snap; pushSignals() }
                        try { onRouteCameras?.invoke(camSnap) } catch (e: Throwable) { }
                    }
                }
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
        if (!slotsOn && (colours == "horizon" || recolourBase)) try { recolourClassic(st) } catch (e: Throwable) { }
        // Standard's newer settings are set once it's loaded (so a style that doesn't know one can't stop the map loading)
        if (!satellite) {
            setBase("show3dTrees", Value.valueOf(treesOn)); setBase("showLandmarkIcons", Value.valueOf(!lite))
            // lane markings, turn arrows, stop lines and 3D overpasses where Mapbox has them (Full 3D only)
            setBase("showHdRoads", Value.valueOf(MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            // detailed building fronts, landmark buildings, footpaths (Settings > Map)
            setBase("show3dFacades", Value.valueOf(facadesOn && buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
            setBase("show3dLandmarks", Value.valueOf(landmarksOn && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
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
        val offCol = if (night) "#4A5062" else "#FBFAF7"
        val isOn = Expression.eq(Expression.get("on"), lit(1))
        addL(st, FillLayer("dl-lane-fill", "dl-lane-bands").inSlot(MID)
            .fillColor(Expression.switchCase(isOn, col(onCol), col(offCol)))
            .fillOpacity(Expression.product(Expression.toNumber(Expression.get("a")), Expression.switchCase(isOn, lit(0.95), lit(if (night) 0.75 else 0.85))))
            .fillEmissiveStrength(1.0).minZoom(15.5))
        val lineCol = if (night) "#FFFFFF" else "#6A7080"
        addL(st, LineLayer("dl-lane-edge", "dl-lane-divs").inSlot(MID).filter(Expression.eq(Expression.get("s"), lit(1)))
            .lineColor(lineCol).lineOpacity(0.7).lineEmissiveStrength(1.0)
            .lineWidth(zoomed(2.0, 15 to 0.15, 20 to 2.5)).minZoom(15.5))
        addL(st, LineLayer("dl-lane-div", "dl-lane-divs").inSlot(MID).filter(Expression.eq(Expression.get("s"), lit(0)))
            .lineColor(lineCol).lineOpacity(0.75).lineEmissiveStrength(1.0).lineDasharray(listOf(3.0, 4.0))
            .lineWidth(zoomed(2.0, 15 to 0.15, 20 to 2.0)).minZoom(15.5))
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
        addS(st, GeoJsonSource.Builder("dl-cams").featureCollection(empty()).build())
        addL(st, SymbolLayer("dl-cams", "dl-cams")
            .iconImage("dl-hz-cam").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 8 to 0.36, 12 to 0.44, 15 to 0.68, 18 to 0.9)))
        addL(st, SymbolLayer("dl-hazards", "dl-hazards")
            .iconImage(Expression.get("img")).iconAllowOverlap(true).iconIgnorePlacement(true).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 11 to 0.45, 15 to 0.75, 18 to 1.0)))
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
        bubbleBmp?.let { addImg(st, "dl-lanebubble", it) }
        addS(st, GeoJsonSource.Builder("dl-lanebubble").featureCollection(FeatureCollection.fromFeatures(
            bubbleAt?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyList())).build())
        addL(st, SymbolLayer("dl-lanebubble-l", "dl-lanebubble").iconImage("dl-lanebubble").iconAnchor(IconAnchor.BOTTOM)
            .iconAllowOverlap(true).iconIgnorePlacement(true).iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0)
            .iconSize(zoomed(1.0, 13 to 0.3, 16 to 0.46, 18 to 0.6)))
        addL(st, SymbolLayer("dl-parked-l", "dl-parked").iconImage("dl-car-pin").iconAllowOverlap(true).iconIgnorePlacement(true)
            .iconPitchAlignment(IconPitchAlignment.VIEWPORT).iconEmissiveStrength(1.0).iconSize(zoomed(1.0, 10 to 0.4, 15 to 0.55, 18 to 0.7)))
        if (im) {
            addImg(st, "dl-pin", pinBitmap())
            addL(st, SymbolLayer("dl-dest-dot", "dl-dest")
                .iconImage("dl-pin").iconAnchor(IconAnchor.BOTTOM).iconEmissiveStrength(1.0)
                .iconAllowOverlap(true).iconIgnorePlacement(true).iconSize(0.6))
        } else addL(st, CircleLayer("dl-dest-dot", "dl-dest")
            .circleRadius(10.0).circleColor("#ff3b30").circleStrokeColor("#ffffff").circleStrokeWidth(3.0).circleEmissiveStrength(1.0))
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
        if (modelWanted && !modelBroken && !satellite && MapStylePolicy.supportsFullDetailFeatures(mapDetail)) installModel(st)
        // (the road-name pill is roadView, see placeLocator)
        spriteName = null
        placeLocator()
        pushAll()
        installExtras(st)
        // the rain radar, if it's on, goes back under the route
        pushRadar()
    }

    fun setView(sat: Boolean, b3d: Boolean) {
        val satChanged = sat != satellite
        satellite = sat
        buildings3d = b3d
        // satellite is Mapbox's Standard Satellite style (a style of its own, so the map reloads); 3D buildings is one of Standard's settings
        if (satChanged && wantedStyle != null) { val r = onRestyle; if (r != null) r() else setStyleUrl(baseStyle()) }
        else if (!sat && slotsOn) setBase("show3dObjects", Value.valueOf(buildings3d && MapStylePolicy.supportsFullDetailFeatures(mapDetail)))
        else if (!sat) style?.let { st ->
            // the Horizon colours' own 3D buildings
            try {
                if (st.styleLayerExists("dl-building-3d")) st.setStyleLayerProperty("dl-building-3d", "visibility", Value.valueOf(if (b3d) "visible" else "none"))
                else if (b3d && colours == "horizon") recolourClassic(st)
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
    private val ROUTE_K: Float get() = routeBoost

    private fun line(p: List<LL>): Feature =
        Feature.fromGeometry(LineString.fromLngLats(p.map { Point.fromLngLat(it.lon, it.lat) }))

    private fun lineSev(p: List<LL>, level: Int): Feature {
        val f = line(p)
        f.addNumberProperty("sev", level)
        return f
    }

    private var mainSev: IntArray? = null
    private var mainOff = 0

    /** Route colour: blue (a deeper blue by day, like the progress bar), amber where traffic is slow, red where it's heavy. */
    private fun sevColor(im: Boolean = false, day: Boolean = false): Expression = Expression.switchCase(
        Expression.eq(Expression.get("sev"), lit(2)), col(if (im) "#D93025" else "#FF4D4D"),
        Expression.eq(Expression.get("sev"), lit(1)), col(if (im) "#F9AB00" else "#FFB020"),
        col(if (day) "#2A7BE4" else if (im) "#2FC4F5" else "#4CC9FF"),
    )

    private fun pushAll() {
        pushLive(); pushRadar(); pushTurn()
        pushRoute(); pushDest(); pushMe(); pushHazards(); pushCams(); pushSignals()
    }

    private var cams: List<LL> = emptyList()

    /** The speed cameras around you, shown on the map whether or not you're on a route. */
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
        fun addRun(seg: List<LL>, firstIdx: Int) {
            if (seg.size < 2) return
            var start = 0; var cur = level(firstIdx)
            for (j in 1 until seg.size - 1) {
                val l = level(firstIdx + j)
                if (l != cur) { feats += lineSev(seg.subList(start, j + 1), cur); start = j; cur = l }
            }
            feats += lineSev(seg.subList(start, seg.size), cur)
        }
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
    /** Map icons by kind: camera, roadworks, closure, police, accident, hazard, breakdown, mobile camera. */
    private val hazardNames = arrayOf("dl-hz-cam", "dl-hz-work", "dl-hz-closed", "dl-hz-police", "dl-hz-accident", "dl-hz-hazard", "dl-hz-breakdown", "dl-hz-mobilecam")

    /** Icons for speed cameras (0), roadworks (1) and closures (2) along the route. */
    fun setHazards(list: List<Pair<LL, Int>>) { hazards = list; pushHazards() }

    private fun pushHazards() {
        val s = src("dl-hazards") ?: return
        val fs = hazards.map { (p, k) ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addStringProperty("img", hazardNames[k.coerceIn(0, hazardNames.size - 1)]) }
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
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
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
        val out = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 12f; color = 0x66000000.toInt() }
        draw(out)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8f
            color = if (on) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt()
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
        val top = if (diff < 0) "${-diff} min faster" else if (diff > 0) "$diff min slower" else "Same time"
        val sub = when (traffic) { -1 -> "Less traffic"; 1 -> "More traffic"; else -> "Similar traffic" }
        // day: a light card with dark writing, like the rest of the day look; night: the dark card. A faster way is green in both
        val day = !nightStyle
        val green = diff < 0
        val lightCard = day && !green
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lightCard) 0xFF14171C.toInt() else android.graphics.Color.WHITE; textSize = 16f * d; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lightCard) 0xFF59616D.toInt() else 0xE6FFFFFF.toInt(); textSize = 12.5f * d }
        val tw = maxOf(tp.measureText(top), sp.measureText(sub))
        val w = (tw + 26f * d).toInt(); val bh = 50f * d; val tail = 9f * d
        val b = Bitmap.createBitmap(w + (6 * d).toInt(), (bh + tail + 6 * d).toInt(), Bitmap.Config.ARGB_8888)
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
        pushCards()
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
        val oldModel = Triple(modelWanted, modelShape, modelColour + modelGlow)
        if (wantModel) {
            val parts = color.split('|')
            modelGlow = parts.getOrNull(2) != "0"
            modelShape = if (parts.getOrNull(0) == "car") "car" else "arrow"
            modelColour = parts.getOrNull(1)?.takeIf { it.startsWith("#") } ?: (if (modelShape == "car") "#F4F5F7" else "#F2F2F6")
        }
        modelWanted = wantModel && !modelBroken
        // your own picture, but none saved (or it was deleted): the 3D arrow
        val noPic = styleName == "custom" && !java.io.File(ctx.filesDir, "locator.png").exists()
        locatorStyle = when { wantModel -> modelShape; noPic -> "arrow"; else -> styleName }
        locatorColor = when { wantModel -> modelColour; noPic -> "#F2F2F6"; else -> color }
        if (style == null) return
        // the model's layers are put in (or taken out, or recoloured) with the rest of the app's layers
        if (oldModel != Triple(modelWanted, modelShape, modelColour + modelGlow)) reinstall() else applyLocatorVisibility()
        spriteName = null
        pushModel(true)
    }

    // ---- the real 3D model locator (Full 3D only) ----
    private var modelWanted = false
    /** The 3D model failed to load once: it isn't tried again this run. */
    private var modelBroken = false
    private var modelShape = "arrow"
    private var modelColour = "#F2F2F6"
    /** Settings > Your arrow > Glow: a soft blue glow on the ground round the model, and the model fully lit. */
    private var modelGlow = true
    /** The 3D model is on the map now (it's then drawn instead of the picture). */
    private var modelOn = false
    private var modelAt: LL? = null
    private var modelHead = -999.0

    /** Screen pixels per model unit (the model is built in the same units as the picture's shape). */
    private fun modelPxPerUnit(): Double = arrowScale * (if (modelShape == "car") 300.0 / 4.4 * 0.6 * 0.28 else 300.0 * 0.42 * 0.31)

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
        m["orientation"] = Value(listOf(Value(0.0), Value(0.0), Value(head)))
        val models = HashMap<String, Value>()
        models["me"] = Value(m)
        return Value(models)
    }

    /** The real 3D car's files for the chosen car type (Settings > Your arrow > Car type); the saloon is the original model. */
    private fun carUri(part: String): String = if (carModel == "saloon" || carModel !in CAR_MODELS) "asset://models/car_$part.glb" else "asset://models/car_${part}_$carModel.glb"

    private fun installModel(st: Style) {
        val car = modelShape == "car"
        val p = me
        val uriA = if (car) carUri("body") else "asset://models/arrow.glb"
        val uriB = if (car) carUri("parts") else "asset://models/disc.glb"
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
                    .circleRadius(scale[0] * (if (car) 2.2 else 1.45)).circleBlur(0.85).circleColor("#4C9BE6").circleOpacity(0.55)
                    .circlePitchAlignment(CirclePitchAlignment.MAP).circleEmissiveStrength(1.0))
            }
            // the untinted part first (the blue disc under the arrow, or the car's glass, lights and tyres), then the coloured part
            // set up the way Mapbox's own location marker is (a "location indicator" model, no shadows): the tested path
            addL(st, ModelLayer("dl-me-model-b", "dl-me-mb").modelType(ModelType.LOCATION_INDICATOR).modelScaleMode(ModelScaleMode.VIEWPORT).modelScale(scale)
                .modelCastShadows(false).modelReceiveShadows(false).modelEmissiveStrength(if (car) (if (modelGlow) 0.85 else 0.6) else 1.0)
                .visibility(vis(p != null)))
            // the arrow / car in your colour. In the default white it keeps the model's own shading (a white top over a grey-blue
            // edge, matched to the picture locator); a chosen colour tints it all. Mostly self-lit, so night doesn't grey it out
            addL(st, ModelLayer("dl-me-model-a", "dl-me-ma").modelType(ModelType.LOCATION_INDICATOR).modelScaleMode(ModelScaleMode.VIEWPORT).modelScale(scale)
                .modelColor(modelColour).modelColorMixIntensity(if (modelColour.equals(if (car) "#F4F5F7" else "#F2F2F6", true)) 0.0 else 1.0)
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
        val car = modelShape == "car"
        try {
            st.setStyleSourceProperty("dl-me-ma", "models", modelSourceValue(if (car) carUri("body") else "asset://models/arrow.glb", p, meHeading))
            st.setStyleSourceProperty("dl-me-mb", "models", modelSourceValue(if (car) carUri("parts") else "asset://models/disc.glb", p, meHeading))
            if (modelGlow && (first || prev == null || Math.abs(prev.lat - p.lat) > 1e-8 || Math.abs(prev.lon - p.lon) > 1e-8))
                src("dl-me-glow")?.featureCollection(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))))
            if (first) { layer("dl-me-model-a")?.visibility(vis(true)); layer("dl-me-model-b")?.visibility(vis(true)) }
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
                        if (custom) customImage(cOpts.getOrNull(0) ?: "")?.let { renderCustom(it, yawB.toDouble(), pitB.toDouble(), !upright, cCircle) }
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
    private fun customImage(ver: String): Bitmap? {
        if (customVer == ver) return customBase
        val f = java.io.File(ctx.filesDir, "locator.png")
        val b = try { if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path) else null } catch (e: Throwable) { null }
        customBase = b; customVer = ver
        return b
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
            val below = if (modelOn) (modelPxPerUnit() * (if (modelShape == "car") 2.3 else 0.85) * Math.cos(Math.toRadians(tilt))).toFloat()
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

    fun setArrowScale(f: Float) {
        arrowScale = f
        val im = theme == "immersive"
        try {
            (layer("dl-me-arrow") as? SymbolLayer)?.iconSize((if (im) 0.25 else 0.625) * f)
            (layer("dl-me-halo") as? CircleLayer)?.circleRadius(15.0 * 0.625 * f)
            if (modelOn) { val sc = modelScale(); (layer("dl-me-model-a") as? ModelLayer)?.modelScale(sc); (layer("dl-me-model-b") as? ModelLayer)?.modelScale(sc)
                (layer("dl-me-glow-l") as? CircleLayer)?.circleRadius(sc[0] * (if (modelShape == "car") 2.2 else 1.45)) }
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
    fun setDest(p: LL?) { dest = p; pushDest() }
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
    class RoadPiece(val pts: List<Point>, val cls: String, val oneway: Boolean?)
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
            when (val g = f.geometry()) {
                is LineString -> info += RoadPiece(g.coordinates(), cls, one)
                is MultiLineString -> for (c in g.coordinates()) info += RoadPiece(c, cls, one)
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
     */
    fun snapToRoad(p: LL, heading: Double): LL? {
        val segs = roadSegs
        if (segs.isEmpty() || android.os.SystemClock.uptimeMillis() - roadSegsAt > 20_000L) return null
        return try {
            val k = Math.cos(Math.toRadians(p.lat)) * 111320.0
            val k2 = 110574.0
            var best: LL? = null
            var bd = 24.0
            for (pts in segs) {
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
                    // only roads that run the way you are heading (either direction), so a parallel street or a crossing road is not chosen
                    val segB = (Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0
                    var diff = Math.abs(segB - heading) % 360.0
                    if (diff > 180.0) diff = 360.0 - diff
                    if (diff > 35.0 && diff < 145.0) continue
                    bd = d
                    best = LL(p.lat + (ay + dy * t) / k2, p.lon + (ax + dx * t) / k)
                }
            }
            best
        } catch (e: Exception) { null }
    }

    /**
     * A tap on the map at screen point [sp] (also used for taps on the Android Auto screen): skips the route fly-through, or picks an
     * alternative route by its card or line. True when it did something. Worked out from where the cards and lines are on screen,
     * so it never has to wait for the map.
     */
    fun tapAt(sp: android.graphics.PointF): Boolean {
        // a tap during the route fly-through skips straight to the overview
        flyDone?.let { d -> flyDone = null; flyToken++; try { mapView.camera.cancelAllAnimators() } catch (e: Throwable) { }; d(); return true }
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
            mbMap.setCamera(CameraOptions.Builder().zoom((z + dz).coerceIn(1.0, 21.0)).anchor(ScreenCoordinate(x.toDouble(), y.toDouble())).build())
        } catch (e: Throwable) { }
    }

    /** Smoothly zooms out (flat, north-up) so every point sits in the area above the route-preview panel. */
    fun fit(points: List<LL>) {
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
        val pl = w * 0.07; val pr = w * 0.07; val pt = h * 0.10; val pb = h * 0.60
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
