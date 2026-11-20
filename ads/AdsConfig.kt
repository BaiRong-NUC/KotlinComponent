package dev.manage.ads

enum class AdFormat { APP_OPEN, INTERSTITIAL, NATIVE, BANNER }
enum class WaitFor { NONE, SDK, ANY, ALL }

data class Placement(val format: AdFormat, val unitId: String)
data class PreloadStage(val placements: List<String>, val timeoutMs: Long)
data class EnterHome(
    val waitFor: WaitFor,
    val placements: List<String>,
    val maxWaitMs: Long,
    val showBeforeHome: String? = null
)
data class AdsConfig(
    val enabled: Boolean,
    val placements: Map<String, Placement>,
    val preloadStages: List<PreloadStage>,
    val enterHome: EnterHome
)
 
enum class AdResult {
    READY, DISMISSED, DISABLED, UNKNOWN_PLACEMENT, WRONG_FORMAT, NOT_READY,
    BUSY, INVALID_HOST, FAILED, TIMED_OUT
}

data class StartupResult(val gate: AdResult, val waitMs: Long, val presentation: AdResult?)
data class PaidEvent(
    val placement: String, val format: AdFormat, val unitId: String,
    val valueMicros: Long, val currencyCode: String, val precision: Int, val network: String?
)
data class AdTiming(val operation: String, val placement: String?, val elapsedMs: Long)
