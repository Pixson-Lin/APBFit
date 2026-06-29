package com.pixsonlin.apbfit.domain.fit

import com.pixsonlin.apbfit.data.model.IntensityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.round
import kotlin.random.Random

class SegmentGeneratorTest {
  private val seededRandom = Random(42)
  private val generator = SegmentGenerator(seededRandom)

  @Test
  fun generate_durationIsWithinBounds() {
    repeat(200) {
      val segment = generator.generate(index = 0, startMillis = 1_000L, level = IntensityLevel.JOG)
      val durationSec = (segment.endTimeMillis - segment.startTimeMillis) / 1_000L
      assertTrue(durationSec in 25..35)
    }
  }

  @Test
  fun generate_stepsAreAtLeastOne() {
    repeat(200) {
      val segment = generator.generate(index = 0, startMillis = 1_000L, level = IntensityLevel.STROLL)
      assertTrue(segment.steps >= 1)
    }
  }

  @Test
  fun generate_distanceMatchesStrideRoundedToTwoDecimals() {
    val level = IntensityLevel.BRISK_WALK
    val segment = generator.generate(index = 0, startMillis = 1_000L, level = level)
    val expected = round(segment.steps * level.strideMeters * 100.0) / 100.0
    assertEquals(expected.toFloat(), segment.distanceMeters, 0.001f)
  }

  @Test
  fun generate_endTimeFollowsStartPlusDuration() {
    val startMillis = 1_700_000_000_000L
    val segment = generator.generate(index = 3, startMillis = startMillis, level = IntensityLevel.MARATHON)
    val durationMillis = segment.endTimeMillis - segment.startTimeMillis
    assertTrue(durationMillis in 25_000L..35_000L)
    assertEquals(startMillis, segment.startTimeMillis)
  }

  @Test
  fun generate_isDeterministicWithSeededRandom() {
    val first = SegmentGenerator(Random(7)).generate(0, 5_000L, IntensityLevel.SPRINT)
    val second = SegmentGenerator(Random(7)).generate(0, 5_000L, IntensityLevel.SPRINT)
    assertEquals(first, second)
  }

  @Test
  fun generate_contiguousSegmentsShareEndAndStart() {
    val gen = SegmentGenerator(Random(99))
    val first = gen.generate(0, 10_000L, IntensityLevel.JOG)
    val second = gen.generate(1, first.endTimeMillis, IntensityLevel.JOG)
    assertEquals(first.endTimeMillis, second.startTimeMillis)
  }
}
