package com.headunit.launcher

/**
 * The Horizon colours on Mapbox's map: a colour look-up table (a 32x32x32 cube as a 1024x32 PNG) given to the Standard style as its
 * custom theme. It keeps Mapbox's own colours and adds a gentle slate-blue hint with a touch of purple, strongest in the dark colours (so the night
 * map is a dark slate blue; the deeper violet of the original is the Twilight colours, [TwilightLut]) and almost none in the light ones (day land, labels); anything green (trees, even in
 * shade, grass and parks) is left as it is. Made by tools/make_lut.py.
 */
object HorizonLut {
    const val BASE64: String =
        "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"
}
