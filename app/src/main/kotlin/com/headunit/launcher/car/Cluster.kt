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

    private val prefs: Prefs get() = Prefs(carContext.applicationContext)

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
            val p = Presentation(carContext, vd.display)
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
            val root = FrameLayout(p.context)
            root.addView(m.mapView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            root.addView(m.roadView, FrameLayout.LayoutParams(1, 1))
            root.addView(m.locatorView, FrameLayout.LayoutParams(1, 1))
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
    }

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
        m.setMe(pos, bearing, 0)
        // a little closer when there's a route (the next turn matters more on a small screen), further out when just driving
        m.cameraSet(pos, bearing, 40.0, if (route.isNotEmpty()) 16.2 else 15.6, 0.35, 0.0)
    }

    /** Where you were last, from the phone's saved start position (for when the main car screen isn't running). */
    private fun lastKnown(): LL? = try {
        val q = prefs.s(PreferenceKeys.STARTPOS, "").split(",")
        val la = q.getOrNull(0)?.toDoubleOrNull(); val lo = q.getOrNull(1)?.toDoubleOrNull()
        if (la != null && lo != null) LL(la, lo) else null
    } catch (e: Exception) { null }

    fun release() {
        stop()
        try { map?.mapView?.onStop() } catch (e: Throwable) { }
        try { map?.mapView?.onDestroy() } catch (e: Throwable) { }
        try { presentation?.dismiss() } catch (e: Throwable) { }
        try { display?.release() } catch (e: Throwable) { }
        try { owner.stop() } catch (e: Throwable) { }
        map = null; presentation = null; display = null
    }
}
