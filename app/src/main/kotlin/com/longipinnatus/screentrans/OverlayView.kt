package com.longipinnatus.screentrans

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.withTranslation
import kotlin.math.max

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private var textBlocks: List<TextBlock> = emptyList(),
    private var textElements: List<TextElement> = emptyList(),
    private val settings: AppSettings.SettingsData = AppSettings.SettingsData(),
) : View(context, attrs, defStyleAttr) {

    private val locationOnScreen = IntArray(2)
    private val debugRect = RectF()
    private var autoHideRunnable: Runnable? = null
    private var streamingDeadline: Long = 0L

    private val textPaint = TextPaint().apply {
        color = Color.WHITE
        isAntiAlias = true

        val baseTypeface = settings.overlayFontPath.takeIf { it.isNotEmpty() }?.let { path ->
            try {
                Typeface.createFromFile(path)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load custom font from $path", e)
                null
            }
        } ?: Typeface.DEFAULT

        val style = when {
            settings.overlayFontBold && settings.overlayFontItalic -> Typeface.BOLD_ITALIC
            settings.overlayFontBold -> Typeface.BOLD
            settings.overlayFontItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        typeface = Typeface.create(baseTypeface, style)
    }

    private val bgPaint = Paint().apply {
        val alpha = (settings.overlayMaskRatio * 255).toInt()
        color = Color.argb(alpha, 0, 0, 0)
    }

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val rawBoxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun updateData(newTextBlocks: List<TextBlock>) {
        this.textBlocks = newTextBlocks.toList()
        invalidate()
    }

    fun startAutoHideTimer() {
        if (!settings.autoHide) return

        // Cancel/reset old tasks (defensive, ensuring no double scheduling)
        cancelPendingHide()

        val delay = calculateDelay()
        scheduleHideTask(delay)
    }

    fun updateStreamingDeadline(incrementalTextLength: Int) {
        if (incrementalTextLength <= 0) return

        val currentTime = System.currentTimeMillis()
        streamingDeadline = max(streamingDeadline, currentTime) + (incrementalTextLength.toLong() * settings.durationPerWordMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Get the current view's absolute position offset on the screen
        getLocationOnScreen(locationOnScreen)

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        if (textBlocks.isEmpty()) {
            Log.d(TAG, "onDraw: mergedBlocks is empty")
        }

        textBlocks.forEach { block ->
            val text = block.translatedText
            if (text == null) {
                Log.d(TAG, "onDraw: block text is null, bounds=${block.bounds}")
                return@forEach
            }
            val bounds = block.bounds

            // Check if original coordinates are out of screen bounds
            if (bounds.left < 0 || bounds.top < 0 || bounds.right > screenWidth || bounds.bottom > screenHeight) {
                Log.e(TAG, "ERROR: Block bounds out of screen! bounds=$bounds, screen=${screenWidth}x$screenHeight")
            }

            // Convert original screen coordinates to local coordinates relative to the current View
            val localLeft = bounds.left.toFloat() - locationOnScreen[0]
            val localTop = bounds.top.toFloat() - locationOnScreen[1]

            if (block.isVertical) {
                drawVerticalBlock(canvas, block, text, localLeft, localTop)
            } else {
                drawHorizontalBlock(canvas, block, text, localLeft, localTop)
            }
        }

        if (settings.showBoxes) {
            textBlocks.forEach { block ->
                debugRect.set(
                    block.bounds.left.toFloat() - locationOnScreen[0],
                    block.bounds.top.toFloat() - locationOnScreen[1],
                    block.bounds.right.toFloat() - locationOnScreen[0],
                    block.bounds.bottom.toFloat() - locationOnScreen[1]
                )
                canvas.drawRect(debugRect, boxPaint)
            }
        }

        if (settings.showRawBoxes) {
            textElements.forEach { element ->
                debugRect.set(
                    element.bounds.left.toFloat() - locationOnScreen[0],
                    element.bounds.top.toFloat() - locationOnScreen[1],
                    element.bounds.right.toFloat() - locationOnScreen[0],
                    element.bounds.bottom.toFloat() - locationOnScreen[1]
                )
                canvas.drawRect(debugRect, rawBoxPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingHide()
    }

    private fun drawHorizontalBlock(canvas: Canvas, block: TextBlock, text: String, left: Float, top: Float) {
        val bounds = block.bounds
        val targetWidth = bounds.width().toFloat()
        val targetHeight = bounds.height().toFloat()

        // Apply adaptive colors
        val originalTextColor = textPaint.color
        val originalBgColor = bgPaint.color
        if (settings.adaptiveColors) {
            block.textColor?.let { textPaint.color = it }
            block.backgroundColor?.let {
                val alpha = (settings.overlayMaskRatio * 255).toInt()
                bgPaint.color = Color.argb(alpha, Color.red(it), Color.green(it), Color.blue(it))
            }
        }

        // 1. Set font size based on median box height
        val medianBoxHeight = block.lines.map { it.bounds.height().toFloat() }.median()
        textPaint.textSize = if (block.sourceTextFontSize > 0f) block.sourceTextFontSize else medianBoxHeight.coerceIn(12f, 120f)

        val fm = Paint.FontMetrics()
        textPaint.getFontMetrics(fm)
        var fontHeight = fm.descent - fm.ascent

        // 2. Calculate line spacing multiplier precisely to match original line distance
        // Use fontHeight as denominator to eliminate downward drift
        val lineSpacingMulti = if (block.lines.size > 1) {
            val dist = block.lines.zipWithNext { a, b -> (b.bounds.top - a.bounds.top).toFloat() }
                .filter { it > 0 }
            if (dist.isNotEmpty()) (dist.median() / fontHeight).coerceIn(0.5f, 3.0f) else 1.0f
        } else 1.0f

        fun buildLayout() = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, max(10, targetWidth.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMulti)
            .setIncludePad(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUseLineSpacingFromFallbacks(false)
                }
            }
            .build()

        var layout = buildLayout()

        // 3. Scaling strategy: shrink text if it wraps more than expected or exceeds target height
        val expectedLineCount = max(1, block.lines.size)
        val shouldShrink = {
            layout.lineCount > expectedLineCount || (expectedLineCount > 1 && layout.height > targetHeight * 1.05f)
        }

        if (shouldShrink()) {
            while (shouldShrink() && textPaint.textSize > 12f) {
                textPaint.textSize -= 0.5f
                layout = buildLayout()
            }
            // Update metrics after final size is decided
            textPaint.getFontMetrics(fm)
            fontHeight = fm.descent - fm.ascent
        }
        if (block.sourceTextFontSize == 0f) {
            block.sourceTextFontSize = textPaint.textSize
        }

        val actualTextWidth = getMaxLineWidth(layout)
        val finalWidth = max(targetWidth, actualTextWidth)
        val finalHeight = max(targetHeight, layout.getLineBaseline(layout.lineCount - 1) + fm.descent)

        // 4. Calculate vertical offset to center the font in the box height
        val verticalOffset = (medianBoxHeight - fontHeight) / 2f

        val drawRect = RectF(left, top, left + finalWidth, top + finalHeight)

        // Check if the drawing area exceeds the current view bounds (screen bounds)
        if (drawRect.left < 0 || drawRect.top < 0 || drawRect.right > width || drawRect.bottom > height) {
            Log.e(TAG, "ERROR: Horizontal block drawn out of screen! rect=$drawRect, viewSize=${width}x${height}")
        }

        // Use a rectangular box for a "textbox" feel and better coverage of original text
        canvas.drawRect(drawRect, bgPaint)

        // Apply translation with vertical centering offset
        canvas.withTranslation(drawRect.left, drawRect.top + verticalOffset) {
            layout.draw(this)
        }

        // Restore original colors
        textPaint.color = originalTextColor
        bgPaint.color = originalBgColor
    }

    private fun drawVerticalBlock(canvas: Canvas, block: TextBlock, text: String, left: Float, top: Float) {
        val bounds = block.bounds
        val targetWidth = bounds.width().toFloat()
        val targetHeight = bounds.height().toFloat()

        // Apply adaptive colors
        val originalTextColor = textPaint.color
        val originalBgColor = bgPaint.color
        if (settings.adaptiveColors) {
            block.textColor?.let { textPaint.color = it }
            block.backgroundColor?.let {
                val alpha = (settings.overlayMaskRatio * 255).toInt()
                bgPaint.color = Color.argb(alpha, Color.red(it), Color.green(it), Color.blue(it))
            }
        }

        // Prioritize median column width for font size
        val medianColWidth = block.lines.map { it.bounds.width().toFloat() }.median()
        var bestTextSize = if (block.sourceTextFontSize > 0f) block.sourceTextFontSize else medianColWidth.coerceIn(12f, 120f)
        textPaint.textSize = bestTextSize

        // Calculate column spacing multiplier from original lines
        val colSpacingMulti = if (block.lines.size > 1) {
            val dist = block.lines.zipWithNext { a, b -> kotlin.math.abs(b.bounds.left - a.bounds.left).toFloat() }
                .filter { it > 0 }
            if (dist.isNotEmpty()) (dist.median() / medianColWidth).coerceIn(0.8f, 2.5f) else 1.2f
        } else 1.2f

        fun layoutVertical(size: Float): List<List<String>> {
            textPaint.textSize = size
            val paragraphs = text.split("\n")
            val allCols = mutableListOf<List<String>>()
            val charHeight = size * 1.1f
            val maxCharsPerCol = (targetHeight / charHeight).toInt().coerceAtLeast(1)

            paragraphs.forEach { para ->
                if (para.isEmpty()) {
                    allCols.add(emptyList())
                    return@forEach
                }
                var i = 0
                while (i < para.length) {
                    val end = minOf(i + maxCharsPerCol, para.length)
                    allCols.add(para.substring(i, end).map { it.toString() })
                    i = end
                }
            }
            return allCols
        }

        var columnGroups = layoutVertical(bestTextSize)
        val colWidth = bestTextSize * colSpacingMulti

        // Adjust vertical layout to match the original width
        if (columnGroups.size * colWidth > targetWidth * 1.1f) {
            while (columnGroups.size * (bestTextSize * colSpacingMulti) > targetWidth * 1.1f && bestTextSize > 12f) {
                bestTextSize -= 0.5f
                columnGroups = layoutVertical(bestTextSize)
            }
        }
        if (block.sourceTextFontSize == 0f) {
            block.sourceTextFontSize = bestTextSize
        }

        val finalColWidth = bestTextSize * colSpacingMulti
        val totalColsWidth = columnGroups.size * finalColWidth
        val finalWidth = max(targetWidth, totalColsWidth)

        val drawRect = RectF(left, top, left + finalWidth, top + targetHeight)

        // Check if the drawing area exceeds the current view bounds (screen bounds)
        if (drawRect.left < 0 || drawRect.top < 0 || drawRect.right > width || drawRect.bottom > height) {
            Log.e(TAG, "ERROR: Vertical block drawn out of screen! rect=$drawRect, viewSize=${width}x${height}")
        }

        canvas.drawRect(drawRect, bgPaint)

        val vFm = textPaint.fontMetrics
        val vFontHeight = vFm.descent - vFm.ascent
        val vOffset = (bestTextSize - vFontHeight) / 2f

        columnGroups.forEachIndexed { colIdx, chars ->
            // Vertical text is usually right-to-left
            val colX = drawRect.right - (colIdx + 1) * finalColWidth
            var currentY = drawRect.top + vOffset

            chars.forEach { charStr ->
                val charW = textPaint.measureText(charStr)
                // Draw text using ascent for precise baseline alignment
                canvas.drawText(charStr, colX + (finalColWidth - charW) / 2, currentY - vFm.ascent, textPaint)
                currentY += bestTextSize * 1.1f
            }
        }

        // Restore original colors
        textPaint.color = originalTextColor
        bgPaint.color = originalBgColor
    }

    private fun getMaxLineWidth(layout: StaticLayout): Float {
        var maxW = 0f
        for (i in 0 until layout.lineCount) {
            val w = layout.getLineWidth(i)
            if (w > maxW) maxW = w
        }
        return maxW
    }

    private fun List<Float>.median(): Float {
        if (isEmpty()) throw NoSuchElementException("Cannot calculate median of an empty list")
        val sorted = sorted()
        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2f
        } else {
            sorted[size / 2]
        }
    }

    private fun calculateDelay(): Long {
        if (settings.autoHideMode == AppSettings.AUTO_HIDE_MODE_FIXED) {
            return settings.displayDurationSec * 1000L
        }

        val currentTime = System.currentTimeMillis()

        // Explicit check: whether in "streaming translation" mode that requires cumulative budget
        val isStreamingFlow = settings.enableStreaming && !settings.ocrOnly

        val deadline = if (isStreamingFlow && streamingDeadline > 0) {
            // Already have a streaming cumulative budget, use that timestamp
            streamingDeadline
        } else {
            // Non-streaming (one-shot display) or OCR-only mode: calculate deadline based on total text length
            val totalLength = textBlocks.sumOf { it.translatedText?.length ?: 0 }
            currentTime + (totalLength * settings.durationPerWordMs)
        }

        return max(0L, deadline - currentTime)
    }

    private fun cancelPendingHide() {
        autoHideRunnable?.let { removeCallbacks(it) }
        autoHideRunnable = null
    }

    private fun scheduleHideTask(delay: Long) {
        val runnable = Runnable {
            OverlayManager.dismiss(OverlayManager.DismissReason.TIMER)
        }
        autoHideRunnable = runnable
        postDelayed(runnable, delay)
    }

    companion object {
        private const val TAG = "OverlayView"
    }
}
