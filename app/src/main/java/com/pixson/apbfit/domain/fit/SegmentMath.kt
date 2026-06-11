package com.pixson.apbfit.domain.fit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.random.Random

internal fun gaussianRound(mean: Double, sigma: Double, random: Random): Int {
    val u1 = random.nextDouble().coerceAtLeast(Double.MIN_VALUE)
    val u2 = random.nextDouble()
    val z = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    return round(mean + sigma * z).toInt()
}

internal fun Double.roundToTwoDecimals(): Double = round(this * 100.0) / 100.0
