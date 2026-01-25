package org.alkaline.taskbrain.dsl

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A chip that displays a directive's execution result.
 *
 * When collapsed, shows only the result value.
 * When expanded, also shows the source directive text.
 */
@Composable
fun DirectiveChip(
    result: DirectiveResult,
    sourceText: String,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        result.error != null -> ErrorChipBackground
        else -> SuccessChipBackground
    }

    val contentColor = when {
        result.error != null -> ErrorChipContent
        else -> SuccessChipContent
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(ChipCornerRadius))
            .background(backgroundColor)
            .clickable { onToggleCollapsed() }
            .animateContentSize()
            .padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding)
    ) {
        // Result row (always visible)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDisplayText(result),
                color = contentColor,
                fontSize = ChipTextSize,
                maxLines = if (collapsed) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = if (collapsed) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.Default.KeyboardArrowUp
                },
                contentDescription = if (collapsed) "Expand" else "Collapse",
                tint = contentColor,
                modifier = Modifier.size(ChipIconSize)
            )
        }

        // Source text (visible when expanded)
        if (!collapsed) {
            Text(
                text = sourceText,
                color = contentColor.copy(alpha = 0.7f),
                fontSize = ChipSourceTextSize,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatDisplayText(result: DirectiveResult): String {
    return when {
        result.error != null -> "Error: ${result.error}"
        result.result != null -> result.toValue()?.toDisplayString() ?: "null"
        else -> "..."
    }
}

// Chip styling constants
private val ChipCornerRadius = 6.dp
private val ChipHorizontalPadding = 8.dp
private val ChipVerticalPadding = 4.dp
private val ChipTextSize = 14.sp
private val ChipSourceTextSize = 12.sp
private val ChipIconSize = 16.dp

// Colors
private val SuccessChipBackground = Color(0xFFE8F5E9) // Light green
private val SuccessChipContent = Color(0xFF2E7D32) // Dark green
private val ErrorChipBackground = Color(0xFFFFEBEE) // Light red
private val ErrorChipContent = Color(0xFFC62828) // Dark red
