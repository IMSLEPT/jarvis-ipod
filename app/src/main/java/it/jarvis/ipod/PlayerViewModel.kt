package it.jarvis.ipod

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import it.jarvis.ipod.data.*
import it.jarvis.ipod.drive.DriveFile
import it.jarvis.ipod.playback.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Playback(val connected: Boolean = false, val id: String? = null, val title: String = "Nessun brano",
    val artist: String = "La tua musica, un giro alla volta", val album: String = "", val artwork: String? = null,
    val playing: Boolean = false, val buffering: Boolean = false, val position: Long = 0, val duration: Long = 0,
    val shuffle: Boolean = false, val repeat: Int = 0, val queue: List<MediaItem> = emptyList(), val error: String? = null)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val graph = application as JarvisApp
    val settings = graph.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val tracks = graph.dao.observeTracks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = graph.dao.observePlaylists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = graph.dao.observeEntries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playback = MutableStateFlow(Playback())
    val message = MutableStateFlow<String?>(null)
    val scanning = MutableStateFlow(false)
    val driveFiles = MutableStateFlow<List<DriveFile>>(emptyList())
    val driveBusy = MutableStateFlow(false)
    val driveError = MutableStateFlow<String?>(null)
    val downloads = MutableStateFlow<Set<String>>(emptySet())
    private var driveJob: Job? = null
    private var controller: MediaController? = null
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    init {
        future.addListener({
            runCatching {
                controller = future.get()
                controller!!.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { update() }
                })
                update()
            }.onFailure { message.value = "Lettore non disponibile: ${it.localizedMessage}" }
        }, ContextCompat.getMainExecutor(application))
        viewModelScope.launch { while (isActive) { update(); delay(400) } }
        keepBackupUpToDate()
    }
    private val resolver get() = getApplication<Application>().contentResolver
    /** Se l'utente ha scelto un file di backup, lo riscrive (dopo una breve pausa) a ogni modifica delle impostazioni. */
    @OptIn(FlowPreview::class)
    private fun keepBackupUpToDate() {
        viewModelScope.launch {
            settings.map { SettingsBackup.toJson(it) to it.backupUri }.distinctUntilChanged().drop(1).debounce(1500).collect { (json, uri) ->
                if (uri.isNotBlank()) try {
                    withContext(Dispatchers.IO) { resolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(json.toByteArray()) } }
                } catch (e: Exception) { message.value = "Backup automatico non riuscito: ${e.localizedMessage ?: "file non raggiungibile"}. Salva di nuovo il backup." }
            }
        }
    }
    /** Salva il backup nel file scelto e lo tiene aggiornato da quel momento. */
    fun saveBackup(uri: Uri) = launch {
        withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")!!.use { it.write(SettingsBackup.toJson(settings.value).toByteArray()) } }
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        graph.preferences.set(Preferences.backupUri, uri.toString())
        message.value = "Backup salvato. Da ora si aggiorna da solo a ogni modifica."
    }
    fun restoreBackup(uri: Uri) = launch {
        val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
        val backup = SettingsBackup.fromJson(text)
        if (backup == null) { message.value = "Il file scelto non è un backup di Jarvis."; return@launch }
        graph.preferences.restore(backup)
        message.value = "Ripristinati: ${backup.driveFolders.size} cartelle Drive" + (if (backup.spotifyClientId.isNotBlank()) ", Client ID Spotify" else "") +
            ". Ricollega Google Drive. Per riattivare il backup automatico, salvalo di nuovo."
    }
    private fun update() {
        val p = controller ?: return
        val m = p.currentMediaItem?.mediaMetadata
        playback.value = Playback(true, p.currentMediaItem?.mediaId, m?.title?.toString() ?: "Nessun brano",
            m?.artist?.toString() ?: "La tua musica, un giro alla volta", m?.albumTitle?.toString() ?: "", m?.artworkUri?.toString(),
            p.playWhenReady && p.playerError == null && p.playbackState != Player.STATE_ENDED,
            p.playbackState == Player.STATE_BUFFERING, p.currentPosition.coerceAtLeast(0), p.duration.coerceAtLeast(0),
            p.shuffleModeEnabled, p.repeatMode, (0 until p.mediaItemCount).map { p.getMediaItemAt(it) },
            p.playerError?.let { "Riproduzione interrotta. Verifica connessione, permessi o account Drive, poi premi Play per riprovare." })
    }
    private fun action(block: (MediaController) -> Unit) {
        val p = controller
        if (p == null) { message.value = "Il lettore si sta collegando. Riprova tra un istante."; return }
        runCatching { block(p); update() }.onFailure { message.value = it.localizedMessage ?: "Comando non disponibile" }
    }
    fun play(list: List<Track>, track: Track) = action { p ->
        if (list.isEmpty()) return@action
        p.setMediaItems(list.map { it.mediaItem() }, list.indexOfFirst { it.id == track.id }.coerceAtLeast(0), 0)
        p.prepare(); p.play()
    }
    fun togglePlay() = action { p ->
        if (p.mediaItemCount == 0) return@action
        when {
            p.playerError != null || p.playbackState == Player.STATE_IDLE -> { p.prepare(); p.play() }
            p.playbackState == Player.STATE_ENDED -> { p.seekToDefaultPosition(); p.play() }
            p.playWhenReady -> p.pause() // Also pause while a cloud file is buffering.
            else -> p.play()
        }
    }
    fun pause() = action { it.pause() }
    fun next() = action { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = action { it.seekToPrevious() }
    fun seekBy(ms: Long) = action { it.seekTo((it.currentPosition + ms).coerceIn(0, it.duration.coerceAtLeast(0))) }
    fun seekTo(ms: Long) = action { it.seekTo(ms.coerceIn(0, it.duration.coerceAtLeast(0))) }
    fun shuffle() = action { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun repeat() = action { it.repeatMode = when(it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }
    fun enqueue(track: Track) = action { it.addMediaItem(track.mediaItem()); message.value = "Aggiunto alla coda" }
    fun removeQueue(index: Int) = action { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
    fun moveQueue(index: Int, target: Int) = action { if (target in 0 until it.mediaItemCount) it.moveMediaItem(index, target) }
    fun playQueue(index: Int) = action { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.prepare(); it.play() } }
    fun favorite(id: String) = launch { graph.dao.toggleFavorite(id) }
    fun scan() {
        if (scanning.value) return
        launch { scanning.value = true; try { message.value = "${graph.local.scan()} brani trovati" } finally { scanning.value = false } }
    }
    /** [pick] = scelta delle cartelle: elenca solo sottocartelle e non salva alcun brano nella libreria. */
    fun loadDrive(id: String, query: String = "", title: String = "Google Drive", pick: Boolean = false) {
        driveJob?.cancel()
        driveJob = launch {
            driveBusy.value = true; driveError.value = null; driveFiles.value = emptyList()
            try {
                val files = graph.drive.list(id, query, foldersOnly = pick)
                if (!pick) graph.drive.remember(files, title)
                driveFiles.value = files
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { driveError.value = e.localizedMessage ?: "Connessione Drive non disponibile" }
            finally { driveBusy.value = false }
        }
    }
    fun addDriveFolder(folder: DriveFolder) = launch {
        val current = settings.value.driveFolders
        if (current.none { it.id == folder.id }) {
            graph.preferences.set(Preferences.driveFolders, Preferences.encodeFolders(current + folder))
        }
        message.value = "Cartella aggiunta: ${folder.name}"
    }
    fun removeDriveFolder(id: String) = launch {
        graph.preferences.set(Preferences.driveFolders, Preferences.encodeFolders(settings.value.driveFolders.filter { it.id != id }))
        message.value = "Cartella rimossa dall’elenco"
    }
    fun driveConnected() = launch {
        val email = graph.drive.accountEmail()
        graph.preferences.set(Preferences.email, email)
        message.value = "Google Drive collegato"
    }
    fun disconnectDrive() = launch {
        if (playback.value.id?.startsWith("drive:") == true) pause()
        graph.auth.disconnect(); driveFiles.value = emptyList(); message.value = "Account scollegato"
    }
    fun download(track: Track) {
        if (track.id in downloads.value) return
        launch {
            downloads.value = downloads.value + track.id
            try { graph.drive.download(track); message.value = "Disponibile offline: ${track.title}" }
            finally { downloads.value = downloads.value - track.id }
        }
    }
    fun deleteOffline(track: Track) = launch { graph.drive.deleteOffline(track) }
    fun createPlaylist(name: String) = launch { if (name.isNotBlank()) graph.dao.createPlaylist(Playlist(name = name.trim())) }
    fun deletePlaylist(id: Long) = launch { graph.dao.deletePlaylist(id) }
    fun addToPlaylist(playlist: Long, track: String) = launch { graph.dao.addEntry(PlaylistEntry(playlist, track)); message.value = "Brano aggiunto alla playlist" }
    fun removeFromPlaylist(playlist: Long, track: String) = launch { graph.dao.removeEntry(playlist, track) }
    fun <T> preference(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) = launch { graph.preferences.set(key, value) }
    fun clearCache() = launch {
        withContext(Dispatchers.IO) { graph.audioCache.keys.forEach { graph.audioCache.removeResource(it) } }
        message.value = "Cache streaming svuotata. I download offline sono conservati."
    }
    fun launch(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch(e: CancellationException) { throw e }
        catch(e: Exception) { message.value = e.localizedMessage ?: "Operazione non riuscita. Riprova." }
    }
    override fun onCleared() { MediaController.releaseFuture(future); super.onCleared() }
}
