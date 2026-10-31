package it.jarvis.ipod.spotify

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.SpotifyRemoteServiceException
import com.spotify.android.appremote.api.error.UnsupportedFeatureVersionException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

enum class SpotifyStatus { IDLE, CONNECTING, CONNECTED, FAILED }

/** Stato del lettore Spotify. [repeat] usa gli stessi valori di Media3: 0 spento, 1 brano, 2 tutto. */
data class SpotifyState(
    val status: SpotifyStatus = SpotifyStatus.IDLE,
    val id: String? = null, val title: String = "", val artist: String = "", val album: String = "",
    val playing: Boolean = false, val position: Long = 0, val duration: Long = 0,
    val shuffle: Boolean = false, val repeat: Int = 0, val cover: Bitmap? = null
)

/**
 * Comanda l'app Spotify installata tramite l'SDK ufficiale App Remote: Spotify riproduce in background (audio, rete, cache
 * e Premium restano suoi), Jarvis mostra brano e copertina e invia i comandi. Serve l'app Spotify, un Client ID registrato
 * nel Dashboard Spotify e, per scegliere un brano preciso, un account Premium.
 */
class SpotifyBridge(private val appContext: Context) {
    companion object {
        const val REDIRECT_URI = "jarvisipod://spotify-callback"

        /** Impronta SHA-1 del certificato con cui è firmata questa installazione: va registrata nel Dashboard Spotify. */
        fun signingSha1(context: Context): String? {
            return try {
                val pm = context.packageManager
                val bytes = if (Build.VERSION.SDK_INT >= 28) {
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
                }
                if (bytes == null) null else MessageDigest.getInstance("SHA-1").digest(bytes).joinToString(":") { "%02X".format(it) }
            } catch (e: Exception) { null }
        }
    }

    private val mutableState = MutableStateFlow(SpotifyState())
    val state: StateFlow<SpotifyState> = mutableState
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Messaggi da mostrare all'utente (connessione fallita, comando rifiutato). */
    val errors: SharedFlow<String> = mutableErrors

