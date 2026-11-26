package com.oriondev.moneywallet.ui.view.chart

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class PieChartLabelDistanceTest {

    companion object {

        private const val TEXT_DISTANCE = 95f
        private const val ICON_DISTANCE = 80f
        private const val ICON_RADIUS = 10f
        private const val MARGIN = 2f

        private const val HALF_ADVANCE = 20f
        private const val HALF_INK_HEIGHT = 6f

        private const val TOLERANCE = 0.001f
    }

    private fun labelDistance(iconAngle: Float, halfAdvance: Float, halfInkHeight: Float): Float {
        return PieChart.labelDistance(iconAngle, TEXT_DISTANCE, ICON_DISTANCE, ICON_RADIUS, MARGIN,
                halfAdvance, halfInkHeight)
    }

    @Test
    fun aLabelBesideAnIconOnTheRightClearsItsAdvance() {
        assertEquals("a label at 0 degrees is not placed past its icon by its half advance and the margin",
                112f, labelDistance(0f, HALF_ADVANCE, HALF_INK_HEIGHT), TOLERANCE)
    }

    @Test
    fun aLabelBesideAnIconOnTheLeftClearsItsAdvance() {
        assertEquals("a label at 180 degrees is not placed past its icon by its half advance and the margin",
                112f, labelDistance(180f, HALF_ADVANCE, HALF_INK_HEIGHT), TOLERANCE)
    }

    @Test
    fun aLabelBelowAnIconClearsItsHeight() {
        assertEquals("a label at 90 degrees is not placed past its icon by its half height and the margin",
                98f, labelDistance(90f, HALF_ADVANCE, HALF_INK_HEIGHT), TOLERANCE)
    }

    @Test
    fun aLabelOnTheDiagonalClearsTheCornerOfItsIcon() {
        assertEquals("a label at 45 degrees is not placed past the corner of its icon",
                114.527f, labelDistance(45f, HALF_ADVANCE, HALF_INK_HEIGHT), TOLERANCE)
    }

    @Test
    fun aNarrowLabelStaysAtTheTextDistance() {
        assertEquals("a label that already clears its icon moved away from the text distance",
                TEXT_DISTANCE, labelDistance(0f, 2f, 2f), TOLERANCE)
    }

    @Test
    fun noLabelOverlapsItsIconAtAnyWholeDegree() {
        for (degree in 0 until 360) {
            val iconAngle = degree.toFloat()
            val distance = labelDistance(iconAngle, HALF_ADVANCE, HALF_INK_HEIGHT)
            val cos = Math.cos(Math.toRadians(iconAngle.toDouble())).toFloat()
            val sin = Math.sin(Math.toRadians(iconAngle.toDouble())).toFloat()
            val iconX = cos * ICON_DISTANCE
            val iconY = sin * ICON_DISTANCE
            val textX = cos * distance
            val textY = sin * distance
            val overlaps = textX - HALF_ADVANCE < iconX + ICON_RADIUS && iconX - ICON_RADIUS < textX + HALF_ADVANCE
                    && textY - HALF_INK_HEIGHT < iconY + ICON_RADIUS && iconY - ICON_RADIUS < textY + HALF_INK_HEIGHT
            assertFalse("the label at $degree degrees overlaps its icon", overlaps)
            assertTrue("the label at $degree degrees moved inside the text distance",
                    distance >= TEXT_DISTANCE)
        }
    }

}
