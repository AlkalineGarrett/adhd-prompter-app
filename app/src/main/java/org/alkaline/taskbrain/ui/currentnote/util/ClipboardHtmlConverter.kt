package org.alkaline.taskbrain.ui.currentnote.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Composable that monitors the clipboard and converts plain text with bullets/checkboxes
 * to HTML format for better compatibility when pasting into apps like Google Docs.
 */
@Composable
fun ClipboardHtmlConverter() {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val isWindowFocused = windowInfo.isWindowFocused

    var previouslyFocused by remember { mutableStateOf(false) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Check clipboard when window gains focus (app comes to foreground)
    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused && !previouslyFocused) {
            handleClipboardChange(clipboardManager)
        }
        previouslyFocused = isWindowFocused
    }

    DisposableEffect(Unit) {
        val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange(clipboardManager)
        }

        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        onDispose {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        }
    }
}

private fun handleClipboardChange(clipboardManager: ClipboardManager) {
    val clip = clipboardManager.primaryClip ?: return
    if (clip.itemCount == 0) return

    val item = clip.getItemAt(0)
    val text = item.text?.toString() ?: return
    val htmlText = item.htmlText

    // Plain text with bullets/checkboxes but no HTML -> add HTML for pasting into other apps
    if (htmlText == null && containsFormattedContent(text)) {
        val html = convertToHtml(text)
        val newClip = ClipData.newHtmlText(clip.description.label, text, html)
        clipboardManager.setPrimaryClip(newClip)
    }
}

private fun containsFormattedContent(text: String): Boolean {
    return text.lines().any { line ->
        LinePrefixes.hasAnyPrefix(line)
    }
}

/**
 * Converts text with bullets and checkboxes to HTML.
 * Bullets (•) and checkboxes (☐/☑) become list items.
 * Indented items (with leading tabs) become nested sublists.
 */
fun convertToHtml(text: String): String {
    val lines = text.lines()
    val body = StringBuilder()
    var currentIndentLevel = -1 // -1 means not in a list

    for (line in lines) {
        val lineInfo = TextLineUtils.parseLine(line)
        val indentLevel = lineInfo.indentation.length

        if (lineInfo.prefix != null) {
            // Adjust nesting level
            while (currentIndentLevel < indentLevel) {
                body.append("<ul>")
                currentIndentLevel++
            }
            while (currentIndentLevel > indentLevel) {
                body.append("</ul>")
                currentIndentLevel--
            }

            val content = escapeHtml(lineInfo.content)
            val checkboxHtml = when (lineInfo.prefix) {
                LinePrefixes.CHECKBOX_CHECKED ->
                    """<input type="checkbox" checked disabled>"""
                LinePrefixes.CHECKBOX_UNCHECKED ->
                    """<input type="checkbox" disabled>"""
                else -> ""
            }
            body.append("<li>$checkboxHtml$content</li>")
        } else {
            // Close all open lists
            while (currentIndentLevel >= 0) {
                body.append("</ul>")
                currentIndentLevel--
            }

            if (line.isEmpty()) {
                body.append("<br>")
            } else {
                body.append("<p>${escapeHtml(line)}</p>")
            }
        }
    }

    // Close any remaining open lists
    while (currentIndentLevel >= 0) {
        body.append("</ul>")
        currentIndentLevel--
    }

    return body.toString()
}

private fun escapeHtml(text: String): String = HtmlEntities.escape(text)
