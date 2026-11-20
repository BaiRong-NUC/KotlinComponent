package dev.manage.ads.data

/** 广告形式。APP_OPEN 开屏、INTERSTITIAL 插页、NATIVE 原生、BANNER 横幅 */
enum class AdFormat { APP_OPEN, INTERSTITIAL, NATIVE, BANNER }

/** 进入首页前的放行条件：NONE 不等直接放行、SDK 只等聚合 SDK 初始化、ANY 任一指定广告位就绪、ALL 全部指定广告位就绪 */
enum class WaitFor { NONE, SDK, ANY, ALL }

/** 一个广告位定义：format 广告形式，unitId AdMob 广告单元 ID */
data class Placement(val format: AdFormat, val unitId: String)

/**
 * 一个预加载阶段：
 * placements 本阶段并行加载的广告位名（引用 AdsConfig.placements 的 key）；
 * timeoutMs 本阶段最长等待毫秒，超时进入下一阶段，已发出的请求继续完成
 */
data class PreloadStage(val placements: List<String>, val timeoutMs: Long)

/**
 * 启动放行（进首页）策略：
 * waitFor 放行条件，见 WaitFor；
 * placements ANY/ALL 模式下参与判断的广告位名单（NONE/SDK 应留空）；
 * maxWaitMs 从首次允许请求起算的最长等待毫秒，覆盖 SDK 初始化和广告加载，条件确定无法满足时提前放行；
 * showBeforeHome 可选开屏位名，放行时若已就绪且未超时则先展示、关闭后返回
 */
data class EnterHome(
    val waitFor: WaitFor,
    val placements: List<String>,
    val maxWaitMs: Long,
    val showBeforeHome: String? = null
)

/**
 * 广告模块总配置：
 * enabled 总开关，false 时不请求任何广告；
 * placements 广告位名 -> Placement 定义；
 * preloadStages 按顺序执行的预加载阶段，同阶段内并行；
 * enterHome 启动进首页的等待/开屏策略
 */
data class AdsConfig(
    val enabled: Boolean,
    val placements: Map<String, Placement>,
    val preloadStages: List<PreloadStage>,
    val enterHome: EnterHome
)

/**
 * 广告操作结果码：
 * READY 已就绪/可展示；
 * DISMISSED 全屏广告被用户关闭；
 * DISABLED 广告未启用或无请求权限；
 * UNKNOWN_PLACEMENT 配置中不存在该广告位名；
 * WRONG_FORMAT 调用的 API 与该位广告形式不匹配；
 * NOT_READY 缓存中没有可取用的已加载广告；
 * BUSY 已有全屏广告正在展示；
 * INVALID_HOST Activity 不可用于展示（正在销毁等）；
 * FAILED 加载或展示失败；
 * TIMED_OUT 超过等待时限
 */
enum class AdResult {
    READY, DISMISSED, DISABLED, UNKNOWN_PLACEMENT, WRONG_FORMAT, NOT_READY,
    BUSY, INVALID_HOST, FAILED, TIMED_OUT
}

/**
 * runStartup 的返回值：
 * gate 放行判定结果；
 * waitMs 从允许请求到判定出结果实际等待的毫秒；
 * presentation showBeforeHome 开屏的展示结果，未触发开屏时为 null
 */
data class StartupResult(val gate: AdResult, val waitMs: Long, val presentation: AdResult?)

/**
 * 广告收益回调事件（GMA onPaidEvent 透传）：
 * placement 产生收益的广告位名；
 * format 广告形式；
 * unitId 广告单元 ID；
 * valueMicros 估算收益，单位微（百万分之一）货币单位；
 * currencyCode 货币代码，如 USD；
 * precision 收益精度类型（GMA precisionType：估算/精确/未知等）；
 * network 实际出广告的广告源名称（adapter adSourceName），可能为 null
 */
data class PaidEvent(
    val placement: String, val format: AdFormat, val unitId: String,
    val valueMicros: Long, val currencyCode: String, val precision: Int, val network: String?
)

/**
 * 耗时埋点事件（install 的 onTiming 回调）：
 * operation 阶段名，如 sdk_ready / sdk_failed / loaded / load_failed / visible / presentation_finished；
 * placement 相关广告位名，SDK 级事件为 null；
 * elapsedMs 该操作耗时毫秒
 */
data class AdTiming(val operation: String, val placement: String?, val elapsedMs: Long)
