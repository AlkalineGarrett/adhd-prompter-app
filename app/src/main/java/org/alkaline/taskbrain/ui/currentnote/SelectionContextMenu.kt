package org.alkaline.taskbrain.ui.currentnote

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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
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
 * Context menu displayed when text is selected.
 * Provides copy, cut, select all, unselect, and delete actions.
 */
@Composable
fun SelectionContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    menuOffset: Offset,
    actions: SelectionMenuActions
) {
    val density = LocalDensity.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = with(density) {
            DpOffset(
                menuOffset.x.toDp(),
                menuOffset.y.toDp() - 48.dp
            )
        }
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
