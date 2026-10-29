package com.headunit.launcher.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.headunit.launcher.MapBinding
import kotlin.math.cos
import kotlin.math.sin

/** Click with a small press-in scale, so buttons feel responsive. Put it first in the chain to scale the whole button. */
fun Modifier.pressClick(onClick: () -> Unit): Modifier = this.composed {
    val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.93f else 1f, androidx.compose.animation.core.tween(90), label = "press")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = {
        try { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
        onClick()
    })
}

@Composable
fun VCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val c = LocalVw.current
    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(c.panel)
            .border(1.dp, c.line.copy(alpha = 0.07f), RoundedCornerShape(32.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Flat inset tile for rows that sit on a VCard, same as the menu tiles. */
@Composable
fun Modifier.inset(radius: androidx.compose.ui.unit.Dp = 20.dp, tint: Color? = null): Modifier {
    val c = LocalVw.current
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(if (tint != null) tint.copy(alpha = 0.16f) else c.tile)
}

/** Small quiet section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = LocalVw.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = c.sub, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f, fill = trailing != null))
        if (trailing != null) Text(trailing, color = c.sub, fontSize = 12.sp)
    }
}

/** Dark rounded tile holding a coloured glyph, like the menu tiles. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = LocalVw.current
    val wash = if (tint == c.text) c.tile else androidx.compose.ui.graphics.lerp(c.tile, tint, if (c.dark) 0.18f else 0.16f)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(wash),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f)) }
}

/** Page title: big headline with an optional subtitle. */
@Composable
fun PageTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val c = LocalVw.current
    Column(modifier) {
        Text(title, color = c.onBg, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        if (subtitle != null) Text(subtitle, color = c.onBgSub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun RoundBtn(
    icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier,
    active: Boolean = false, size: Dp = 50.dp, tint: Color? = null,
) {
    val c = LocalVw.current
    Box(
        modifier
            .pressClick(onClick)
            .size(size)
            .clip(CircleShape)
            .background(if (active) c.accent else c.surface.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint ?: if (active) Color.White else c.text, modifier = Modifier.size(size * 0.5f))
    }
}

/** Text that rolls vertically to its new value instead of snapping. */
@Composable
fun RollText(
    text: String, color: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    androidx.compose.animation.AnimatedContent(
        targetState = text,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(170))) togetherWith
                (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
        },
        label = "roll",
    ) { t -> Text(t, color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
}

/** Speed colour that warms from white to amber as you near the limit (red once over it); null when there is no reason to tint. */
@Composable
fun speedTint(engine: com.headunit.launcher.NavEngine): Color? {
    if (engine.overLimit) return Bad
    val lim = engine.speedLimit ?: return null
    val sp = engine.fix?.speed ?: return null
    if (engine.limitEstimated || lim <= 0) return null
    val r = (sp / lim).toFloat()
    if (r < 0.85f) return null
    return androidx.compose.ui.graphics.lerp(LocalVw.current.text, Warn, ((r - 0.85f) / 0.12f).coerceIn(0f, 1f))
}

@Composable
fun Pill(
    text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, selectedColor: Color? = null,
) {
    val c = LocalVw.current
    val sel = selectedColor ?: c.accent
    val bg by androidx.compose.animation.animateColorAsState(if (selected) sel else c.tile, androidx.compose.animation.core.tween(160), label = "pill")
    val fg = if (selected) (if (sel.luminance() > 0.6f) Color(0xFF14171C) else Color.White) else c.text
    Box(
        modifier
            .pressClick(onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, letterSpacing = 0.1.sp)
    }
}

@Composable
fun MapHost(map: MapBinding, modifier: Modifier = Modifier, cornerDp: Int = 0) {
    val dens by androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.platform.LocalDensity.current.density)
    val tint = LocalVw.current.mapTint
    Box(modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                if (cornerDp > 0) {
                                        outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, cornerDp * dens)
                        }
                    }
                    clipToOutline = true
                }
                (map.mapView.parent as? ViewGroup)?.removeView(map.mapView)
                addView(map.mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                // the 3D locator sits just above the map (it's moved to the car's spot on screen as the camera moves)
                (map.roadView.parent as? ViewGroup)?.removeView(map.roadView)
                addView(map.roadView, FrameLayout.LayoutParams(1, 1))
                (map.locatorView.parent as? ViewGroup)?.removeView(map.locatorView)
                addView(map.locatorView, FrameLayout.LayoutParams(1, 1))
                post { map.placeLocator() }
            }
        },
        update = { it.invalidateOutline() },
        onRelease = { it.removeAllViews() },
    )
    // colour wash so the map matches the chosen look; touches pass straight through to the map
    if (tint.alpha > 0f) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(cornerDp.dp)).background(tint))
    }
}

