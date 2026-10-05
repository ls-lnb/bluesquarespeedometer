package net.nhiroki.bluesquarespeedometer

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * Applies the system bar and display cutout insets as padding *on top of* the
 * padding the layout already declares.
 *
 * This keeps content clear of curved display edges: side edges of a curved
 * screen carry no system bar, so replacing the padding with the system bar
 * insets alone would have pushed the content against the screen edge.
 */
fun View.applyWindowInsetsPreservingPadding(ignoreSystemBarVisibility: Boolean = false) {
    val baseLeft = this.paddingLeft
    val baseTop = this.paddingTop
    val baseRight = this.paddingRight
    val baseBottom = this.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val systemBars = if (ignoreSystemBarVisibility) {
            insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.getInsets(WindowInsetsCompat.Type.systemBars())
        }
        // System bar and cutout insets are both anchored to the screen edge,
        // so their union is the larger one per side
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        v.setPadding(
            baseLeft + max(systemBars.left, cutout.left),
            baseTop + max(systemBars.top, cutout.top),
            baseRight + max(systemBars.right, cutout.right),
            baseBottom + max(systemBars.bottom, cutout.bottom))
        insets
    }
}
