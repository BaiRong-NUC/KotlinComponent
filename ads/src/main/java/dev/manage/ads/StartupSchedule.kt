package dev.manage.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

internal class StartupSchedule(
    scope: CoroutineScope,
    config: AdsConfig,
    sdk: Deferred<Boolean>,
    private val clock: () -> Long,
    load: (String) -> Deferred<AdResult>
) {
    private val outcomes = MutableStateFlow(emptyMap<String, AdResult>())
    private val started = clock()
    private val policy = config.enterHome
    private var presentationConsumed = false
    private var presentation: Deferred<AdResult>? = null

    init {
        scope.launch {
            yield()
            if (!sdk.await()) return@launch
            for (stage in config.preloadStages) {
                val requests = stage.placements.map { name ->
                    val request = load(name)
                    scope.async {
                        val result = request.await()
                        outcomes.update { it + (name to result) }
                    }
                }
                withTimeoutOrNull(stage.timeoutMs) { requests.awaitAll() }
            }
        }
    }

    val gate = scope.async {
        val remaining = policy.maxWaitMs - (clock() - started)
        val result = when {
            policy.waitFor == WaitFor.NONE -> AdResult.READY
            remaining <= 0 -> AdResult.TIMED_OUT
            else -> withTimeoutOrNull(remaining) {
                when {
                    !sdk.await() -> AdResult.FAILED
                    policy.waitFor == WaitFor.SDK -> AdResult.READY
                    else -> decision(outcomes.first { decision(it) != null })!!
                }
            } ?: AdResult.TIMED_OUT
        }
        StartupResult(result, clock() - started, null)
    }

    suspend fun presentOnce(result: StartupResult, show: (String) -> Deferred<AdResult>): AdResult? {
        if (!presentationConsumed) {
            presentationConsumed = true
            val name = policy.showBeforeHome
            if (name != null && result.gate != AdResult.TIMED_OUT && clock() - started <= policy.maxWaitMs) {
                presentation = show(name)
            }
        }
        return presentation?.await()
    }

    private fun decision(outcomes: Map<String, AdResult>): AdResult? {
        val results = policy.placements.map { outcomes[it] }
        return when (policy.waitFor) {
            WaitFor.ANY -> when {
                AdResult.READY in results -> AdResult.READY
                null !in results -> AdResult.FAILED
                else -> null
            }
            WaitFor.ALL -> when {
                results.any { it != null && it != AdResult.READY } -> AdResult.FAILED
                results.all { it == AdResult.READY } -> AdResult.READY
                else -> null
            }
            else -> null
        }
    }
}
