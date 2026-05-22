package com.longipinnatus.screentrans

import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object OcrPostProcessor {
    private const val TAG = "OcrPostProcessor"

    // Thresholds for merging elements
    private const val ELEMENTS_OVERLAP_RATIO = 0.6f
    private const val ELEMENTS_GAP_RATIO = 1.2f
    // Thresholds for merging lines
    private const val LINES_OVERLAP_RATIO = 0.5f
    private const val LINES_MAX_GAP_RATIO = 1.3f
    // Thresholds for adding line break
    private const val HARD_BREAK_RATIO = 3.0f
    private const val INDENT_RATIO = 2.5f

    fun process(elements: List<TextElement>, settings: AppSettings.SettingsData): List<TextBlock> {
        val copiedElements = elements.map { it.copy() }
        val (ignoredElements, filteredElements) = copiedElements.partition { shouldIgnore(it, settings) }

        val merged = if (settings.mergeTextBoxes) {
            mergeToBlocks(mergeToLines(filteredElements))
        } else {
            filteredElements.map { it.toTextBlock() }
        }

        val (ignoredMerged, filteredMerged) = merged.partition { shouldIgnore(it, settings) }

        val logEntries = buildList {
            if (ignoredElements.isNotEmpty()) {
                add(LogEntry("Ignored Elements (${ignoredElements.size})", ignoredElements.toLogJson()))
            }
            if (filteredElements.isNotEmpty()) {
                add(LogEntry("Filtered Elements (${filteredElements.size})", filteredElements.toLogJson()))
            }

            if (settings.mergeTextBoxes) {
                if (ignoredMerged.isNotEmpty()) {
                    add(LogEntry("Ignored Blocks (${ignoredMerged.size})", ignoredMerged.toLogJson()))
                }
                if (filteredMerged.isNotEmpty()) {
                    add(LogEntry("Filtered Blocks (${filteredMerged.size})", filteredMerged.toLogJson()))
                }
            }
        }

        if (logEntries.isNotEmpty()) LogManager.log(LogType.DEBUG, TAG, logEntries)

        return filteredMerged
    }

    private fun <T : OcrEntity> shouldIgnore(item: T, settings: AppSettings.SettingsData): Boolean {
        val text = item.text
        val width = item.bounds.width()
        val height = item.bounds.height()
        val isMerged = item !is TextElement
        Log.d(TAG, "Checking Text: $text, Size: $width * $height, Merged: $isMerged")

        return settings.filterRules.asSequence()
            .filter { it.enabled }
            .filter { if (isMerged) it.applyToMerged else it.applyToRaw }
            .any { rule ->
                val sizeMatch = (rule.minWidth == 0 || width < rule.minWidth) &&
                        (rule.minHeight == 0 || height < rule.minHeight)
                if (!sizeMatch) return@any false
                if (rule.regex.isEmpty()) return@any true
                rule.pattern?.containsMatchIn(text) ?: false
            }
    }

    private fun mergeToLines(elements: List<TextElement>): List<TextLine> {
        if (elements.isEmpty()) return emptyList()

        val sorted = elements.sortedWith { e1, e2 ->
            if (e1.isVertical != e2.isVertical) {
                e1.isVertical.compareTo(e2.isVertical)
            } else if (e1.isVertical) {
                if (abs(e1.bounds.centerX() - e2.bounds.centerX()) < (e1.bounds.width() + e2.bounds.width()) / 4) {
                    e1.bounds.top.compareTo(e2.bounds.top)
                } else {
                    e2.bounds.right.compareTo(e1.bounds.right)
                }
            } else {
                if (abs(e1.bounds.centerY() - e2.bounds.centerY()) < (e1.bounds.height() + e2.bounds.height()) / 4) {
                    e1.bounds.left.compareTo(e2.bounds.left)
                } else {
                    e1.bounds.top.compareTo(e2.bounds.top)
                }
            }
        }

        return sorted.groupAndMerge(::shouldMergeElements, ::mergeElements)
    }

    private fun shouldMergeElements(a: TextElement, b: TextElement): Boolean {
        if (a.isVertical != b.isVertical) return false
        val r1 = a.bounds
        val r2 = b.bounds
        val avgColW = (r1.width() + r2.width()) / 2f
        val avgRowH = (r1.height() + r2.height()) / 2f

        if (a.isVertical) {
            val hOverlap = max(0, min(r1.right, r2.right) - max(r1.left, r2.left))
            val vGap = if (r1.top < r2.top) r2.top - r1.bottom else r1.top - r2.bottom
            if (hOverlap > avgColW * ELEMENTS_OVERLAP_RATIO && vGap < avgColW * ELEMENTS_GAP_RATIO) return true
        } else {
            val vOverlap = max(0, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val hGap = if (r1.left < r2.left) r2.left - r1.right else r1.left - r2.right
            if (vOverlap > avgRowH * ELEMENTS_OVERLAP_RATIO && hGap < avgRowH * ELEMENTS_GAP_RATIO) return true
        }
        return false
    }

    private fun mergeElements(elements: List<TextElement>): TextLine {
        if (elements.size == 1) return elements[0].toTextLine()
        val first = elements[0]
        val isVertical = first.isVertical
        val bounds = Rect()
        elements.forEach { bounds.union(it.bounds) }

        val sortedElements = elements.sortedBy {
            if (isVertical) it.bounds.top else it.bounds.left
        }

        val text = sortedElements.joinToString(if (isCJK(sortedElements.first().text)) "" else " ") { it.text }
        val (textColor, bgColor, colorWeight) = resolveDominantColor(elements.map {
            ColorInfo(it.textColor, it.backgroundColor, it.colorWeight)
        })

        return TextLine(
            sortedElements,
            text,
            bounds,
            isVertical,
            textColor,
            bgColor,
            colorWeight
        )
    }

    private fun mergeToBlocks(lines: List<TextLine>): List<TextBlock> {
        if (lines.isEmpty()) return emptyList()
        return lines.groupAndMerge(::shouldMergeLines, ::mergeLines)
    }

    private fun shouldMergeLines(a: TextLine, b: TextLine): Boolean {
        if (a.isVertical != b.isVertical) return false
        val r1 = a.bounds
        val r2 = b.bounds
        val avgColW = (r1.width() + r2.width()) / 2f
        val avgRowH = (r1.height() + r2.height()) / 2f

        if (a.isVertical) {
            val vOverlap = max(0, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val hGap = if (r1.left < r2.left) r2.left - r1.right else r1.left - r2.right
            if (vOverlap > min(r1.height(), r2.height()) * LINES_OVERLAP_RATIO && hGap < avgColW * LINES_MAX_GAP_RATIO) return true
        } else {
            val hOverlap = max(0, min(r1.right, r2.right) - max(r1.left, r2.left))
            val vGap = if (r1.top < r2.top) r2.top - r1.bottom else r1.top - r2.bottom
            if (hOverlap > min(r1.width(), r2.width()) * LINES_OVERLAP_RATIO && vGap < avgRowH * LINES_MAX_GAP_RATIO) return true
        }
        return false
    }

    private fun mergeLines(lines: List<TextLine>): TextBlock {
        if (lines.size == 1) return lines[0].toTextBlock()
        val first = lines[0]
        val isVertical = first.isVertical
        val bounds = Rect()
        lines.forEach { bounds.union(it.bounds) }

        val sortedLines = lines.sortedBy {
            if (isVertical) -it.bounds.left else it.bounds.top
        }

        val textBuilder = StringBuilder()
        for (i in 0 until sortedLines.size - 1) {
            val current = sortedLines[i]
            val next = sortedLines[i + 1]
            textBuilder.append(current.text)

            val sep = if (isVertical) {
                val avgW = (current.bounds.width() + next.bounds.width()) / 2f
                val isHardBreak = current.bounds.bottom < bounds.bottom - avgW * HARD_BREAK_RATIO
                val isIndent = next.bounds.top > current.bounds.top + avgW * INDENT_RATIO
                when {
                    isHardBreak || isIndent -> "\n"
                    isCJK(current.text) && isCJK(next.text) -> ""
                    else -> " "
                }
            } else {
                val avgH = (current.bounds.height() + next.bounds.height()) / 2f
                val isHardBreak = current.bounds.right < bounds.right - avgH * HARD_BREAK_RATIO
                val isIndent = next.bounds.left > current.bounds.left + avgH * INDENT_RATIO
                when {
                    isHardBreak || isIndent -> "\n"
                    isCJK(current.text) && isCJK(next.text) -> ""
                    else -> " "
                }
            }
            textBuilder.append(sep)
        }
        textBuilder.append(sortedLines.last().text)

        val (textColor, bgColor, colorWeight) = resolveDominantColor(lines.map {
            ColorInfo(it.textColor, it.backgroundColor, it.colorWeight)
        })

        return TextBlock(
            sortedLines,
            textBuilder.toString(),
            bounds,
            isVertical,
            null,
            textColor,
            bgColor,
            colorWeight
        )
    }

    private fun <T, R> List<T>.groupAndMerge(
        shouldMerge: (T, T) -> Boolean,
        merge: (List<T>) -> R
    ): List<R> {
        val size = this.size
        if (size == 0) return emptyList()
        if (size == 1) return listOf(merge(this))

        val parent = IntArray(size) { it }
        fun find(i: Int): Int {
            var curr = i
            while (parent[curr] != curr) {
                parent[curr] = parent[parent[curr]]
                curr = parent[curr]
            }
            return curr
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootI] = rootJ
        }

        for (i in 0 until size) {
            for (j in i + 1 until size) {
                if (shouldMerge(this[i], this[j])) {
                    union(i, j)
                }
            }
        }

        return indices.groupBy { find(it) }
            .map { (_, indices) -> merge(indices.map { this[it] }) }
    }


    private fun resolveDominantColor(items: List<ColorInfo>): ColorInfo {
        if (items.isEmpty()) return ColorInfo(null, null, 0)
        return items.reduce { acc, next ->
            if (acc.textColor == next.textColor) {
                ColorInfo(acc.textColor, acc.bgColor, acc.weight + next.weight)
            } else {
                when {
                    acc.weight > next.weight -> ColorInfo(acc.textColor, acc.bgColor, acc.weight - next.weight)
                    next.weight > acc.weight -> ColorInfo(next.textColor, next.bgColor, next.weight - acc.weight)
                    else -> ColorInfo(acc.textColor, acc.bgColor, 0)
                }
            }
        }
    }

    private fun isCJK(text: String): Boolean {
        return text.any { c ->
            val block = Character.UnicodeBlock.of(c)
            val sc = Character.UnicodeScript.of(c.code)

            sc == Character.UnicodeScript.HAN ||
            sc == Character.UnicodeScript.HIRAGANA ||
            sc == Character.UnicodeScript.KATAKANA ||
            sc == Character.UnicodeScript.HANGUL ||

            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
        }
    }

    private data class ColorInfo(
        val textColor: Int?,
        val bgColor: Int?,
        val weight: Int
    )
}
