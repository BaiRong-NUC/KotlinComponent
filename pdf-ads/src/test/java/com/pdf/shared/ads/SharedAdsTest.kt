package com.pdf.shared.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedAdsTest {
    @Test fun lateCallbackCannotFinishAnotherPresentation() {
        val session = FullScreenAdSession<List<Int>>()
        val first = mutableListOf(1)
        val second = mutableListOf(1)
        session.start(first)
        assertThrows(IllegalStateException::class.java) { session.start(second) }
        assertTrue(session.finish(first))
        session.start(second)
        assertFalse(session.finish(first))
        assertTrue(session.owns(second))
        assertTrue(session.finish(second))
        assertFalse(session.isShowing)
    }

    @Test fun cooldownExpiresExactlyAtConfiguredBoundary() {
        var now = 100L
        val cooldown = AppOpenAdCooldown(20) { now }
        assertFalse(cooldown.isCoolingDown())
        cooldown.recordShown()
        now += 19
        assertTrue(cooldown.isCoolingDown())
        now++
        assertFalse(cooldown.isCoolingDown())
    }

    @Test fun zeroCooldownAllowsImmediateReuse() {
        val cooldown = AppOpenAdCooldown(0) { 100L }
        cooldown.recordShown()
        assertFalse(cooldown.isCoolingDown())
    }

    @Test fun paidImpressionRetainsPrecisionAndAttribution() {
        val revenue = createAdMobRevenue(1, "EUR", "unit", "rewarded", "Meta")
        assertEquals("admob_sdk", revenue.source)
        assertEquals(0.000001, revenue.revenue, 0.0)
        assertEquals("EUR", revenue.currency)
        assertEquals(1, revenue.adImpressionsCount)
        assertEquals("unit", revenue.adRevenueUnit)
        assertEquals("rewarded", revenue.callbackParameters["ad_format"])
        assertEquals("Meta", revenue.adRevenueNetwork)
    }

    @Test fun missingNetworkIsOmittedFromRevenue() {
        val revenue = createAdMobRevenue(0, "USD", "unit", "native", " ")
        assertNull(revenue.adRevenueNetwork)
        assertEquals(0.0, revenue.revenue, 0.0)
    }
}
