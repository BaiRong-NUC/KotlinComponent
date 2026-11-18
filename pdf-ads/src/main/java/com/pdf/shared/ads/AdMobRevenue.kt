package com.pdf.shared.ads

import com.adjust.sdk.AdjustAdRevenue

fun createAdMobRevenue(
    valueMicros: Long,
    currencyCode: String,
    adUnitId: String,
    adFormat: String,
    network: String?
): AdjustAdRevenue = AdjustAdRevenue("admob_sdk").apply {
    setRevenue(valueMicros / 1_000_000.0, currencyCode)
    setAdImpressionsCount(1)
    setAdRevenueUnit(adUnitId)
    if (!network.isNullOrBlank()) setAdRevenueNetwork(network)
    addCallbackParameter("ad_format", adFormat)
}
