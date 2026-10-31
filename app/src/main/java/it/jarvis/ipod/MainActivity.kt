package it.jarvis.ipod

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.Identity
import it.jarvis.ipod.ui.JarvisUI

class MainActivity : ComponentActivity() {
    private val model: PlayerViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.scan() else model.message.value = "Permesso audio non concesso. Puoi abilitarlo nelle impostazioni Android; Google Drive resta disponibile."
    }
    private val authorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            runCatching { model.graph.auth.acceptIntent(result.data); model.driveConnected() }
                .onFailure { model.message.value = "Autorizzazione Google non riuscita: ${it.localizedMessage}" }
        } else model.message.value = "Collegamento Google annullato"
    }
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(model::saveBackup) }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::restoreBackup) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            JarvisUI(model, ::requestAudio, ::connectDrive,
                saveBackup = { createBackup.launch("jarvis-backup.json") },
                restoreBackup = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        }
        if (audioGranted()) model.scan()
    }
    private fun audioPermission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun audioGranted() = ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED
    private fun requestAudio() { if (audioGranted()) model.scan() else permission.launch(audioPermission()) }
    private fun connectDrive() {
        Identity.getAuthorizationClient(this).authorize(model.graph.auth.request())
            .addOnSuccessListener(this) { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending != null) authorization.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    else model.message.value = "Google non ha restituito la schermata di autorizzazione"
                } else {
                    runCatching { model.graph.auth.accept(result); model.driveConnected() }
                        .onFailure { model.message.value = it.localizedMessage }
                }
            }.addOnFailureListener(this) { model.message.value = "Impossibile collegare Google. Verifica Play Services, rete e configurazione OAuth (package e SHA-1). ${it.localizedMessage ?: ""}" }
    }
}
