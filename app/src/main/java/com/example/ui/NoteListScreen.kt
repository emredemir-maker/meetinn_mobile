package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.platform.LocalContext
import com.example.utils.ReminderManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Note
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: MeetingViewModel,
    onAddTextNoteClick: () -> Unit,
    onRecordAudioClick: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onMeetingClick: (com.example.network.MeetingDto) -> Unit
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val upcomingMeetings by viewModel.upcomingMeetings.collectAsStateWithLifecycle()
    val pastMeetings by viewModel.pastMeetings.collectAsStateWithLifecycle()
    val selectedMeeting by viewModel.selectedMeeting.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val pendingSummary by viewModel.pendingSummary.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val reminderManager = remember { ReminderManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var meetingToRemind by remember { mutableStateOf<com.example.network.MeetingDto?>(null) }
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            meetingToRemind?.let { 
                reminderManager.scheduleMeetingReminder(it) 
                meetingToRemind = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Hatırlatıcı ayarlandı")
                }
            }
        }
    }

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    if (pendingSummary != null) {
        var editedSummary by remember(pendingSummary) { mutableStateOf(pendingSummary ?: "") }
        
        AlertDialog(
            onDismissRequest = { viewModel.discardSummary() },
            title = { Text("Toplantı Özeti") },
            text = {
                OutlinedTextField(
                    value = editedSummary,
                    onValueChange = { editedSummary = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 400.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveSummary(editedSummary) }) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.discardSummary() }) {
                    Text("İptal")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "Meeting AI", 
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Dashboard", 
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.summarizeMeeting() }) {
                            Icon(Icons.Default.Star, contentDescription = "Özetle", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { viewModel.syncWithWeb() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person, 
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                SmallFloatingActionButton(
                    onClick = onRecordAudioClick,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .testTag("record_audio_fab"),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Ses Kaydet")
                }
                FloatingActionButton(
                    onClick = onAddTextNoteClick,
                    modifier = Modifier.testTag("add_note_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Yeni Not")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp), // for FAB
        ) {
            item {
                Text(
                    text = "Bekleyen Toplantılar",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            if (upcomingMeetings.isEmpty()) {
                item {
                    Text(
                        text = "Bekleyen toplantı yok.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(upcomingMeetings, key = { it.id }) { meeting ->
                            val isSelected = selectedMeeting?.id == meeting.id
                            
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
                            
                            val displayStatus = when (meeting.status) {
                                "upcoming", "pending" -> "Bekleyen"
                                "completed" -> "Tamamlandı"
                                else -> "Bekleyen"
                            }
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { onMeetingClick(meeting) },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(meeting.title, style = MaterialTheme.typography.bodyMedium)
                                        if (meeting.date.isNotBlank()) {
                                            Text("$displayDate - $displayStatus", style = MaterialTheme.typography.labelSmall)
                                        } else if (displayStatus.isNotBlank()) {
                                            Text(displayStatus, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Event, contentDescription = null)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    reminderManager.scheduleMeetingReminder(meeting)
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Hatırlatıcı ayarlandı")
                                                    }
                                                } else {
                                                    meetingToRemind = meeting
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            } else {
                                                reminderManager.scheduleMeetingReminder(meeting)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Hatırlatıcı ayarlandı")
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = "Hatırlatıcı Kur",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            if (pastMeetings.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Geçmiş Toplantılar",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pastMeetings, key = { it.id }) { meeting ->
                            val isSelected = selectedMeeting?.id == meeting.id
                            
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
                            
                            val displayStatus = when (meeting.status) {
                                "upcoming", "pending" -> "Bekleyen"
                                "completed" -> "Tamamlandı"
                                else -> "Tamamlandı"
                            }
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { onMeetingClick(meeting) },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(meeting.title, style = MaterialTheme.typography.bodyMedium)
                                        if (meeting.date.isNotBlank()) {
                                            Text("$displayDate - $displayStatus", style = MaterialTheme.typography.labelSmall)
                                        } else if (displayStatus.isNotBlank()) {
                                            Text(displayStatus, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.EventAvailable, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Geçmiş Toplantı Özetleri ve Notları",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            if (notes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Henüz not eklenmedi", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        NoteCard(
                            note = note,
                            onDelete = { viewModel.deleteNote(note.id) },
                            onPlay = { note.audioPath?.let { onPlayAudio(it) } }
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                var isDebugExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDebugExpanded = !isDebugExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Senkronizasyon Tanılama Raporu",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isDebugExpanded) "Gizle" else "Göster",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isDebugExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val rawText = viewModel.rawMeetingsText.collectAsStateWithLifecycle().value
                            Box(
                                modifier = Modifier
                                    .heightIn(max = 350.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = if (rawText.isBlank()) "Rapor hazırlanıyor veya oturum açılmamış..." else rawText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun NoteCard(note: Note, onDelete: () -> Unit, onPlay: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(note.timestamp))

    val containerColor = when (note.id % 3) {
        0 -> MaterialTheme.colorScheme.secondaryContainer // BentoCard1
        1 -> MaterialTheme.colorScheme.primaryContainer // BentoPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant // BentoCard3
    }
    val contentColor = when (note.id % 3) {
        0 -> MaterialTheme.colorScheme.onSecondaryContainer
        1 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_item_${note.id}"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isSynced) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Senkronize Edildi",
                            tint = contentColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor
                        )
                        if (!note.meetingTitle.isNullOrBlank()) {
                            Text(
                                text = "Toplantı: ${note.meetingTitle}",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (note.isAudio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Mic, 
                        contentDescription = "Sesli Not", 
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Transkript",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
