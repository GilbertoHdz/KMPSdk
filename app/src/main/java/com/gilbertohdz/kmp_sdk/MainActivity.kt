package com.gilbertohdz.kmp_sdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gilbertohdz.kmp_sdk.ui.theme.KMPSdkTheme
import com.gilbertohdz.sdk.model.Post
import com.gilbertohdz.sdk.model.User

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KMPSdkTheme {
                OtelDemoScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtelDemoScreen(viewModel: SdkViewModel = viewModel()) {
    val usersState by viewModel.usersState.collectAsState()
    val postState by viewModel.postState.collectAsState()
    val errorState by viewModel.errorState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("KMP SDK + OpenTelemetry") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // --- Button 1: GET Users ---
            Button(
                onClick = { viewModel.getUsers() },
                modifier = Modifier.fillMaxWidth(),
                enabled = usersState !is UiState.Loading
            ) {
                Text("GET /users")
            }

            StateCard(state = usersState) { users ->
                users.forEach { user ->
                    Text(
                        text = "[${user.id}] ${user.name} — ${user.email}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // --- Button 2: POST Post ---
            Button(
                onClick = { viewModel.createPost() },
                modifier = Modifier.fillMaxWidth(),
                enabled = postState !is UiState.Loading
            ) {
                Text("POST /posts")
            }

            StateCard(state = postState) { post ->
                Text("id: ${post.id}", fontFamily = FontFamily.Monospace)
                Text("title: ${post.title}", fontFamily = FontFamily.Monospace)
                Text("userId: ${post.userId}", fontFamily = FontFamily.Monospace)
            }

            // --- Button 3: GET Error (404) ---
            Button(
                onClick = { viewModel.getPostWithError() },
                modifier = Modifier.fillMaxWidth(),
                enabled = errorState !is UiState.Loading,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("GET /posts/99999 (Error 404)")
            }

            StateCard(state = errorState) { post ->
                Text("id: ${post.id}", fontFamily = FontFamily.Monospace)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Revisa Logcat (tag: LoggingSpanExporter) para ver los spans",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun <T> StateCard(state: UiState<T>, content: @Composable (T) -> Unit) {
    when (state) {
        is UiState.Idle -> Unit
        is UiState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Error: ${state.message}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp)
            )
        }
        is UiState.Success -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                content(state.data)
            }
        }
    }
}