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

    var lastDebugLog: String = ""

    /** Past/meetings owned by the signed-in user (replaces the old REST mock). */
    suspend fun fetchMeetings(): List<MeetingDto> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val email = auth.currentUser?.email ?: ""
        val debugSb = StringBuilder()
        debugSb.append("UID: $uid\nEmail: $email\n")
        
        val allDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        
        val queries = mutableListOf<Pair<String, com.google.firebase.firestore.Query>>()
        
        queries.add("meetings ownerId == uid" to db.collection("meetings").whereEqualTo("ownerId", uid))
        queries.add("meetings userId == uid" to db.collection("meetings").whereEqualTo("userId", uid))
        queries.add("meetings participants contains uid" to db.collection("meetings").whereArrayContains("participants", uid))
        queries.add("events ownerId == uid" to db.collection("events").whereEqualTo("ownerId", uid))
        queries.add("events userId == uid" to db.collection("events").whereEqualTo("userId", uid))
        queries.add("calendar_events userId == uid" to db.collection("calendar_events").whereEqualTo("userId", uid))
        
        if (email.isNotBlank()) {
            queries.add("meetings ownerEmail == email" to db.collection("meetings").whereEqualTo("ownerEmail", email))
            queries.add("meetings userEmail == email" to db.collection("meetings").whereEqualTo("userEmail", email))
            queries.add("meetings participants contains email" to db.collection("meetings").whereArrayContains("participants", email))
            queries.add("meetings attendees contains email" to db.collection("meetings").whereArrayContains("attendees", email))
            queries.add("events ownerEmail == email" to db.collection("events").whereEqualTo("ownerEmail", email))
            queries.add("events userEmail == email" to db.collection("events").whereEqualTo("userEmail", email))
            queries.add("events participants contains email" to db.collection("events").whereArrayContains("participants", email))
            queries.add("events attendees contains email" to db.collection("events").whereArrayContains("attendees", email))
            queries.add("calendar_events userEmail == email" to db.collection("calendar_events").whereEqualTo("userEmail", email))
            queries.add("calendar_events participants contains email" to db.collection("calendar_events").whereArrayContains("participants", email))
            queries.add("calendar_events attendees contains email" to db.collection("calendar_events").whereArrayContains("attendees", email))
            queries.add("calendar userId == uid" to db.collection("calendar").whereEqualTo("userId", uid))
            queries.add("calendar userEmail == email" to db.collection("calendar").whereEqualTo("userEmail", email))
            queries.add("calendarEvents userId == uid" to db.collection("calendarEvents").whereEqualTo("userId", uid))
            queries.add("calendarEvents userEmail == email" to db.collection("calendarEvents").whereEqualTo("userEmail", email))
        }
        
        // Add fallbacks
        queries.add("users/meetings" to db.collection("users").document(uid).collection("meetings"))
        queries.add("users/events" to db.collection("users").document(uid).collection("events"))
        queries.add("users/calendar_events" to db.collection("users").document(uid).collection("calendar_events"))
        queries.add("users/calendarEvents" to db.collection("users").document(uid).collection("calendarEvents"))
        queries.add("users/calendar" to db.collection("users").document(uid).collection("calendar"))
        
        queries.add("meetings fallback" to db.collection("meetings"))
        queries.add("events fallback" to db.collection("events"))
        queries.add("calendar_events fallback" to db.collection("calendar_events"))
        queries.add("calendarEvents fallback" to db.collection("calendarEvents"))
        queries.add("calendar fallback" to db.collection("calendar"))
        
        for ((name, q) in queries) {
            try {
                val snap = q.get().await()
                if (!snap.isEmpty) {
                    debugSb.append("$name: Succeeded (${snap.size()} docs)\n")
                    allDocs.addAll(snap.documents)
                } else {
                    debugSb.append("$name: Succeeded (Empty)\n")
                }
            } catch (e: Exception) {
                debugSb.append("$name: Failed (${e.message})\n")
            }
        }
        
        // Remove duplicates by ID
        val uniqueDocs = allDocs.associateBy { it.id }.values
        debugSb.append("Unique documents count: ${uniqueDocs.size}\n")
        
        val result = uniqueDocs.mapNotNull { d ->
            val title = d.getString("title") 
                ?: d.getString("name") 
                ?: d.getString("summary") 
                ?: d.getString("subject") 
                ?: d.getString("description")
                ?: "Doc:${d.id} Keys:${d.data?.keys?.joinToString(",")}"
                
            var dateStr = findDateInDocument(d) ?: ""
            
            val status = d.getString("status") ?: ""
            if (uniqueDocs.indexOf(d) < 10) {
                debugSb.append(" - Doc: ${d.id}, Data: ${d.data}\n")
            } else {
                debugSb.append(" - Doc: ${d.id}, Title: $title, Date: $dateStr, Status: $status\n")
            }
            
            MeetingDto(
                id = d.id,
                title = title,
                date = dateStr,
                status = status
            )
        }.sortedByDescending { it.date }
        
        lastDebugLog = debugSb.toString()
        return result
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

        if (title == "Toplantı Özeti") {
            db.collection("meetings").document(meetingId)
                .update(
                    "summary", content,
                    "updatedAt", FieldValue.serverTimestamp()
                ).await()
        } else {
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
        }

        meetingId
    }

    private fun findDateInDocument(d: com.google.firebase.firestore.DocumentSnapshot): String? {
        val dateFields = listOf(
            "date", "startDate", "startTime", "start_time", "scheduledAt", 
            "scheduled_at", "time", "timestamp", "createdAt", "created_at"
        )
        
        for (field in dateFields) {
            val v = d.get(field) ?: continue
            if (v is Map<*, *>) {
                val dateTime = v["dateTime"] ?: v["date"] ?: v["value"] ?: v["formatted"]
                if (dateTime is String) return dateTime
                if (dateTime is com.google.firebase.Timestamp) {
                    return formatTimestamp(dateTime)
                }
            }
            if (v is com.google.firebase.Timestamp) {
                return formatTimestamp(v)
            }
            if (v is java.util.Date) {
                return formatJavaDate(v)
            }
            if (v is String && v.isNotBlank()) {
                return v
            }
            if (v is Long || v is Double || v is Int) {
                val num = (v as Number).toLong()
                val ms = if (num < 10000000000L) num * 1000 else num
                return formatJavaDate(java.util.Date(ms))
            }
        }
        
        val startVal = d.get("start")
        if (startVal is Map<*, *>) {
            val dateTime = startVal["dateTime"] ?: startVal["date"]
            if (dateTime is String) return dateTime
        }
        
        return null
    }

    private fun formatTimestamp(ts: com.google.firebase.Timestamp): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(ts.toDate())
    }

    private fun formatJavaDate(date: java.util.Date): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
