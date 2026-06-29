package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.network.MeetingDto
import com.example.network.NoteSyncDto
import com.example.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeetingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    val notes: StateFlow<List<Note>>
    
    val upcomingMeetings = MutableStateFlow<List<MeetingDto>>(emptyList())
    val selectedMeeting = MutableStateFlow<MeetingDto?>(null)

    val isSyncing = MutableStateFlow(false)
    val syncMessage = MutableStateFlow<String?>(null)
    
    val pendingSummary = MutableStateFlow<String?>(null)

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
        fetchMeetings()
    }

    private fun fetchMeetings() {
        viewModelScope.launch {
            try {
                // Fetch from backend
                val meetings = RetrofitClient.apiService.getMeetings()
                upcomingMeetings.value = meetings
            } catch (e: Exception) {
                // Mock data if backend is unreachable for preview
                upcomingMeetings.value = listOf(
                    MeetingDto("m1", "Proje Planlama", "Bugün", "pending"),
                    MeetingDto("m2", "Müşteri Görüşmesi", "Yarın", "pending"),
                    MeetingDto("m3", "Aylık Değerlendirme", "Cuma", "pending")
                )
            }
        }
    }

    fun selectMeeting(meeting: MeetingDto?) {
        selectedMeeting.value = meeting
    }

    fun syncWithWeb() {
        viewModelScope.launch {
            isSyncing.value = true
            syncMessage.value = "Senkronize ediliyor..."
            try {
                // 1. Fetch remote notes
                val remoteNotes = RetrofitClient.apiService.getPreviousNotes()
                
                // Convert and save remote notes locally (basic example)
                remoteNotes.forEach { remoteNote ->
                    // Note: Here you'd ideally check if the note already exists
                    repository.insert(
                        Note(
                            title = remoteNote.title,
                            content = remoteNote.content,
                            isAudio = remoteNote.isAudio,
                            timestamp = remoteNote.timestamp
                        )
                    )
                }

                // 2. Push local notes
                val localNotes = notes.value.filter { !it.isSynced }
                val dtos = localNotes.map { local ->
                    NoteSyncDto(
                        id = local.id.toString(),
                        title = local.title,
                        content = local.content,
                        isAudio = local.isAudio,
                        timestamp = local.timestamp,
                        meetingId = local.meetingId
                    )
                }
                
                if (dtos.isNotEmpty()) {
                    RetrofitClient.apiService.syncNotes(dtos)
                    // Mark as synced
                    localNotes.forEach {
                        repository.insert(it.copy(isSynced = true))
                    }
                }

                syncMessage.value = "Başarıyla senkronize edildi."
            } catch (e: Exception) {
                e.printStackTrace()
                // In a real app, you would handle network errors gracefully
                syncMessage.value = "Web servisine bağlanılamadı. API ayarlarını kontrol edin."
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
