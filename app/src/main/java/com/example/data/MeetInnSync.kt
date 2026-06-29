package com.example.data

import com.example.network.MeetingDto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Firestore sync layer — talks DIRECTLY to the same Firestore the Meet-Inn web
 * app uses (no REST backend exists; the only Cloud Function is Firestore-
 * triggered). Maps the local [Note] model onto the web schema:
 *
 *   each pushed note → a `meetings/{id}` doc (source:'mobile', location) +
 *   one `transcriptSegments` entry holding the note text.
 *
 * If a note is tied to an existing web meeting ([existingMeetingId]), the
 * segment is appended to THAT meeting instead of creating a new one.
 *
 * Writes obey firestore.rules: meeting create needs
 * title/date/status/ownerId/createdAt/updatedAt with server timestamps and a
 * sanitized doc id; segment create needs ownerId == uid under an owned meeting.
 */
class MeetInnSync {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)

    private fun isoUtc(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timestampMs))

    private fun clock(timestampMs: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))

    /** Past/meetings owned by the signed-in user (replaces the old REST mock). */
    suspend fun fetchMeetings(): List<MeetingDto> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val snap = db.collection("meetings")
            .whereEqualTo("ownerId", uid)
            .get().await()
        return snap.documents.mapNotNull { d ->
            val title = d.getString("title") ?: return@mapNotNull null
            MeetingDto(
                id = d.id,
                title = title,
                date = d.getString("date") ?: "",
                status = d.getString("status") ?: "completed"
            )
        }.sortedByDescending { it.date }
    }

    /**
     * Push one local note to the web app. Returns the meetingId it landed in.
     */
    suspend fun pushNote(
        localId: Int,
        title: String,
        content: String,
        timestampMs: Long,
        existingMeetingId: String?,
        lat: Double?,
        lng: Double?
    ): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Giriş yapılmadı")
        val speaker = auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Mobil Not"

        val meetingId: String = if (!existingMeetingId.isNullOrBlank()) {
            existingMeetingId
        } else {
            val id = sanitizeId("${uid}_mobile_$localId")
            val meeting = hashMapOf<String, Any>(
                "title" to title.ifBlank { "Mobil Not" },
                "date" to isoUtc(timestampMs),
                "status" to "completed",
                "ownerId" to uid,
                "source" to "mobile",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (lat != null && lng != null) {
                meeting["location"] = hashMapOf("lat" to lat, "lng" to lng)
            }
            db.collection("meetings").document(id).set(meeting).await()
            id
        }

        val segment = hashMapOf<String, Any>(
            "meetingId" to meetingId,
            "t" to clock(timestampMs),
            "speaker" to speaker,
            "text" to content,
            "isFinal" to true,
            "ownerId" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("meetings").document(meetingId)
            .collection("transcriptSegments").add(segment).await()

        meetingId
    }
}
