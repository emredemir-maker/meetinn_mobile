package com.example.data

import android.content.Context
import com.example.auth.AuthManager
import com.example.network.MeetingDto
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reads the next 7 days of Google Calendar events directly from the Calendar v3
 * REST API — the SAME source the Meet-Inn web app uses for its upcoming meetings
 * (ŞİMDİ / BUGÜN / BU HAFTA). These events are ephemeral (not persisted to
 * Firestore), which is why they never appeared in the mobile app before.
 *
 * The OAuth access token is obtained via [GoogleAuthUtil] for the
 * calendar.readonly scope requested at sign-in. All failures degrade
 * gracefully: an empty list is returned and the reason is recorded in
 * [lastDebugLog], so the Firestore meetings still render.
 */
class CalendarSync(private val context: Context) {

    private val http: OkHttpClient = OkHttpClient()

    var lastDebugLog: String = ""

    /** Upcoming Google Calendar events for the next 7 days, as [MeetingDto]s
     *  with status "upcoming". Empty on any error (see [lastDebugLog]). */
    suspend fun fetchUpcoming(): List<MeetingDto> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
        if (account == null) {
            lastDebugLog = "Takvim: imzalı Google hesabı bulunamadı."
            return@withContext emptyList()
        }

        val token = try {
            GoogleAuthUtil.getToken(context, account, "oauth2:${AuthManager.CALENDAR_READONLY_SCOPE}")
        } catch (e: UserRecoverableAuthException) {
            lastDebugLog = "Takvim izni verilmemiş. Çıkış yapıp tekrar giriş yaparak takvim erişimini onaylayın."
            return@withContext emptyList()
        } catch (e: Exception) {
            lastDebugLog = "Takvim token hatası: ${e.message}"
            return@withContext emptyList()
        }

        try {
            val nowMs = System.currentTimeMillis()
            val timeMin = isoUtc(nowMs)
            val timeMax = isoUtc(nowMs + 7L * 24 * 60 * 60 * 1000)
            val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                "?timeMin=${enc(timeMin)}&timeMax=${enc(timeMax)}" +
                "&orderBy=startTime&singleEvents=true&maxResults=50"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // A 401 means the cached token is stale — clear it so the next
                    // pull mints a fresh one.
                    if (resp.code == 401) runCatching { GoogleAuthUtil.clearToken(context, token) }
                    lastDebugLog = "Takvim API hatası: HTTP ${resp.code}"
                    return@withContext emptyList()
                }
                val body = resp.body?.string().orEmpty()
                val items = JSONObject(body).optJSONArray("items")
                    ?: run {
                        lastDebugLog = "Takvim: 0 etkinlik."
                        return@withContext emptyList()
                    }

                val result = ArrayList<MeetingDto>(items.length())
                for (i in 0 until items.length()) {
                    val ev = items.optJSONObject(i) ?: continue
                    val start = ev.optJSONObject("start")
                    val dateStr = start?.optString("dateTime").takeUnless { it.isNullOrBlank() }
                        ?: start?.optString("date").takeUnless { it.isNullOrBlank() }
                        ?: continue
                    val title = ev.optString("summary").ifBlank { "Başlıksız etkinlik" }
                    result.add(
                        MeetingDto(
                            id = "cal:${ev.optString("id")}",
                            title = title,
                            date = dateStr,
                            status = "upcoming"
                        )
                    )
                }
                lastDebugLog = "Takvim: ${result.size} yaklaşan etkinlik."
                result
            }
        } catch (e: Exception) {
            lastDebugLog = "Takvim çekme hatası: ${e.message}"
            emptyList()
        }
    }

    private fun isoUtc(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
