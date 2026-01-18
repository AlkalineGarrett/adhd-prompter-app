package org.alkaline.taskbrain.ui.currentnote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import android.text.Html

private const val BULLET_PREFIX = "• "
private const val CHECKBOX_UNCHECKED_PREFIX = "☐ "
private const val CHECKBOX_CHECKED_PREFIX = "☑ "

/**
 * Composable that monitors the clipboard and converts plain text with bullets/checkboxes
 * to HTML format for better compatibility when pasting into apps like Google Docs.
 */
@Composable
fun ClipboardHtmlConverter() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange(clipboardManager)
        }

        clipboardManager.addPrimaryClipChangedListener(listener)

        onDispose {
            clipboardManager.removePrimaryClipChangedListener(listener)
        }
    }
}

private fun handleClipboardChange(clipboardManager: ClipboardManager) {
    val clip = clipboardManager.primaryClip ?: return
    if (clip.itemCount == 0) return

    val item = clip.getItemAt(0)
    val text = item.text?.toString() ?: return

    // Skip if already has HTML (we already processed it, or it came from another source)
    if (item.htmlText != null) return

    // Check if text contains our formatting
    if (!containsFormattedContent(text)) return

    // Convert to HTML and update clipboard
    val html = convertToHtml(text)
    val newClip = ClipData.newHtmlText(clip.description.label, text, html)
    clipboardManager.setPrimaryClip(newClip)
}

private fun containsFormattedContent(text: String): Boolean {
    return text.lines().any { line ->
        line.startsWith(BULLET_PREFIX) ||
        line.startsWith(CHECKBOX_UNCHECKED_PREFIX) ||
        line.startsWith(CHECKBOX_CHECKED_PREFIX)
    }
}

/**
 * Converts text with bullets and checkboxes to HTML.
 * - Bullets (•) and checkboxes (☐/☑) both become <ul><li> elements
 * - Consecutive formatted lines are grouped into the same list
 */
fun convertToHtml(text: String): String {
    val lines = text.lines()
    val body = StringBuilder()
    var inList = false

    for (line in lines) {
        when {
            line.startsWith(BULLET_PREFIX) -> {
                if (!inList) {
                    body.append("<ul>")
                    inList = true
                }
                val content = escapeHtml(line.removePrefix(BULLET_PREFIX))
                body.append("<li>$content</li>")
            }
            line.startsWith(CHECKBOX_UNCHECKED_PREFIX) || line.startsWith(CHECKBOX_CHECKED_PREFIX) -> {
                if (!inList) {
                    body.append("<ul>")
                    inList = true
                }
                // Treat checkboxes as bullets - strip the checkbox prefix
                val prefix = if (line.startsWith(CHECKBOX_CHECKED_PREFIX)) CHECKBOX_CHECKED_PREFIX else CHECKBOX_UNCHECKED_PREFIX
                val content = escapeHtml(line.removePrefix(prefix))
                body.append("<li>$content</li>")
            }
            else -> {
                if (inList) {
                    body.append("</ul>")
                    inList = false
                }

                if (line.isEmpty()) {
                    body.append("<br>")
                } else {
                    body.append("<p>${escapeHtml(line)}</p>")
                }
            }
        }
    }

    if (inList) {
        body.append("</ul>")
    }

    return body.toString()
}

private fun escapeHtml(text: String): String {
    return Html.escapeHtml(text)
}
