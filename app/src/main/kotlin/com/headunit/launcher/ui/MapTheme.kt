package com.headunit.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.headunit.launcher.NavEngine
import com.headunit.launcher.PreferenceKeys

/** Deep teal of the turn card (and a darker shade for its lane and "then" rows). */
val GmGreen = Color(0xFF127A6C)
val GmGreenDeep = Color(0xFF0C5C52)

/** True while the Modern map colours are selected. */
val NavEngine.immersive: Boolean get() = prefs.s(PreferenceKeys.MAP_THEME, "immersive") == "immersive"

/** Surface of floating pills and chips: near-black at night, white by day. */
@Composable
fun immSurface(): Color = if (LocalVw.current.dark) Color(0xFF1C2030) else Color(0xFFFFFFFF)

/**
 * The palette shared by the map screen and the settings: soft charcoal-blue at night, clean white by day,
 * with a Google-like blue accent.
 */
fun navVw(c: Vw): Vw =
    if (c.dark) c.copy(
        bg = Color(0xFF10131C), panel = Color(0xFF181C29),
        surface = Color(0xFF1C2030), surface2 = Color(0xFF262B3F), tile = Color(0xFF272C42),
        text = Color(0xFFF1F3FA), sub = Color(0xFF9CA4BF), accent = Color(0xFF8AB4F8), line = Color(0x1FFFFFFF),
        onBg = Color(0xFFF1F3FA), onBgSub = Color(0xFF9CA4BF),
    ) else c.copy(
        bg = Color(0xFFEEF1F7), panel = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF2F4FA), tile = Color(0xFFEDF0F7),
        text = Color(0xFF1C2130), sub = Color(0xFF5E667A), accent = Color(0xFF1A73E8), line = Color(0x14000000),
        onBg = Color(0xFF1C2130), onBgSub = Color(0xFF5E667A),
    )

/**
 * Glass panels: the cards turn see-through and pick up the colours of the map under them, with a slightly brighter edge.
 * Real blur is too slow for a head unit, so this is a tinted see-through look; text keeps full contrast.
 */
fun glassVw(c: Vw): Vw =
    if (c.dark) c.copy(surface = Color(0xB8161B2B), tile = Color(0x99262C44), line = Color(0x33FFFFFF))
    else c.copy(surface = Color(0xC7FFFFFF), tile = Color(0xB3EEF1F8), line = Color(0x26000000))

/**
 * A floating card: clipped to its shape and filled. Solid cards get a soft drop shadow; see-through (glass) cards don't,
 * because the shadow would show through the fill as a faint ring and corner lines.
 */
fun Modifier.panel(elev: Dp, shape: Shape, fill: Color): Modifier =
    (if (fill.alpha >= 0.99f) this.shadow(elev, shape) else this).clip(shape).background(fill)