/** How far round a roundabout's exit is from the road you came in on (0 ahead, 90 right, -90 left), or NaN when unknown. */
fun roundaboutTurn(inBearing: Double, outBearing: Double): Float {
    if (inBearing < 0.0 || outBearing < 0.0) return Float.NaN
    var d = (outBearing - inBearing) % 360.0
    if (d > 180.0) d -= 360.0
    if (d <= -180.0) d += 360.0
    return d.toFloat()
}

/** Turn arrow drawn from the manoeuvre name (no image assets needed). */
@Composable
fun ManeuverArrow(
    maneuver: String,
    angle: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    roundaboutExit: Int = 0,
    roundaboutClockwise: Boolean = true,
    /** The real exit direction at a roundabout (degrees, 0 ahead, 90 right), when the route data has it. */
    exitAngle: Float = Float.NaN,
) {
    Canvas(modifier) {
        val s = size.minDimension / 48f
        val sw = 5f * s
        val u = maneuver.uppercase()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        if (u.startsWith("ARRIVE")) {
            val flag = Path().apply { moveTo(14 * s, 42 * s); lineTo(14 * s, 6 * s) }
            drawPath(flag, color, style = stroke)
            val f = Path().apply { moveTo(14 * s, 8 * s); lineTo(38 * s, 8 * s); lineTo(32 * s, 16 * s); lineTo(38 * s, 24 * s); lineTo(14 * s, 24 * s); close() }
            drawPath(f, color)
            return@Canvas
        }
        if (u == "MAKE_UTURN" || u == "ROUNDABOUT_BACK" || u == "LEFT_U_TURN" || u == "RIGHT_U_TURN") {
            // Direction-specific U-turns: the road stem and return curve mirror for left/right
            // manoeuvres, while a generic U-turn retains the standard left-return symbol.
            val rightReturn = u == "RIGHT_U_TURN"
            val x = if (rightReturn) 16f else 32f
            val otherX = if (rightReturn) 32f else 16f
            val startAngle = if (rightReturn) 180f else 0f
            val sweep = if (rightReturn) 180f else -180f
            val path = Path().apply {
                moveTo(x * s, 44 * s); lineTo(x * s, 20 * s)
                arcTo(Rect(Offset(16 * s, 8 * s), Size(16 * s, 24 * s)), startAngle, sweep, false)
                lineTo(otherX * s, 30 * s)
            }
            drawPath(path, color, style = stroke)
            val head = Path().apply {
                if (rightReturn) { moveTo(24 * s, 28 * s); lineTo(40 * s, 28 * s); lineTo(32 * s, 40 * s) }
                else { moveTo(8 * s, 28 * s); lineTo(24 * s, 28 * s); lineTo(16 * s, 40 * s) }
                close()
            }
            drawPath(head, color)
            return@Canvas
        }
        if (u.startsWith("ROUNDABOUT")) {
            // The ring with the road you come in on (bottom) and the exit you take, drawn in the direction the real exit
            // points ([exitAngle] from the route's road bearings when known, otherwise the manoeuvre's left/right/ahead).
            // The highlighted arc follows the traffic: clockwise where people keep left (UK), anticlockwise elsewhere.
            // (The exit's number isn't used for the direction: a "2nd exit" can point anywhere, depending on the roundabout.)
            val cx = 24f; val cy = 25f; val rr = 8f
            val ringRect = Rect(Offset((cx - rr) * s, (cy - rr) * s), Size(rr * 2f * s, rr * 2f * s))
            val ringStroke = Stroke(width = 3.5f * s, cap = StrokeCap.Round)
            drawCircle(color.copy(alpha = 0.3f), rr * s, p(cx, cy), style = ringStroke)
            drawLine(color, p(24f, 46f), p(24f, cy + rr), sw, StrokeCap.Round)
            val turn = if (exitAngle.isFinite()) exitAngle else angle
            // screen angle of the exit on the ring (0 = right, 90 = down, where you come in)
            val exitDeg = turn - 90f
            fun wrap(d: Float) = ((d % 360f) + 360f) % 360f
            var sweep = if (roundaboutClockwise) wrap(exitDeg - 90f) else -wrap(90f - exitDeg)
            if (kotlin.math.abs(sweep) < 25f) sweep = if (roundaboutClockwise) 335f else -335f // all the way round
            drawArc(color, 90f, sweep, false, ringRect.topLeft, ringRect.size, style = ringStroke)
            val endDeg = Math.toRadians((90f + sweep).toDouble())
            val dx = kotlin.math.cos(endDeg).toFloat()
            val dy = kotlin.math.sin(endDeg).toFloat()
            val ex = cx + dx * (rr + 10f); val ey = cy + dy * (rr + 10f)
            drawLine(color, p(cx + dx * rr, cy + dy * rr), p(ex, ey), sw, StrokeCap.Round)
            val tip = p(ex + dx * 5f, ey + dy * 5f)
            val px = -dy; val py = dx
            val h1 = p(ex - dx * 3f + px * 4.5f, ey - dy * 3f + py * 4.5f)
            val h2 = p(ex - dx * 3f - px * 4.5f, ey - dy * 3f - py * 4.5f)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(h1.x, h1.y); lineTo(h2.x, h2.y); close() }, color)
            return@Canvas
        }
        val r = Math.toRadians(angle.toDouble())
        val x1 = 24f; val y1 = 28f; val len = 16f
        val ux = sin(r).toFloat(); val uy = -cos(r).toFloat()
        val x2 = x1 + ux * len; val y2 = y1 + uy * len
        if (u.startsWith("ROUNDABOUT")) drawCircle(color, 6f * s, p(24f, 38f), style = Stroke(width = 3f * s))
        val shaft = Path().apply { moveTo(24 * s, 44 * s); lineTo(x1 * s, y1 * s); lineTo(x2 * s, y2 * s) }
        drawPath(shaft, color, style = stroke)
        val px = -uy; val py = ux
        val hl = 11f; val hw = 9f
        val tip = p(x2 + ux * hl * 0.6f, y2 + uy * hl * 0.6f)
        val b1 = p(x2 - ux * hl * 0.4f + px * hw * 0.55f, y2 - uy * hl * 0.4f + py * hw * 0.55f)
        val b2 = p(x2 - ux * hl * 0.4f - px * hw * 0.55f, y2 - uy * hl * 0.4f - py * hw * 0.55f)
        val head = Path().apply { moveTo(tip.x, tip.y); lineTo(b1.x, b1.y); lineTo(b2.x, b2.y); close() }
        drawPath(head, color)
    }
}

