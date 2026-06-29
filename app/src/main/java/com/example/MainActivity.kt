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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthManager
import com.example.ui.SignInScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MeetingViewModel by viewModels()
    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speechRecognizerManager = SpeechRecognizerManager(this)
        authManager = AuthManager(this)

        setContent {
            MyApplicationTheme {
                var signedIn by remember { mutableStateOf(authManager.isSignedIn()) }
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { signedIn = it.currentUser != null }
                    Firebase.auth.addAuthStateListener(listener)
                    onDispose { Firebase.auth.removeAuthStateListener(listener) }
                }
                val scope = rememberCoroutineScope()
                val signInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    scope.launch { 
                        authManager.handleSignInResult(result.data).onFailure { error ->
                            android.widget.Toast.makeText(this@MainActivity, "Giriş hatası: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }

                if (!signedIn) {
                    SignInScreen(onSignIn = { signInLauncher.launch(authManager.signInIntent()) })
                    return@MyApplicationTheme
                }

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
                        val upcomingMeetings by viewModel.upcomingMeetings.collectAsStateWithLifecycle()
                        val pastMeetings by viewModel.pastMeetings.collectAsStateWithLifecycle()
                        val selectedMeeting by viewModel.selectedMeeting.collectAsStateWithLifecycle()
                        
                        RecordAudioScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onSaveTranscript = { title, content, lat, lng ->
                                viewModel.addTranscriptNote(title, content, lat, lng)
                            },
                            speechRecognizerManager = speechRecognizerManager,
                            upcomingMeetings = upcomingMeetings,
                            pastMeetings = pastMeetings,
                            selectedMeeting = selectedMeeting,
                            onMeetingSelected = { viewModel.selectMeeting(it) }
                        )
                    }
                }
            }
        }
    }
}
