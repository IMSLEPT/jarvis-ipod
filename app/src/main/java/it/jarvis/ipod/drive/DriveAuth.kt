package it.jarvis.ipod.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import it.jarvis.ipod.data.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

class DriveAuth(private val context: Context, private val preferences: Preferences) {
    companion object { const val SCOPE = "https://www.googleapis.com/auth/drive.readonly" }
    private val lock = Mutex()
    @Volatile private var token: String? = null
    private var received = 0L
    fun request(accountEmail: String? = null): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE))).apply {
            if (!accountEmail.isNullOrBlank()) setAccount(Account(accountEmail, "com.google"))
        }.build()
    fun accept(result: AuthorizationResult) {
        token = result.accessToken ?: throw IOException("Google non ha restituito un token. Ricollega Drive.")
        received = android.os.SystemClock.elapsedRealtime()
    }
    fun acceptIntent(intent: Intent?) { accept(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)) }
    suspend fun accessToken(): String = lock.withLock {
        token?.takeIf { android.os.SystemClock.elapsedRealtime() - received < 45 * 60 * 1000 }?.let { return@withLock it }
        val email = preferences.settings.first().driveEmail
        if (email.isBlank()) throw IOException("Collega Google Drive dalle impostazioni.")
        val result = Identity.getAuthorizationClient(context).authorize(request(email)).await()
        if (result.hasResolution()) throw IOException("L'autorizzazione Google è scaduta. Ricollega Drive.")
        accept(result)
        token!!
    }
    suspend fun invalidate() {
        val previous = token
        token = null
        if (previous != null) withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, previous) } }
    }
    suspend fun disconnect() {
        val email = preferences.settings.first().driveEmail
        if (email.isNotBlank()) {
            Identity.getAuthorizationClient(context).revokeAccess(RevokeAccessRequest.builder()
                .setAccount(Account(email, "com.google")).setScopes(listOf(Scope(SCOPE))).build()).await()
        }
        token = null
        preferences.set(Preferences.email, "")
    }
}
