package dev.manage.ads

import dev.manage.ads.data.AdResult
import dev.manage.ads.data.AdsConfig
import dev.manage.ads.data.EnterHome
import dev.manage.ads.data.PreloadStage
import dev.manage.ads.data.WaitFor
import dev.manage.ads.runtime.StartupSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupScheduleTest {
    private class Startup(val test: TestScope, mode: WaitFor, names: List<String>) {
        val sdk = CompletableDeferred<Boolean>()
        val orders = mutableListOf<String>()
        val requests = listOf("open", "native", "exit").associateWith { CompletableDeferred<AdResult>() }
        val plan = StartupSchedule(test.backgroundScope, AdsConfig(
            true, emptyMap(), listOf(
                PreloadStage(listOf("open", "native"), 2500),
                PreloadStage(listOf("exit"), 1500)
            ), EnterHome(mode, names, 4000, "open")
        ), sdk, { test.currentTime }) { name ->
            orders.add(name)
            requests.getValue(name)
        }
        fun complete(name: String, result: AdResult) = requests.getValue(name).complete(result)
    }

    @Test fun noneReleasesBeforeAnySdkOrLoadCallback() = runTest {
        val startup = Startup(this, WaitFor.NONE, emptyList())
        runCurrent()
        assertEquals(AdResult.READY, startup.plan.gate.await().gate)
        assertTrue(startup.orders.isEmpty())
        startup.sdk.complete(true)
        runCurrent()
        assertEquals(listOf("open", "native"), startup.orders)
    }

    @Test fun sdkGateNeedsOnlySdkCallback() = runTest {
        val startup = Startup(this, WaitFor.SDK, emptyList())
        runCurrent()
        assertFalse(startup.plan.gate.isCompleted)
        startup.sdk.complete(true)
        runCurrent()
        assertEquals(AdResult.READY, startup.plan.gate.await().gate)
    }

    @Test fun allWaitsForNamedAdsOnlyAndStagesContinueAfterRelease() = runTest {
        val startup = Startup(this, WaitFor.ALL, listOf("open"))
        startup.sdk.complete(true)
        runCurrent()
        advanceTimeBy(800)
        startup.complete("open", AdResult.READY)
        runCurrent()
        assertEquals(800L, startup.plan.gate.await().waitMs)
        assertEquals(listOf("open", "native"), startup.orders)
        startup.complete("native", AdResult.FAILED)
        runCurrent()
        assertEquals(listOf("open", "native", "exit"), startup.orders)
    }

    @Test fun anyCanSucceedAfterFirstFailure() = runTest {
        val startup = Startup(this, WaitFor.ANY, listOf("open", "native"))
        startup.sdk.complete(true)
        startup.complete("open", AdResult.FAILED)
        runCurrent()
        assertFalse(startup.plan.gate.isCompleted)
        startup.complete("native", AdResult.READY)
        runCurrent()
        assertEquals(AdResult.READY, startup.plan.gate.await().gate)
    }

    @Test fun anyEndsOnceLastCandidateFails() = runTest {
        val startup = Startup(this, WaitFor.ANY, listOf("open", "native"))
        startup.sdk.complete(true)
        startup.complete("open", AdResult.FAILED)
        startup.complete("native", AdResult.FAILED)
        runCurrent()
        assertEquals(AdResult.FAILED, startup.plan.gate.await().gate)
    }

    @Test fun allFailsEarlyEvenIfOtherLoadIsPending() = runTest {
        val startup = Startup(this, WaitFor.ALL, listOf("open", "native"))
        startup.sdk.complete(true)
        startup.complete("native", AdResult.FAILED)
        runCurrent()
        assertEquals(AdResult.FAILED, startup.plan.gate.await().gate)
    }

    @Test fun stageDeadlineStartsNextStageWhileOriginalRequestContinues() = runTest {
        val startup = Startup(this, WaitFor.ALL, listOf("open", "exit"))
        startup.sdk.complete(true)
        runCurrent()
        advanceTimeBy(2500)
        runCurrent()
        assertEquals(listOf("open", "native", "exit"), startup.orders)
        startup.complete("exit", AdResult.READY)
        runCurrent()
        assertFalse(startup.plan.gate.isCompleted)
        startup.complete("open", AdResult.READY)
        runCurrent()
        assertEquals(AdResult.READY, startup.plan.gate.await().gate)
    }

    @Test fun slowSdkSharesDeadlineWithLoadingAndLateAdCannotShow() = runTest {
        val startup = Startup(this, WaitFor.ALL, listOf("open"))
        runCurrent()
        advanceTimeBy(3600)
        startup.sdk.complete(true)
        advanceTimeBy(400)
        runCurrent()
        val result = startup.plan.gate.await()
        assertEquals(AdResult.TIMED_OUT, result.gate)
        assertEquals(4000L, result.waitMs)
        startup.complete("open", AdResult.READY)
        runCurrent()
        assertNull(startup.plan.presentOnce(result) { error("late presentation") })
    }

    @Test fun failedInitializationReleasesGateWithoutRequests() = runTest {
        val startup = Startup(this, WaitFor.ALL, listOf("open"))
        startup.sdk.complete(false)
        runCurrent()
        assertEquals(AdResult.FAILED, startup.plan.gate.await().gate)
        assertTrue(startup.orders.isEmpty())
    }

    @Test fun replacementOwnerAwaitsExistingDisplayAndOldOwnerCannotNavigate() = runTest {
        val startup = Startup(this, WaitFor.ALL, listOf("open"))
        startup.sdk.complete(true)
        startup.complete("open", AdResult.READY)
        runCurrent()
        val gate = startup.plan.gate.await()
        val dismissal = CompletableDeferred<AdResult>()
        var navigations = 0
        val original = async {
            startup.plan.presentOnce(gate) { dismissal }
            navigations++
        }
        runCurrent()
        original.cancel()
        val replacement = async {
            startup.plan.presentOnce(gate) { error("duplicate presentation") }
            navigations++
        }
        advanceTimeBy(6000)
        assertFalse(replacement.isCompleted)
        dismissal.complete(AdResult.DISMISSED)
        runCurrent()
        assertEquals(1, navigations)
    }
}
