package dev.manage.ads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdInventoryTest {
    private class Cache(test: TestScope) {
        val callbacks = mutableListOf<(String?) -> Unit>()
        val destroyed = mutableListOf<String>()
        val inventory = AdInventory<String>(test.backgroundScope, { test.currentTime }, { 1000L },
            { destroyed.add(it) }, { _, callback -> callbacks.add(callback) })
    }

    @Test fun concurrentRequestsShareOneAdAndTakeConsumesItOnce() = runTest {
        val cache = Cache(this)
        val result = cache.inventory.load("open")
        assertSame(result, cache.inventory.load("open"))
        cache.callbacks.single()("ad")
        assertEquals(AdResult.READY, result.await())
        assertSame(result, cache.inventory.load("open"))
        assertEquals("ad", cache.inventory.take("open"))
        assertNull(cache.inventory.take("open"))
        assertTrue(cache.destroyed.isEmpty())
    }

    @Test fun clearedSlotReleasesLateAdWithoutTouchingReplacement() = runTest {
        val cache = Cache(this)
        val old = cache.inventory.load("native")
        cache.inventory.clear()
        val next = cache.inventory.load("native")
        cache.callbacks.first()("old")
        cache.callbacks.last()("new")
        assertEquals(AdResult.DISABLED, old.await())
        assertEquals(AdResult.READY, next.await())
        assertEquals(listOf("old"), cache.destroyed)
        assertEquals("new", cache.inventory.take("native"))
    }

    @Test fun expiredAdIsReleasedAtBoundary() = runTest {
        val cache = Cache(this)
        cache.inventory.load("open")
        cache.callbacks.single()("old")
        advanceTimeBy(1000)
        assertNull(cache.inventory.take("open"))
        runCurrent()
        assertEquals(listOf("old"), cache.destroyed)
        cache.inventory.load("open")
        assertEquals(2, cache.callbacks.size)
    }

    @Test fun failedRequestCanBeRetried() = runTest {
        val cache = Cache(this)
        val first = cache.inventory.load("native")
        cache.callbacks.single()(null)
        assertEquals(AdResult.FAILED, first.await())
        val second = cache.inventory.load("native")
        cache.callbacks.last()("new")
        assertEquals(AdResult.READY, second.await())
    }

    @Test fun fullScreensShareOwnershipAndIgnoreLateDismissals() {
        val session = FullScreenAdSession<Any>()
        val open = Any()
        val interstitial = Any()
        session.start(open)
        assertThrows(IllegalStateException::class.java) { session.start(interstitial) }
        assertTrue(session.finish(open))
        session.start(interstitial)
        assertFalse(session.finish(open))
        assertTrue(session.isShowing)
        assertTrue(session.finish(interstitial))
    }
}
