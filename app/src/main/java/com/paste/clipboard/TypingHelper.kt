package com.paste.clipboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class TypingHelper {

    companion object {
        const val MIN_DELAY = 30L
        const val MAX_DELAY = 80L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    var onComplete: ((Boolean) -> Unit)? = null
    var onProgress: ((Int, Int) -> Unit)? = null

    fun isActive(): Boolean = isTyping

    fun startTyping(rootNode: AccessibilityNodeInfo?, text: String): Boolean {
        if (isTyping || text.isEmpty()) return false

        val targetNode = findFocusedEditableNode(rootNode)
        if (targetNode == null) return false

        val textState = TextState.from(targetNode)
        val endOffsets = buildCodePointEndOffsets(text)
        isTyping = true
        onProgress?.invoke(0, endOffsets.size)
        typeCharacter(targetNode, textState, text, endOffsets, 0)
        return true
    }

    fun cancel() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
        onComplete = null
        onProgress = null
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isFocused && node.isEditable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) {
                if (result !== child) {
                    child.recycle()
                }
                return result
            }
            child.recycle()
        }

        return null
    }

    private fun typeCharacter(
        node: AccessibilityNodeInfo,
        textState: TextState,
        fullText: String,
        endOffsets: List<Int>,
        index: Int
    ) {
        if (!isTyping) return

        if (index >= endOffsets.size) {
            finish(success = true)
            return
        }

        val insertedText = fullText.substring(0, endOffsets[index])
        val currentText = textState.prefix + insertedText + textState.suffix

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                currentText
            )
        }
        val actionSucceeded = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!actionSucceeded) {
            finish(success = false)
            return
        }

        setCursor(node, textState.prefix.length + insertedText.length)

        onProgress?.invoke(index + 1, endOffsets.size)

        val delay = Random.nextLong(MIN_DELAY, MAX_DELAY + 1)
        handler.postDelayed({
            typeCharacter(node, textState, fullText, endOffsets, index + 1)
        }, delay)
    }

    private fun setCursor(node: AccessibilityNodeInfo, cursor: Int) {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    private fun buildCodePointEndOffsets(text: String): List<Int> {
        val offsets = mutableListOf<Int>()
        var offset = 0
        while (offset < text.length) {
            offset = text.offsetByCodePoints(offset, 1)
            offsets.add(offset)
        }
        return offsets
    }

    private fun finish(success: Boolean) {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
        val callback = onComplete
        onComplete = null
        onProgress = null
        callback?.invoke(success)
    }

    private data class TextState(
        val prefix: String,
        val suffix: String
    ) {
        companion object {
            fun from(node: AccessibilityNodeInfo): TextState {
                val currentText = node.text?.toString().orEmpty()
                val selectionStart = node.textSelectionStart
                val selectionEnd = node.textSelectionEnd

                if (selectionStart !in 0..currentText.length ||
                    selectionEnd !in 0..currentText.length
                ) {
                    return TextState(prefix = currentText, suffix = "")
                }

                val start = min(selectionStart, selectionEnd)
                val end = max(selectionStart, selectionEnd)
                return TextState(
                    prefix = currentText.substring(0, start),
                    suffix = currentText.substring(end)
                )
            }
        }
    }
}
