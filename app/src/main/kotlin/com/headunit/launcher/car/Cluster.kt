package com.headunit.launcher.car

import android.app.Presentation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.headunit.launcher.LL
import com.headunit.launcher.MapBinding
import com.headunit.launcher.MapboxRest
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.Prefs

/**
 * The main car screen's engine, for the driver's display to follow (each Android Auto screen gets its own session, and they can't
 * reach each other otherwise). Null while the main car screen isn't running.
 */
object ClusterLink {
    @Volatile var engine: NavEngine? = null
}

/**
 * The driver's display (the screen behind the steering wheel), on cars whose Android Auto offers it to navigation apps.
 * Android Auto only shows map tiles there, so this is just the map: Mapbox's plain 2D driving map (much lighter for the phone to
 * draw than the 3D one on the main screen), following your position, heading and route from the main car screen. It can be the
 * only Android Auto screen the app has (when the main screen is showing another app); then it shows the map where you last were.
 * Switched off in the car's Settings > Map on the driver's display.
 */
class ClusterSession : Session() {
    private var surface: ClusterSurface? = null

    override fun onCreateScreen(intent: android.content.Intent): Screen {
        val s = ClusterSurface(carContext)
        surface = s
        try { carContext.getCarService(AppManager::class.java).setSurfaceCallback(s) } catch (e: Exception) { }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { try { s.release() } catch (e: Exception) { } }
        })
        return ClusterScreen(carContext)
    }
}

/** The driver's display only shows the map: the template is just there because Android Auto needs one. */
class ClusterScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = NavigationTemplate.Builder()
        .setActionStrip(ActionStrip.Builder().addAction(Action.Builder().setTitle("Horizon Nav").setOnClickListener { }.build()).build())
        .build()
}

class ClusterSurface(private val carContext: CarContext) : SurfaceCallback {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var map: MapBinding? = null
    private val main = Handler(Looper.getMainLooper())
    private var width = 1
    private var height = 1
    private var routeShown: List<LL>? = null
    private var dark: Boolean? = null
    private var running = false
    /** The map needs a lifecycle to attach to (there's no activity on the driver's display): without it, it crashed the app. */
    private val owner = CarComposeOwner()
    @Volatile private var closing = false

