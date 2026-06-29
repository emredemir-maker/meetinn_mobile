package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                title = { Text(detail?.title ?: "Toplantı Raporu", maxLines = 1) },
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
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Rapor bulunamadı veya bu toplantıya erişimin yok.")
                }
            }
            else -> {
                val d = detail as MeetingDetail
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Button(onClick = { viewModel.selectMeetingById(meetingId); onTakeNote() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.height(0.dp))
                            Text("  Bu toplantıya not al")
                        }
                    }

                    item {
                        ReportSection(title = "Genel Özet") {
                            Text(
                                text = d.summary?.takeIf { it.isNotBlank() } ?: "Bu toplantı için henüz özet üretilmemiş.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    item {
                        ReportSection(title = "Kararlar") {
                            if (d.decisions.isEmpty()) {
                                Text("Karar kaydı yok.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    d.decisions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                    }

                    item {
                        ReportSection(title = "Aksiyonlar") {
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
                                            Text(
                                                text = (if (done) "☑ " else "☐ ") + a.description,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (meta.isNotBlank()) {
                                                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (d.transcript.isNotEmpty()) {
                        item {
                            ReportSection(title = "Transkript") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
private fun ReportSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
