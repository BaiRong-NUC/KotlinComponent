package dev.manage.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import dev.manage.ads.data.AdFormat
import dev.manage.ads.data.AdResult
import dev.manage.ads.data.AdTiming
import dev.manage.ads.data.AdsConfig
import dev.manage.ads.data.PaidEvent
import dev.manage.ads.data.Placement
import dev.manage.ads.data.StartupResult
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
    private var onTiming: (AdTiming) -> Unit = {}

    val isEnabled: Boolean get() = ::config.isInitialized && config.enabled

    // 单例初始化
    /**
     * @param application Application 实例，供 SDK 初始化和广告加载使用
     * @param config 广告配置（开关、各 placement 等），默认取 AppAdsConfig.value
     * @param onPaid 广告产生收益（paid event）时的回调
     * @param onTiming 耗时埋点回调，如 sdk_ready、loaded、load_failed 等阶段的计时上报
     */
    fun install(
        application: Application, config: AdsConfig = AppAdsConfig.value, onPaid: (PaidEvent) -> Unit = {},
        onTiming: (AdTiming) -> Unit = {}
    ) {
        checkMain() // 强制要求在主线程调用

        //防重复初始化
        if (::app.isInitialized) {
            require(app === application && this.config == config) { "AdsManager already installed with another configuration" }
            return
        }
        app = application
        this.config = config
        this.onTiming = onTiming
        /*
         * AdMobBackend 封装了按广告形式加载广告的逻辑（开屏/插页/原生/横幅），内部用 AdMobRequests 发请求。
         * 
         * onPaid 回调被包进 backend：每个广告加载成功后，backend 会给广告挂 onPaidEventListener，GMA SDK 上报收益时组装成 PaidEvent 回调给业务方。
         */
        backend = AdMobBackend(app, onPaid)
    }

    // 一次广告可用会话类
    private class Session {
        /**
         * 定义协程作用域:
         *
         * SupervisorJob：子协程失败不连累兄弟（一个广告加载失败不影响其他）。
         *
         * Dispatchers.Main.immediate：在主线程跑，且已在主线程时立即执行不排队（广告 SDK 多要求主线程）
         *
         * Session 销毁时 scope.cancel()，里面跑的预加载、过期任务全停。
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)


        // 广告库存管理器,负责广告的加载、缓存、过期处理等

        /**
         * scope: 协程作用域，用于管理广告加载、缓存等协程任务
         * SystemClock::elapsedRealtime: 时间戳获取函数，用于计算广告过期时间
         * ttl: 广告过期时间，根据广告形式（开屏/插页/原生/横幅）设置不同的过期时间
         * discard: 广告销毁回调，用于释放广告资源
         * request: 广告请求回调，用于加载广告
         */
        val inventory = AdInventory(
            scope, SystemClock::elapsedRealtime,
            ttl = { name -> TimeUnit.HOURS.toMillis(if (config.placements.getValue(name).format == AdFormat.APP_OPEN) 4 else 1) },
            discard = LoadedAd::destroy,
            request = { name, callback ->
                val start = SystemClock.elapsedRealtime() // 记录开始时间
                try {
                    // AdMobBackend 按该位配置发请求
                    backend.load(name, config.placements.getValue(name)) { ad ->
                        // callback 是 AdInventory 在调用 request(name, callback) 时传进来的一个函数参数，作用是"广告加载完了，把结果交回仓库去存/标记结果"
                        callback(ad)
                        // 上报耗时埋点
                        timing(if (ad == null) "load_failed" else "loaded", name, start)
                    }
                } catch (error: RuntimeException) {
                    // 加载抛异常时 callback(null) 并记 load_failed
                    callback(null)
                    timing("load_failed", name, start)
                }
            })

        // 记录本次广告会话的启动任务单 ——预加载发了哪些、放行判定结果、开屏有没有展示过
        var startup: StartupSchedule? = null
    }

    // 广告会话总开关,权限给了且配置启用时就创建 Session（允许预加载/展示）
    fun setRequestPermission(canRequestAds: Boolean) {
        checkMain()
        check(::config.isInitialized) { "Call AdsManager.install first" }
        // 业务传入的"现在有请求广告的权限"，比如隐私同意已给、GDPR/ATT 通过）
        // 并且 config.enabled（配置总开关没关）
        if (canRequestAds && config.enabled) {
            if (session == null) session = Session()
        } else {
            // 没有广告，清理广告资源
            val previous = session ?: return
            session = null
            previous.inventory.clear() // 把缓存里已加载的广告全部取出销毁
            previous.scope.cancel() // 取消Session的协程资源
        }
    }

    //SDK 初始化——第一次调真正初始化、返回可等待的 Deferred；
    //成功后被记住复用，失败则清掉允许重试；全程异步跑在 IO 线程
    private fun initialize(): Deferred<Boolean> {
        sdk?.let { return it }
        //创建一个手动完成的 Deferred（CompletableDeferred），把引用存到 sdk
        val result = CompletableDeferred<Boolean>()
        sdk = result
        val start = SystemClock.elapsedRealtime() //记初始化开始时刻

        //在 mainScope（AdsManager 的协程作用域）上启一个协程，切到 IO 线程
        mainScope.launch(Dispatchers.IO) {
            //SDK 就绪回调
            try {
                MobileAds.initialize(app) {
                    // 初始化完成后切到主线程发送点位
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

    suspend fun runStartup(activity: Activity, canRequestAds: Boolean): StartupResult =
        withContext(Dispatchers.Main.immediate) {
            setRequestPermission(canRequestAds)
            val current = session ?: return@withContext StartupResult(AdResult.DISABLED, 0, null)
            val schedule = current.startup ?: StartupSchedule(
                current.scope, config, initialize(),
                SystemClock::elapsedRealtime, current.inventory::load
            ).also { current.startup = it }
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
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return CompletableDeferred(AdResult.INVALID_HOST)
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
                override fun onAdShowedFullScreenContent() {
                    shownAt = SystemClock.elapsedRealtime()
                }

                override fun onAdDismissedFullScreenContent() = finish(AdResult.DISMISSED)
                override fun onAdFailedToShowFullScreenContent(error: AdError) = finish(AdResult.FAILED)
            })
        } catch (error: RuntimeException) {
            finish(AdResult.FAILED)
        }
        return result
    }

    suspend fun withNativeAd(name: String, use: suspend (NativeAd) -> Unit): AdResult =
        withPlacement(name, AdFormat.NATIVE) { current, _ ->
            val result = current.inventory.load(name).await()
            if (result != AdResult.READY) return@withPlacement result
            val ad = current.inventory.take(name) as? LoadedAd.Native ?: return@withPlacement AdResult.NOT_READY
            try {
                use(ad.ad)
                AdResult.READY
            } finally {
                ad.destroy()
            }
        }

    suspend fun withBannerAd(
        context: Context, name: String, widthDp: Int,
        onLoadResult: (AdResult) -> Unit = {}, use: suspend (AdView) -> Unit
    ): AdResult =
        withPlacement(name, AdFormat.BANNER) { _, slot ->
            require(widthDp > 0) { "Banner width must be positive" }
            val view = AdView(context)
            var active = true
            try {
                try {
                    backend.loadBanner(view, name, slot, widthDp) { if (active) onLoadResult(it) }
                } catch (error: RuntimeException) {
                    return@withPlacement AdResult.FAILED
                }
                use(view)
                AdResult.READY
            } finally {
                active = false
                view.destroy()
            }
        }

    private suspend fun withPlacement(
        name: String, format: AdFormat,
        use: suspend (Session, Placement) -> AdResult
    ): AdResult =
        withContext(Dispatchers.Main.immediate) {
            val current = session ?: return@withContext AdResult.DISABLED
            val slot = config.placements[name] ?: return@withContext AdResult.UNKNOWN_PLACEMENT
            if (slot.format != format) return@withContext AdResult.WRONG_FORMAT
            val usage = current.scope.async {
                if (initialize().await()) use(current, slot) else AdResult.FAILED
            }
            try {
                usage.await()
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (current === session) throw error
                AdResult.DISABLED
            } finally {
                usage.cancel()
            }
        }

    // 判断是否是主线程
    private fun checkMain() = check(Looper.myLooper() == Looper.getMainLooper()) { "Call on the Android main thread" }

    // 上传点位
    private fun timing(operation: String, name: String?, start: Long) =
        onTiming(AdTiming(operation, name, SystemClock.elapsedRealtime() - start))
}