    private val prefs: Prefs by lazy { Prefs(carContext.applicationContext) }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        // switched off in the car's settings: nothing is drawn (Android Auto shows its own plain screen)
        if (!prefs.b(PreferenceKeys.CLUSTER_MAP, true)) return
        val s = surfaceContainer.surface ?: return
        width = surfaceContainer.width.coerceAtLeast(1); height = surfaceContainer.height.coerceAtLeast(1)
        val existing = display
        if (existing != null) {
            existing.surface = s
            existing.resize(width, height, surfaceContainer.dpi)
            map?.carResume()
            start()
            return
        }
        try {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay("HorizonNavCluster", width, height, surfaceContainer.dpi, s, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            display = vd
            // (kept open if the driver's display changes size, as the main car screen's is: Android would otherwise close it)
            val p = object : Presentation(carContext, vd.display) {
                override fun cancel() { if (closing) super.cancel() }
            }
            try {
                val tok = MapboxRest.clean(Prefs(p.context.applicationContext).s(PreferenceKeys.MAPBOX_TOKEN, ""))
                if (tok.isNotEmpty() && com.mapbox.common.MapboxOptions.accessToken != tok) com.mapbox.common.MapboxOptions.accessToken = tok
            } catch (e: Throwable) { }
            val dens = p.context.resources.displayMetrics.density
            val m = MapBinding(p.context, true, 1f, (dens * 1.3f).coerceIn(1.25f, 2.5f))
            // the lightest map: no 3D extras, no lamps (it's a second map for the phone to draw, on top of the main screen's)
            m.lampsEnabled(false)
            m.setExtras(false, false, false, false, false, false, false)
            m.setTheme("immersive")
            arrowSet = -1
            val root = FrameLayout(p.context)
            root.addView(m.mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            root.addView(m.roadView, FrameLayout.LayoutParams(1, 1))
            root.addView(m.locatorView, FrameLayout.LayoutParams(1, 1))
            // the trip bar at the bottom centre (time left · distance · arrival, as Google Maps has), only with a route
            val tv = android.widget.TextView(p.context).apply {
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13f)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                val padH = (12 * dens).toInt(); val padV = (4 * dens).toInt()
                setPadding(padH, padV, padH, padV)
                maxLines = 1
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 100f * dens; setColor(0xD910151D.toInt()) }
                visibility = android.view.View.GONE
            }
            root.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (8 * dens).toInt() })
            tripBar = tv; barDens = dens
            owner.start()
            root.setViewTreeLifecycleOwner(owner)
            root.setViewTreeSavedStateRegistryOwner(owner)
            root.setViewTreeViewModelStoreOwner(owner)
            p.setContentView(root)
            p.show()
            try { m.mapView.onStart() } catch (e: Throwable) { }
            presentation = p; map = m
            val tok = MapboxRest.clean(prefs.s(PreferenceKeys.MAPBOX_TOKEN, ""))
            if (tok.isNotEmpty()) m.setToken(tok)
            start()
        } catch (e: Exception) { android.util.Log.w("CarNav", "couldn't put the map on the driver's display", e) }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        stop()
        map?.carPause()
        display?.surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        val m = map ?: return
        if (visibleArea.width() <= width * 0.3 || visibleArea.height() <= height * 0.3) return
        m.viewInset = Rect(visibleArea.left.coerceAtLeast(0), visibleArea.top.coerceAtLeast(0),
            (width - visibleArea.right).coerceAtLeast(0), (height - visibleArea.bottom).coerceAtLeast(0))
        // the trip bar stays inside the part of the display the driver can see, centred on it
        tripBar?.let { tv ->
            try {
                val lp = tv.layoutParams as FrameLayout.LayoutParams
                lp.bottomMargin = (height - visibleArea.bottom).coerceAtLeast(0) + (8 * barDens).toInt()
                val shift = (visibleArea.left.coerceAtLeast(0) - (width - visibleArea.right).coerceAtLeast(0)) / 2
                lp.leftMargin = shift.coerceAtLeast(0); lp.rightMargin = (-shift).coerceAtLeast(0)
                tv.layoutParams = lp
            } catch (e: Exception) { }
        }
    }
    private var tripBar: android.widget.TextView? = null
    private var barDens = 1f
    private var barText = ""
    private var barAt = 0L

    override fun onStableAreaChanged(stableArea: Rect) {}

    // ---- following the main car screen: about 15 times a second (smooth enough for a small screen, and light on the phone) ----
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try { follow() } catch (e: Exception) { }
            main.postDelayed(this, 66L)
        }
    }

    private fun start() { if (!running) { running = true; main.post(tick) } }
    private fun stop() { running = false; main.removeCallbacks(tick) }

    private fun follow() {
        val m = map ?: return
        val e = ClusterLink.engine
        // day or night as the car says, with Mapbox's driving map in the matching colours
        val night = try { carContext.isDarkMode } catch (x: Exception) { true }
        if (night != dark) {
            dark = night
            m.setStyleUrl(if (night) "mapbox://styles/mapbox/navigation-night-v1" else "mapbox://styles/mapbox/navigation-day-v1")
        }
        val st = e?.clusterState()
        val pos = st?.first ?: lastKnown() ?: return
        val bearing = st?.second ?: 0.0
        val route = st?.third ?: emptyList()
        if (route !== routeShown) {
            routeShown = route
            try { m.setRoute(route, emptyList()) } catch (x: Exception) { }
        }
        // the arrow follows the car screen's Arrow size (it used to stay at the map's own, much bigger, size on the driver's display)
        val arrowPct = prefs.i(PreferenceKeys.CAR_ARROW_SIZE, 100).coerceIn(30, 180)
        if (arrowPct != arrowSet) { arrowSet = arrowPct; try { m.setArrowScale(1.2f * arrowPct / 100f) } catch (x: Exception) { } }
        m.setMe(pos, bearing, 0)
        // further out than before (it was too close to be much use, GitHub), a little closer with a route for the next turn, easing out
        // at speed so there's more road ahead, and Closer / Further from the car's settings
        val kmh = (e?.fix?.speed ?: 0.0) * 3.6
        val speedOut = ((kmh - 50.0) / 70.0).coerceIn(0.0, 1.0) * 0.8
        val user = prefs.i(PreferenceKeys.CLUSTER_ZOOM, 0).coerceIn(-2, 1) * 0.7
        val target = (if (route.isNotEmpty()) 15.1 else 14.7) - speedOut + user
        // eased, so the speed part doesn't make the map breathe in and out
        zoomNow = if (zoomNow < 0) target else zoomNow + (target - zoomNow) * 0.05
        m.cameraSet(pos, bearing, 40.0, zoomNow, 0.35, 0.0)
        updateTripBar(e, route.isNotEmpty())
    }

    /** Time left · distance · arrival, refreshed once a second (the text only changes when the numbers do). */
    private fun updateTripBar(e: NavEngine?, routing: Boolean) {
        val tv = tripBar ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - barAt < 1000L) return
        barAt = now
        val eta = if (routing) e?.eta else null
        val t = if (eta == null) "" else try {
            listOf(com.headunit.launcher.Geo.fmtDur(eta.secs), com.headunit.launcher.Geo.fmtDist(eta.dist, e?.imperial == true), eta.arrive).joinToString(" · ")
        } catch (x: Exception) { "" }
        if (t == barText) return
        barText = t
        if (t.isEmpty()) tv.visibility = android.view.View.GONE else { tv.text = t; tv.visibility = android.view.View.VISIBLE }
    }
    private var arrowSet = -1
    private var zoomNow = -1.0

    /** Where you were last, from the phone's saved start position (for when the main car screen isn't running). */
    private fun lastKnown(): LL? = try {
        val q = prefs.s(PreferenceKeys.STARTPOS, "").split(",")
        val la = q.getOrNull(0)?.toDoubleOrNull(); val lo = q.getOrNull(1)?.toDoubleOrNull()
        if (la != null && lo != null) LL(la, lo) else null
    } catch (e: Exception) { null }

    fun release() {
        closing = true
        stop()
        try { map?.mapView?.onStop() } catch (e: Throwable) { }
        try { map?.mapView?.onDestroy() } catch (e: Throwable) { }
        try { presentation?.dismiss() } catch (e: Throwable) { }
        try { display?.release() } catch (e: Throwable) { }
        try { owner.stop() } catch (e: Throwable) { }
        map = null; presentation = null; display = null; tripBar = null
    }
}
