package org.alkaline.taskbrain.ui.notelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.ui.components.ActionButton
import org.alkaline.taskbrain.ui.components.ActionButtonBar
import org.alkaline.taskbrain.ui.components.ErrorDialog

@Composable
fun NoteListScreen(
    noteListViewModel: NoteListViewModel = viewModel(),
    onNoteClick: (String) -> Unit = {}
) {
    val notes by noteListViewModel.notes.observeAsState(emptyList())
    val deletedNotes by noteListViewModel.deletedNotes.observeAsState(emptyList())
    val loadStatus by noteListViewModel.loadStatus.observeAsState()
    val createNoteStatus by noteListViewModel.createNoteStatus.observeAsState()

    LaunchedEffect(Unit) {
        noteListViewModel.loadNotes()
    }

    // Show error dialogs
    if (loadStatus is LoadStatus.Error) {
        ErrorDialog(
            title = "Load Error",
            throwable = (loadStatus as LoadStatus.Error).throwable,
            onDismiss = { noteListViewModel.clearLoadError() }
        )
    }

    if (createNoteStatus is CreateNoteStatus.Error) {
        ErrorDialog(
            title = "Create Note Error",
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
                }
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
                        text = "An error occurred",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    if (notes.isEmpty() && deletedNotes.isEmpty() && loadStatus is LoadStatus.Success) {
                        Text(
                            text = "No notes found",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notes) { note ->
                                NoteItem(note = note, onClick = { onNoteClick(note.id) })
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
                                        isDeleted = true
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
fun NoteListTopBar(onAddNoteClick: () -> Unit) {
    ActionButtonBar(
        modifier = Modifier,
        content = {
            ActionButton(
                text = stringResource(R.string.action_add_note),
                icon = Icons.Filled.Add,
                onClick = onAddNoteClick
            )
        }
    )
}

@Composable
fun NoteItem(note: Note, onClick: () -> Unit, isDeleted: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        // Display only the first line of the note
        val firstLine = note.content.lines().firstOrNull() ?: ""

        Text(
            text = firstLine.ifEmpty { "Empty Note" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isDeleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )
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
