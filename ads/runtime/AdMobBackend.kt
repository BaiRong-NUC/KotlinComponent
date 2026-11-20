package dev.manage.ads.runtime

import android.app.Activity
import android.app.Application
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import dev.manage.ads.data.AdFormat
import dev.manage.ads.data.AdResult
import dev.manage.ads.data.PaidEvent
import dev.manage.ads.data.Placement
import dev.manage.ads.net.AdMobRequests

internal sealed class LoadedAd {
    class FullScreen(val present: (Activity, FullScreenContentCallback) -> Unit) : LoadedAd()
    class Native(val ad: NativeAd) : LoadedAd()

    fun destroy() {
        if (this is Native) ad.destroy()
    }
}

internal class AdMobBackend(app: Application, private val paid: (PaidEvent) -> Unit) {
    private val requests = AdMobRequests(app)

    private fun paidListener(name: String, slot: Placement, response: () -> ResponseInfo?) =
        OnPaidEventListener { value ->
            paid(PaidEvent(name, slot.format, slot.unitId, value.valueMicros, value.currencyCode,
                value.precisionType, response()?.loadedAdapterResponseInfo?.adSourceName))
        }

    fun loadBanner(view: AdView, name: String, slot: Placement, widthDp: Int, onResult: (AdResult) -> Unit) {
        view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(view.context, widthDp))
        view.adUnitId = slot.unitId
        view.onPaidEventListener = paidListener(name, slot) { view.responseInfo }
        view.adListener = object : AdListener() {
            override fun onAdLoaded() = onResult(AdResult.READY)
            override fun onAdFailedToLoad(error: LoadAdError) = onResult(AdResult.FAILED)
        }
        requests.loadBanner(view)
    }

    fun load(name: String, slot: Placement, complete: (LoadedAd?) -> Unit) {
        when (slot.format) {
            AdFormat.APP_OPEN -> requests.loadAppOpen(slot.unitId) { loaded ->
                complete(loaded?.let { ad ->
                    ad.onPaidEventListener = paidListener(name, slot) { ad.responseInfo }
                    LoadedAd.FullScreen { activity, callback ->
                        ad.fullScreenContentCallback = callback
                        ad.show(activity)
                    }
                })
            }
            AdFormat.INTERSTITIAL -> requests.loadInterstitial(slot.unitId) { loaded ->
                complete(loaded?.let { ad ->
                    ad.onPaidEventListener = paidListener(name, slot) { ad.responseInfo }
                    LoadedAd.FullScreen { activity, callback ->
                        ad.fullScreenContentCallback = callback
                        ad.show(activity)
                    }
                })
            }
            AdFormat.NATIVE -> requests.loadNative(slot.unitId) { loaded ->
                complete(loaded?.let { ad ->
                    ad.setOnPaidEventListener(paidListener(name, slot) { ad.responseInfo })
                    LoadedAd.Native(ad)
                })
            }
            AdFormat.BANNER -> error("Banner requests require a measured view")
        }
    }
}
