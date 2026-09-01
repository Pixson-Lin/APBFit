package com.pixsonlin.apbfit.domain.fit

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.testing.stubs.MutableStub
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HealthConnectWriterTest {
    private val account: GoogleSignInAccount = GoogleSignInAccount.createDefault()

    @Test
    fun ensureDataSources_succeedsWhenWritePermissionsGranted() = runTest {
        val fake = FakeHealthConnectTestHarness.createWithGrantedPermissions()
        val writer = HealthConnectWriter(fake.clientProvider, HealthConnectDebugReadback())

        val result = writer.ensureDataSources(account)

        assertTrue(result.isSuccess)
    }

    @Test
    fun ensureDataSources_failsWhenWritePermissionsMissing() = runTest {
        val fake = FakeHealthConnectTestHarness.createWithoutPermissions()
        val writer = HealthConnectWriter(fake.clientProvider, HealthConnectDebugReadback())

        val result = writer.ensureDataSources(account)

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("permissions", ignoreCase = true) == true,
        )
    }

    @Test
    fun writeSegments_emptyList_succeeds() = runTest {
        val fake = FakeHealthConnectTestHarness.createWithGrantedPermissions()
        val writer = HealthConnectWriter(fake.clientProvider, HealthConnectDebugReadback())

        val result = writer.writeSegments(account, emptyList())

        assertTrue(result.isSuccess)
    }

    @Test
    fun writeSegments_insertsStepsDistanceAndExerciseRecords() = runTest {
        val fake = FakeHealthConnectTestHarness.createWithGrantedPermissions()
        val writer = HealthConnectWriter(fake.clientProvider, HealthConnectDebugReadback())
        val segment = pastSegment(
            index = 0,
            startMillis = 1_700_000_000_000L,
            endMillis = 1_700_000_030_000L,
            steps = 42,
            distanceMeters = 30.24f,
        )

        val result = writer.writeSegments(account, listOf(segment))

        assertTrue(result.isSuccess)
        val steps = fake.client.readRecords(
            ReadRecordsRequest(StepsRecord::class, timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH)),
        ).records
        val distance = fake.client.readRecords(
            ReadRecordsRequest(DistanceRecord::class, timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH)),
        ).records
        val exercise = fake.client.readRecords(
            ReadRecordsRequest(
                ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
            ),
        ).records

        assertEquals(1, steps.size)
        assertEquals(42L, steps.single().count)
        assertEquals(1, distance.size)
        assertEquals(30.24, distance.single().distance.inMeters, 0.01)
        assertEquals(1, exercise.size)
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            exercise.single().exerciseType,
        )
    }

    @Test
    fun writeSegments_failsWhenSegmentEndInFuture() = runTest {
        val fake = FakeHealthConnectTestHarness.createWithGrantedPermissions()
        val writer = HealthConnectWriter(fake.clientProvider, HealthConnectDebugReadback())
        val futureEnd = System.currentTimeMillis() + 60_000L
        val segment = SegmentData(
            segmentIndex = 0,
            startTimeMillis = futureEnd - 30_000L,
            endTimeMillis = futureEnd,
            steps = 10,
            distanceMeters = 8f,
        )

        val result = writer.writeSegments(account, listOf(segment))

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("future", ignoreCase = true) == true,
        )
    }

    @Test
    fun writeSegments_propagatesInsertFailure() = runTest {
        val fake = FakeHealthConnectTestHarness.createWithGrantedPermissions()
        fake.client.overrides.insertRecords =
            MutableStub(defaultError = IllegalStateException("insert failed"))
        val writer = HealthConnectWriter(fake.clientProvider, HealthConnectDebugReadback())
        val segment = pastSegment(
            index = 0,
            startMillis = 1_700_000_100_000L,
            endMillis = 1_700_000_130_000L,
            steps = 12,
            distanceMeters = 9.6f,
        )

        val result = writer.writeSegments(account, listOf(segment))

        assertTrue(result.isFailure)
        assertEquals("insert failed", result.exceptionOrNull()?.message)
    }

    private fun pastSegment(
        index: Int,
        startMillis: Long,
        endMillis: Long,
        steps: Int,
        distanceMeters: Float,
    ): SegmentData {
        val now = System.currentTimeMillis()
        require(endMillis <= now) { "Test segment must end in the past." }
        return SegmentData(
            segmentIndex = index,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            steps = steps,
            distanceMeters = distanceMeters,
        )
    }
}
