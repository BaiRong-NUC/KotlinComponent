package dev.manage.ads.runtime

import dev.manage.ads.AdResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AdInventory<Ad : Any>(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val ttl: (String) -> Long,
    private val discard: (Ad) -> Unit,
    private val request: (String, (Ad?) -> Unit) -> Unit
) {
    private class Slot<Ad> {
        val result = CompletableDeferred<AdResult>()
        var ad: Ad? = null
        var loadedAt = 0L
        var expiry: Job? = null
    }

    private val slots = mutableMapOf<String, Slot<Ad>>()

    fun load(name: String): Deferred<AdResult> {
        current(name)?.let { return it.result }
        val slot = Slot<Ad>()
        slots[name] = slot
        request(name) { ad ->
            if (slots[name] !== slot || slot.result.isCompleted) {
                ad?.let(discard)
            } else if (ad == null) {
                slots.remove(name)
                slot.result.complete(AdResult.FAILED)
            } else {
                slot.ad = ad
                slot.loadedAt = clock()
                slot.expiry = scope.launch {
                    delay(ttl(name))
                    if (slots[name] === slot) remove(name)
                }
                slot.result.complete(AdResult.READY)
            }
        }
        return slot.result
    }

    fun take(name: String): Ad? {
        val slot = current(name) ?: return null
        val ad = slot.ad ?: return null
        slots.remove(name)
        slot.expiry?.cancel()
        return ad
    }

    fun clear() = slots.keys.toList().forEach(::remove)

    private fun current(name: String): Slot<Ad>? {
        val slot = slots[name] ?: return null
        if (slot.ad != null && clock() - slot.loadedAt >= ttl(name)) {
            remove(name)
            return null
        }
        return slot
    }

    private fun remove(name: String) {
        val slot = slots.remove(name) ?: return
        slot.expiry?.cancel()
        slot.ad?.let(discard)
        slot.result.complete(AdResult.DISABLED)
    }
}
