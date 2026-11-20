package dev.manage.ads.runtime

internal class FullScreenAdSession<T : Any> {
    private var activeAd: T? = null

    val isShowing: Boolean get() = activeAd != null

    fun owns(ad: T): Boolean = activeAd === ad

    fun start(ad: T) {
        check(activeAd == null) { "A fullscreen ad is already active" }
        activeAd = ad
    }

    fun finish(ad: T): Boolean {
        if (!owns(ad)) return false
        activeAd = null
        return true
    }
}
