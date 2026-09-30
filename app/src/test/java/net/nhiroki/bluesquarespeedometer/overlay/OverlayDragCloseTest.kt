package net.nhiroki.bluesquarespeedometer.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDragCloseTest {
    @Test
    fun closeTargetIsCenteredHorizontally() {
        assertEquals(470, OverlayDragClose.closeTargetLeft(1080, 140))
        assertEquals(0, OverlayDragClose.closeTargetLeft(140, 140))
        // Odd sizes round down instead of overflowing
        assertEquals(469, OverlayDragClose.closeTargetLeft(1079, 140))
    }

    @Test
    fun closeTargetSitsAboveTheBottomMargin() {
        assertEquals(1900, OverlayDragClose.closeTargetTop(2400, 140, 360))
        // Still well defined when the screen is barely taller than the badge
        assertEquals(0, OverlayDragClose.closeTargetTop(200, 140, 60))
    }

    @Test
    fun overlayCenterInsideTheBadgeArmsTheClose() {
        // Badge spans (470, 1900) - (610, 2040)
        assertTrue(OverlayDragClose.isOverCloseTarget(540, 1970, 470, 1900, 610, 2040, 40))
        assertTrue(OverlayDragClose.isOverCloseTarget(470, 2040, 470, 1900, 610, 2040, 0))
    }

    @Test
    fun slopWidensTheBadgeInEveryDirection() {
        // 40px outside the badge on both axes is still a hit with 40px slop
        assertTrue(OverlayDragClose.isOverCloseTarget(430, 1860, 470, 1900, 610, 2040, 40))
        assertTrue(OverlayDragClose.isOverCloseTarget(650, 2080, 470, 1900, 610, 2040, 40))
        // Just beyond the slop misses
        assertFalse(OverlayDragClose.isOverCloseTarget(429, 1970, 470, 1900, 610, 2040, 40))
    }

    @Test
    fun farAwayPointsNeverArmTheClose() {
        // Where the overlay usually lives (top-left corner of the screen)
        assertFalse(OverlayDragClose.isOverCloseTarget(72, 180, 470, 1900, 610, 2040, 40))
        assertFalse(OverlayDragClose.isOverCloseTarget(540, 100, 470, 1900, 610, 2040, 40))
    }

    @Test
    fun armsWhenEitherTheCenterOrTheFingerIsOverTheBadge() {
        val l = 470; val t = 1900; val r = 610; val b = 2040; val slop = 40
        // Center on the badge, finger holding an edge far away -> armed
        assertTrue(OverlayDragClose.isArmed(540, 1970, 400, 1000, l, t, r, b, slop))
        // Finger on the badge, overlay center lagging behind -> armed
        assertTrue(OverlayDragClose.isArmed(400, 1000, 540, 1970, l, t, r, b, slop))
        // Both outside -> not armed
        assertFalse(OverlayDragClose.isArmed(400, 1000, 420, 1200, l, t, r, b, slop))
        // A quick flick whose last point is beyond the slop -> not armed
        assertFalse(OverlayDragClose.isArmed(700, 2200, 760, 2300, l, t, r, b, slop))
    }
}
