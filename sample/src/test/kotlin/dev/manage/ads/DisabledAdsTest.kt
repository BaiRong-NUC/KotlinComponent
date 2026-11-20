package dev.manage.ads

import android.app.Activity
import android.app.Application
import dev.manage.ads.data.AdFormat
import dev.manage.ads.data.AdResult
import dev.manage.ads.data.AdTiming
import dev.manage.ads.data.AdsConfig
import dev.manage.ads.data.EnterHome
import dev.manage.ads.data.Placement
import dev.manage.ads.data.PreloadStage
import dev.manage.ads.data.WaitFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisabledAdsTest {
    @Test fun configuredOffBlocksEveryAdEntryEvenWhenConsentIsGranted() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val events = mutableListOf<AdTiming>()
            AdsManager.install(Application(), AdsConfig(
                false, mapOf(
                    "open" to Placement(AdFormat.APP_OPEN, "unused"),
                    "exit" to Placement(AdFormat.INTERSTITIAL, "unused")
                ), listOf(PreloadStage(listOf("open"), 1000)), EnterHome(WaitFor.ALL, listOf("open"), 4000)
            ),
                onTiming = events::add)
            val host = Activity()
            assertEquals(AdResult.DISABLED, AdsManager.runStartup(host, false).gate)
            assertEquals(AdResult.DISABLED, AdsManager.runStartup(host, true).gate)
            assertEquals(AdResult.DISABLED, AdsManager.preload("open"))
            assertEquals(AdResult.DISABLED, AdsManager.withNativeAd("native") { error("Unexpected native ad") })
            assertEquals(AdResult.DISABLED, AdsManager.withBannerAd(host, "banner", 320) { error("Unexpected banner") })
            assertEquals(AdResult.DISABLED, AdsManager.showAppOpen(host, "open"))
            assertEquals(AdResult.DISABLED, AdsManager.showInterstitial(host, "exit"))
            assertTrue(events.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
