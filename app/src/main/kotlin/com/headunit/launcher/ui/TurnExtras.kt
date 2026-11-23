package com.headunit.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.AvgInfo
import com.headunit.launcher.Geo
import com.headunit.launcher.L
import com.headunit.launcher.LorryWarn
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys
import com.headunit.launcher.ServiceAhead

/*
 * What used to be separate cards stacked under the turn card (a lorry's limit ahead, an average speed check, the next services,
 * car parks near the end, advice for the conditions) as lines along the bottom of the turn card itself, in its own colours, so
 * the map has one card on it rather than a column of boxes. The trip card's "Faster route found" is a strip across its top.
 */

private val Sub = Color.White.copy(alpha = 0.75f)
private val RedOnDark = Color(0xFFFF7A6E)

/**
 * The lines, most important first, at most [max] of them (a list of car parks takes one line per car park after its heading),
 * so the card can be kept clear of the trip card below it on a short screen. [advice]: the line of advice for the conditions.
 */
@Composable
internal fun TurnCardExtras(engine: NavEngine, max: Int, advice: Boolean = true, parking: Boolean = true) {
    if (max <= 0) return
    val keys = ArrayList<String>()
    if (engine.lorryWarn != null) keys += "lorry"
    if (engine.avgInfo != null) keys += "avg"
    if (parking && engine.parkingNear.isNotEmpty()) keys += "park"
    if (engine.servicesAhead.isNotEmpty() && engine.avgInfo == null && engine.prefs.b(PreferenceKeys.SERVICESCARD, true)) keys += "services"
    if (advice && engine.advice != null) keys += "advice"
    if (keys.isEmpty()) return
    var left = max
    Column(Modifier.fillMaxWidth().background(GmGreenDeep)) {
        for (k in keys) {
            if (left <= 0) break
            when (k) {
                "lorry" -> engine.lorryWarn?.let { LorryLine(engine, it) }
                "avg" -> engine.avgInfo?.let { AvgLine(engine, it) }
                "park" -> { ParkingLines(engine, left); left -= (if (left <= 1) 1 else 1 + minOf(engine.parkingNear.size, left - 1, 3)) - 1 }
                "services" -> engine.servicesAhead.firstOrNull()?.let { ServicesLine(engine, it) }
                "advice" -> engine.advice?.let { AdviceLine(it) }
            }
            left--
        }
    }
}

