package dev.manage.ads

import android.app.Activity
import android.app.Application
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd

internal sealed class LoadedAd {
    class FullScreen(val present: (Activity, FullScreenContentCallback) -> Unit) : LoadedAd()
    class Native(val ad: NativeAd) : LoadedAd()

    fun destroy() {
        if (this is Native) ad.destroy()
    }
}

internal class AdMobBackend(private val app: Application, private val paid: (PaidEvent) -> Unit) {
    fun paidListener(name: String, slot: Placement, response: () -> ResponseInfo?) =
        OnPaidEventListener { value ->
            paid(PaidEvent(name, slot.format, slot.unitId, value.valueMicros, value.currencyCode,
                value.precisionType, response()?.loadedAdapterResponseInfo?.adSourceName))
        }

    fun load(name: String, slot: Placement, complete: (LoadedAd?) -> Unit) {
        val request = AdRequest.Builder().build()
        when (slot.format) {
            AdFormat.APP_OPEN -> AppOpenAd.load(app, slot.unitId, request, object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) = complete(null)
                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.onPaidEventListener = paidListener(name, slot) { ad.responseInfo }
                    complete(LoadedAd.FullScreen { activity, callback ->
                        ad.fullScreenContentCallback = callback
                        ad.show(activity)
                    })
                }
            })
            AdFormat.INTERSTITIAL -> InterstitialAd.load(app, slot.unitId, request, object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) = complete(null)
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.onPaidEventListener = paidListener(name, slot) { ad.responseInfo }
                    complete(LoadedAd.FullScreen { activity, callback ->
                        ad.fullScreenContentCallback = callback
                        ad.show(activity)
                    })
                }
            })
            AdFormat.NATIVE -> AdLoader.Builder(app, slot.unitId)
                .forNativeAd { ad ->
                    ad.setOnPaidEventListener(paidListener(name, slot) { ad.responseInfo })
                    complete(LoadedAd.Native(ad))
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) = complete(null)
                })
                .build().loadAd(request)
            AdFormat.BANNER -> error("Banner requests require a measured view")
        }
    }
}
