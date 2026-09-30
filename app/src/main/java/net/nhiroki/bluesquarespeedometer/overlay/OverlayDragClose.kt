package net.nhiroki.bluesquarespeedometer.overlay

/**
 * Pure geometry for the "drag the overlay onto the close badge" gesture,
 * similar to what picture-in-picture and other overlay apps do.
 *
 * Deliberately free of any Android classes so plain JVM unit tests can cover
 * the arithmetic.
 */
object OverlayDragClose {
    /** Horizontal position of the close badge: centered on the screen. */
    fun closeTargetLeft(screenWidth: Int, targetSize: Int): Int = (screenWidth - targetSize) / 2

    /** Vertical position of the close badge: [bottomMargin] above the screen bottom. */
    fun closeTargetTop(screenHeight: Int, targetSize: Int, bottomMargin: Int): Int =
        screenHeight - targetSize - bottomMargin

    /**
     * True when the dragged overlay's center is over the close badge. [slop]
     * widens the hit area in every direction so the badge is easy to hit
     * without aiming precisely.
     */
    fun isOverCloseTarget(
        centerX: Int,
        centerY: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        slop: Int
    ): Boolean =
        centerX >= left - slop && centerX <= right + slop &&
            centerY >= top - slop && centerY <= bottom + slop
}
