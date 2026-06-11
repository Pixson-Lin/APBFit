package com.pixson.apbfit.data.model

enum class IntensityLevel(
    val displayName: String,
    val cadenceSpm: Int,
    val strideMeters: Double,
) {
    STROLL("Stroll", 80, 0.60),
    BRISK_WALK("Brisk Walk", 110, 0.72),
    JOG("Jog", 150, 0.85),
    MARATHON("Marathon", 170, 0.92),
    SPRINT("Sprint", 190, 1.00),
}
