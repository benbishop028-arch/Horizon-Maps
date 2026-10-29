package com.headunit.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headunit.launcher.AlertInfo
import com.headunit.launcher.NavEngine

private fun alertColor(kind: String): Color = when (kind) {
    "camera", "limit", "avg", "wrongway" -> Bad
    "plan" -> NavBlue
    "rain" -> NavBlue
    "zone" -> Good
    else -> Warn
}

private fun alertLabel(kind: String): String = when (kind) {
    "camera" -> "SPEED CAMERA"
    "limit" -> "SPEED LIMIT"
    "incident" -> "HAZARD AHEAD"
    "rain" -> "WEATHER"
    "break" -> "TAKE A BREAK"
    "avg" -> "AVERAGE SPEED CHECK"
    "bump" -> "SPEED BUMP"
    "rail" -> "LEVEL CROSSING"
    "ford" -> "FORD"
    "school" -> "SCHOOL"
    "zone" -> "CLEAN AIR ZONE"
    "report" -> "REPORTED AHEAD"
    "wrongway" -> "WRONG WAY"
    "plan" -> "PLANNED TRIP"
    else -> "ALERT"
}

private fun alertIcon(a: AlertInfo): ImageVector {
    val t = a.text.lowercase()
    return when (a.kind) {
        "camera" -> Icons.Rounded.CameraAlt
        "incident" -> when {
            t.contains("work") -> Icons.Rounded.Construction
            t.contains("accident") || t.contains("crash") || t.contains("collision") -> Icons.Rounded.CarCrash
            else -> Icons.Rounded.PriorityHigh
        }
        "rain" -> if (t.startsWith("snow")) Icons.Rounded.AcUnit else Icons.Rounded.WaterDrop
        "break" -> Icons.Rounded.Coffee
        "avg" -> Icons.Rounded.Speed
        "bump" -> Icons.Rounded.Warning
        "school" -> Icons.Rounded.School
        "zone" -> Icons.Rounded.Eco
        "plan" -> Icons.Rounded.Schedule
        "report" -> if (t.startsWith("accident")) Icons.Rounded.CarCrash else if (t.startsWith("police")) Icons.Rounded.LocalPolice else Icons.Rounded.PriorityHigh
        else -> Icons.Rounded.Notifications
    }
}

