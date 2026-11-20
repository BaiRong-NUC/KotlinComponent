package dev.manage.ads

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.math.roundToInt

typealias NativeRenderer = (Context, NativeAd) -> NativeAdView

class AdBinding internal constructor(
    private val container: ViewGroup,
    private val owner: LifecycleOwner,
    private val onClose: (AdBinding) -> Unit
) : AutoCloseable, DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var banner: AdView? = null
    private var nativeAd: NativeAd? = null
    private var nativeView: NativeAdView? = null
    var closed = false
        private set

    init {
        (container.getTag(R.id.manage_ads_binding) as? AdBinding)?.close()
        container.setTag(R.id.manage_ads_binding, this)
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) close()
        else owner.lifecycle.addObserver(this)
    }

    internal fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    internal suspend fun loadBanner(unitId: String, paid: (AdView) -> OnPaidEventListener, result: (AdResult) -> Unit) {
        val width = awaitContentWidth()
        val view = AdView(container.context)
        banner = view
        val widthDp = (width / container.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        try {
            view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(container.context, widthDp))
            view.adUnitId = unitId
            view.onPaidEventListener = paid(view)
            view.adListener = object : AdListener() {
                override fun onAdLoaded() { if (!closed) result(AdResult.READY) }
                override fun onAdFailedToLoad(error: LoadAdError) { if (!closed) result(AdResult.FAILED) }
            }
            container.addView(view)
            view.loadAd(AdRequest.Builder().build())
            if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.pause()
        } catch (error: RuntimeException) {
            close()
            result(AdResult.FAILED)
        }
    }

    private suspend fun awaitContentWidth(): Int {
        fun width() = container.width - container.paddingLeft - container.paddingRight
        if (width() > 0) return width()
        return suspendCancellableCoroutine { continuation ->
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    val measured = width()
                    if (measured <= 0) return
                    container.removeOnLayoutChangeListener(this)
                    if (continuation.isActive) continuation.resume(measured)
                }
            }
            container.addOnLayoutChangeListener(listener)
            continuation.invokeOnCancellation { container.removeOnLayoutChangeListener(listener) }
        }
    }

    internal fun attachNative(ad: NativeAd, renderer: NativeRenderer, result: (AdResult) -> Unit) {
        nativeAd = ad
        val view = try {
            renderer(container.context, ad).also {
                nativeView = it
                it.setNativeAd(ad)
                container.addView(it)
            }
        } catch (error: RuntimeException) {
            close()
            result(AdResult.FAILED)
            return
        }
        if (view.parent === container) result(AdResult.READY)
    }

    override fun onResume(owner: LifecycleOwner) { banner?.resume() }
    override fun onPause(owner: LifecycleOwner) { banner?.pause() }
    override fun onDestroy(owner: LifecycleOwner) = close()

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Close on the Android main thread" }
        if (closed) return
        closed = true
        owner.lifecycle.removeObserver(this)
        scope.cancel()
        banner?.let { container.removeView(it); it.destroy() }
        nativeView?.let { container.removeView(it); it.destroy() }
        nativeAd?.destroy()
        banner = null
        nativeView = null
        nativeAd = null
        if (container.getTag(R.id.manage_ads_binding) === this) container.setTag(R.id.manage_ads_binding, null)
        onClose(this)
    }
}

internal fun defaultNativeView(context: Context, ad: NativeAd): NativeAdView {
    val view = NativeAdView(context)
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    view.addView(column)
    fun label(text: CharSequence?) = TextView(context).also {
        it.text = text
        it.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        column.addView(it)
    }
    label(context.getString(R.string.manage_ads_attribution))
    view.headlineView = label(ad.headline)
    ad.icon?.drawable?.let { image ->
        val size = (48 * context.resources.displayMetrics.density).roundToInt()
        view.iconView = ImageView(context).also {
            it.setImageDrawable(image)
            column.addView(it, size, size)
        }
    }
    view.advertiserView = label(ad.advertiser)
    view.mediaView = MediaView(context).also {
        it.mediaContent = ad.mediaContent
        column.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, (180 * context.resources.displayMetrics.density).roundToInt())
    }
    view.bodyView = label(ad.body)
    view.callToActionView = Button(context).also {
        it.text = ad.callToAction
        it.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        column.addView(it)
    }
    return view
}
