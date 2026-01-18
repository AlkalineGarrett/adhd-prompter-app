package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.Dimens
import org.alkaline.taskbrain.ui.components.ActionButtonBar
import org.alkaline.taskbrain.ui.components.ErrorDialog

@Composable
fun CurrentNoteScreen(
    noteId: String? = null,
    currentNoteViewModel: CurrentNoteViewModel = viewModel()
) {
    val saveStatus by currentNoteViewModel.saveStatus.observeAsState()
    val loadStatus by currentNoteViewModel.loadStatus.observeAsState()
    val contentModified by currentNoteViewModel.contentModified.observeAsState(false)
    val isAgentProcessing by currentNoteViewModel.isAgentProcessing.observeAsState(false)
    
    // State to hold the user content
    var userContent by remember { mutableStateOf("") }
    // State to track if the content is saved
    var isSaved by remember { mutableStateOf(true) }
    
    // State for agent command (placeholder for now)
    var agentCommand by remember { mutableStateOf("") }

    // State for agent section expansion (collapsed by default)
    var isAgentSectionExpanded by remember { mutableStateOf(false) }

    // Track if agent text field has ever been focused (to avoid collapsing on initial render)
    var agentFieldHasBeenFocused by remember { mutableStateOf(false) }

    // Focus requester for main content text field
    val mainContentFocusRequester = remember { FocusRequester() }

    // Reset focus tracking when section collapses
    LaunchedEffect(isAgentSectionExpanded) {
        if (!isAgentSectionExpanded) {
            agentFieldHasBeenFocused = false
        }
    }

    // Handle initial data loading
    LaunchedEffect(noteId) {
        currentNoteViewModel.loadContent(noteId)
    }
    
    // Update content when loaded from VM
    LaunchedEffect(loadStatus) {
        if (loadStatus is LoadStatus.Success) {
            userContent = (loadStatus as LoadStatus.Success).content
        }
    }
    
    // React to content modification signal (e.g. from Agent)
    LaunchedEffect(contentModified) {
        if (contentModified) {
            isSaved = false
        }
    }

    // Handle save status changes
    LaunchedEffect(saveStatus) {
        if (saveStatus is SaveStatus.Success) {
            isSaved = true
            currentNoteViewModel.markAsSaved()
        }
    }

    // Show error dialogs
    if (saveStatus is SaveStatus.Error) {
        ErrorDialog(
            title = "Save Error",
            throwable = (saveStatus as SaveStatus.Error).throwable,
            onDismiss = { currentNoteViewModel.clearSaveError() }
        )
    }

    if (loadStatus is LoadStatus.Error) {
        ErrorDialog(
            title = "Load Error",
            throwable = (loadStatus as LoadStatus.Error).throwable,
            onDismiss = { currentNoteViewModel.clearLoadError() }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status Bar
        StatusBar(
            isSaved = isSaved,
            onSaveClick = {
                currentNoteViewModel.saveContent(userContent)
            }
        )

        // User Content Area
        TextField(
            value = userContent,
            onValueChange = {
                userContent = it
                if (isSaved) isSaved = false
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(mainContentFocusRequester),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            )
        )

        // Agent Chat Label Bar (clickable to expand)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.brand_color))
                .clickable { isAgentSectionExpanded = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.agent_chat_label),
                color = colorResource(R.color.brand_text_color),
                fontSize = 18.sp
            )
            // Down arrow to manually collapse
            if (isAgentSectionExpanded) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = colorResource(R.color.brand_text_color),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Transfer focus to main content to keep keyboard open
                            mainContentFocusRequester.requestFocus()
                            isAgentSectionExpanded = false
                        }
                )
            }
        }

        // Processing Indicator Bar
        if (isAgentProcessing) {
            ProcessingIndicatorBar()
        }

        // Agent Command Text Area (collapsible)
        if (isAgentSectionExpanded) {
            TextField(
                value = agentCommand,
                onValueChange = { agentCommand = it },
                enabled = !isAgentProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp) // Approx 5 lines
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            agentFieldHasBeenFocused = true
                        } else if (agentFieldHasBeenFocused && agentCommand.isBlank()) {
                            // Only collapse when losing focus if it was previously focused
                            isAgentSectionExpanded = false
                        }
                    },
                trailingIcon = {
                     IconButton(
                         enabled = !isAgentProcessing && agentCommand.isNotBlank(),
                         onClick = {
                             if (agentCommand.isNotBlank()) {
                                 currentNoteViewModel.processAgentCommand(userContent, agentCommand)
                                 agentCommand = ""
                             }
                         }
                     ) {
                         Icon(
                             imageVector = Icons.AutoMirrored.Filled.Send,
                             contentDescription = "Send",
                             tint = if (isAgentProcessing || agentCommand.isBlank()) Color.Gray else colorResource(R.color.brand_color)
                         )
                     }
                },
                 colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (isAgentProcessing) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent,
                    unfocusedContainerColor = if (isAgentProcessing) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent,
                    disabledContainerColor = Color.LightGray.copy(alpha = 0.3f)
                ),
                maxLines = 5,
                minLines = 5
            )
        }
    }
}

@Composable
fun ProcessingIndicatorBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "processing_animation")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_offset"
    )
    
    val brush = Brush.linearGradient(
        colors = listOf(
            colorResource(R.color.brand_color),
            Color.White,
            colorResource(R.color.brand_color)
        ),
        start = Offset(x = offset * 1000f, y = 0f),
        end = Offset(x = offset * 1000f + 500f, y = 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(brush)
    )
}

@Composable
fun StatusBar(
    isSaved: Boolean,
    onSaveClick: () -> Unit
) {
    ActionButtonBar {
        // Save Button (custom ActionButton instead of IconButton)
        androidx.compose.material3.Button(
            onClick = onSaveClick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.action_button_background),
                contentColor = Color.White
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.StatusBarButtonCornerRadius),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.StatusBarButtonHorizontalPadding, vertical = 0.dp),
            modifier = Modifier.height(Dimens.StatusBarButtonHeight)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_save),
                contentDescription = stringResource(id = R.string.action_save),
                modifier = Modifier.size(Dimens.StatusBarButtonIconSize),
                tint = Color.White
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Dimens.StatusBarButtonIconTextSpacing))
            Text(
                text = stringResource(id = R.string.action_save),
                fontSize = Dimens.StatusBarButtonTextSize
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Dimens.StatusBarItemSpacing))

        Text(
            text = if (isSaved) stringResource(id = R.string.status_saved) else stringResource(id = R.string.status_unsaved),
            color = Color.Black,
            fontSize = Dimens.StatusTextSize
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Dimens.StatusTextIconSpacing))

        Icon(
            painter = if (isSaved) painterResource(id = R.drawable.ic_check_circle) else painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = if (isSaved) Color(0xFF4CAF50) else Color(0xFFFFC107),
            modifier = Modifier.size(Dimens.StatusIconSize)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CurrentNoteScreenPreview() {
    CurrentNoteScreen()
}
