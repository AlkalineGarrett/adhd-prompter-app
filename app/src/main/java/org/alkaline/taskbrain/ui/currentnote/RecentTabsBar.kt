package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.RecentTab

/**
 * A horizontal scrollable bar showing recently accessed note tabs.
 * Tabs are ordered by most recently accessed (left = most recent).
 */
@Composable
fun RecentTabsBar(
    tabs: List<RecentTab>,
    currentNoteId: String,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Don't show if no tabs
    if (tabs.isEmpty()) {
        return
    }

    LazyRow(
        modifier = modifier
            .background(TabBarBackgroundColor)
            .padding(horizontal = TabBarHorizontalPadding, vertical = TabBarVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TabSpacing)
    ) {
        items(
            items = tabs,
            key = { tab -> tab.noteId }
        ) { tab ->
            val isCurrentTab = tab.noteId == currentNoteId
            TabItem(
                tab = tab,
                isCurrentTab = isCurrentTab,
                onClick = { if (!isCurrentTab) onTabClick(tab.noteId) },
                onClose = { onTabClose(tab.noteId) },
                modifier = Modifier.animateItem(
                    placementSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    fadeInSpec = tween(durationMillis = 200),
                    fadeOutSpec = tween(durationMillis = 200)
                )
            )
        }
    }
}

@Composable
private fun TabItem(
    tab: RecentTab,
    isCurrentTab: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isCurrentTab) {
        colorResource(R.color.action_button_background)
    } else {
        TabInactiveBackgroundColor
    }
    val textColor = if (isCurrentTab) Color.White else Color.Black

    Box(
        modifier = modifier
            .height(TabHeight)
            .widthIn(max = TabMaxWidth)
            .clip(RoundedCornerShape(TabCornerRadius))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(start = TabContentPaddingStart, end = TabContentPaddingEnd),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tab.displayText.ifEmpty { "New Note" },
                color = textColor,
                fontSize = TabTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Close tab",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .offset(x = (-4).dp)
                    .size(CloseIconSize)
                    .clickable(onClick = onClose)
            )
        }
    }
}

// Tab bar styling constants
private val TabBarBackgroundColor = Color(0xFFF5F5F5)
private val TabInactiveBackgroundColor = Color(0xFFE0E0E0)
private val TabBarHorizontalPadding = 8.dp
private val TabBarVerticalPadding = 4.dp
private val TabSpacing = 4.dp
private val TabHeight = 28.dp
private val TabMaxWidth = 72.dp
private val TabCornerRadius = 4.dp
private val TabTextSize = 12.sp
private val TabContentPaddingStart = 4.dp
private val TabContentPaddingEnd = 2.dp
private val CloseIconSize = 12.dp