@Composable
private fun ExtraRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** A small round sign (a limit, a lorry's height or weight). */
@Composable
private fun MiniSign(text: String) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White).border(3.dp, Color(0xFFD93025), CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF202124), fontSize = if (text.length <= 2) 12.sp else 9.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun LorryLine(engine: NavEngine, w: LorryWarn) {
    // (red, as it was: the lorry can't get past this, so it mustn't look like the rest)
    ExtraRow(Modifier.background(Color(0xFF7A1C17))) {
        MiniSign(w.sign)
        Column(Modifier.weight(1f)) {
            Text("${w.title} · ${Geo.fmtDist(w.dist, engine.imperial)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (w.detail.isNotBlank()) Text(w.detail, color = Color(0xFFF2C2BF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AvgLine(engine: NavEngine, a: AvgInfo) {
    val k = if (engine.imperial) 2.23694 else 3.6
    val unit = if (engine.imperial) "mph" else "km/h"
    val lim = a.limitMs?.let { (Math.round(it * k / 5.0) * 5).toInt() }
    Column(Modifier.fillMaxWidth()) {
        ExtraRow {
            MiniSign(lim?.toString() ?: "?")
            Column(Modifier.weight(1f)) {
                Text(if (a.ahead) L.t("AVERAGE SPEED CHECK AHEAD") else L.t("AVERAGE SPEED CHECK"), color = RedOnDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
                // what you can do from here and still come out under the limit, when it's worth saying; otherwise how far is left
                val safe = a.safeMs; val lm = a.limitMs
                val (msg, col) = when {
                    a.ahead -> L.t("Starts in {0} · {1} long", Geo.fmtDist(a.toStartM, engine.imperial), Geo.fmtDist(a.leftM, engine.imperial)) to Color.White
                    safe != null && lm != null && (safe <= 0.5 || Math.floor(safe * k) < Math.round(lm * k) - 25) -> L.t("Slow down to bring your average back under") to RedOnDark
                    safe != null && lm != null && Math.floor(safe * k) < Math.round(lm * k) -> L.t("Keep under {0} {1} for the rest", Math.floor(safe * k).toInt(), unit) to Warn
                    else -> "${Geo.fmtDist(a.leftM, engine.imperial)} left" to Sub
                }
                Text(msg, color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!a.ahead) Column(horizontalAlignment = Alignment.End) {
                Text("${Math.round(a.avgMs * k)}", color = if (a.over) RedOnDark else Good, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(L.t("avg {0}", unit), color = Sub, fontSize = 10.sp, maxLines = 1)
            }
        }
        // how far through the check you are
        if (!a.ahead && a.lenM > 50.0) {
            val done = (1.0 - a.leftM / a.lenM).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(done).clip(RoundedCornerShape(2.dp)).background(if (a.over) RedOnDark else Good))
            }
        }
    }
}

@Composable
private fun ServicesLine(engine: NavEngine, s: ServiceAhead) {
    ExtraRow {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF4C8DFF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LocalCafe, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text(s.name + (if (s.fuel.isNotBlank()) " · " + s.fuel else ""), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(Geo.fmtDist(s.dist, engine.imperial), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Car parks near the end: a heading and one line each, tap one to drive there instead; just the nearest with one line to spare. */
@Composable
private fun ParkingLines(engine: NavEngine, lines: Int) {
    val list = engine.parkingNear
    val close: @Composable () -> Unit = {
        Box(Modifier.size(30.dp).clip(CircleShape).tapOn(engine, "park-x") { engine.parkingNear = emptyList() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, L.t("Close"), tint = Sub, modifier = Modifier.size(16.dp))
        }
    }
    val one: @Composable (Int, Boolean) -> Unit = { pi, withClose ->
        val n = list[pi]
        Row(Modifier.fillMaxWidth().tapOn(engine, "park-$pi") { engine.parkAt(n) }.padding(start = 14.dp, end = if (withClose) 6.dp else 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2E6FD8)), contentAlignment = Alignment.Center) {
                Text("P", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Text(n.place.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // a walk at about 80 m a minute
            Text(L.t("{0} min walk", maxOf(1, Math.round(n.dist / 80.0).toInt())), color = Sub, fontSize = 12.sp, maxLines = 1)
            if (withClose) close()
        }
    }
    if (lines <= 1) { one(0, true); return }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(L.t("Parking near {0}", engine.dest?.name ?: "your destination"), color = Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        close()
    }
    for (pi in 0 until minOf(list.size, lines - 1, 3)) one(pi, false)
}

@Composable
private fun AdviceLine(text: String) {
    ExtraRow {
        Icon(Icons.Rounded.WaterDrop, null, tint = Color(0xFF7FB2FF), modifier = Modifier.size(18.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Faster route found" across the top of the trip card, with Switch and Keep; gone once you've picked. */
@Composable
internal fun FasterStrip(engine: NavEngine, modifier: Modifier = Modifier, edge: androidx.compose.ui.unit.Dp = 14.dp) {
    val fr = engine.faster ?: return
    val c = LocalVw.current
    Row(modifier.background(Good.copy(alpha = 0.16f)).padding(start = edge, end = edge - 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Route, null, tint = Good, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(L.t("Faster route found"), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(L.t("Saves about {0} min", fr.savedMin), color = Good, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StripButton(engine, "faster-yes", L.t("Switch"), true) { engine.acceptFaster() }
        StripButton(engine, "faster-no", L.t("Keep"), false) { engine.dismissFaster() }
    }
}

@Composable
private fun StripButton(engine: NavEngine, id: String, text: String, filled: Boolean, act: () -> Unit) {
    val c = LocalVw.current
    Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (filled) Good else c.line).tapOn(engine, id, act).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.White else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/**
 * The roadworks or incident and the speed camera ahead as lines along the bottom of the turn card (the portrait route layout; Android
 * Auto has its own), each left out while its own pop-up is showing.
 */
@Composable
internal fun RouteHazardLines(engine: NavEngine) {
    val inc = engine.incidentAhead; val cam = engine.cameraDist
    val alert = engine.alert
    val incShown = inc != null && alert?.kind != "incident"
    val camShown = cam != null && alert?.kind != "camera" && engine.avgInfo == null
    if (!incShown && !camShown) return
    Column(Modifier.fillMaxWidth().background(GmGreenDeep).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (incShown && inc != null) HazardLine("incident", L.t(inc.label), Geo.fmtDist(inc.dist, engine.imperial))
        if (camShown && cam != null) HazardLine("camera", L.t("Speed camera"), Geo.fmtDist(cam.toDouble(), engine.imperial))
    }
}

@Composable
private fun HazardLine(kind: String, label: String, dist: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AlertSign(com.headunit.launcher.AlertInfo(kind, label), 22.dp)
        Text("$label " + L.t("in {0}", dist), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}