/** A road-sign style badge for an alert: speed-limit roundel, warning triangle, or a coloured disc. */
@Composable
fun AlertSign(a: AlertInfo, size: Dp) {
    val col = alertColor(a.kind)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when (a.kind) {
            "limit" -> {
                val n = Regex("\\d+").find(a.text)?.value ?: ""
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White).border(size * 0.09f, Bad, CircleShape), contentAlignment = Alignment.Center) {
                    Text(n, color = Color.Black, fontSize = (size.value * (if (n.length >= 3) 0.30f else 0.40f)).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            "stop" -> {
                // a red octagon with a white rim and STOP across it
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    // (the canvas's own size: the badge's [size] parameter has the same name)
                    val cx = this.size.width / 2f; val cy = this.size.height / 2f
                    fun oct(r: Float) = androidx.compose.ui.graphics.Path().apply {
                        for (i in 0 until 8) {
                            val a = Math.toRadians(22.5 + 45.0 * i)
                            val x = cx + r * Math.cos(a).toFloat(); val y = cy + r * Math.sin(a).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    val r = this.size.minDimension / 2f
                    drawPath(oct(r), Color.White)
                    drawPath(oct(r * 0.86f), Color(0xFFC8102E))
                }
                Text("STOP", color = Color.White, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            "wrongway" -> {
                // the no-entry sign
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Bad).border(size * 0.05f, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = size * 0.62f, height = size * 0.16f).background(Color.White))
                }
            }
            // UK warning signs drawn in full: a level crossing (gate) and a ford (water)
            "rail", "ford" -> {
                val rail = a.kind == "rail"
                Canvas(Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    val p = Path().apply { moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.95f, h * 0.90f); lineTo(w * 0.05f, h * 0.90f); close() }
                    drawPath(p, Color.White)
                    drawPath(p, Bad, style = Stroke(w * 0.09f, join = StrokeJoin.Round))
                    val sw = w * 0.055f
                    if (rail) {
                        // a gate: two posts and a bar across, with a rail along the bottom
                        drawLine(Color.Black, Offset(w * 0.30f, h * 0.76f), Offset(w * 0.70f, h * 0.76f), sw)
                        drawLine(Color.Black, Offset(w * 0.34f, h * 0.76f), Offset(w * 0.34f, h * 0.52f), sw)
                        drawLine(Color.Black, Offset(w * 0.66f, h * 0.76f), Offset(w * 0.66f, h * 0.52f), sw)
                        drawLine(Color.Black, Offset(w * 0.30f, h * 0.56f), Offset(w * 0.70f, h * 0.56f), sw)
                    } else {
                        // two rows of waves
                        for (row in 0..1) {
                            val y = h * (0.62f + row * 0.13f)
                            val wave = Path().apply {
                                moveTo(w * 0.26f, y)
                                var x = w * 0.26f
                                while (x < w * 0.72f) { quadraticTo(x + w * 0.06f, y - h * 0.07f, x + w * 0.12f, y); x += w * 0.12f }
                            }
                            drawPath(wave, Color.Black, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                    }
                }
            }
            "camera", "incident", "bump", "school", "report", "avg" -> {
                Canvas(Modifier.fillMaxSize()) {
                    val w = this.size.width
                    val h = this.size.height
                    val p = Path().apply {
                        moveTo(w * 0.5f, h * 0.08f)
                        lineTo(w * 0.95f, h * 0.90f)
                        lineTo(w * 0.05f, h * 0.90f)
                        close()
                    }
                    drawPath(p, Color.White)
                    drawPath(p, Bad, style = Stroke(w * 0.09f, join = StrokeJoin.Round))
                }
                Icon(alertIcon(a), null, tint = Color.Black, modifier = Modifier.size(size * 0.40f).offset(y = size * 0.10f))
            }
            else -> {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(col.copy(alpha = 0.18f)).border(2.dp, col, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(alertIcon(a), null, tint = col, modifier = Modifier.size(size * 0.54f))
                }
            }
        }
    }
}

/** Large alert banner with a road-sign badge and a countdown bar. */
@Composable
fun AlertBanner(a: AlertInfo, modifier: Modifier = Modifier) {
    val col = alertColor(a.kind)
    val prog = remember(a) { Animatable(1f) }
    LaunchedEffect(a) { prog.animateTo(0f, tween(a.ms.toInt().coerceAtLeast(500), easing = LinearEasing)) }
    // compact, so it fits between the turn card and the buttons on the right without covering either
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.widthIn(max = 420.dp).shadow(14.dp, shape).clip(shape)
            .background(Color(0xF2121922)).border(1.5.dp, col.copy(alpha = 0.7f), shape),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AlertSign(a, 54.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(alertLabel(a.kind), color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1)
                Text(a.text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(prog.value).fillMaxHeight().background(col))
        }
    }
}

/**
 * Compact hazard pill for hazards ahead on the map: the road sign and the distance side by side, in the map cards' own day / night
 * colours. Kept small and low so it never runs into the cards above it (the parking card used to slide under the old tall badge).
 */
