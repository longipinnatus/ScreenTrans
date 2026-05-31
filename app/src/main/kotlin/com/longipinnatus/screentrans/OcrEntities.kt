package com.longipinnatus.screentrans

import android.graphics.Rect
import androidx.annotation.Keep
import com.google.gson.GsonBuilder

@Keep
interface OcrEntity {
    val text: String
    val bounds: Rect
}

@Keep
data class TextElement(
    override val text: String,
    override val bounds: Rect,
    val isVertical: Boolean = false,
    val textColor: Int? = null,
    val backgroundColor: Int? = null,
    val colorWeight: Int = 0
) : OcrEntity {
    fun offset(dx: Int, dy: Int) {
        bounds.offset(dx, dy)
    }

    fun toTextLine(): TextLine = TextLine(
        elements = listOf(this),
        text = text,
        bounds = Rect(bounds),
        isVertical = isVertical,
        textColor = textColor,
        backgroundColor = backgroundColor,
        colorWeight = colorWeight
    )

    fun toTextBlock(): TextBlock = toTextLine().toTextBlock()

    fun copy(): TextElement = copy(bounds = Rect(bounds))
}

@Keep
data class TextLine(
    val elements: List<TextElement>,
    override val text: String,
    override val bounds: Rect,
    val isVertical: Boolean = false,
    val textColor: Int? = null,
    val backgroundColor: Int? = null,
    val colorWeight: Int = 0
) : OcrEntity {
    init {
        require(elements.isNotEmpty()) { "TextLine must contain at least one element" }
    }

    fun offset(dx: Int, dy: Int) {
        bounds.offset(dx, dy)
        elements.forEach { it.offset(dx, dy) }
    }

    fun toTextBlock(): TextBlock = TextBlock(
        lines = listOf(this),
        text = text,
        bounds = Rect(bounds),
        isVertical = isVertical,
        textColor = textColor,
        backgroundColor = backgroundColor,
        colorWeight = colorWeight
    )
}

@Keep
data class TextBlock(
    val lines: List<TextLine>,
    override val text: String,
    override val bounds: Rect,
    val isVertical: Boolean = false,
    var translatedText: String? = null,
    var textColor: Int? = null,
    var backgroundColor: Int? = null,
    var colorWeight: Int = 0,
    var sourceTextFontSize: Float = 0f
) : OcrEntity {
    init {
        require(lines.isNotEmpty()) { "TextBlock must contain at least one line" }
    }

    fun offset(dx: Int, dy: Int) {
        bounds.offset(dx, dy)
        lines.forEach { it.offset(dx, dy) }
    }
}

private val logGson = GsonBuilder().setPrettyPrinting().create()

fun List<OcrEntity>.toLogJson(): String = logGson.toJson(map { entity ->
    val toHex = { c: Int? -> c?.let { String.format("#%08X", it) } }

    buildMap {
        put("text", entity.text)
        put(
            "rect(x,y,w,h)",
            "[${entity.bounds.left}, ${entity.bounds.top}, ${entity.bounds.width()}, ${entity.bounds.height()}]"
        )

        when (entity) {
            is TextElement -> {
                toHex(entity.textColor)?.let { put("textColor", it) }
                toHex(entity.backgroundColor)?.let { put("backgroundColor", it) }
                if (entity.isVertical) put("vertical", true)
            }

            is TextLine -> {
                toHex(entity.textColor)?.let { put("textColor", it) }
                toHex(entity.backgroundColor)?.let { put("backgroundColor", it) }
                if (entity.isVertical) put("vertical", true)
                put("count", entity.elements.size)
            }

            is TextBlock -> {
                toHex(entity.textColor)?.let { put("textColor", it) }
                toHex(entity.backgroundColor)?.let { put("backgroundColor", it) }
                if (entity.isVertical) put("vertical", true)
                put("lines", entity.lines.size)
            }
        }
    }
})
