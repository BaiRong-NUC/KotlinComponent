package dev.manage.ads

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import dev.manage.ads.runtime.AdInventory
import dev.manage.ads.runtime.AdMobBackend
import dev.manage.ads.runtime.FullScreenAdSession
import dev.manage.ads.runtime.LoadedAd
import dev.manage.ads.runtime.StartupSchedule
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object AdsManager {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Application
    private lateinit var config: AdsConfig
    private lateinit var backend: AdMobBackend
    private var sdk: CompletableDeferred<Boolean>? = null
    private var session: Session? = null
    private val fullscreen = FullScreenAdSession<LoadedAd.FullScreen>()
    private val bindings = mutableMapOf<ViewGroup, AdBinding>()
    private var onTiming: (AdTiming) -> Unit = {}

    val isEnabled: Boolean get() = ::config.isInitialized && config.enabled

    fun install(application: Application, config: AdsConfig = AppAdsConfig.value, onPaid: (PaidEvent) -> Unit = {},
                onTiming: (AdTiming) -> Unit = {}) {
        checkMain()
        if (::app.isInitialized) {
            require(app === application && this.config == config) { "AdsManager already installed with another configuration" }
            return
        }
        app = application
        this.config = config
        this.onTiming = onTiming
        backend = AdMobBackend(app, onPaid)
    }

    private class Session {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val inventory = AdInventory(scope, SystemClock::elapsedRealtime,
            ttl = { name -> TimeUnit.HOURS.toMillis(if (config.placements.getValue(name).format == AdFormat.APP_OPEN) 4 else 1) },
            discard = LoadedAd::destroy,
            request = { name, callback ->
                val start = SystemClock.elapsedRealtime()
                try {
                    backend.load(name, config.placements.getValue(name)) { ad ->
                        callback(ad)
                        timing(if (ad == null) "load_failed" else "loaded", name, start)
                    }
                } catch (error: RuntimeException) {
                    callback(null)
                    timing("load_failed", name, start)
                }
            })
        var startup: StartupSchedule? = null
    }

    fun setRequestPermission(canRequestAds: Boolean) {
        checkMain()
        check(::config.isInitialized) { "Call AdsManager.install first" }
        if (canRequestAds && config.enabled) {
            if (session == null) session = Session()
        } else {
            val previous = session ?: return
            session = null
            previous.inventory.clear()
            previous.scope.cancel()
            bindings.values.toList().forEach(AdBinding::close)
        }
    }

    private fun initialize(): Deferred<Boolean> {
        sdk?.let { return it }
        val result = CompletableDeferred<Boolean>()
        sdk = result
        val start = SystemClock.elapsedRealtime()
        mainScope.launch(Dispatchers.IO) {
            try {
                MobileAds.initialize(app) {
                    mainScope.launch {
                        result.complete(true)
                        timing("sdk_ready", null, start)
                    }
                }
            } catch (error: RuntimeException) {
                mainScope.launch {
                    sdk = null
                    result.complete(false)
                    timing("sdk_failed", null, start)
                }
            }
        }
        return result
    }

    suspend fun runStartup(activity: Activity, canRequestAds: Boolean): StartupResult = withContext(Dispatchers.Main.immediate) {
        setRequestPermission(canRequestAds)
        val current = session ?: return@withContext StartupResult(AdResult.DISABLED, 0, null)
        val schedule = current.startup ?: StartupSchedule(current.scope, config, initialize(),
            SystemClock::elapsedRealtime, current.inventory::load).also { current.startup = it }
        val gate = try {
            schedule.gate.await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            return@withContext StartupResult(AdResult.DISABLED, 0, null)
        }
        if (session !== current) return@withContext gate.copy(gate = AdResult.DISABLED)
        val presentation = schedule.presentOnce(gate) { name -> present(activity, name, AdFormat.APP_OPEN) }
        gate.copy(presentation = presentation)
    }

    suspend fun preload(name: String): AdResult = withContext(Dispatchers.Main.immediate) {
        val current = session ?: return@withContext AdResult.DISABLED
        val slot = config.placements[name] ?: return@withContext AdResult.UNKNOWN_PLACEMENT
        if (slot.format == AdFormat.BANNER) return@withContext AdResult.WRONG_FORMAT
        if (!initialize().await()) return@withContext AdResult.FAILED
        if (current !== session) return@withContext AdResult.DISABLED
        current.inventory.load(name).await()
    }

    suspend fun showAppOpen(activity: Activity, name: String): AdResult =
        withContext(Dispatchers.Main.immediate) { present(activity, name, AdFormat.APP_OPEN).await() }

    suspend fun showInterstitial(activity: Activity, name: String): AdResult =
        withContext(Dispatchers.Main.immediate) { present(activity, name, AdFormat.INTERSTITIAL).await() }

    private fun present(activity: Activity, name: String, format: AdFormat): Deferred<AdResult> {
        val current = session ?: return CompletableDeferred(AdResult.DISABLED)
        val slot = config.placements[name] ?: return CompletableDeferred(AdResult.UNKNOWN_PLACEMENT)
        if (slot.format != format) return CompletableDeferred(AdResult.WRONG_FORMAT)
        val owner = activity as? LifecycleOwner
        if (activity.isFinishing || activity.isDestroyed || owner == null ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return CompletableDeferred(AdResult.INVALID_HOST)
        if (fullscreen.isShowing) return CompletableDeferred(AdResult.BUSY)
        val ad = current.inventory.take(name) as? LoadedAd.FullScreen ?: return CompletableDeferred(AdResult.NOT_READY)
        val result = CompletableDeferred<AdResult>()
        val started = SystemClock.elapsedRealtime()
        var shownAt: Long? = null
        fullscreen.start(ad)
        lateinit var observer: DefaultLifecycleObserver
        fun finish(outcome: AdResult) {
            if (!fullscreen.finish(ad)) return
            owner.lifecycle.removeObserver(observer)
            result.complete(outcome)
            timing("presentation_finished", name, started)
            shownAt?.let { timing("visible", name, it) }
        }
        observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = finish(AdResult.INVALID_HOST)
        }
        owner.lifecycle.addObserver(observer)
        try {
            ad.present(activity, object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() { shownAt = SystemClock.elapsedRealtime() }
                override fun onAdDismissedFullScreenContent() = finish(AdResult.DISMISSED)
                override fun onAdFailedToShowFullScreenContent(error: AdError) = finish(AdResult.FAILED)
            })
        } catch (error: RuntimeException) {
            finish(AdResult.FAILED)
        }
        return result
    }

    fun bindBanner(container: ViewGroup, name: String, owner: LifecycleOwner,
                   onResult: (AdResult) -> Unit = {}): AdBinding =
        bind(container, name, owner, AdFormat.BANNER, onResult, defaultNativeRenderer)

    fun bindNative(container: ViewGroup, name: String, owner: LifecycleOwner,
                   renderer: NativeRenderer = defaultNativeRenderer, onResult: (AdResult) -> Unit = {}): AdBinding =
        bind(container, name, owner, AdFormat.NATIVE, onResult, renderer)

    fun bindNative(container: ViewGroup, name: String, owner: LifecycleOwner,
                   adLabel: CharSequence, onResult: (AdResult) -> Unit = {}): AdBinding =
        bindNative(container, name, owner, { context, ad -> defaultNativeView(context, ad, adLabel) }, onResult)

    private fun bind(container: ViewGroup, name: String, owner: LifecycleOwner, format: AdFormat,
                     onResult: (AdResult) -> Unit, renderer: NativeRenderer): AdBinding {
        checkMain()
        bindings[container]?.close()
        val binding = AdBinding(container, owner) { closed ->
            if (bindings[container] === closed) bindings.remove(container)
        }
        if (binding.closed) {
            onResult(AdResult.INVALID_HOST)
            return binding
        }
        bindings[container] = binding
        val current = session
        val slot = if (::config.isInitialized) config.placements[name] else null
        val error = when {
            current == null -> AdResult.DISABLED
            slot == null -> AdResult.UNKNOWN_PLACEMENT
            slot.format != format -> AdResult.WRONG_FORMAT
            else -> null
        }
        if (error != null) {
            binding.close()
            onResult(error)
            return binding
        }
        binding.launch {
            if (!initialize().await()) {
                onResult(AdResult.FAILED)
            } else if (format == AdFormat.BANNER) {
                binding.loadBanner(slot!!.unitId, { view -> backend.paidListener(name, slot) { view.responseInfo } }, onResult)
            } else {
                val result = current!!.inventory.load(name).await()
                val ad = current.inventory.take(name) as? LoadedAd.Native
                if (ad != null) binding.attachNative(ad.ad, renderer, onResult)
                else onResult(if (result == AdResult.READY) AdResult.NOT_READY else result)
            }
        }
        return binding
    }

    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper()) { "Call on the Android main thread" }
    private fun timing(operation: String, name: String?, start: Long) =
        onTiming(AdTiming(operation, name, SystemClock.elapsedRealtime() - start))
}
