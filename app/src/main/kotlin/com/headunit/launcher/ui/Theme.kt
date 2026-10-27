package com.headunit.launcher.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.headunit.launcher.Sun

data class Vw(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color,
    val text: Color, val sub: Color, val accent: Color, val line: Color,
    val panel: Color, val tile: Color,
    /** Text colours for things drawn straight on the background, so they stay readable when the background colour is overridden. */
    val onBg: Color = text, val onBgSub: Color = sub,
    /** Wash laid over the map so it matches non-default looks. */
    val mapTint: Color = Color.Transparent,
)

val DarkVw = Vw(true, Color(0xFF08090B), Color(0xFF232326), Color(0xFF303034), Color(0xFFF4F5F7), Color(0xFF9A9AA3), Color(0xFF3FA9F5), Color(0xFF3C3C41), Color(0xFF2A2A2D), Color(0xFF151517))
val LightVw = Vw(false, Color(0xFFB0B8C8), Color(0xFFE6E9F0), Color(0xFFD9DDE7), Color(0xFF14171C), Color(0xFF59616D), Color(0xFF0B79D0), Color(0xFFBCC2CB), Color(0xFFD9DDE3), Color(0xFFE8EBEF))

val LocalVw = staticCompositionLocalOf { DarkVw }

val Blue = Color(0xFF2A3647)
val BlueSoft = Color(0xFF364559)
val Good = Color(0xFF34C759)
val Warn = Color(0xFFFFB020)
val Bad = Color(0xFFFF4D4D)

@Composable
fun resolveDark(mode: String, lat: Double?, lon: Double?, nowMs: Long, sysSeen: Boolean = false, system: Boolean = isSystemInDarkTheme(),
                nightFrom: String = "19:00", dayFrom: String = "07:00"): Boolean {
    val sun = if (lat != null && lon != null) !Sun.isDay(lat, lon, nowMs) else null
    return when (mode) {
        "day" -> false
        "night" -> true
        // your own times (for a unit with no GPS fix to work out sunset from, or if you just prefer them)
        "times" -> isNightByClock(nowMs, nightFrom, dayFrom)
        "sun" -> if (lat != null && lon != null) !Sun.isDay(lat, lon, nowMs) else system
        // headlights: head units that switch their own night mode with the lights (the illumination wire) are followed; until this
        // unit has been seen doing that, sunrise and sunset stand in
        "lights" -> if (system) true else if (sysSeen) false else if (lat != null && lon != null) !Sun.isDay(lat, lon, nowMs) else false
        // auto: the head unit's own night mode once it has been seen switching to it; sunrise and sunset until then (many units never
        // switch their system mode, and auto then never went dark)
        else -> if (system) true else if (sysSeen) false else sun ?: false
    }
}

/** Whether [nowMs] falls between [nightFrom] and [dayFrom] ("HH:MM", local time; the night runs over midnight when it starts later). */
fun isNightByClock(nowMs: Long, nightFrom: String, dayFrom: String): Boolean {
    fun mins(t: String, def: Int): Int {
        val p = t.trim().replace('.', ':').split(':')
        val h = p.getOrNull(0)?.toIntOrNull() ?: return def
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return if (h in 0..23 && m in 0..59) h * 60 + m else def
    }
    val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
    val n = mins(nightFrom, 19 * 60); val d = mins(dayFrom, 7 * 60)
    return if (n > d) now >= n || now < d else now in n until d
}

/**
 * The head unit's own night mode, read from the system every few seconds as well as when the screen's settings change: some units
 * switch it with the headlights without telling the app it changed, so the map stayed in day mode.
 */
@Composable
fun rememberSystemNight(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val fromConfig = isSystemInDarkTheme()
    var polled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(readSystemNight(ctx)) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(4000L); polled = readSystemNight(ctx) } }
    return fromConfig || polled
}

fun readSystemNight(ctx: android.content.Context): Boolean = try {
    val cfg = ctx.applicationContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    val um = (ctx.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.nightMode
    cfg == android.content.res.Configuration.UI_MODE_NIGHT_YES || um == android.app.UiModeManager.MODE_NIGHT_YES
} catch (e: Throwable) { false }

@Composable
fun VwTheme(dark: Boolean, content: @Composable () -> Unit) {
    var v = if (dark) DarkVw else LightVw
    // text that sits directly on the background always takes whichever of white or near-black reads best
    run {
        fun cr(a: Color, b: Color): Float {
            val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
            return kotlin.math.max(la, lb) / kotlin.math.min(la, lb)
        }
        val white = Color(0xFFFFFFFF); val black = Color(0xFF0B0D10)
        val on = if (cr(white, v.bg) >= cr(black, v.bg)) white else black
        var sub = androidx.compose.ui.graphics.lerp(on, v.bg, 0.22f)
        if (cr(sub, v.bg) < 4.5f) sub = on
        v = v.copy(onBg = on, onBgSub = sub)
    }
    val scheme = if (dark) darkColorScheme(primary = v.accent, background = v.bg, surface = v.surface, onSurface = v.text, onBackground = v.text)
    else lightColorScheme(primary = v.accent, background = v.bg, surface = v.surface, onSurface = v.text, onBackground = v.text)
    CompositionLocalProvider(LocalVw provides v) { MaterialTheme(colorScheme = scheme, content = content) }
}

val NavBlue = Color(0xFF4DA3FF)
