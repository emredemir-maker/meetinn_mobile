package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Event
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
    onPlayAudio: (String) -> Unit
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val upcomingMeetings by viewModel.upcomingMeetings.collectAsStateWithLifecycle()
    val selectedMeeting by viewModel.selectedMeeting.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val pendingSummary by viewModel.pendingSummary.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }

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
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    if (isSelected) viewModel.selectMeeting(null) 
                                    else viewModel.selectMeeting(meeting) 
                                },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(meeting.title, style = MaterialTheme.typography.bodyMedium)
                                        Text("${meeting.date} - ${meeting.status}", style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Event, contentDescription = null)
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
