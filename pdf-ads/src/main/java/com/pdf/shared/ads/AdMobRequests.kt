package com.pdf.shared.ads

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdMobRequests {
    fun loadAppOpen(activity: Activity, unitId: String, callback: AppOpenAd.AppOpenAdLoadCallback) {
        AppOpenAd.load(activity, unitId, AdRequest.Builder().build(), callback)
    }

    fun loadInterstitial(activity: Activity, unitId: String, callback: InterstitialAdLoadCallback) {
        InterstitialAd.load(activity, unitId, AdRequest.Builder().build(), callback)
    }

    fun loadRewarded(activity: Activity, unitId: String, callback: RewardedAdLoadCallback) {
        RewardedAd.load(activity, unitId, AdRequest.Builder().build(), callback)
    }

    fun loadBanner(view: AdView) {
        view.loadAd(AdRequest.Builder().build())
    }

    fun loadNative(
        activity: Activity,
        unitId: String,
        listener: AdListener,
        options: NativeAdOptions? = null,
        onLoaded: (NativeAd) -> Unit
    ) {
        val builder = AdLoader.Builder(activity, unitId)
            .forNativeAd { ad -> onLoaded(ad) }
            .withAdListener(listener)
        if (options != null) builder.withNativeAdOptions(options)
        builder.build().loadAd(AdRequest.Builder().build())
    }
}
