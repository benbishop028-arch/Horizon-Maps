package com.headunit.launcher

/**
 * The Twilight colours on Mapbox's map (the original Horizon look, now a step more purple so it stands apart from Horizon): a colour look-up table (a 32x32x32 cube as a 1024x32 PNG) given to the Standard style as its
 * custom theme. It keeps Mapbox's own colours and adds a gentle purple-blue hint, strongest in the dark colours (so the night map
 * gets the deep blue-violet of the original app's map) and almost none in the light ones (day land, labels); anything green (trees, even in
 * shade, grass and parks) is left as it is. Made by tools/make_lut.py.
 */
object TwilightLut {
    const val BASE64: String =
        "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" +
        "eufP/wO3caqpxcYX3gAAAABJRU5ErkJggg=="
}
