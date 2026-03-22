package org.alkaline.taskbrain.ui.notelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.ui.components.ActionButton
import org.alkaline.taskbrain.ui.components.ActionButtonBar
import org.alkaline.taskbrain.ui.components.ErrorDialog
import android.text.format.DateFormat
import java.util.Date

@Composable
fun NoteListScreen(
    noteListViewModel: NoteListViewModel = viewModel(),
    onNoteClick: (String) -> Unit = {},
    onSaveCompleted: SharedFlow<Unit>? = null
) {
    val notes by noteListViewModel.notes.observeAsState(emptyList())
    val deletedNotes by noteListViewModel.deletedNotes.observeAsState(emptyList())
    val loadStatus by noteListViewModel.loadStatus.observeAsState()
    val createNoteStatus by noteListViewModel.createNoteStatus.observeAsState()

    LaunchedEffect(Unit) {
        noteListViewModel.loadNotes()
    }

    // Refresh when a note is saved from CurrentNoteScreen (silent refresh, no loading indicator)
    LaunchedEffect(onSaveCompleted) {
        onSaveCompleted?.collect {
            noteListViewModel.refreshNotes()
        }
    }

    // Show error dialogs
    if (loadStatus is LoadStatus.Error) {
        ErrorDialog(
            title = stringResource(R.string.error_load),
            throwable = (loadStatus as LoadStatus.Error).throwable,
            onDismiss = { noteListViewModel.clearLoadError() }
        )
    }

    if (createNoteStatus is CreateNoteStatus.Error) {
        ErrorDialog(
            title = stringResource(R.string.error_create_note),
            throwable = (createNoteStatus as CreateNoteStatus.Error).throwable,
            onDismiss = { noteListViewModel.clearCreateNoteError() }
        )
    }

    Scaffold(
        topBar = {
            NoteListTopBar(
                onAddNoteClick = {
                    noteListViewModel.createNote(onSuccess = { noteId ->
                        onNoteClick(noteId)
                    })
                },
                onRefreshClick = { noteListViewModel.loadNotes() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (loadStatus) {
                is LoadStatus.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is LoadStatus.Error -> {
                    // Error dialog is shown above, just show a simple message here
                    Text(
                        text = stringResource(R.string.error_an_error_occurred),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    if (notes.isEmpty() && deletedNotes.isEmpty() && loadStatus is LoadStatus.Success) {
                        Text(
                            text = stringResource(R.string.no_notes_found),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notes) { note ->
                                NoteItem(
                                    note = note,
                                    onClick = { onNoteClick(note.id) },
                                    onDelete = { noteListViewModel.softDeleteNote(note.id) }
                                )
                                HorizontalDivider()
                            }

                            if (deletedNotes.isNotEmpty()) {
                                item {
                                    DeletedNotesHeader()
                                }
                                items(deletedNotes) { note ->
                                    NoteItem(
                                        note = note,
                                        onClick = { onNoteClick(note.id) },
                                        isDeleted = true,
                                        onRestore = { noteListViewModel.undeleteNote(note.id) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
            
            // Show loading overlay when creating a note
            if (createNoteStatus is CreateNoteStatus.Loading) {
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun NoteListTopBar(onAddNoteClick: () -> Unit, onRefreshClick: () -> Unit) {
    ActionButtonBar(
        modifier = Modifier,
        content = {
            ActionButton(
                text = stringResource(R.string.action_add_note),
                icon = Icons.Filled.Add,
                onClick = onAddNoteClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            ActionButton(
                text = stringResource(R.string.action_refresh),
                icon = Icons.Filled.Refresh,
                onClick = onRefreshClick
            )
        }
    )
}

@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit,
    isDeleted: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val firstLine = note.content.lines().firstOrNull() ?: ""

    val timestamp = note.lastAccessedAt ?: note.updatedAt

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = firstLine.ifEmpty { stringResource(R.string.empty_note) },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isDeleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (timestamp != null) {
            Text(
                text = formatTimestamp(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    modifier = Modifier.size(20.dp),
                    tint = colorResource(R.color.icon_default)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (isDeleted && onRestore != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_restore_note)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_restore),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRestore()
                        }
                    )
                } else if (!isDeleted && onDelete != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.action_delete_note),
                                color = colorResource(R.color.menu_danger_text)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = colorResource(R.color.menu_danger_text)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeletedNotesHeader() {
    Text(
        text = stringResource(R.string.section_deleted_notes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun formatTimestamp(timestamp: Timestamp): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val date = timestamp.toDate()
    val dateStr = DateFormat.getMediumDateFormat(context).format(date)
    val timeStr = DateFormat.getTimeFormat(context).format(date)
    return "$dateStr, $timeStr"
}
