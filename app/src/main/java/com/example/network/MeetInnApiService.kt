package com.example.network

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class NoteSyncDto(
    val id: String?,
    val title: String,
    val content: String,
    val isAudio: Boolean,
    val timestamp: Long,
    val meetingId: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val success: Boolean,
    val message: String?
)

@JsonClass(generateAdapter = true)
data class MeetingDto(
    val id: String,
    val title: String,
    val date: String,
    val status: String // "pending", "completed"
)

interface MeetInnApiService {
    @GET("api/meetings")
    suspend fun getMeetings(): List<MeetingDto>

    @GET("api/notes")
    suspend fun getPreviousNotes(): List<NoteSyncDto>

    @POST("api/notes/sync")
    suspend fun syncNotes(@Body notes: List<NoteSyncDto>): SyncResponse
}
