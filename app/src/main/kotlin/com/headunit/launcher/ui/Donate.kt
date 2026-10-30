package com.headunit.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Where donations go. */
internal const val DONATE_URL = "https://www.paypal.me/BBishop462"
internal const val DONATE_SHORT = "paypal.me/BBishop462"

/** Opens the PayPal page in a browser; a head unit with no browser gets a note to scan the code with a phone instead. */
internal fun openDonate(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(ctx, "There's no web browser on this unit. Scan the code with your phone instead.", android.widget.Toast.LENGTH_LONG).show()
    }
}

/** A QR code of [text], black on white, [px] pixels square (null if it couldn't be made). */
/** A QR code of [text] for other screens (the phone pairing code). */
internal fun donateQr(text: String, px: Int): ImageBitmap? = qrBitmap(text, px)

private fun qrBitmap(text: String, px: Int): ImageBitmap? = try {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val w = m.width; val h = m.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) pixels[y * w + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
} catch (e: Throwable) { null }

/**
 * The donate card: a short note, a QR code that opens PayPal on a phone, "Donate on this screen" and "Maybe later". Wide (two panes)
 * in landscape like the What's new card, one column in portrait. Either button closes it.
 */
@Composable
internal fun DonateDialog(dark: Boolean, onClose: () -> Unit) {
    val v = if (dark) DarkVw else LightVw
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    val wide = conf.screenWidthDp > conf.screenHeightDp && conf.screenWidthDp >= 640
    val pink = if (dark) Color(0xFFFF9CC8) else Color(0xFFB0306E)
    val headBg = if (dark) Color(0xFF3A2340) else Color(0xFFFBE3EF)
    val headText = if (dark) Color(0xFFE5D3E6) else Color(0xFF5B3150)
    val qr = remember { qrBitmap(DONATE_URL, 400) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = !wide)) {
        val shape = RoundedCornerShape(28.dp)
        val maxH = (conf.screenHeightDp.dp - 32.dp).coerceAtLeast(240.dp)
        val head: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(headBg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(pink.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, null, tint = pink, modifier = Modifier.size(24.dp))
                }
                Text("HORIZON NAV", color = pink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Enjoying Horizon Nav?", color = v.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                Text("It's free, with no ads, made by one person in their spare time. If it's useful to you, a small donation helps keep it going.",
                    color = headText, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        val code: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (qr != null) Box(Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp)) {
                    Image(qr, "QR code for the PayPal donation page", Modifier.size(124.dp), filterQuality = FilterQuality.None)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Scan with your phone", color = v.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Opens PayPal on your phone", color = v.sub, fontSize = 14.sp)
                    Text(DONATE_SHORT, color = v.sub, fontSize = 14.sp)
                }
            }
        }
        val buttons: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { openDonate(ctx); onClose() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pink, contentColor = if (dark) Color(0xFF3A0F28) else Color.White)) {
                    Text("Donate on this screen", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
                Button(onClick = onClose, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = v.tile, contentColor = v.text)) {
                    Text("Maybe later", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        if (wide) {
            val cardW = (conf.screenWidthDp.dp * 0.88f).coerceAtMost(980.dp)
            Row(Modifier.width(cardW).heightIn(max = maxH).clip(shape).background(v.surface).border(1.dp, v.line, shape).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(0.42f)) { head() }
                Column(Modifier.weight(0.58f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { code(); buttons() }
            }
        } else {
            Column(Modifier.fillMaxWidth().heightIn(max = maxH).clip(shape).background(v.surface).border(1.dp, v.line, shape).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) { head(); code(); buttons() }
        }
    }
}
