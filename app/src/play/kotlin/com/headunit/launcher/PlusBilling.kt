package com.headunit.launcher

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play's billing for Horizon Maps Plus: one subscription ("horizon_plus") with two base plans, "monthly" and "yearly",
 * each with a free-trial offer for people who haven't had one. Asks Play what this person has whenever the app starts (and after
 * a purchase), and tells [Plus]. Off until the key server is set up ([Plus.offered]).
 */
object PlusBilling {
    const val PRODUCT = "horizon_plus"
    const val PRODUCT_ID = PRODUCT
    private var client: BillingClient? = null
    private var prefs: Prefs? = null
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun start(ctx: Context, p: Prefs) {
        if (!Plus.offered || client != null) return
        prefs = p
        val c = BillingClient.newBuilder(ctx.applicationContext)
            .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty()) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { loadOffers(); refresh() }
                else main.post { Plus.problem = "Google Play billing isn't available (${result.debugMessage})" }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    /** Asks Play which subscriptions this person has. */
    fun refresh() {
        val c = client ?: return
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases, all = true)
        }
    }

    private fun loadOffers() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build())).build()
        c.queryProductDetailsAsync(params) { result, details ->
            val pd = details.productDetailsList.firstOrNull()
            if (result.responseCode != BillingClient.BillingResponseCode.OK || pd == null) {
                main.post { Plus.problem = "Plus isn't on sale yet" }
                return@queryProductDetailsAsync
            }
            val out = ArrayList<Plus.Offer>()
            for (plan in listOf("monthly", "yearly")) {
                val all = pd.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == plan }
                // the free-trial offer when Play gives it to this person (it only does when they're allowed one), else the plan itself
                val pick = all.firstOrNull { o -> o.offerId != null && o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: all.firstOrNull { it.offerId == null } ?: continue
                val phases = pick.pricingPhases.pricingPhaseList
                val paid = phases.lastOrNull { it.priceAmountMicros > 0L } ?: continue
                val trial = phases.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod?.let { days(it) } ?: 0
                out += Plus.Offer(plan, paid.formattedPrice, trial, pd to pick.offerToken)
            }
            main.post { Plus.offers = out; Plus.problem = if (out.isEmpty()) "Plus isn't on sale yet" else "" }
        }
    }

    /** "P7D", "P1W", "P1M" to days. */
    private fun days(p: String): Int {
        val n = p.filter { it.isDigit() }.toIntOrNull() ?: return 0
        return when { p.endsWith("D") -> n; p.endsWith("W") -> n * 7; p.endsWith("M") -> n * 30; else -> 0 }
    }

    /** Opens Play's own payment sheet for [offer]. */
    fun buy(activity: Activity, offer: Plus.Offer) {
        val c = client ?: return
        val h = offer.handle as? Pair<*, *> ?: return
        val pd = h.first as? ProductDetails ?: return
        val token = h.second as? String ?: return
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).setOfferToken(token).build())).build()
        c.launchBillingFlow(activity, params)
    }

    /** What Play says this person has: Plus is on while a paid (or trial) subscription is. New ones are acknowledged (Play refunds
     *  any that aren't within three days). [all]: the full list (so none at all means Plus is off). */
    private fun handle(purchases: List<Purchase>, all: Boolean = false) {
        val p = prefs ?: return
        val mine = purchases.filter { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        for (pu in mine) if (!pu.isAcknowledged) {
            client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(pu.purchaseToken).build()) { }
        }
        val best = mine.firstOrNull()
        if (best != null) main.post { Plus.set(p, true, best.purchaseToken, PRODUCT) }
        else if (all) main.post { Plus.set(p, false, "", "") }
    }
}
