package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.L
import com.headunit.launcher.NavEngine

/**
 * Quick reports on the Android Auto screen (GitHub #290): the flag on the trip card opens this small panel over the map, and one
 * tap reports at where you are and closes it. It also closes by itself after 8 seconds, so it never stays over the map. (Cars
 * that only have a knob still have Report among Android Auto's own buttons.)
 */
object CarReportCard {
    var open by mutableStateOf(false)
}

@Composable
internal fun CarReportPanel(engine: NavEngine, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(8000); CarReportCard.open = false }
    val shape = RoundedCornerShape(20.dp)
    fun send(kind: String) { engine.report(kind); CarReportCard.open = false }
    Column(modifier.width(280.dp).panel(8.dp, shape, c.surface).border(1.dp, c.line, shape).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L.t("Report here"), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.line).carTap("report-x") { CarReportCard.open = false }, contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Close, null, tint = c.text, modifier = Modifier.size(20.dp))
            }
        }
        @Composable
        fun tile(id: String, icon: ImageVector, label: String, bg: Color, fg: Color, m: Modifier) {
            Column(m.height(62.dp).clip(RoundedCornerShape(14.dp)).background(bg).carTap("report-$id") { send(id) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(24.dp))
                Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tile("police", Icons.Rounded.LocalPolice, L.t("Police"), Color(0xFF1E66F5), Color.White, Modifier.weight(1f))
            tile("accident", Icons.Rounded.CarCrash, L.t("Crash"), Color(0xFFD93B3B), Color.White, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tile("hazard", Icons.Rounded.PriorityHigh, L.t("Hazard"), Color(0xFFE6A21A), Color(0xFF2A1C00), Modifier.weight(1f))
            tile("breakdown", Icons.Rounded.Build, L.t("Broken down"), Color(0xFFFB8C00), Color(0xFF2A1600), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tile("traffic", Icons.Rounded.Traffic, L.t("Traffic jam"), Color(0xFFB01C2E), Color.White, Modifier.weight(1f))
            tile("mobilecam", Icons.Rounded.CameraAlt, L.t("Mobile camera"), Color(0xFF1E66F5), Color.White, Modifier.weight(1f))
        }
    }
}
