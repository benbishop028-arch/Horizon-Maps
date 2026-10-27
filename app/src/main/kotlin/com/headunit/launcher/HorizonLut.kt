package com.headunit.launcher

/**
 * The Horizon colours on Mapbox's map: a colour look-up table (a 32x32x32 cube as a 1024x32 PNG) given to the Standard style as its
 * custom theme. It keeps Mapbox's own colours and adds a gentle purple-blue hint, strongest in the dark colours (so the night map
 * gets the deep blue-violet of the original app's map) and almost none in the light ones (day land, labels); anything green (trees, even in
 * shade, grass and parks) is left as it is. Made by tools/make_lut.py.
 */
object HorizonLut {
    const val BASE64: String =
        "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" +
        "uIrBcL5f+fM/Zc/7GUXo/m8AAAAASUVORK5CYII="
}
