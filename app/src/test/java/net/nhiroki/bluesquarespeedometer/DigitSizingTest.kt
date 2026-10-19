package net.nhiroki.bluesquarespeedometer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitSizingTest {
    private val unitRatio = 0.4f
    private val fillRatio = 0.5f

    @Test
    fun heightDrivesTheSizeWhenThereIsEnoughWidth() {
        val size = DigitSizing.textSizePx(
            sectionHeightPx = 200, availableWidthPx = 1000, digitCount = 3, unitCharCount = 4,
            unitSizeRatio = unitRatio, fillRatio = fillRatio, minSizePx = 8f)
        assertEquals(100f, size, 0.01f)
    }

    @Test
    fun widthLimitsTheSizeOnNarrowSections() {
        // A 4 digit altitude such as "1500" in a tall but narrow section
        val size = DigitSizing.textSizePx(
            sectionHeightPx = 400, availableWidthPx = 300, digitCount = 4, unitCharCount = 1,
            unitSizeRatio = unitRatio, fillRatio = fillRatio, minSizePx = 8f)
        assertTrue("expected a width limited size, got $size", size < 400 * fillRatio)
        assertTrue(size > 0f)
    }

    @Test
    fun neverGoesBelowTheMinimum() {
        val size = DigitSizing.textSizePx(
            sectionHeightPx = 10, availableWidthPx = 10, digitCount = 4, unitCharCount = 4,
            unitSizeRatio = unitRatio, fillRatio = fillRatio, minSizePx = 14f)
        assertEquals(14f, size, 0.01f)
    }

    @Test
    fun theResultAlwaysFitsTheAvailableWidth() {
        val digitCounts = listOf(1, 2, 3, 4)
        val unitCounts = listOf(1, 4)
        val heights = listOf(20, 100, 260, 400, 800)
        val widths = listOf(120, 200, 320, 388, 900)

        for (digits in digitCounts) {
            for (units in unitCounts) {
                for (height in heights) {
                    for (width in widths) {
                        val size = DigitSizing.textSizePx(
                            sectionHeightPx = height, availableWidthPx = width,
                            digitCount = digits, unitCharCount = units,
                            unitSizeRatio = unitRatio, fillRatio = fillRatio, minSizePx = 14f)
                        // Same model as the one used to compute the size
                        val neededWidth = size * (0.62f * digits + 0.55f * units * unitRatio)
                        assertTrue(
                            "digits=$digits units=$units height=$height width=$width -> $size needs $neededWidth",
                            neededWidth <= width + 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun smallChangesAreNotWorthApplying() {
        assertFalse(DigitSizing.isSignificantChange(100f, 101f))
        assertFalse(DigitSizing.isSignificantChange(100f, 100f))
        assertTrue(DigitSizing.isSignificantChange(100f, 104f))
        assertTrue(DigitSizing.isSignificantChange(100f, 80f))
    }

    @Test
    fun theFirstMeasurementIsAlwaysApplied() {
        assertTrue(DigitSizing.isSignificantChange(0f, 100f))
    }
}