    private var remote: SpotifyAppRemote? = null
    private var lastImage: String? = null
    private var basePosition = 0L
    private var baseAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Spotify manda la posizione solo quando cambia lo stato: tra un evento e l'altro la facciamo avanzare noi.
        scope.launch {
            while (true) {
                delay(400)
                val s = mutableState.value
                if (s.status == SpotifyStatus.CONNECTED && s.playing && s.duration > 0) {
                    val position = (basePosition + SystemClock.elapsedRealtime() - baseAt).coerceIn(0, s.duration)
                    mutableState.update { it.copy(position = position) }
                }
            }
        }
    }

    /** [context] deve essere quello dell'Activity: Spotify può dover mostrare la schermata di autorizzazione. */
    fun connect(context: Context, clientId: String) {
        val id = clientId.trim()
        if (id.isEmpty()) { fail("Inserisci il Client ID di Spotify."); return }
        val status = mutableState.value.status
        if (status == SpotifyStatus.CONNECTING || (status == SpotifyStatus.CONNECTED && remote?.isConnected == true)) return
        if (!SpotifyAppRemote.isSpotifyInstalled(context)) {
            mutableState.update { it.copy(status = SpotifyStatus.FAILED) }
            fail("L’app Spotify non è installata su questo telefono.")
            return
        }
        mutableState.update { it.copy(status = SpotifyStatus.CONNECTING) }
        val params = ConnectionParams.Builder(id).setRedirectUri(REDIRECT_URI).showAuthView(true).build()
        try {
            SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    remote = spotifyAppRemote
                    mutableState.update { it.copy(status = SpotifyStatus.CONNECTED) }
                    subscribe(spotifyAppRemote)
                }
                override fun onFailure(error: Throwable) {
                    remote = null
                    mutableState.update { it.copy(status = SpotifyStatus.FAILED) }
                    fail(explain(error))
                }
            })
        } catch (e: Exception) {
            mutableState.update { it.copy(status = SpotifyStatus.FAILED) }
            fail(explain(e))
        }
    }

    /** Chiude solo il collegamento: Spotify continua a riprodurre. */
    fun disconnect() {
        remote?.let { runCatching { SpotifyAppRemote.disconnect(it) } }
        remote = null
        lastImage = null
        mutableState.value = SpotifyState()
    }

    private fun subscribe(r: SpotifyAppRemote) {
        r.playerApi.subscribeToPlayerState()
            .setEventCallback { s -> onPlayerState(r, s) }
            .setErrorCallback { t -> fail(explain(t)) }
    }

    private fun onPlayerState(r: SpotifyAppRemote, s: PlayerState) {
        val track = s.track
        basePosition = s.playbackPosition
        baseAt = SystemClock.elapsedRealtime()
        val image = track?.imageUri?.raw
        if (image == null) lastImage = null
        else if (image != lastImage) {
            lastImage = image
            r.imagesApi.getImage(track.imageUri, Image.Dimension.LARGE)
                .setResultCallback { bitmap -> mutableState.update { it.copy(cover = bitmap) } }
                .setErrorCallback { }
        }
        mutableState.update {
            it.copy(
                id = track?.uri, title = track?.name.orEmpty(), artist = track?.artist?.name.orEmpty(), album = track?.album?.name.orEmpty(),
                playing = !s.isPaused, position = s.playbackPosition, duration = track?.duration ?: 0L,
                shuffle = s.playbackOptions?.isShuffling == true, repeat = s.playbackOptions?.repeatMode ?: 0,
                cover = if (image == null) null else it.cover
            )
        }
    }

    private fun fail(message: String) { mutableErrors.tryEmit(message) }

    private fun watch(call: CallResult<*>) { call.setErrorCallback { t -> fail(explain(t)) } }

    private fun api(block: (SpotifyAppRemote) -> Unit) {
        val r = remote
        if (r == null || !r.isConnected) { fail("Spotify non è collegato. Apri la sezione Spotify per riconnetterti."); return }
        try { block(r) } catch (e: Exception) { fail(explain(e)) }
    }

    fun resume() = api { watch(it.playerApi.resume()) }
    fun pause() = api { watch(it.playerApi.pause()) }
    fun toggle() = api { if (mutableState.value.playing) watch(it.playerApi.pause()) else watch(it.playerApi.resume()) }
    fun next() = api { watch(it.playerApi.skipNext()) }
    fun previous() = api { watch(it.playerApi.skipPrevious()) }
    fun seekTo(ms: Long) = api { watch(it.playerApi.seekTo(ms.coerceIn(0, mutableState.value.duration.coerceAtLeast(0)))) }
    fun seekBy(ms: Long) = seekTo(mutableState.value.position + ms)
    fun shuffle() = api { watch(it.playerApi.setShuffle(!mutableState.value.shuffle)) }
    fun repeat() = api {
        val next = when (mutableState.value.repeat) { Repeat.OFF -> Repeat.ALL; Repeat.ALL -> Repeat.ONE; else -> Repeat.OFF }
        watch(it.playerApi.setRepeat(next))
    }

    /** Avvia un brano, album o playlist da un link o URI Spotify. Restituisce false se il testo non è riconosciuto. */
    fun playLink(text: String): Boolean {
        val uri = SpotifyLinks.parse(text)
        if (uri == null) { fail("Link non riconosciuto. Incolla un link “Condividi” di Spotify o un URI spotify:…"); return false }
        api { watch(it.playerApi.play(uri)) }
        return true
    }

    private fun explain(t: Throwable): String = when (t) {
        is CouldNotFindSpotifyApp -> "L’app Spotify non è installata su questo telefono."
        is NotLoggedInException -> "Apri l’app Spotify, accedi al tuo account e riprova."
        is AuthenticationFailedException -> "Spotify ha rifiutato l’app. Controlla Client ID, nome pacchetto, impronta SHA-1 e Redirect URI nel Dashboard Spotify."
        is UserNotAuthorizedException -> "Non hai ancora consentito a Jarvis di controllare Spotify."
        is OfflineModeException -> "Spotify è in modalità offline: serve una connessione per collegarsi."
        is SpotifyDisconnectedException, is SpotifyConnectionTerminatedException -> "Il collegamento con Spotify si è interrotto."
        is SpotifyRemoteServiceException -> "Spotify non risponde. Aprilo una volta e riprova."
        is UnsupportedFeatureVersionException -> "Questa funzione richiede un’app Spotify più recente."
        else -> (t.localizedMessage ?: "Operazione Spotify non riuscita") + ". Per scegliere un brano preciso serve Spotify Premium."
    }
}
