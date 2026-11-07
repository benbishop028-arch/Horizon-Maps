package com.headunit.launcher

/**
 * The Horizon colours on Mapbox's map: a colour look-up table (a 32x32x32 cube as a 1024x32 PNG) given to the Standard style as its
 * custom theme. It keeps Mapbox's own colours and adds a cool moonlight-blue hint, strongest in the dark colours, so the night map is a moonlit blue (Twilight is the purple one)
 * map is a dark slate blue; the deeper violet of the original is the Twilight colours, [TwilightLut]) and almost none in the light ones (day land, labels); anything green (trees, even in
 * shade, grass and parks) is left as it is. Made by tools/make_lut.py.
 */
object HorizonLut {
    const val BASE64: String =
        "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" +
        "rfVvcchIoA3yf6MAAAAASUVORK5CYII="
}
