package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.audio.SpeechRecognizerManager
import com.example.ui.AddTextNoteScreen
import com.example.ui.MeetingViewModel
import com.example.ui.NoteListScreen
import com.example.ui.RecordAudioScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MeetingViewModel by viewModels()
    private lateinit var speechRecognizerManager: SpeechRecognizerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speechRecognizerManager = SpeechRecognizerManager(this)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "list") {
                    composable("list") {
                        NoteListScreen(
                            viewModel = viewModel,
                            onAddTextNoteClick = { navController.navigate("add_text") },
                            onRecordAudioClick = { navController.navigate("record_audio") },
                            onPlayAudio = { _ -> }
                        )
                    }
                    composable("add_text") {
                        AddTextNoteScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveNote = { title, content, lat, lng ->
                                viewModel.addTextNote(title, content, lat, lng)
                            }
                        )
                    }
                    composable("record_audio") {
                        RecordAudioScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveTranscript = { title, content, lat, lng ->
                                viewModel.addTranscriptNote(title, content, lat, lng)
                            },
                            speechRecognizerManager = speechRecognizerManager
                        )
                    }
                }
            }
        }
    }
}
