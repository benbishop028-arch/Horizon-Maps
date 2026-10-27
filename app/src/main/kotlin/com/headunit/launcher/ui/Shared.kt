package com.headunit.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.NavEngine
import com.headunit.launcher.Prefs
import com.headunit.launcher.PreferenceKeys

/** True when the temperature unit is Fahrenheit. */
/** Fahrenheit: as chosen in Settings, else from the country you're in (the US and a few others), else Celsius. */
fun isImperial(prefs: Prefs): Boolean = prefs.s(PreferenceKeys.TEMPERATURE_UNIT, if (prefs.s(PreferenceKeys.COUNTRYDETECTED, "").uppercase() in com.headunit.launcher.FAHRENHEIT_COUNTRIES) "F" else "C") == "F"

/** Colour for a weather code (Open-Meteo / WMO codes). */
internal fun wxTint(code: Int): Color = when (code) {
    0 -> Color(0xFFFFC233)
    1, 2 -> Color(0xFFFFD27A)
    3, 45, 48 -> Color(0xFFA9B4C4)
    in 51..67, in 80..82 -> Color(0xFF4DA3FF)
    in 71..77, in 85..86 -> Color(0xFFDCEBFF)
    in 95..99 -> Color(0xFFFFB020)
    else -> Color(0xFFA9B4C4)
}

internal fun wxIcon(code: Int): ImageVector = when (code) {
    0 -> Icons.Rounded.WbSunny
    1, 2 -> Icons.Rounded.WbCloudy
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
    in 71..77, in 85..86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Bolt
    else -> Icons.Rounded.Cloud
}

/** One settings row: a label on the left, its controls on the right, and a hairline under it. */
@Composable
internal fun Row2(title: String, content: @Composable () -> Unit) {
    val c = LocalVw.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.28f)))
}

/** Opens an Android settings screen, falling back to the main settings app. */
internal fun openSystemSettings(ctx: android.content.Context, action: String, uri: Uri? = null) {
    try {
        val i = Intent(action)
        if (uri != null) i.data = uri
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }
}

/**
 * A clock for gentle effects: milliseconds since it started, ticking [fps] times a second (not at the screen's full rate).
 * Read it inside a draw block so only the drawing repeats, never the composition.
 */
@Composable
fun rememberEffectClock(fps: Int): androidx.compose.runtime.State<Long> {
    val ms = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(fps) {
        val start = android.os.SystemClock.uptimeMillis()
        val gap = (1000L / fps.coerceIn(1, 60))
        while (true) { kotlinx.coroutines.delay(gap); ms.longValue = android.os.SystemClock.uptimeMillis() - start }
    }
    return ms
}

/** Light rain streaks drawn over the map (purely cosmetic, off by default). */
@Composable
fun RainOverlay(modifier: Modifier = Modifier, heavy: Boolean = false) {
    val drops = remember(heavy) { List(if (heavy) 130 else 70) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.6f + Math.random().toFloat() * 0.8f) } }
    // 30 updates a second is plenty for rain and halves the work compared with the full screen rate
    val clock = rememberEffectClock(30)
    val night = LocalVw.current.dark
    androidx.compose.foundation.Canvas(modifier) {
        val t = (clock.value % 1100L) / 1100f
        // Resolve density-dependent dimensions once per frame, not once for every raindrop.
        val baseLength = 16.dp.toPx()
        val strokeWidth = 1.5.dp.toPx()
        val baseColour = if (night) Color.White else Color(0xFF3F5F80)
        val baseAlpha = if (night) 0.30f else 0.50f
        for (d in drops) {
            val x = d[0] * size.width
            val y = ((d[1] + t * d[2]) % 1f) * size.height
            val len = baseLength * d[2]
            drawLine(
                baseColour.copy(alpha = baseAlpha * d[2]),
                Offset(x, y), Offset(x - len * 0.25f, y + len), strokeWidth,
            )
        }
    }
}

/** Snow drifting down the screen, slowly and with a little sway. */
@Composable
fun SnowOverlay(modifier: Modifier = Modifier, heavy: Boolean = false) {
    val flakes = remember { List(if (heavy) 80 else 50) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), 0.5f + Math.random().toFloat() * 0.8f, Math.random().toFloat() * 6.28f) } }
    val clock = rememberEffectClock(24)
    androidx.compose.foundation.Canvas(modifier) {
        val t = (clock.value % 9000L) / 9000f
        // Convert the sway distance once per frame instead of for every flake.
        val swayPx = 14.dp.toPx()
        for (f in flakes) {
            val y = ((f[1] + t * f[2]) % 1f) * size.height
            val x = f[0] * size.width + kotlin.math.sin(t * 12.56f + f[3]) * swayPx * f[2]
            drawCircle(Color.White.copy(alpha = 0.55f * f[2]), (1.6f + 1.8f * f[2]) * density, Offset(x, y))
        }
    }
}

