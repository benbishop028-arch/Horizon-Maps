package com.headunit.launcher

/**
 * The Horizon colours on Mapbox's map: a colour look-up table (a 32x32x32 cube as a 1024x32 PNG) given to the Standard style as its
 * custom theme. It keeps Mapbox's own colours and adds a gentle slate-blue hint with a little lilac, strongest in the dark colours (so the night
 * map is a dark slate blue; the deeper violet of the original is the Twilight colours, [TwilightLut]) and almost none in the light ones (day land, labels); anything green (trees, even in
 * shade, grass and parks) is left as it is. Made by tools/make_lut.py.
 */
object HorizonLut {
    const val BASE64: String =
        "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"
}
