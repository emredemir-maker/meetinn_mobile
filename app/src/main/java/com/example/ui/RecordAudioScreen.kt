package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.SpeechRecognizerManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAudioScreen(
    onNavigateBack: () -> Unit,
    onSaveTranscript: (String, String) -> Unit,
    speechRecognizerManager: SpeechRecognizerManager
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    
    val isListening by speechRecognizerManager.isListening.collectAsStateWithLifecycle()
    val transcription by speechRecognizerManager.transcription.collectAsStateWithLifecycle()
    val error by speechRecognizerManager.error.collectAsStateWithLifecycle()
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizerManager.stopListening()
            speechRecognizerManager.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canlı Transkript") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Toplantı / Kayıt Başlığı") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_title_input"),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Transcription Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (transcription.isEmpty() && !isListening) {
                    Text(
                        text = "Konuşulanlar burada görünecek...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Text(
                        text = transcription,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Mikrofon İzni Ver")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Kayıt yapabilmek için mikrofon izni gereklidir.", style = MaterialTheme.typography.bodySmall)
            } else {
                FloatingActionButton(
                    onClick = {
                        if (isListening) {
                            speechRecognizerManager.stopListening()
                        } else {
                            if (transcription.isNotEmpty()) {
                                speechRecognizerManager.clear()
                            }
                            speechRecognizerManager.startListening()
                        }
                    },
                    modifier = Modifier.size(80.dp),
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Dinlemeyi Durdur" else "Dinlemeye Başla",
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(if (isListening) "Dinleniyor..." else "Başlamak İçin Dokunun")
                
                if (transcription.isNotEmpty() && !isListening) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onSaveTranscript(title.ifBlank { "İsimsiz Toplantı" }, transcription)
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Transkripti Kaydet")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
