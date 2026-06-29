package com.pixsonlin.apbfit.domain.fit

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FailingFitWriterTest {
    private val writer = FailingFitWriter("test failure")
    private val account: GoogleSignInAccount = GoogleSignInAccount.createDefault()

    @Test
    fun ensureDataSources_succeeds() = runTest {
        val result = writer.ensureDataSources(account)
        assertTrue(result.isSuccess)
    }

    @Test
    fun writeSegments_returnsFailureWithMessage() = runTest {
        val segment = SegmentData(
            segmentIndex = 0,
            startTimeMillis = 1_000L,
            endTimeMillis = 30_000L,
            steps = 42,
            distanceMeters = 30.24f,
        )
        val result = writer.writeSegments(account, listOf(segment))
        assertTrue(result.isFailure)
        assertEquals("test failure", result.exceptionOrNull()?.message)
    }
}
