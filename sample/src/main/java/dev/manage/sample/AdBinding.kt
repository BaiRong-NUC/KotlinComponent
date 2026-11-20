package dev.manage.sample

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import dev.manage.ads.AdsManager
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal fun bindBanner(container: ViewGroup, name: String, owner: LifecycleOwner): Job =
    owner.lifecycleScope.launch {
        val width = container.awaitContentWidth()
        val widthDp = (width / container.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        AdsManager.withBannerAd(container.context, name, widthDp) { view ->
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) = view.resume()
                override fun onPause(owner: LifecycleOwner) = view.pause()
            }
            try {
                container.addView(view)
                owner.lifecycle.addObserver(observer)
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.pause()
                awaitCancellation()
            } finally {
                owner.lifecycle.removeObserver(observer)
                container.removeView(view)
            }
        }
    }

private suspend fun ViewGroup.awaitContentWidth(): Int {
    fun contentWidth() = width - paddingLeft - paddingRight
    if (contentWidth() > 0) return contentWidth()
    return suspendCancellableCoroutine { continuation ->
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                val measured = contentWidth()
                if (measured <= 0) return
                removeOnLayoutChangeListener(this)
                if (continuation.isActive) continuation.resume(measured)
            }
        }
        addOnLayoutChangeListener(listener)
        continuation.invokeOnCancellation { removeOnLayoutChangeListener(listener) }
    }
}

internal fun bindNative(container: ViewGroup, name: String, owner: LifecycleOwner): Job =
    owner.lifecycleScope.launch {
        AdsManager.withNativeAd(name) { ad ->
            val view = defaultNativeView(container.context, ad, "Ad")
            try {
                view.setNativeAd(ad)
                container.addView(view)
                awaitCancellation()
            } finally {
                container.removeView(view)
                view.destroy()
            }
        }
    }

private fun defaultNativeView(context: Context, ad: NativeAd, adLabel: CharSequence): NativeAdView {
    val view = NativeAdView(context)
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    view.addView(column)
    fun label(text: CharSequence?) = TextView(context).also {
        it.text = text
        it.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        column.addView(it)
    }
    label(adLabel.ifBlank { "Ad" })
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
