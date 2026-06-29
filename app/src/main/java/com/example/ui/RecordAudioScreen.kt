package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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

import com.example.network.MeetingDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAudioScreen(
    onNavigateBack: () -> Unit,
    onSaveTranscript: (String, String, Double?, Double?) -> Unit,
    speechRecognizerManager: SpeechRecognizerManager,
    upcomingMeetings: List<MeetingDto>,
    pastMeetings: List<MeetingDto>,
    selectedMeeting: MeetingDto?,
    onMeetingSelected: (MeetingDto?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(selectedMeeting?.title ?: "") }
    
    // Update title when selectedMeeting changes
    LaunchedEffect(selectedMeeting) {
        if (selectedMeeting != null) {
            title = selectedMeeting.title
        }
    }
    
    var expanded by remember { mutableStateOf(false) }
    var showMeetingModal by remember { mutableStateOf(false) }
    
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

    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false)
        
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        lat = it.latitude
                        lng = it.longitude
                    }
                }
            } catch (e: SecurityException) {}
        }
    }

    LaunchedEffect(Unit) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineLocationGranted || coarseLocationGranted) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        lat = it.latitude
                        lng = it.longitude
                    }
                }
            } catch (e: SecurityException) {}
        }
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
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedMeeting?.title ?: "Yeni Toplantı (İlişkilendirilmemiş)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("İlişkili Toplantı") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Yeni Toplantı (İlişkilendirilmemiş)") },
                        onClick = {
                            onMeetingSelected(null)
                            expanded = false
                        }
                    )
                    upcomingMeetings.forEach { meeting ->
                        DropdownMenuItem(
                            text = { Text(meeting.title) },
                            onClick = {
                                onMeetingSelected(meeting)
                                expanded = false
                            }
                        )
                    }
                    if (pastMeetings.isNotEmpty()) {
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Geçmiş Toplantılar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) },
                            onClick = { }
                        )
                        pastMeetings.forEach { meeting ->
                            DropdownMenuItem(
                                text = { Text(meeting.title) },
                                onClick = {
                                    onMeetingSelected(meeting)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

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
                Button(onClick = { 
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text("İzin Ver")
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
                            if (selectedMeeting == null && upcomingMeetings.isNotEmpty()) {
                                showMeetingModal = true
                            } else {
                                speechRecognizerManager.startListening()
                            }
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
                            onSaveTranscript(title.ifBlank { "İsimsiz Toplantı" }, transcription, lat, lng)
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
    
    if (showMeetingModal) {
        AlertDialog(
            onDismissRequest = { 
                showMeetingModal = false
            },
            title = { Text("Toplantı Seçin") },
            text = {
                Column {
                    Text("Bu kaydı yaklaşan bir toplantı ile ilişkilendirebilirsiniz:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(upcomingMeetings, key = { it.id }) { meeting ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onMeetingSelected(meeting)
                                        showMeetingModal = false
                                        speechRecognizerManager.startListening()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(meeting.title, style = MaterialTheme.typography.bodyLarge)
                                    if (meeting.date.isNotBlank()) {
                                        val displayDate = try {
                                            var parsedDate: java.util.Date? = null
                                            val formats = listOf(
                                                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                                                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                                                "yyyy-MM-dd'T'HH:mm:ss",
                                                "yyyy-MM-dd'T'HH:mm",
                                                "yyyy-MM-dd"
                                            )
                                            for (fmt in formats) {
                                                try {
                                                    val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                                                    if (fmt.endsWith("'Z'")) {
                                                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    }
                                                    parsedDate = sdf.parse(meeting.date)
                                                    if (parsedDate != null) break
                                                } catch (e: Exception) {}
                                            }
                                            if (parsedDate != null) {
                                                val sdfOut = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                                sdfOut.format(parsedDate)
                                            } else {
                                                meeting.date
                                            }
                                        } catch (e: Exception) {
                                            meeting.date
                                        }
                                        Text(displayDate, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMeetingModal = false
                    speechRecognizerManager.startListening()
                }) {
                    Text("İlişkilendirmeden Devam Et")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMeetingModal = false
                }) {
                    Text("İptal")
                }
            }
        )
    }
}
