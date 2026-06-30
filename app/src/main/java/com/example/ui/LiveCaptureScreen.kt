package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.capture.CaptionAccessibilityService
import com.example.capture.CaptionLog

/** Returns true if our caption accessibility service is enabled in system settings. */
private fun isCaptionServiceEnabled(context: Context): Boolean {
    val component = ComponentName(context, CaptionAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        val cn = ComponentName.unflattenFromString(it)
        cn == component || it.endsWith(CaptionAccessibilityService::class.java.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lines by CaptionLog.lines.collectAsStateWithLifecycle()
    val connected by CaptionLog.serviceConnected.collectAsStateWithLifecycle()

    // Re-check the settings flag whenever the screen recomposes / returns.
    var enabledInSettings by remember { mutableStateOf(isCaptionServiceEnabled(context)) }
    LaunchedEffect(connected, lines.size) {
        enabledInSettings = isCaptionServiceEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canlı Altyazı Yakalama") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (enabledInSettings) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = if (enabledInSettings) "Erişilebilirlik servisi AÇIK" else "Erişilebilirlik servisi KAPALI",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (connected) "Servis bağlı, altyazı dinleniyor." else "Servis henüz bağlanmadı.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (enabledInSettings) "Erişilebilirlik Ayarlarını Aç" else "Servisi Etkinleştir (Ayarlar)")
            }

            // Instructions
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Test adımları (tanılama)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("1. Yukarıdaki butondan erişilebilirlik servisini aç (\"Meet-Inn Altyazı Yakalama\").", style = MaterialTheme.typography.bodySmall)
                    Text("2. Google Meet toplantısına gir, uygulamada ALTYAZIYI (CC) aç.", style = MaterialTheme.typography.bodySmall)
                    Text("3. Birkaç cümle konuşulsun.", style = MaterialTheme.typography.bodySmall)
                    Text("4. Bu ekrana dön, aşağıdaki kaydı KOPYALA ve bana gönder — altyazı satırlarını seçicilere bağlayacağız.", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Diagnostic log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Yakalanan düğümler (${lines.size})", style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(CaptionLog.dump())) }) { Text("Kopyala") }
                    TextButton(onClick = { CaptionLog.clear() }) { Text("Temizle") }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                SelectionContainer {
                    Text(
                        text = if (lines.isEmpty())
                            "Henüz düğüm yakalanmadı. Servisi açıp Meet'te altyazıyı etkinleştirdiğinden emin ol."
                        else lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp).heightIn(min = 100.dp, max = 480.dp)
                    )
                }
            }
        }
    }
}
