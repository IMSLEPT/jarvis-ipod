package it.jarvis.ipod.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.*
import it.jarvis.ipod.JarvisApp
import it.jarvis.ipod.MainActivity
import it.jarvis.ipod.data.Preferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = application as JarvisApp
    private var restoring = true
    private var snapshot: Job? = null
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scope.launch { if (player.playerError != null && player.playWhenReady) player.prepare() }
        }
    }
    override fun onCreate() {
        super.onCreate()
        val resolver = ResolvingDataSource.Resolver { spec ->
            if (spec.uri.scheme != "drive") spec else runBlocking(Dispatchers.IO) {
                val id = spec.uri.host ?: error("File Drive non valido")
                val track = graph.dao.track("drive:$id")
                val offline = track?.offlinePath?.let(::File)?.takeIf { it.exists() }
                if (offline != null) spec.withUri(Uri.fromFile(offline))
                else {
                    // OAuth: bearer; senza account: chiave API (e resource key) scelte dal repository.
                    val (url, headers) = graph.drive.streamRequest(id)
                    spec.withUri(Uri.parse(url)).withRequestHeaders(spec.httpRequestHeaders + headers)
                }
            }
        }
        // Resolve first, then dispatch the resulting URI to HTTP or the private offline file reader.
        val upstream = ResolvingDataSource.Factory(DefaultDataSource.Factory(this,
            DefaultHttpDataSource.Factory().setConnectTimeoutMs(20_000).setReadTimeoutMs(30_000)), resolver)
        val cached = CacheDataSource.Factory().setCache(graph.audioCache).setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Il PCM passa da BeatAnalyzer (nessun permesso, nessuna modifica dell'audio) per far reagire l'alone neon.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(BeatAnalyzer()))).build()
        }
        player = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(cached)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    if (!restoring && item != null) scope.launch { graph.dao.played(item.mediaId, System.currentTimeMillis()) }
                }
                override fun onEvents(p: Player, events: Player.Events) { if (!restoring) persistSoon() }
                override fun onPlayerError(error: PlaybackException) {
                    // A 401 may come from a long playback session; invalidate the cached grant.
                    var cause: Throwable? = error
                    while (cause != null) {
                        if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 401) {
                            scope.launch { graph.auth.invalidate() }
                            break
                        }
                        cause = cause.cause
                    }
                }
            })
        }
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(launch)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return if (controller.packageName == packageName || controller.isTrusted)
                        super.onConnect(session, controller) else MediaSession.ConnectionResult.reject()
                }
            }).build()
        connectivity.registerDefaultNetworkCallback(networkCallback)
        scope.launch {
            restore()
            restoring = false
            while (isActive) { delay(3000); save() }
        }
    }
    private suspend fun restore() {
        runCatching {
            val pref = graph.preferences.data.first()
            val values = JSONArray(pref[Preferences.queue] ?: "[]")
            val tracks = (0 until values.length()).mapNotNull { graph.dao.track(values.getString(it)) }
            if (tracks.isNotEmpty() && player.mediaItemCount == 0) {
                player.setMediaItems(tracks.map { it.mediaItem() }, (pref[Preferences.queueIndex] ?: 0).coerceIn(tracks.indices),
                    (pref[Preferences.position] ?: 0).coerceAtLeast(0))
                player.shuffleModeEnabled = pref[Preferences.shuffle] ?: false
                player.repeatMode = pref[Preferences.repeat] ?: Player.REPEAT_MODE_OFF
                player.prepare() // Always restore paused. No surprise playback on launch.
            }
        }
    }
    private fun persistSoon() { snapshot?.cancel(); snapshot = scope.launch { delay(200); save() } }
    private suspend fun save() {
        if (restoring) return
        graph.preferences.savePlayback((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0), player.shuffleModeEnabled, player.repeatMode)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady) stopSelf() }
    override fun onDestroy() {
        // Capture player state on its looper, then persist without blocking Android's
        // main thread. DataStore may already be processing a transform from that thread.
        if (::player.isInitialized && !restoring) {
            val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
            val index = player.currentMediaItemIndex.coerceAtLeast(0)
            val position = player.currentPosition.coerceAtLeast(0)
            val shuffled = player.shuffleModeEnabled
            val repeated = player.repeatMode
            graph.persistenceScope.launch {
                runCatching { graph.preferences.savePlayback(ids, index, position, shuffled, repeated) }
                    .onFailure { android.util.Log.w("PlaybackService", "Unable to save playback on shutdown", it) }
            }
        }
        scope.cancel()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        session?.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
