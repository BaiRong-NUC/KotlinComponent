package com.pdf.shared.ads

class AppOpenAdCooldown(
    private val durationMillis: Long,
    private val clock: () -> Long
) {
    var lastShownElapsedMillis: Long? = null
        private set

    fun isCoolingDown(): Boolean {
        val shownAt = lastShownElapsedMillis ?: return false
        return clock() - shownAt < durationMillis
    }

    fun recordShown() {
        lastShownElapsedMillis = clock()
    }
}
