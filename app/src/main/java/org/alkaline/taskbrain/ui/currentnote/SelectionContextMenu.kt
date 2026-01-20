package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.alkaline.taskbrain.R

/**
 * Context menu actions that can be performed on selected text.
 */
data class SelectionMenuActions(
    val onCopy: () -> Unit,
    val onCut: () -> Unit,
    val onSelectAll: () -> Unit,
    val onUnselect: () -> Unit,
    val onDelete: () -> Unit
)

/**
 * Selection bounds for positioning the context menu.
 */
data class SelectionBounds(
    val startOffset: Offset,
    val endOffset: Offset,
    val startLineHeight: Float,
    val endLineHeight: Float
) {
    /** True if selection spans multiple lines */
    val isMultiline: Boolean get() = startOffset.y != endOffset.y

    /** Top Y coordinate of the selection */
    val topY: Float get() = minOf(startOffset.y, endOffset.y)

    /** Bottom Y coordinate of the selection (including line height) */
    val bottomY: Float get() = maxOf(startOffset.y + startLineHeight, endOffset.y + endLineHeight)

    /** Leftmost X coordinate of the selection */
    val leftX: Float get() = minOf(startOffset.x, endOffset.x)

    /** Rightmost X coordinate of the selection */
    val rightX: Float get() = maxOf(startOffset.x, endOffset.x)
}

// Approximate menu dimensions for positioning calculations
private val MENU_WIDTH = 150.dp
private val MENU_MARGIN = 8.dp

/**
 * Context menu displayed when text is selected.
 * Provides copy, cut, select all, unselect, and delete actions.
 *
 * Positioning strategy:
 * - Position to the left or right of the selection (whichever has more space)
 * - Multi-line selection: prefer right edge of screen (less text there typically)
 * - Vertically centered on the selection
 */
@Composable
fun SelectionContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    menuOffset: Offset,
    actions: SelectionMenuActions,
    selectionBounds: SelectionBounds? = null
) {
    val density = LocalDensity.current

    BoxWithConstraints {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val menuWidthPx = with(density) { MENU_WIDTH.toPx() }
        val marginPx = with(density) { MENU_MARGIN.toPx() }

        val offset = with(density) {
            if (selectionBounds != null) {
                // Calculate vertical position: centered on selection
                val centerY = (selectionBounds.topY + selectionBounds.bottomY) / 2
                val y = (centerY - 100f).coerceAtLeast(marginPx)  // Offset up a bit since menu expands down

                // Calculate horizontal position: left or right of selection
                val spaceOnRight = screenWidthPx - selectionBounds.rightX
                val spaceOnLeft = selectionBounds.leftX

                val x = if (selectionBounds.isMultiline) {
                    // Multi-line: prefer right edge (text typically doesn't extend there)
                    screenWidthPx - menuWidthPx - marginPx
                } else if (spaceOnRight >= menuWidthPx + marginPx * 2) {
                    // Enough space on right - position to the right of selection
                    selectionBounds.rightX + marginPx
                } else if (spaceOnLeft >= menuWidthPx + marginPx * 2) {
                    // Enough space on left - position to the left of selection
                    selectionBounds.leftX - menuWidthPx - marginPx
                } else {
                    // Not enough space on either side - position at right edge
                    screenWidthPx - menuWidthPx - marginPx
                }

                DpOffset(x.toDp(), y.toDp())
            } else {
                // Fallback: use provided offset (legacy behavior)
                DpOffset(
                    menuOffset.x.toDp(),
                    menuOffset.y.toDp() - 48.dp
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            // Don't take focus so keyboard stays open
            properties = PopupProperties(focusable = false)
        ) {
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_copy),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = actions.onCopy
        )
        DropdownMenuItem(
            text = { Text("Cut") },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cut),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = actions.onCut
        )
        DropdownMenuItem(
            text = { Text("Select All") },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_select_all),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = actions.onSelectAll
        )
        DropdownMenuItem(
            text = { Text("Unselect") },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_deselect),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = actions.onUnselect
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = actions.onDelete
        )
        }
    }
}
