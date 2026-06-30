package com.example.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In → Firebase Auth, against the SAME Firebase project as the
 * Meet-Inn web app. A successful sign-in yields the same Firebase `uid` the
 * web app uses, so Firestore data (meetings/transcriptSegments) is shared and
 * the security rules (ownerId == auth.uid) pass.
 *
 * Requires `google-services.json` in app/ (added by AI Studio "Connect
 * Firebase" or the Firebase console). The generated string resource
 * `default_web_client_id` is looked up by name so the project still COMPILES
 * before the file is present — sign-in just won't work until it is.
 */
class AuthManager(context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val googleClient: GoogleSignInClient

    init {
        val webClientId = run {
            val id = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            if (id != 0) context.getString(id) else ""
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply { if (webClientId.isNotBlank()) requestIdToken(webClientId) }
            .requestEmail()
            // Read-only Google Calendar access so the mobile app can show the
            // same upcoming meetings (BUGÜN / BU HAFTA) the web app reads from
            // Google Calendar. Granted once at sign-in; CalendarSync fetches the
            // OAuth access token for these scopes via GoogleAuthUtil.
            .requestScopes(Scope(CALENDAR_READONLY_SCOPE))
            .build()
        googleClient = GoogleSignIn.getClient(context, gso)
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun isSignedIn(): Boolean = auth.currentUser != null

    /** Intent to launch the Google account chooser. */
    fun signInIntent(): Intent = googleClient.signInIntent

    /** Exchange the Google sign-in result for a Firebase credential. */
    suspend fun handleSignInResult(data: Intent?): Result<Unit> = runCatching {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)
        val idToken = account.idToken ?: error("Google ID token alınamadı (default_web_client_id eksik olabilir)")
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(cred).await()
        Unit
    }

    suspend fun signOut() {
        runCatching { googleClient.signOut().await() }
        auth.signOut()
    }

    companion object {
        const val CALENDAR_READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    }
}