/** Returns a function that opens the system speech recogniser and passes back the first result. */
@Composable
fun rememberVoiceInput(prompt: String, onResult: (String) -> Unit): () -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val latest = androidx.compose.runtime.rememberUpdatedState(onResult)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { r ->
        val text = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == android.app.Activity.RESULT_OK && !text.isNullOrBlank()) latest.value(text)
    }
    return {
        try {
            launcher.launch(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, prompt),
            )
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "No voice recognition app found on this device", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/** The shared floating-card look: soft shadow, solid surface and a hairline outline. */
@Composable
fun Modifier.floating(radius: Dp = 24.dp): Modifier {
    val c = LocalVw.current
    val shape = RoundedCornerShape(radius)
    return (if (c.surface.alpha >= 0.99f) this.shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)) else this).clip(shape).background(c.surface).border(1.dp, if (c.surface.alpha < 0.9f) c.line else c.line.copy(alpha = 0.07f), shape)
}

/**
 * A looping 0..1 value that only exists while [active]. Running infinite animations keep the screen redrawing 60 times a second,
 * so they are switched on only when something is actually pulsing.
 */
@Composable
fun rememberPulse(active: Boolean, ms: Int, from: Float = 0f, to: Float = 1f): androidx.compose.runtime.State<Float> {
    if (!active) return androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(from) }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(
        from, to,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "pulseV",
    )
}

/** Themed on/off switch: accent track when on, quiet outline when off. */
@Composable
fun Switch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalVw.current
    androidx.compose.material3.Switch(
        checked = checked, onCheckedChange = onChange,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
            uncheckedThumbColor = c.sub, uncheckedTrackColor = c.tile, uncheckedBorderColor = c.line,
        ),
    )
}
