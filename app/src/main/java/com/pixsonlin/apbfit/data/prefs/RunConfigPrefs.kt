package com.pixsonlin.apbfit.data.prefs

import android.content.Context
import com.pixsonlin.apbfit.data.model.IntensityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunConfigPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SavedRunConfig? {
        if (!prefs.contains(KEY_INTENSITY)) return null
        val intensityName = prefs.getString(KEY_INTENSITY, null) ?: return null
        val intensity = runCatching { IntensityLevel.valueOf(intensityName) }.getOrNull() ?: return null
        return SavedRunConfig(
            intensityLevel = intensity,
            durationMinutes = prefs.getInt(KEY_DURATION, DEFAULT_DURATION_MINUTES),
            batchSize = prefs.getInt(KEY_BATCH_SIZE, DEFAULT_BATCH_SIZE),
        )
    }

    fun save(config: SavedRunConfig) {
        prefs.edit()
            .putString(KEY_INTENSITY, config.intensityLevel.name)
            .putInt(KEY_DURATION, config.durationMinutes)
            .putInt(KEY_BATCH_SIZE, config.batchSize)
            .apply()
    }

    data class SavedRunConfig(
        val intensityLevel: IntensityLevel,
        val durationMinutes: Int,
        val batchSize: Int,
    )

    companion object {
        private const val PREFS_NAME = "apbfit_run_config_prefs"
        private const val KEY_INTENSITY = "intensity_level"
        private const val KEY_DURATION = "duration_minutes"
        private const val KEY_BATCH_SIZE = "batch_size"
        const val DEFAULT_DURATION_MINUTES = 30
        const val DEFAULT_BATCH_SIZE = 3
    }
}
