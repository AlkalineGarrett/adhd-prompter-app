package org.alkaline.taskbrain.ui.notelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.alkaline.taskbrain.data.Note

@Composable
fun NoteListScreen(
    noteListViewModel: NoteListViewModel = viewModel(),
    onNoteClick: (String) -> Unit = {}
) {
    val notes by noteListViewModel.notes.observeAsState(emptyList())
    val loadStatus by noteListViewModel.loadStatus.observeAsState()

    LaunchedEffect(Unit) {
        noteListViewModel.loadNotes()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (loadStatus) {
            is LoadStatus.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is LoadStatus.Error -> {
                Text(
                    text = (loadStatus as LoadStatus.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                if (notes.isEmpty() && loadStatus is LoadStatus.Success) {
                    Text(
                        text = "No notes found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(notes) { note ->
                            NoteItem(note = note, onClick = { onNoteClick(note.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItem(note: Note, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        // Display only the first line of the note
        val firstLine = note.content.lines().firstOrNull() ?: ""
        
        Text(
            text = firstLine.ifEmpty { "Empty Note" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
