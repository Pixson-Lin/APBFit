package com.pixsonlin.apbfit.domain.fit

import com.pixsonlin.apbfit.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When enabled (debug build + debug run), [HealthConnectWriter] read-backs steps after insert.
 */
@Singleton
class HealthConnectDebugReadback @Inject constructor() {
    private val debugRunActive = AtomicBoolean(false)

    fun setDebugRunActive(active: Boolean) {
        debugRunActive.set(active)
    }

    fun shouldReadBackAfterWrite(): Boolean = BuildConfig.DEBUG && debugRunActive.get()
}
