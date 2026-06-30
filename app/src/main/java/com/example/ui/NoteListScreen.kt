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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ActionItemDto
import com.example.data.Note
import com.example.network.MeetingDto
import java.text.SimpleDateFormat
import java.util.*

/** Parse a stored ISO/date string and render it as "dd MMM, HH:mm"; falls back
 *  to the raw string when it can't be parsed. */
fun formatMeetingDate(raw: String): String {
    if (raw.isBlank()) return ""
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
            val sdf = SimpleDateFormat(fmt, Locale.US)
            if (fmt.endsWith("'Z'")) sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(raw)
            if (parsed != null) {
                return SimpleDateFormat("dd MMM, HH:mm", Locale("tr")).format(parsed)
            }
        } catch (e: Exception) { /* try next */ }
    }
    return raw
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: MeetingViewModel,
    onAddTextNoteClick: () -> Unit,
    onRecordAudioClick: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onMeetingClick: (MeetingDto) -> Unit
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val nowMeeting by viewModel.nowMeeting.collectAsStateWithLifecycle()
    val todayMeetings by viewModel.todayMeetings.collectAsStateWithLifecycle()
    val weekMeetings by viewModel.weekMeetings.collectAsStateWithLifecycle()
    val pendingActions by viewModel.pendingActions.collectAsStateWithLifecycle()
    val pastMeetings by viewModel.pastMeetings.collectAsStateWithLifecycle()
    val selectedMeeting by viewModel.selectedMeeting.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val pendingSummary by viewModel.pendingSummary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val reminderManager = remember { ReminderManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var meetingToRemind by remember { mutableStateOf<MeetingDto?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            meetingToRemind?.let {
                reminderManager.scheduleMeetingReminder(it)
                meetingToRemind = null
                coroutineScope.launch { snackbarHostState.showSnackbar("Hatırlatıcı ayarlandı") }
            }
        }
    }

    // Reminder action shared by all upcoming sections.
    val onRemind: (MeetingDto) -> Unit = { meeting ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            meetingToRemind = meeting
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            reminderManager.scheduleMeetingReminder(meeting)
            coroutineScope.launch { snackbarHostState.showSnackbar("Hatırlatıcı ayarlandı") }
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
            confirmButton = { TextButton(onClick = { viewModel.saveSummary(editedSummary) }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { viewModel.discardSummary() }) { Text("İptal") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Meet-Inn",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Anasayfa",
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
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                SmallFloatingActionButton(
                    onClick = onRecordAudioClick,
                    modifier = Modifier.padding(bottom = 16.dp).testTag("record_audio_fab"),
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
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // ── ŞİMDİ ──
            item { SectionHeader("ŞİMDİ", icon = Icons.Default.Bolt) }
            item {
                val now = nowMeeting
                if (now == null) {
                    EmptyHint("Şu an aktif veya yaklaşan toplantı yok.")
                } else {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        NowMeetingCard(
                            meeting = now,
                            isSelected = selectedMeeting?.id == now.id,
                            onClick = { onMeetingClick(now) },
                            onRemind = { onRemind(now) }
                        )
                    }
                }
            }

            // ── BUGÜN ──
            item { SectionHeader("BUGÜN", count = todayMeetings.size, icon = Icons.Default.Today) }
            if (todayMeetings.isEmpty()) {
                item { EmptyHint("Bugün için başka toplantı yok.") }
            } else {
                item {
                    MeetingChipRow(
                        meetings = todayMeetings,
                        selectedId = selectedMeeting?.id,
                        leadingIcon = Icons.Default.Event,
                        onClick = onMeetingClick,
                        onRemind = onRemind
                    )
                }
            }

            // ── BU HAFTA ──
            item { SectionHeader("BU HAFTA", count = weekMeetings.size, icon = Icons.Default.DateRange) }
            if (weekMeetings.isEmpty()) {
                item { EmptyHint("Bu hafta için yaklaşan toplantı yok.") }
            } else {
                item {
                    MeetingChipRow(
                        meetings = weekMeetings,
                        selectedId = selectedMeeting?.id,
                        leadingIcon = Icons.Default.Event,
                        onClick = onMeetingClick,
                        onRemind = onRemind
                    )
                }
            }

            // ── GEÇMİŞ TOPLANTILAR ──
            if (pastMeetings.isNotEmpty()) {
                item { SectionHeader("GEÇMİŞ TOPLANTILAR", count = pastMeetings.size, icon = Icons.Default.EventAvailable) }
                item {
                    MeetingChipRow(
                        meetings = pastMeetings,
                        selectedId = selectedMeeting?.id,
                        leadingIcon = Icons.Default.EventAvailable,
                        onClick = onMeetingClick,
                        onRemind = null
                    )
                }
            }

            // ── Notlar ──
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
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Henüz not eklenmedi", style = MaterialTheme.typography.bodyLarge) }
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

            // ── Tanılama ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                var isDebugExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isDebugExpanded = !isDebugExpanded },
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
                                modifier = Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())
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
private fun SectionHeader(title: String, count: Int? = null, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingChipRow(
    meetings: List<MeetingDto>,
    selectedId: String?,
    leadingIcon: ImageVector,
    onClick: (MeetingDto) -> Unit,
    onRemind: ((MeetingDto) -> Unit)?
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(meetings, key = { it.id }) { meeting ->
            val displayDate = formatMeetingDate(meeting.date)
            FilterChip(
                selected = selectedId == meeting.id,
                onClick = { onClick(meeting) },
                label = {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(meeting.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (displayDate.isNotBlank()) {
                            Text(displayDate, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                leadingIcon = { Icon(leadingIcon, contentDescription = null) },
                trailingIcon = if (onRemind != null) {
                    {
                        IconButton(onClick = { onRemind(meeting) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Notifications, contentDescription = "Hatırlatıcı Kur", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null
            )
        }
    }
}

@Composable
private fun NowMeetingCard(
    meeting: MeetingDto,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemind: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val displayDate = formatMeetingDate(meeting.date)
                if (displayDate.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            IconButton(onClick = onRemind) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Hatırlatıcı Kur",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PendingActionCard(action: ActionItemDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(action.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    action.assignee?.takeIf { it.isNotBlank() && it != "Atanmadı" },
                    action.dueDate?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        0 -> MaterialTheme.colorScheme.secondaryContainer
        1 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (note.id % 3) {
        0 -> MaterialTheme.colorScheme.onSecondaryContainer
        1 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("note_item_${note.id}"),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
                        Text(text = note.title, style = MaterialTheme.typography.titleLarge, color = contentColor)
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
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = contentColor.copy(alpha = 0.7f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = dateString, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(12.dp))

            if (note.isAudio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = "Sesli Not", tint = contentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Transkript", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.8f))
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
