package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.Geo
import com.headunit.launcher.NavEngine

/**
 * On the phone while Android Auto is connected: the car screen has the map and does the talking, so the phone shows the trip in brief
 * (no second live map drawing away, which saves battery and keeps the phone cool in a car mount). The map can still be opened here.
 */
@Composable
internal fun CarConnectedScreen(engine: NavEngine, onShowMap: () -> Unit, onSettings: () -> Unit) {
    val c = LocalVw.current
    val guiding = engine.route != null && !engine.previewing
    val e = engine.eta
    Box(Modifier.fillMaxSize().background(c.bg).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DirectionsCar, null, tint = c.accent, modifier = Modifier.size(44.dp))
            }
            Text(if (guiding) "Navigating on your car screen" else "Horizon Nav is on your car screen", color = c.text, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (guiding && e != null) {
                val dc = if (e.delayMin >= 10) Bad else if (e.delayMin >= 1) Warn else Good
                Column(Modifier.panel(10.dp, RoundedCornerShape(24.dp), c.surface).padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(engine.dest?.name ?: "Your destination", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        ImmStat(Geo.fmtDur(e.secs), if (e.delayMin >= 1) "+${e.delayMin} min traffic" else "time left", dc)
                        ImmStat(e.arrive, "arrival", c.text)
                        ImmStat(Geo.fmtDist(e.dist, engine.imperial), "to go", c.text)
                    }
                }
                ImmPill("End route", { engine.endRoute() }, icon = Icons.Rounded.Close)
            } else {
                Text("Pick where to go on the car screen, or share a place to Horizon Nav from any app and it appears there.",
                    color = c.sub, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ImmPill("Show the map here", onShowMap, icon = Icons.Rounded.Map)
                ImmPill("Settings", onSettings, icon = Icons.Rounded.Settings)
            }
        }
    }
}
