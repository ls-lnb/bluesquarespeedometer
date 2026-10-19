package net.nhiroki.bluesquarespeedometer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Computes the text size of the big speed / altitude digits.
 *
 * The digits fill their (weighted, screen filling) section, but must also stay
 * inside it horizontally: sizing from the section height alone overflows on
 * narrow screens or with 4 digit altitudes such as "1500" (measured in metres).
 *
 * The width estimate uses the average glyph advance of the monospace digits and
 * of the proportional unit label, in multiples of the text size. It is a pure
 * calculation so it can be covered by plain JVM unit tests.
 */
object DigitSizing {
    private const val DIGIT_ADVANCE_EM = 0.62f
    private const val UNIT_ADVANCE_EM = 0.55f

    // Keep a little room on the width estimate so the digits never sit exactly
    // on the section boundary
    private const val WIDTH_SAFETY_MARGIN = 1.03f

    // Only resize when the target moved noticeably, so the digits do not
    // flicker while the accuracy details gain or lose a line
    private const val SIGNIFICANT_CHANGE_RATIO = 0.02f

    fun textSizePx(
        sectionHeightPx: Int,
        availableWidthPx: Int,
        digitCount: Int,
        unitCharCount: Int,
        unitSizeRatio: Float,
        fillRatio: Float,
        minSizePx: Float
    ): Float {
        val heightBased = max(sectionHeightPx * fillRatio, minSizePx)
        if (availableWidthPx <= 0 || digitCount <= 0) {
            return heightBased
        }

        val widthInEm = DIGIT_ADVANCE_EM * digitCount + UNIT_ADVANCE_EM * unitCharCount * unitSizeRatio
        val widthBased = availableWidthPx / (widthInEm * WIDTH_SAFETY_MARGIN)
        return max(min(heightBased, widthBased), minSizePx)
    }

    /**
     * True when [nextPx] differs from [currentPx] enough to be worth applying.
     * Comparing against the applied size keeps the error bounded by
     * [SIGNIFICANT_CHANGE_RATIO].
     */
    fun isSignificantChange(currentPx: Float, nextPx: Float): Boolean {
        if (currentPx <= 0f) {
            return true
        }
        return abs(nextPx - currentPx) >= max(nextPx * SIGNIFICANT_CHANGE_RATIO, 1f)
    }
}
