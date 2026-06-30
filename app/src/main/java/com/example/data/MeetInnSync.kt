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

/** Bir web toplantısının rapor içeriği (mobil rapor ekranı için). */
data class MeetingDetail(
    val id: String,
    val title: String,
    val status: String,
    val summary: String?,
    val decisions: List<String>,
    val actions: List<ActionItemDto>,
    val transcript: List<String>
)

data class ActionItemDto(
    val description: String,
    val assignee: String?,
    val dueDate: String?,
    val status: String
)

/**
 * Firestore sync layer — talks DIRECTLY to the same Firestore the Meet-Inn web
 * app uses (no REST backend exists; the only Cloud Function is Firestore-
 * triggered). Maps the local [Note] model onto the web schema:
 *
 *   each pushed note → a `meetings/{id}` doc (source:'mobile', location) +
 *   one `transcriptSegments` entry holding the note text (or the meeting
 *   `summary` field when the note is a generated summary).
 *
 * Reads obey firestore.rules: the ONLY meetings query the rules allow is one
 * filtered by `ownerId == uid` (the list rule requires it), so that is the
 * single source — querying other collections/fields just yields permission
 * errors. fetchMeetingDetail reads the meeting doc + its actionItems + a few
 * transcript segments, all owner-scoped.
 */
class MeetInnSync {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    var lastDebugLog: String = ""

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)

    private fun isoUtc(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timestampMs))

    private fun clock(timestampMs: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))

    /**
     * The signed-in user's meetings. Firestore rules only permit the
     * `ownerId == uid` query, so that is the single, correct source. Note:
     * the web app's *upcoming* calendar events are EPHEMERAL (not persisted to
     * Firestore until opened), so only materialized/extension-created meetings
     * appear here.
     */
    suspend fun fetchMeetings(): List<MeetingDto> {
        val uid = auth.currentUser?.uid ?: run {
            lastDebugLog = "Giriş yapılmamış."
            return emptyList()
        }
        val sb = StringBuilder("UID: $uid\nE-posta: ${auth.currentUser?.email ?: "-"}\n")
        return try {
            val snap = db.collection("meetings").whereEqualTo("ownerId", uid).get().await()
            sb.append("meetings(ownerId==uid): ${snap.size()} kayıt\n")
            val list = snap.documents.mapNotNull { d ->
                val title = d.getString("title") ?: return@mapNotNull null
                MeetingDto(
                    id = d.id,
                    title = title,
                    date = findDateInDocument(d) ?: (d.getString("date") ?: ""),
                    status = d.getString("status") ?: ""
                )
            }.sortedByDescending { it.date }
            list.forEach { sb.append(" - ${it.id} | ${it.title} | ${it.status} | ${it.date}\n") }
            lastDebugLog = sb.toString()
            list
        } catch (e: Exception) {
            lastDebugLog = sb.append("HATA: ${e.message}\n").toString()
            emptyList()
        }
    }

    /** Full report content for one meeting: summary, decisions, action items,
     *  and a few transcript lines. */
    suspend fun fetchMeetingDetail(meetingId: String): MeetingDetail? {
        val uid = auth.currentUser?.uid ?: return null
        val doc = db.collection("meetings").document(meetingId).get().await()
        if (!doc.exists()) return null

        val title = doc.getString("title") ?: "Toplantı"
        val status = doc.getString("status") ?: ""
        val summary = doc.getString("summary") ?: doc.getString("neutralSummary")
        val decisions = parseTextList(doc.get("decisions"))

        val actions = try {
            db.collection("actionItems")
                .whereEqualTo("meetingId", meetingId)
                .whereEqualTo("ownerId", uid)
                .get().await()
                .documents.map { a ->
                    ActionItemDto(
                        description = a.getString("description") ?: "",
                        assignee = a.getString("assignee"),
                        dueDate = a.getString("dueDate"),
                        status = a.getString("status") ?: "pending"
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }

        val transcript = try {
            db.collection("meetings").document(meetingId)
                .collection("transcriptSegments")
                .get().await()
                .documents.mapNotNull { s ->
                    val text = s.getString("text")
                    if (text.isNullOrBlank()) return@mapNotNull null
                    val speaker = s.getString("speaker")
                    if (speaker.isNullOrBlank()) text else "$speaker: $text"
                }
        } catch (e: Exception) {
            emptyList()
        }

        return MeetingDetail(meetingId, title, status, summary, decisions, actions, transcript)
    }

    /** Pending action items assigned to the signed-in user, across all
     *  meetings — the web "BEKLEYEN AKSİYONLAR" panel. Owner-scoped to satisfy
     *  firestore.rules; completed items are filtered out. */
    suspend fun fetchPendingActions(): List<ActionItemDto> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            db.collection("actionItems")
                .whereEqualTo("ownerId", uid)
                .get().await()
                .documents.mapNotNull { a ->
                    val status = a.getString("status") ?: "pending"
                    if (status.equals("completed", ignoreCase = true)) return@mapNotNull null
                    val description = a.getString("description") ?: return@mapNotNull null
                    if (description.isBlank()) return@mapNotNull null
                    ActionItemDto(
                        description = description,
                        assignee = a.getString("assignee"),
                        dueDate = a.getString("dueDate"),
                        status = status
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** decisions/risks/questions are stored as arrays of maps ({text, ...}) on
     *  the web; pull out the human-readable text. */
    private fun parseTextList(raw: Any?): List<String> {
        if (raw !is List<*>) return emptyList()
        return raw.mapNotNull { item ->
            when (item) {
                is Map<*, *> -> item["text"] as? String
                is String -> item
                else -> null
            }
        }
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
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(ts.toDate())
    }

    private fun formatJavaDate(date: java.util.Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
