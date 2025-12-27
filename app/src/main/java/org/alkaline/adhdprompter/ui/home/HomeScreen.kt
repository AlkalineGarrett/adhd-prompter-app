package org.alkaline.adhdprompter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.alkaline.adhdprompter.R

@Composable
fun HomeScreen(homeViewModel: HomeViewModel = viewModel()) {
    val saveStatus by homeViewModel.saveStatus.observeAsState()
    val loadStatus by homeViewModel.loadStatus.observeAsState()
    
    // State to hold the user content
    var userContent by remember { mutableStateOf("") }
    // State to track if the content is saved
    var isSaved by remember { mutableStateOf(true) }
    
    // State for agent command (placeholder for now)
    var agentCommand by remember { mutableStateOf("") }

    // Handle initial data loading
    LaunchedEffect(Unit) {
        homeViewModel.loadContent()
    }
    
    // Update content when loaded from VM
    LaunchedEffect(loadStatus) {
        if (loadStatus is LoadStatus.Success) {
            userContent = (loadStatus as LoadStatus.Success).content
            isSaved = true
        }
    }

    // Handle save status changes
    LaunchedEffect(saveStatus) {
        if (saveStatus is SaveStatus.Success) {
            isSaved = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status Bar
        StatusBar(
            isSaved = isSaved,
            onSaveClick = {
                homeViewModel.saveContent(userContent)
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
                .fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            )
        )

        // Agent Chat Label
        Text(
            text = stringResource(id = R.string.agent_chat_label),
            color = Color.Black,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFAED581))
                .padding(4.dp)
        )

        // Agent Command Text Area
        TextField(
            value = agentCommand,
            onValueChange = { agentCommand = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp), // Approx 5 lines
             colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            maxLines = 5,
            minLines = 5
        )
    }
}

@Composable
fun StatusBar(
    isSaved: Boolean,
    onSaveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSaveClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_save),
                contentDescription = stringResource(id = R.string.action_save),
                tint = Color.Black
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = if (isSaved) stringResource(id = R.string.status_saved) else stringResource(id = R.string.status_unsaved),
            color = Color.Black,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            painter = if (isSaved) painterResource(id = R.drawable.ic_check_circle) else painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = if (isSaved) Color(0xFF4CAF50) else Color(0xFFFFC107),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}