package com.pixson.apbfit.data.model

enum class IntensityLevel(
    val displayName: String,
    val cadenceSpm: Int,
    val strideMeters: Double,
) {
    STROLL("散步", 80, 0.60),
    BRISK_WALK("快走", 110, 0.63),
    SUPER_SLOW_JOG("超慢跑", 140, 0.67),
    JOG("慢跑", 165, 0.70),
    MARATHON("馬拉松", 180, 0.78),
    FAST_RUN("快跑", 190, 0.92),
    SPRINT("衝刺", 210, 1.00),
}
