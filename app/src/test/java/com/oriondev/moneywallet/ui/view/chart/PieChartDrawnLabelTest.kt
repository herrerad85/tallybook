package com.oriondev.moneywallet.ui.view.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View

import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class PieChartDrawnLabelTest {

    companion object {

        private const val LONG_EDGE = 600
        private const val SHORT_EDGE = 300

        private const val LEFT_EDGE_CHART_WIDTH = 400
        private const val RIGHT_EDGE_CHART_WIDTH = 350
        private const val TOP_AND_BOTTOM_EDGE_CHART_HEIGHT = 320

        private const val MAX_RADIUS = SHORT_EDGE / 2f

        private const val TEXT_DISTANCE = MAX_RADIUS * 0.95f
        private const val ICON_DISTANCE = MAX_RADIUS * 0.8f
        private const val ICON_RADIUS = MAX_RADIUS * 0.1f
        private const val LABEL_MARGIN_DP = 2f

        private const val ATTEMPTS = 200
        private const val WAIT_MILLIS = 25L

        private const val TOLERANCE = 0.5f
    }

    private class Label(val text: String, val x: Float, val y: Float, val halfAdvance: Float, val bounds: Rect) {

        val left get() = x - halfAdvance
        val right get() = x + halfAdvance
        val top get() = y + bounds.top
        val bottom get() = y + bounds.bottom
    }

    private class LabelCanvas(bitmap: Bitmap) : Canvas(bitmap) {

        val labels = ArrayList<Label>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            labels.add(Label(text, x, y, paint.measureText(text) / 2, bounds))
            super.drawText(text, x, y, paint)
        }
    }

    private fun slices(vararg values: Long): List<PieSlice> {
        return values.map { PieSlice("slice", it, ColorDrawable(Color.BLACK)) }
    }

    private fun labels(slices: List<PieSlice>, width: Int, height: Int): List<Label> {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme)
        val attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.pie_chartPercentageEnabled, "true").build()
        val chart = PieChart(context, attributes)
        chart.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        chart.layout(0, 0, width, height)
        chart.setChartData(PieData(slices))
        val canvas = LabelCanvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        for (attempt in 0 until ATTEMPTS) {
            shadowOf(Looper.getMainLooper()).idle()
            chart.draw(canvas)
            if (canvas.labels.isNotEmpty()) {
                assertEquals("the chart did not draw one label per slice", slices.size, canvas.labels.size)
                return canvas.labels
            }
            Thread.sleep(WAIT_MILLIS)
        }
        throw AssertionError("the chart never drew a label, so its slice angles were never computed")
    }

    private fun distance(label: Label, iconAngle: Float, width: Int, height: Int): Float {
        val cos = Math.cos(Math.toRadians(iconAngle.toDouble())).toFloat()
        val sin = Math.sin(Math.toRadians(iconAngle.toDouble())).toFloat()
        return (label.x - width / 2f) * cos + (label.y + (label.bounds.top + label.bounds.bottom) / 2f - height / 2f) * sin
    }

    private fun assertClearOfItsIcon(label: Label, slice: PieSlice, iconAngle: Float, width: Int, height: Int) {
        assertEquals("the slice icon is not at $iconAngle degrees", iconAngle, slice.getIconAngle(), TOLERANCE)
        val icon = slice.getIcon()!!.bounds
        val overlaps = label.left < icon.right && icon.left < label.right
                && label.top < icon.bottom && icon.top < label.bottom
        assertFalse("the label ${label.text} at $iconAngle degrees overlaps its icon", overlaps)
        assertTrue("the label ${label.text} at $iconAngle degrees moved inside the text distance",
                distance(label, iconAngle, width, height) >= TEXT_DISTANCE - TOLERANCE)
        assertTrue("the label ${label.text} at $iconAngle degrees crosses an edge of the chart, so it was moved in",
                label.left >= 0f && label.right <= width && label.top >= 0f && label.bottom <= height)
    }

    private fun assertMovedInsideTheChart(label: Label, slice: PieSlice, iconAngle: Float, width: Int, height: Int) {
        assertEquals("the slice icon is not at $iconAngle degrees", iconAngle, slice.getIconAngle(), TOLERANCE)
        assertTrue("the label ${label.text} pushed into an edge still crosses an edge of the chart",
                label.left >= 0f && label.right <= width && label.top >= 0f && label.bottom <= height)
    }

    private fun assertAtItsLabelDistance(slices: List<PieSlice>, width: Int, height: Int) {
        val margin = LABEL_MARGIN_DP * ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        val labels = labels(slices, width, height)
        for (i in slices.indices) {
            val iconAngle = slices[i].getIconAngle()
            val expected = PieChart.labelDistance(iconAngle, TEXT_DISTANCE, ICON_DISTANCE, ICON_RADIUS, margin,
                    labels[i].halfAdvance, labels[i].bounds.height() / 2f)
            assertTrue("the label ${labels[i].text} at $iconAngle degrees touches an edge of the chart, so it may have been moved in",
                    labels[i].left > 0f && labels[i].right < width && labels[i].top > 0f && labels[i].bottom < height)
            assertEquals("the label ${labels[i].text} at $iconAngle degrees is not drawn at its label distance",
                    expected, distance(labels[i], iconAngle, width, height), TOLERANCE)
            assertEquals("the label ${labels[i].text} at $iconAngle degrees is not drawn on the ray of its icon",
                    0f, distance(labels[i], iconAngle + 90f, width, height), TOLERANCE)
        }
    }

    @Test
    fun aDrawnLabelOnTheLeftClearsItsIcon() {
        val slices = slices(1L)
        assertClearOfItsIcon(labels(slices, LONG_EDGE, SHORT_EDGE)[0], slices[0], 180f, LONG_EDGE, SHORT_EDGE)
    }

    @Test
    fun drawnLabelsBelowAndAboveTheirIconsClearThem() {
        val slices = slices(1L, 1L)
        val labels = labels(slices, SHORT_EDGE, LONG_EDGE)
        assertClearOfItsIcon(labels[0], slices[0], 90f, SHORT_EDGE, LONG_EDGE)
        assertClearOfItsIcon(labels[1], slices[1], 270f, SHORT_EDGE, LONG_EDGE)
    }

    @Test
    fun aDrawnLabelPushedIntoAnEdgeIsMovedInsideTheChart() {
        val slices = slices(1L)
        val label = labels(slices, LEFT_EDGE_CHART_WIDTH, SHORT_EDGE)[0]
        assertMovedInsideTheChart(label, slices[0], 180f, LEFT_EDGE_CHART_WIDTH, SHORT_EDGE)
        assertEquals("the label ${label.text} was not moved in to touch the left edge of the chart",
                0f, label.left, TOLERANCE)
    }

    @Test
    fun aDrawnLabelPushedIntoTheRightEdgeIsMovedInsideTheChart() {
        val slices = slices(1L, 20L)
        val label = labels(slices, RIGHT_EDGE_CHART_WIDTH, SHORT_EDGE)[0]
        assertMovedInsideTheChart(label, slices[0], 8.57f, RIGHT_EDGE_CHART_WIDTH, SHORT_EDGE)
        assertEquals("the label ${label.text} was not moved in to touch the right edge of the chart",
                RIGHT_EDGE_CHART_WIDTH.toFloat(), label.right, TOLERANCE)
    }

    @Test
    fun aDrawnLabelPushedIntoTheTopEdgeIsMovedInsideTheChart() {
        val slices = slices(1L, 1L)
        val label = labels(slices, SHORT_EDGE, TOP_AND_BOTTOM_EDGE_CHART_HEIGHT)[1]
        assertMovedInsideTheChart(label, slices[1], 270f, SHORT_EDGE, TOP_AND_BOTTOM_EDGE_CHART_HEIGHT)
        assertEquals("the label ${label.text} was not moved in to touch the top edge of the chart",
                0f, label.top, TOLERANCE)
    }

    @Test
    fun aDrawnLabelPushedIntoTheBottomEdgeIsMovedInsideTheChart() {
        val slices = slices(1L, 1L)
        val label = labels(slices, SHORT_EDGE, TOP_AND_BOTTOM_EDGE_CHART_HEIGHT)[0]
        assertMovedInsideTheChart(label, slices[0], 90f, SHORT_EDGE, TOP_AND_BOTTOM_EDGE_CHART_HEIGHT)
        assertEquals("the label ${label.text} was not moved in to touch the bottom edge of the chart",
                TOP_AND_BOTTOM_EDGE_CHART_HEIGHT.toFloat(), label.bottom, TOLERANCE)
    }

    @Test
    fun everyDrawnLabelSitsAtItsLabelDistance() {
        assertAtItsLabelDistance(slices(1L), LONG_EDGE, SHORT_EDGE)
        assertAtItsLabelDistance(slices(1L, 1L), SHORT_EDGE, LONG_EDGE)
        val slices = slices(100L, 1L, 1L)
        assertAtItsLabelDistance(slices, LONG_EDGE, SHORT_EDGE)
        val middleAngle = slices[1].getStartAngle() + slices[1].getSweepAngle() / 2
        assertTrue("the icon at ${slices[1].getIconAngle()} degrees was not moved off the middle of its slice at $middleAngle degrees",
                Math.abs(slices[1].getIconAngle() - middleAngle) > 1f)
    }

}
