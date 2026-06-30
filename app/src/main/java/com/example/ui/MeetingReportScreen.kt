package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MeetingDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingReportScreen(
    meetingId: String,
    viewModel: MeetingViewModel,
    onBack: () -> Unit,
    onTakeNote: () -> Unit
) {
    LaunchedEffect(meetingId) { viewModel.loadMeetingDetail(meetingId) }
    val detail by viewModel.meetingDetail.collectAsStateWithLifecycle()
    val loading by viewModel.isLoadingDetail.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "Toplantı Detayı", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            detail == null -> {
                val msg = if (meetingId.startsWith("cal:")) {
                    "Bu yaklaşan toplantının henüz raporu yok. Toplantı gündemi veya asistan hazırlığı mevcut ise burada görünecek."
                } else {
                    "Rapor bulunamadı veya bu toplantıya erişimin yok."
                }
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(msg, modifier = Modifier.padding(24.dp))
                }
            }
            else -> {
                val d = detail as MeetingDetail
                val statusClean = d.status.lowercase().trim()
                val isCompleted = statusClean in listOf("completed", "done", "tamamlandı")

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button to record a note / transcribe
                    item {
                        Button(onClick = { viewModel.selectMeetingById(meetingId); onTakeNote() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bu toplantıya not al / ses kaydet")
                        }
                    }

                    // ── TOPLANTI GÜNDEMİ (MEETING AGENDA) ──
                    if (!d.agenda.isNullOrBlank()) {
                        item {
                            ReportSection(title = "Toplantı Gündemi", textToCopy = d.agenda) {
                                Text(
                                    text = d.agenda,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // ── ASİSTAN HAZIRLIĞI (ASSISTANT PREPARATION / AI BRIEF) ──
                    if (!d.preparation.isNullOrBlank()) {
                        item {
                            ReportSection(title = "Asistan Hazırlığı & Gündem Bilgisi", textToCopy = d.preparation) {
                                Text(
                                    text = d.preparation,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (!isCompleted) {
                        // For upcoming/not completed meetings:
                        // Show a placeholder notice if neither agenda nor preparation is available
                        if (d.agenda.isNullOrBlank() && d.preparation.isNullOrBlank()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Bu yaklaşan toplantı için henüz bir gündem veya asistan hazırlığı bulunmuyor.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // For completed meetings: Show post-meeting details (Summary, Decisions, Actions, etc.)

                        // ── GENEL ÖZET (SUMMARY) ──
                        val summaryText = d.summary?.takeIf { it.isNotBlank() } ?: "Bu toplantı için henüz özet üretilmemiş."
                        item {
                            ReportSection(title = "Genel Özet", textToCopy = if (d.summary.isNullOrBlank()) null else d.summary) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // ── KARARLAR (DECISIONS) ──
                        item {
                            val decisionsText = if (d.decisions.isEmpty()) null else d.decisions.joinToString("\n") { "• $it" }
                            ReportSection(title = "Kararlar", textToCopy = decisionsText) {
                                if (d.decisions.isEmpty()) {
                                    Text("Karar kaydı yok.", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.decisions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                                    }
                                }
                            }
                        }

                        // ── AKSİYONLAR (ACTIONS) ──
                        val actionsCopyText = if (d.actions.isEmpty()) null else d.actions.joinToString("\n") { a ->
                            val done = if (a.status.equals("completed", ignoreCase = true)) "[x]" else "[ ]"
                            val meta = listOfNotNull(
                                a.assignee?.takeIf { it.isNotBlank() && it != "Atanmadı" },
                                a.dueDate?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) "$done ${a.description} ($meta)" else "$done ${a.description}"
                        }

                        item {
                            ReportSection(title = "Aksiyonlar", textToCopy = actionsCopyText) {
                                if (d.actions.isEmpty()) {
                                    Text("Aksiyon maddesi yok.", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        d.actions.forEach { a ->
                                            val meta = listOfNotNull(
                                                a.assignee?.takeIf { it.isNotBlank() && it != "Atanmadı" },
                                                a.dueDate?.takeIf { it.isNotBlank() }
                                            ).joinToString(" · ")
                                            val done = a.status.equals("completed", ignoreCase = true)
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (done) "☑ " else "☐ ",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = a.description,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                if (meta.isNotBlank()) {
                                                    Text(
                                                        text = "  $meta",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── TRANSKRİPT (TRANSCRIPT) ──
                    // Show ONLY if not completed AND has transcript
                    // (User: "Toplantı detayındaki notlarda toplantı transcripti hazır açık gelmesin. Hatta geçmiş ve analiz edilmiş toplantılar için transcripte gerek yok.")
                    if (!isCompleted && d.transcript.isNotEmpty()) {
                        item {
                            var isTranscriptExpanded by remember { mutableStateOf(false) }
                            val transcriptCopyText = d.transcript.joinToString("\n")

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.clickable { isTranscriptExpanded = !isTranscriptExpanded },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Transkript",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = if (isTranscriptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isTranscriptExpanded) "Gizle" else "Göster",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (isTranscriptExpanded && transcriptCopyText.isNotBlank()) {
                                            val clipboardManager = LocalClipboardManager.current
                                            val context = LocalContext.current
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(transcriptCopyText))
                                                    Toast.makeText(context, "Transkript kopyalandı!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Kopyala",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isTranscriptExpanded) {
                                        Spacer(Modifier.height(12.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            d.transcript.take(100).forEach {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSection(
    title: String,
    textToCopy: String? = null,
    content: @Composable () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (!textToCopy.isNullOrBlank()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            Toast.makeText(context, "$title kopyalandı!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopyala",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
