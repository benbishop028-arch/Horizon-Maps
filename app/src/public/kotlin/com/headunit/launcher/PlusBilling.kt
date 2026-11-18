package com.headunit.launcher

import android.app.Activity
import android.content.Context

/** The GitHub edition has no subscription (people use their own keys): nothing to do here. */
object PlusBilling {
    const val PRODUCT_ID = "horizon_plus"
    fun start(ctx: Context, prefs: Prefs) {}
    fun buy(activity: Activity, offer: Plus.Offer) {}
    fun refresh() {}
}
