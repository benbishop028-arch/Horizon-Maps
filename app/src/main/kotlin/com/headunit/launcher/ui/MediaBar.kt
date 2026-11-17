package com.headunit.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.L
import com.headunit.launcher.MediaNow

/** Keeps [MediaNow] following what's playing while the driving screen is up (and the bar is switched on). */
@Composable
internal fun FollowMedia(on: Boolean) {
    val ctx = LocalContext.current
    if (!on) return
    DisposableEffect(Unit) {
        MediaNow.start(ctx)
        onDispose { MediaNow.stop() }
    }
}

/**
 * The media bar (Settings › Screen & sizes › While driving › Media controls): the artwork, song and artist, and back,
 * play / pause and next, for whatever is playing on the head unit. Without notification access it says so instead of the song,
 * and a tap on that part opens the page to give it.
 */
@Composable
internal fun MediaBar(modifier: Modifier = Modifier) {
    val c = LocalVw.current
    val ctx = LocalContext.current
    // (back from Android's settings page with access given: pick it up straight away)
    LaunchedEffect(Unit) { if (!MediaNow.hasAccess && MediaNow.accessGranted(ctx)) MediaNow.recheck() }
    Row(
        modifier.panel(6.dp, RoundedCornerShape(26.dp), c.surface).padding(start = 6.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.weight(1f).then(if (!MediaNow.hasAccess) Modifier.pressClick { MediaNow.openAccessSettings(ctx) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.tile), contentAlignment = Alignment.Center) {
                val a = MediaNow.art
                if (a != null) Image(a, null, Modifier.size(40.dp), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = c.sub, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 10.dp, end = 4.dp)) {
                val title = MediaNow.title.ifBlank { L.t("Media") }
                val sub = when {
                    !MediaNow.hasAccess -> L.t("Tap to allow song info")
                    MediaNow.artist.isNotBlank() && MediaNow.app.isNotBlank() -> "${MediaNow.artist} · ${MediaNow.app}"
                    else -> MediaNow.artist.ifBlank { MediaNow.app }
                }
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, color = c.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaKey(Icons.Rounded.SkipPrevious, L.t("Previous"), false) { MediaNow.press(ctx, "prev") }
        MediaKey(if (MediaNow.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, L.t(if (MediaNow.playing) "Pause" else "Play"), true) { MediaNow.press(ctx, "toggle") }
        MediaKey(Icons.Rounded.SkipNext, L.t("Next"), false) { MediaNow.press(ctx, "next") }
    }
}

/** A round button, big enough to hit while driving (44 dp). */
@Composable
private fun MediaKey(icon: ImageVector, desc: String, main: Boolean, onClick: () -> Unit) {
    val c = LocalVw.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).then(if (main) Modifier.background(c.text) else Modifier).pressClick(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (main) c.surface.copy(alpha = 1f).let { if (it.alpha < 0.5f) Color.Black else it } else c.text, modifier = Modifier.size(26.dp)) }
}