/** Fog: a soft white haze that thickens towards the horizon. Nothing moves, so it costs nothing to draw. */
@Composable
fun FogOverlay(modifier: Modifier = Modifier) {
    val night = LocalVw.current.dark
    val col = if (night) Color(0xFF9AA3B5) else Color(0xFFF2F4F7)
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0.22f), col.copy(alpha = 0.08f))), Offset.Zero, size)
    }
}

/** The weather effect for an Open-Meteo weather code: rain (heavier in a downpour or storm), snow or fog. */
@Composable
fun WeatherFx(code: Int, modifier: Modifier = Modifier) {
    when (code) {
        45, 48 -> FogOverlay(modifier)
        in 71..77, 85, 86 -> SnowOverlay(modifier, heavy = code == 75 || code == 86)
        in 51..67, in 80..82, in 95..99 -> {
            RainOverlay(modifier, heavy = code == 65 || code == 82 || code >= 95 || code == 63 || code == 81)
        }
        else -> { }
    }
}

// ------------------------------------------------------------------ share ETA by text message
@Composable
fun rememberPhonePicker(onPicked: (String, String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) onPicked(it.getString(0) ?: "", it.getString(1) ?: "")
            }
        } catch (e: Exception) { }
    }
    return {
        try { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } catch (e: Exception) { }
    }
}

@Composable
fun rememberEtaContactPicker(prefs: Prefs, onPicked: () -> Unit = {}): () -> Unit =
    rememberPhonePicker { num, name ->
        prefs.put(PreferenceKeys.ETA_NUMBER, num)
        prefs.put(PreferenceKeys.ETA_NAME, name)
        onPicked()
    }

@Composable
fun rememberShareEta(engine: NavEngine): () -> Unit {
    val ctx = LocalContext.current
    val prefs = engine.prefs
    fun message(): String {
        val d = engine.dest?.name ?: "my destination"
        val e = engine.eta
        val p = engine.fix?.pos
        return "I'm on my way to $d" + (if (e != null) ", arriving about ${e.arrive}" else "") +
            (if (p != null) ". Where I am now: https://maps.google.com/?q=${p.lat},${p.lon}" else ".")
    }
    fun shareNow() {
        val num = prefs.s(PreferenceKeys.ETA_NUMBER, "")
        if (num.isBlank()) return
        val msg = message()
        try {
            // Hand the prepared message to the user's messaging app. Horizon Nav never sends an SMS directly.
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
                .putExtra("sms_body", msg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            engine.toast("No messaging app available", 3000)
        }
    }
    val pickContact = rememberEtaContactPicker(prefs) { engine.toast("Contact saved. Tap share again to send.", 3500) }
    return {
        if (prefs.s(PreferenceKeys.ETA_NUMBER, "").isBlank()) {
            engine.toast("Choose who to send your ETA to", 3000)
            pickContact()
        } else shareNow()
    }
}


// ------------------------------------------------------------------ your own locator picture
/**
 * Opens the gallery / file picker for a locator picture. The picture is shrunk (so a 12-megapixel photo costs nothing to draw)
 * and saved inside the app as locator.png, then [onDone] gets true, or false when it couldn't be read. Returns null when the
 * head unit has no app that can pick a picture.
 */
@Composable
fun rememberLocatorImagePicker(onDone: (Boolean) -> Unit): () -> Boolean {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val app = ctx.applicationContext
        Thread {
            val ok = try { saveLocatorImage(app, uri) } catch (e: Throwable) { false }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(ok) }
        }.start()
    }
    return {
        val tries = listOf(
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),
            Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),
        )
        var started = false
        for (i in tries) {
            if (started) break
            try { pick.launch(i); started = true } catch (e: Exception) { }
        }
        started
    }
}

/** Reads the picked picture at a small size and saves it as the locator (largest side 256 px, transparency kept). */
fun saveLocatorImage(ctx: android.content.Context, uri: android.net.Uri): Boolean {
    val cr = ctx.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // (decoding only the size returns no picture, so this must not be mistaken for a failure)
    val probe = cr.openInputStream(uri) ?: return false
    probe.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return false
    val k = 256f / maxOf(raw.width, raw.height)
    val small = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(raw, maxOf(1, (raw.width * k).toInt()), maxOf(1, (raw.height * k).toInt()), true) else raw
    val f = java.io.File(ctx.filesDir, "locator.png")
    val tmp = java.io.File(ctx.filesDir, "locator.tmp")
    tmp.outputStream().use { small.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    if (small !== raw) small.recycle()
    raw.recycle()
    if (!tmp.renameTo(f)) { tmp.copyTo(f, true); tmp.delete() }
    return true
}
