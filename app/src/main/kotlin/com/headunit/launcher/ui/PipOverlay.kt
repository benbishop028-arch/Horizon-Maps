package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.NavEngine

/** The app is showing in Android's small picture-in-picture window (a phone, on a route, with another app in front; #301). */
object PipUi {
    var on by mutableStateOf(false)
}

/**
 * What the picture-in-picture window shows over the map: the next turn along the top (arrow, distance, road) and the time left and
 * arrival along the bottom. No buttons: the window can't be tapped into, and Android's own controls on it open the app.
 */
@Composable
internal fun PipOverlay(engine: NavEngine) {
    Box(Modifier.fillMaxSize()) {
        val t = engine.turn
        if (t != null) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(GmGreen).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ManeuverArrow(t.maneuver, Geo.maneuverAngle(t.maneuver), Modifier.size(26.dp), roundaboutExit = t.exit, roundaboutClockwise = engine.roundaboutClockwise)
            Column(Modifier.padding(start = 6.dp)) {
                Text(Geo.fmtDist(t.dist, engine.imperial), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (t.street.isNotBlank()) Text(t.street, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val e = engine.eta
        if (e != null) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE61F2328)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(Geo.fmtDur(e.secs), color = Color(0xFF4CD384), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Text(e.arrive, color = Color.White, fontSize = 12.sp, maxLines = 1)
        }
    }
}
