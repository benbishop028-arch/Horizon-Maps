package com.headunit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** These names are persisted in SharedPreferences and exported backups; changing them breaks existing installs. */
class PreferenceKeysTest {
    @Test fun coreMapAndLocatorKeysRemainBackwardCompatible() {
        assertEquals("apikey", PreferenceKeys.API_KEY)
        assertEquals("mapstyle", PreferenceKeys.MAP_STYLE)
        assertEquals("mapdetail", PreferenceKeys.MAP_DETAIL)
        assertEquals("maplite", PreferenceKeys.MAP_LITE)
        assertEquals("gkey", PreferenceKeys.GOOGLE_API_KEY)
        assertEquals("mbtoken", PreferenceKeys.MAPBOX_TOKEN)
        assertEquals("mbmigrated", PreferenceKeys.MAPBOX_MIGRATED)
        assertEquals("locator", PreferenceKeys.LOCATOR)
        assertEquals("locimg", PreferenceKeys.LOCATOR_IMAGE)
        assertEquals("modelshape", PreferenceKeys.MODEL_SHAPE)
    }

    @Test fun additionalLegacyNavigationAndMapKeysRemainStable() {
        assertEquals("avoid_motorway", PreferenceKeys.AVOID_MOTORWAY)
        assertEquals("avoid_toll", PreferenceKeys.AVOID_TOLL)
        assertEquals("countrydetected", PreferenceKeys.COUNTRYDETECTED)
        assertEquals("lastlat", PreferenceKeys.LASTLAT)
        assertEquals("lastlon", PreferenceKeys.LASTLON)
        assertEquals("mbautoroute", PreferenceKeys.MBAUTOROUTE)
        assertEquals("routechevrons", PreferenceKeys.ROUTECHEVRONS)
        assertEquals("sysdarkseen", PreferenceKeys.SYSDARKSEEN)
        assertEquals("updlast", PreferenceKeys.UPDATE_LAST_CHECK)
    }

    @Test fun commonlySharedSettingsKeepTheirOriginalNames() {
        assertEquals("traffic", PreferenceKeys.TRAFFIC)
        assertEquals("satellite", PreferenceKeys.SATELLITE)
        assertEquals("buildings3d", PreferenceKeys.BUILDINGS_3D)
        assertEquals("soundmode", PreferenceKeys.SOUND_MODE)
        assertEquals("fueltype", PreferenceKeys.FUEL_TYPE)
        assertEquals("mapfps", PreferenceKeys.MAP_FPS)
        assertEquals("uiscale", PreferenceKeys.UI_SCALE)
        assertEquals("thenmode", PreferenceKeys.THEN_MODE)
        assertEquals("places_fav", PreferenceKeys.PLACES_FAVOURITES)
        assertEquals("trips", PreferenceKeys.TRIPS)
    }
}
