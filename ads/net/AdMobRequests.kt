package dev.manage.ads.net

import android.app.Application
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd

internal class AdMobRequests(private val app: Application) {
    fun loadAppOpen(unitId: String, complete: (AppOpenAd?) -> Unit) {
        AppOpenAd.load(app, unitId, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) = complete(null)
            override fun onAdLoaded(ad: AppOpenAd) = complete(ad)
        })
    }

    fun loadInterstitial(unitId: String, complete: (InterstitialAd?) -> Unit) {
        InterstitialAd.load(app, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) = complete(null)
            override fun onAdLoaded(ad: InterstitialAd) = complete(ad)
        })
    }

    fun loadNative(unitId: String, complete: (NativeAd?) -> Unit) {
        AdLoader.Builder(app, unitId)
            .forNativeAd { ad -> complete(ad) }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) = complete(null)
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    fun loadBanner(view: AdView) {
        view.loadAd(AdRequest.Builder().build())
    }
}
