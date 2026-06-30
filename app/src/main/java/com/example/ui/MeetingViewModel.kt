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
    private val calendarSync = com.example.data.CalendarSync(application)
    private val firebaseAuth: FirebaseAuth = Firebase.auth
    val notes: StateFlow<List<Note>>

    // Home dashboard buckets, mirroring the web app's HomeScreen.
    val nowMeeting = MutableStateFlow<MeetingDto?>(null)        // ŞİMDİ
    val todayMeetings = MutableStateFlow<List<MeetingDto>>(emptyList())  // BUGÜN
    val weekMeetings = MutableStateFlow<List<MeetingDto>>(emptyList())   // BU HAFTA
    val pendingActions = MutableStateFlow<List<com.example.data.ActionItemDto>>(emptyList()) // BEKLEYEN AKSİYONLAR

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

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun fetchMeetings() {
        viewModelScope.launch {
            try {
                val nowMs = System.currentTimeMillis()
                val weekMs = 7L * 24 * 60 * 60 * 1000

                // Firestore meetings (persisted: completed, active, mobile notes).
                val firestore = sync.fetchMeetings()
                // Google Calendar events (ephemeral upcoming meetings) — same
                // source the web app uses for BUGÜN / BU HAFTA.
                val calendar = calendarSync.fetchUpcoming()

                // Upcoming pool: calendar events + non-completed Firestore meetings
                // that carry a date. Deduped by id; bucketed purely by time.
                val pool = (calendar + firestore.filter {
                    val s = it.status.lowercase().trim()
                    s != "completed" && s != "done" && s != "tamamlandı" &&
                        s != "cancelled" && s != "iptal" && it.date.isNotBlank()
                }).distinctBy { it.id }

                val timed = pool.mapNotNull { m -> parseIsoDate(m.date)?.let { m to it } }
                    .sortedBy { it.second }

                // ŞİMDİ: started in the last hour OR starting within the next hour.
                val now = timed.firstOrNull {
                    val diffMin = (it.second - nowMs) / 60000.0
                    diffMin > -60 && diffMin < 60
                }?.first

                // BUGÜN: today, still in the future, excluding the ŞİMDİ meeting.
                val today = timed.filter {
                    isSameDay(it.second, nowMs) && it.second > nowMs && it.first.id != now?.id
                }.map { it.first }

                // BU HAFTA: not today, within the next 7 days.
                val week = timed.filter {
                    !isSameDay(it.second, nowMs) &&
                        it.second >= nowMs && (it.second - nowMs) <= weekMs
                }.map { it.first }

                nowMeeting.value = now
                todayMeetings.value = today
                weekMeetings.value = week

                val upcoming = (listOfNotNull(now) + today + week)
                upcomingMeetings.value = upcoming

                // Everything else from Firestore (completed + stale) → archive.
                val bucketIds = upcoming.map { it.id }.toSet()
                pastMeetings.value = firestore
                    .filter { it.id !in bucketIds }
                    .sortedByDescending { parseIsoDate(it.date) ?: 0L }

                pendingActions.value = sync.fetchPendingActions()

                rawMeetingsText.value = buildString {
                    append(sync.lastDebugLog)
                    append("\n--- Takvim ---\n")
                    append(calendarSync.lastDebugLog)
                    append("\nŞİMDİ: ${if (now != null) 1 else 0} | BUGÜN: ${today.size} | BU HAFTA: ${week.size} | Bekleyen aksiyon: ${pendingActions.value.size}")
                }
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
