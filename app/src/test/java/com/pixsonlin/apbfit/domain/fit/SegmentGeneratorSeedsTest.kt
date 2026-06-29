package com.pixsonlin.apbfit.domain.fit

import com.pixsonlin.apbfit.data.model.IntensityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SegmentGeneratorSeedsTest {
    @Test
    fun seedForAccount_isDeterministic() {
        val first = SegmentGenerator(seedForAccount("session-a", "account-1"))
        val second = SegmentGenerator(seedForAccount("session-a", "account-1"))
        val segmentA = first.generate(0, 1_000L, IntensityLevel.JOG)
        val segmentB = second.generate(0, 1_000L, IntensityLevel.JOG)
        assertEquals(segmentA, segmentB)
    }

    @Test
    fun seedForAccount_differsAcrossAccountsInSameSession() {
        val sessionId = "session-shared"
        val accountA = SegmentGenerator(seedForAccount(sessionId, "account-a"))
        val accountB = SegmentGenerator(seedForAccount(sessionId, "account-b"))
        val segmentA = accountA.generate(0, 1_000L, IntensityLevel.BRISK_WALK)
        val segmentB = accountB.generate(0, 1_000L, IntensityLevel.BRISK_WALK)
        assertNotEquals(segmentA, segmentB)
    }

    @Test
    fun intensityLevel_hasSevenPresetsInSpmOrder() {
        val levels = IntensityLevel.entries
        assertEquals(7, levels.size)
        val spmValues = levels.map { it.cadenceSpm }
        assertEquals(spmValues, spmValues.sorted())
        assertTrue(levels.all { it.strideMeters > 0 })
    }
}
