package com.pixsonlin.apbfit.domain.fit

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import kotlinx.coroutines.withTimeout

/**
 * Writes simulated run segments to Health Connect (on-device only).
 *
 * [GoogleSignInAccount] is accepted for [FitWriter] compatibility; Health Connect access is
 * device-scoped and does not use the Google Fit account for writes.
 */
@Singleton
class HealthConnectWriter @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
    private val debugReadback: HealthConnectDebugReadback,
) : FitWriter {

    override suspend fun ensureDataSources(account: GoogleSignInAccount): Result<Unit> = runCatching {
        val client = clientProvider.getClient()
        val granted = client.permissionController.getGrantedPermissions()
        val missing = HealthConnectPermissions.requestPermissions - granted
        if (missing.isNotEmpty()) {
            throw SecurityException(
                "Health Connect permissions not granted: ${missing.joinToString()}",
            )
        }
    }

    override suspend fun writeSegments(
        account: GoogleSignInAccount,
        segments: List<SegmentData>,
    ): Result<Unit> {
        if (segments.isEmpty()) return Result.success(Unit)

        val ensureResult = ensureDataSources(account)
        if (ensureResult.isFailure) {
            return Result.failure(ensureResult.exceptionOrNull()!!)
        }

        return runCatching {
            validateSegments(segments)
            val client = clientProvider.getClient()
            val records = segments.flatMap { segment -> segment.toHealthConnectRecords() }
            withTimeout(FitConstants.WRITE_TIMEOUT_MS) {
                client.insertRecords(records)
            }
            if (debugReadback.shouldReadBackAfterWrite()) {
                readBackSteps(client, segments)
            }
        }
    }

    private fun validateSegments(segments: List<SegmentData>) {
        val nowMillis = System.currentTimeMillis()
        segments.forEach { segment ->
            require(segment.startTimeMillis < segment.endTimeMillis) {
                "Segment ${segment.segmentIndex} start must be before end."
            }
            require(segment.endTimeMillis <= nowMillis) {
                "Segment ${segment.segmentIndex} end time must not be in the future."
            }
        }
    }

    private suspend fun readBackSteps(client: HealthConnectClient, segments: List<SegmentData>) {
        segments.forEach { segment ->
            val start = Instant.ofEpochMilli(segment.startTimeMillis)
            val end = Instant.ofEpochMilli(segment.endTimeMillis)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            if (response.records.isEmpty()) {
                Log.e(
                    TAG,
                    "HC readback: no steps for segment ${segment.segmentIndex} " +
                        "[$start, $end]",
                )
            } else {
                response.records.forEach { record ->
                    Log.d(
                        TAG,
                        "HC readback segment=${segment.segmentIndex} " +
                            "count=${record.count} " +
                            "origin=${record.metadata.dataOrigin.packageName}",
                    )
                }
            }
        }
    }

    private fun SegmentData.toHealthConnectRecords(): List<Record> {
        val startInstant = Instant.ofEpochMilli(startTimeMillis)
        val endInstant = Instant.ofEpochMilli(endTimeMillis)
        val startZoneOffset = ZoneId.systemDefault().rules.getOffset(startInstant)
        val endZoneOffset = ZoneId.systemDefault().rules.getOffset(endInstant)
        // Match HC_verify_app: simulated data uses manualEntry() for Pikmin-compatible steps.
        val metadata = Metadata.manualEntry()

        return listOf(
            StepsRecord(
                count = steps.toLong(),
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = startZoneOffset,
                endZoneOffset = endZoneOffset,
                metadata = metadata,
            ),
            DistanceRecord(
                distance = Length.meters(distanceMeters.toDouble()),
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = startZoneOffset,
                endZoneOffset = endZoneOffset,
                metadata = metadata,
            ),
            ExerciseSessionRecord(
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = startZoneOffset,
                endZoneOffset = endZoneOffset,
                metadata = metadata,
            ),
        )
    }

    companion object {
        private const val TAG = "HealthConnectWriter"
    }
}