@Composable
fun HazardBadge(kind: String, label: String, dist: String) {
    val c = navVw(LocalVw.current)
    val shape = RoundedCornerShape(50)
    // (room round the sign, so the triangle's corners never crowd the pill's rounded ends)
    Row(Modifier.panel(6.dp, shape, c.surface).border(1.dp, c.line, shape).padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AlertSign(AlertInfo(kind, label), 24.dp)
        Text(dist, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** UK-style speed limit roundel for the current road; pulses when the limit changes and tints when you're over it. */
@Composable
fun SpeedLimitSign(engine: NavEngine, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val l = engine.speedLimit ?: return
    val k = if (engine.imperial) 2.23694 else 3.6
    val n = (Math.round(l * k / 5.0) * 5).toInt()
    val pulse by rememberPulse(engine.limitPulse, 450, 1f, 1.18f)
    val sc = if (engine.limitPulse) pulse else 1f
    val over = engine.overLimit
    val board = engine.limitSignStyle
    if (board != "round") { LimitBoard(n, board == "ca", over, engine.limitEstimated, size, sc, modifier, onClick); return }
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (engine.limitEstimated) 0.62f else 1f }.size(size).shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // a real-looking roundel: a slightly domed white face, a thick red ring that's lighter at the top and deeper at the bottom,
        // a thin dark rim round the outside and a fine shine across the top
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val ctr = center
            drawCircle(Color(0xFF7A0A14), r, ctr)
            drawCircle(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFF5A5F), Color(0xFFE0142B), Color(0xFFB30C1F)), startY = ctr.y - r, endY = ctr.y + r), r * 0.965f, ctr)
            val face = r * 0.74f
            drawCircle(Color(0x33000000), face + r * 0.03f, ctr)
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(if (over) Color(0xFFFFE3E3) else Color.White, if (over) Color(0xFFFFC2C2) else Color(0xFFF1F1F3), if (over) Color(0xFFF3AFAF) else Color(0xFFE1E2E6)),
                center = Offset(ctr.x - face * 0.25f, ctr.y - face * 0.35f), radius = face * 1.5f,
            ), face, ctr)
            drawArc(Color.White.copy(alpha = 0.35f), 200f, 140f, false, topLeft = Offset(ctr.x - r * 0.88f, ctr.y - r * 0.88f),
                size = androidx.compose.ui.geometry.Size(r * 1.76f, r * 1.76f), style = Stroke(width = r * 0.05f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("$n", color = if (engine.limitEstimated) Color(0xFF4A4A4A) else Color(0xFF111111), fontSize = (size.value * (if (n >= 100) 0.31f else 0.39f)).sp, fontWeight = FontWeight.Black, maxLines = 1,
            letterSpacing = (-0.5).sp, style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x33000000), Offset(0f, 1.5f), 2f)))
    }
}

/**
 * The North American speed limit sign: a white upright board with a black border, "SPEED LIMIT" (US) or "MAXIMUM" (Canada)
 * across the top and the number large below. It turns pink when you're over, like the roundel.
 */
@Composable
private fun LimitBoard(n: Int, canada: Boolean, over: Boolean, estimated: Boolean, size: Dp, sc: Float, modifier: Modifier, onClick: (() -> Unit)?) {
    val w = size * 0.8f
    val shape = RoundedCornerShape(size * 0.1f)
    Box(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (estimated) 0.62f else 1f }.size(w, size).shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape).background(Color(0xFF151515)).padding(size * 0.035f).clip(RoundedCornerShape(size * 0.075f))
            .background(if (over) Color(0xFFFFD6D6) else Color.White).padding(size * 0.03f).border(size * 0.022f, Color(0xFF151515), RoundedCornerShape(size * 0.055f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            val head = (size.value * 0.12f).sp
            if (canada) Text("MAXIMUM", color = Color(0xFF151515), fontSize = (size.value * 0.105f).sp, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = head)
            else {
                Text("SPEED", color = Color(0xFF151515), fontSize = head, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = head)
                Text("LIMIT", color = Color(0xFF151515), fontSize = head, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = head)
            }
            Text("$n", color = if (estimated) Color(0xFF4A4A4A) else Color(0xFF111111), fontSize = (size.value * (if (n >= 100) 0.3f else 0.38f)).sp,
                fontWeight = FontWeight.Black, maxLines = 1, lineHeight = (size.value * 0.4f).sp)
        }
    }
}
