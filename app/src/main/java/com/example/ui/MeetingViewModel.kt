package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.data.MeetInnSync
import com.example.network.MeetingDto
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeetingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    private val sync = MeetInnSync()
    private val firebaseAuth: FirebaseAuth = Firebase.auth
    val notes: StateFlow<List<Note>>

    val upcomingMeetings = MutableStateFlow<List<MeetingDto>>(emptyList())
    val pastMeetings = MutableStateFlow<List<MeetingDto>>(emptyList())
    val rawMeetingsText = MutableStateFlow("")
    val selectedMeeting = MutableStateFlow<MeetingDto?>(null)

    val isSignedIn = MutableStateFlow(firebaseAuth.currentUser != null)
    val isSyncing = MutableStateFlow(false)
    val syncMessage = MutableStateFlow<String?>(null)

    val pendingSummary = MutableStateFlow<String?>(null)

    val meetingDetail = MutableStateFlow<com.example.data.MeetingDetail?>(null)
    val isLoadingDetail = MutableStateFlow(false)

    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val signed = a.currentUser != null
        isSignedIn.value = signed
        if (signed) fetchMeetings()
    }

    init {
        val database = androidx.room.Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "meeting_database"
        )
        .fallbackToDestructiveMigration()
        .build()
        repository = NoteRepository(database.noteDao())

        notes = repository.allNotes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        // Auth state drives the meeting fetch; the listener fires immediately
        // with the current user on registration.
        firebaseAuth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        super.onCleared()
        firebaseAuth.removeAuthStateListener(authListener)
    }

    private fun parseIsoDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        val clean = dateStr.trim()
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
                return sdf.parse(clean)?.time
            } catch (e: Exception) {
                // continue
            }
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                return java.time.Instant.parse(clean).toEpochMilli()
            }
        } catch (e: Exception) {}
        
        return clean.toLongOrNull()
    }

    private fun fetchMeetings() {
        viewModelScope.launch {
            try {
                val allMeetings = sync.fetchMeetings()
                val nowMs = System.currentTimeMillis()

                val upcoming = mutableListOf<MeetingDto>()
                val past = mutableListOf<MeetingDto>()

                for (m in allMeetings) {
                    val statusClean = m.status.lowercase().trim()
                    if (statusClean == "cancelled" || statusClean == "iptal") continue

                    if (statusClean == "completed" || statusClean == "done" || statusClean == "tamamlandı") {
                        past.add(m)
                        continue
                    }

                    val meetingTimeMs = parseIsoDate(m.date)
                    val isExplicitlyActive = statusClean in listOf("scheduled", "upcoming", "pending", "active", "in_progress", "planned")
                    val isRecentOrFuture = meetingTimeMs != null && meetingTimeMs >= (nowMs - 24 * 60 * 60 * 1000L)

                    if (isExplicitlyActive || isRecentOrFuture || meetingTimeMs == null) {
                        upcoming.add(m)
                    } else {
                        past.add(m)
                    }
                }

                upcomingMeetings.value = upcoming
                pastMeetings.value = past
                rawMeetingsText.value = sync.lastDebugLog
            } catch (e: Exception) {
                e.printStackTrace()
                rawMeetingsText.value = "Exception: ${e.message}\n" + sync.lastDebugLog
            }
        }
    }

    fun selectMeeting(meeting: MeetingDto?) {
        selectedMeeting.value = meeting
    }

    /** Bir toplantıyı id ile seç (rapor ekranındaki "bu toplantıya not al"). */
    fun selectMeetingById(meetingId: String) {
        val m = (upcomingMeetings.value + pastMeetings.value).firstOrNull { it.id == meetingId }
        if (m != null) selectedMeeting.value = m
    }

    fun loadMeetingDetail(meetingId: String) {
        viewModelScope.launch {
            isLoadingDetail.value = true
            meetingDetail.value = null
            try {
                meetingDetail.value = sync.fetchMeetingDetail(meetingId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingDetail.value = false
            }
        }
    }

    fun clearMeetingDetail() {
        meetingDetail.value = null
    }

    fun syncWithWeb() {
        viewModelScope.launch {
            if (firebaseAuth.currentUser == null) {
                syncMessage.value = "Senkron için önce Google ile giriş yap."
                return@launch
            }
            isSyncing.value = true
            syncMessage.value = "Senkronize ediliyor..."
            try {
                val localNotes = notes.value.filter { !it.isSynced }
                var ok = 0
                for (n in localNotes) {
                    val res = sync.pushNote(
                        localId = n.id,
                        title = n.title,
                        content = n.content,
                        timestampMs = n.timestamp,
                        existingMeetingId = n.meetingId,
                        lat = n.latitude,
                        lng = n.longitude
                    )
                    if (res.isSuccess) {
                        repository.insert(n.copy(isSynced = true))
                        ok++
                    }
                }
                // Refresh the web meeting list after pushing.
                fetchMeetings()
                syncMessage.value = when {
                    localNotes.isEmpty() -> "Senkron güncel."
                    ok == localNotes.size -> "$ok not web uygulamasına senkronlandı."
                    else -> "$ok/${localNotes.size} not senkronlandı (bazıları başarısız)."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                syncMessage.value = "Senkron hatası: ${e.localizedMessage}"
            } finally {
                isSyncing.value = false
            }
        }
    }
    
    fun clearSyncMessage() {
        syncMessage.value = null
    }

    fun addTextNote(title: String, content: String, lat: Double? = null, lng: Double? = null) {
        viewModelScope.launch {
            val m = selectedMeeting.value
            repository.insert(Note(title = title, content = content, isAudio = false, meetingId = m?.id, meetingTitle = m?.title, latitude = lat, longitude = lng))
        }
    }

    fun addTranscriptNote(title: String, transcript: String, lat: Double? = null, lng: Double? = null) {
        viewModelScope.launch {
            val m = selectedMeeting.value
            repository.insert(Note(title = title, content = transcript, isAudio = true, audioPath = null, meetingId = m?.id, meetingTitle = m?.title, latitude = lat, longitude = lng))
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun summarizeMeeting() {
        viewModelScope.launch {
            val currentNotes = notes.value
            val transcriptParts = currentNotes.filter { it.isAudio }.joinToString("\n\n") { it.content }
            
            if (transcriptParts.isBlank()) {
                syncMessage.value = "Özetlenecek transkript bulunamadı."
                return@launch
            }

            isSyncing.value = true
            syncMessage.value = "Toplantı özetleniyor..."

            try {
                val prompt = "Aşağıdaki toplantı transkriptlerinden toplantının ana kararlarını ve eylem maddelerini (action items) çıkararak özetle. Ayrıca toplantıda geçen veya araştırılan firmalar vb. kuruluşlar varsa bunlar hakkında Google Search kullanarak kısaca bilgi (sektör, vb.) ekle:\n\n$transcriptParts"
                
                val request = com.example.network.GenerateContentRequest(
                    contents = listOf(
                        com.example.network.Content(
                            parts = listOf(
                                com.example.network.Part(text = prompt)
                            )
                        )
                    ),
                    tools = listOf(
                        com.example.network.Tool(googleSearch = emptyMap())
                    )
                )

                val response = com.example.network.GeminiApiClient.apiService.generateContent(
                    com.example.BuildConfig.GEMINI_API_KEY, 
                    request
                )

                val summary = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Özet oluşturulamadı."

                pendingSummary.value = summary

            } catch (e: Exception) {
                e.printStackTrace()
                syncMessage.value = "Özetleme başarısız: ${e.localizedMessage}"
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun saveSummary(content: String) {
        viewModelScope.launch {
            val m = selectedMeeting.value
            repository.insert(Note(title = "Toplantı Özeti", content = content, isAudio = false, meetingId = m?.id, meetingTitle = m?.title))
            pendingSummary.value = null
            syncMessage.value = "Toplantı özeti oluşturuldu ve kaydedildi."
        }
    }

    fun discardSummary() {
        pendingSummary.value = null
        syncMessage.value = "Özetleme iptal edildi."
    }
}
