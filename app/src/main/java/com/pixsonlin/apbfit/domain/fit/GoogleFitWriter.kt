package com.pixsonlin.apbfit.domain.fit

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.pixsonlin.apbfit.data.prefs.DataSourcePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleFitWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSourcePrefs: DataSourcePrefs,
) : FitWriter {

    override suspend fun ensureDataSources(account: GoogleSignInAccount): Result<Unit> = runCatching {
        val accountId = requireAccountId(account)
        val existing = dataSourcePrefs.getDataSourceIds(accountId)
        if (existing.first != null && existing.second != null && existing.third != null) {
            return@runCatching
        }

        val steps = buildDataSource(DataType.TYPE_STEP_COUNT_DELTA, FitConstants.STREAM_STEP_COUNT)
        val distance = buildDataSource(DataType.TYPE_DISTANCE_DELTA, FitConstants.STREAM_DISTANCE)
        val activity = buildDataSource(DataType.TYPE_ACTIVITY_SEGMENT, FitConstants.STREAM_ACTIVITY)

        dataSourcePrefs.saveDataSourceIds(
            accountId = accountId,
            steps = steps.streamIdentifier,
            distance = distance.streamIdentifier,
            activity = activity.streamIdentifier,
        )
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

        val stepsSource = buildDataSource(DataType.TYPE_STEP_COUNT_DELTA, FitConstants.STREAM_STEP_COUNT)
        val distanceSource = buildDataSource(DataType.TYPE_DISTANCE_DELTA, FitConstants.STREAM_DISTANCE)
        val activitySource = buildDataSource(DataType.TYPE_ACTIVITY_SEGMENT, FitConstants.STREAM_ACTIVITY)

        return runCatching {
            val historyClient = Fitness.getHistoryClient(context, account)
            historyClient.insertData(buildStepDataSet(stepsSource, segments)).awaitWithTimeout()
            historyClient.insertData(buildDistanceDataSet(distanceSource, segments)).awaitWithTimeout()
            historyClient.insertData(buildActivityDataSet(activitySource, segments)).awaitWithTimeout()
        }
    }

    private fun buildDataSource(dataType: DataType, streamName: String): DataSource =
        DataSource.Builder()
            .setAppPackageName(context.packageName)
            .setDataType(dataType)
            .setType(DataSource.TYPE_RAW)
            .setStreamName(streamName)
            .build()

    private fun buildStepDataSet(dataSource: DataSource, segments: List<SegmentData>): DataSet {
        val dataSet = DataSet.create(dataSource)
        segments.forEach { segment ->
            val point = dataSet.createDataPoint()
            setInterval(point, segment)
            point.getValue(Field.FIELD_STEPS).setInt(segment.steps)
            dataSet.add(point)
        }
        return dataSet
    }

    private fun buildDistanceDataSet(dataSource: DataSource, segments: List<SegmentData>): DataSet {
        val dataSet = DataSet.create(dataSource)
        segments.forEach { segment ->
            val point = dataSet.createDataPoint()
            setInterval(point, segment)
            point.getValue(Field.FIELD_DISTANCE).setFloat(segment.distanceMeters)
            dataSet.add(point)
        }
        return dataSet
    }

    private fun buildActivityDataSet(dataSource: DataSource, segments: List<SegmentData>): DataSet {
        val dataSet = DataSet.create(dataSource)
        segments.forEach { segment ->
            val point = dataSet.createDataPoint()
            setInterval(point, segment)
            point.getValue(Field.FIELD_ACTIVITY).setInt(SegmentGenerator.ACTIVITY_TYPE_RUNNING)
            dataSet.add(point)
        }
        return dataSet
    }

    private fun setInterval(point: DataPoint, segment: SegmentData) {
        point.setTimeInterval(
            segment.startTimeMillis,
            segment.endTimeMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun requireAccountId(account: GoogleSignInAccount): String =
        account.id ?: throw IllegalStateException("Signed-in account is missing an ID.")
}
