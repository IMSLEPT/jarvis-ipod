package it.jarvis.ipod.ui

import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.jarvis.ipod.*
import it.jarvis.ipod.data.*
import it.jarvis.ipod.drive.DriveFolder
import it.jarvis.ipod.drive.DriveLinks
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private data class Route(val type: String, val title: String, val arg: String = "")
private data class Entry(val title: String, val icon: ImageVector = Icons.Rounded.ChevronRight,
    val subtitle: String? = null, val color: Color = Ice, val track: Track? = null, val run: () -> Unit)

@Composable
fun JarvisUI(vm: PlayerViewModel, requestAudio: () -> Unit, connectDrive: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistEntries by vm.entries.collectAsStateWithLifecycle()
    val playing by vm.playback.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val driveFiles by vm.driveFiles.collectAsStateWithLifecycle()
    val driveBusy by vm.driveBusy.collectAsStateWithLifecycle()
    val driveError by vm.driveError.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val routes = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Route>, String>(
        save = { it.flatMap { r -> listOf(r.type, r.title, r.arg) } },
        restore = { values -> mutableStateListOf<Route>().apply { values.chunked(3).forEach { add(Route(it[0], it[1], it[2])) } } }
    )) { mutableStateListOf(Route("home", "iPod")) }
    val route = routes.last()
    var selected by rememberSaveable(route) { mutableIntStateOf(0) }
    var query by rememberSaveable(route) { mutableStateOf("") }
    var songOptions by remember { mutableStateOf<Track?>(null) }
    var playlistTarget by remember { mutableStateOf<Track?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var keyDialog by remember { mutableStateOf(false) }
    var keyText by remember { mutableStateOf("") }
    var folderDialog by remember { mutableStateOf(false) }
    var folderText by remember { mutableStateOf("") }
    var removeFolder by remember { mutableStateOf<DriveFolder?>(null) }
    val keyMode = settings.driveEmail.isBlank() && settings.apiKey.isNotBlank()
    val savedFolders = remember(settings.folders) { DriveLinks.decode(settings.folders) }
    val context = LocalContext.current
    val view = LocalView.current
    fun push(type: String, title: String, arg: String = "") { routes += Route(type, title, arg) }
    fun back() { if (routes.size > 1) routes.removeAt(routes.lastIndex) }
    BackHandler(routes.size > 1) { back() }
    LaunchedEffect(route.type, route.arg, query, settings.driveEmail, settings.apiKey) {
        if (route.type == "drive" && (settings.driveEmail.isNotBlank() || keyMode)) {
            if (query.isNotBlank()) delay(350)
            vm.loadDrive(route.arg.ifBlank { "root" }, query, route.title)
        }
    }
    val sorted = remember(tracks, settings.sort) { when(settings.sort) {
        "Artista" -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        "Album" -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
        "Data" -> tracks.sortedByDescending { it.added }
        else -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    } }
    val local = sorted.filter { it.source == "local" }
    val current = tracks.find { it.id == playing.id }
    fun play(list: List<Track>, track: Track) { vm.play(list, track); push("playing", "In riproduzione") }
    fun songs(values: List<Track>): List<Entry> = values.map { track ->
        Entry(track.title, Icons.Rounded.MusicNote, "${track.artist}${if (track.offlinePath != null) " · offline" else ""}", track = track) { play(values, track) }
    }
    fun launchSpotify(app: Boolean) {
        vm.pause()
        try {
            if (app) {
                val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (intent != null) context.startActivity(intent)
                else { vm.message.value = "App Spotify non installata. Apro il sito ufficiale."; CustomTabsIntent.Builder().setShowTitle(false).build().launchUrl(context, Uri.parse("https://open.spotify.com")) }
            } else CustomTabsIntent.Builder().setShowTitle(false).build().launchUrl(context, Uri.parse("https://open.spotify.com"))
        } catch (e: Exception) { vm.message.value = "Nessuna app o browser disponibile per aprire Spotify." }
    }
    fun openUrl(url: String) {
        try { CustomTabsIntent.Builder().setShowTitle(false).build().launchUrl(context, Uri.parse(url)) }
        catch (e: Exception) { vm.message.value = "Nessun browser disponibile per aprire il link." }
    }
    val maskedKey = if (settings.apiKey.isBlank()) "Non impostata" else settings.apiKey.take(4) + "…" + settings.apiKey.takeLast(4)
    val driveItems = driveFiles.map { f -> Entry(f.name, if (f.folder) Icons.Rounded.Folder else Icons.Rounded.MusicNote,
        if (f.folder) "Cartella" else "Google Drive", track = if (f.folder) null else tracks.find { it.id == "drive:${f.id}" }) {
        if (f.folder) push("drive", f.name, f.id)
        else {
            val folderTracks = driveFiles.filter { !it.folder }.mapNotNull { file -> tracks.find { it.id == "drive:${file.id}" } }
            folderTracks.find { it.id == "drive:${f.id}" }?.let { play(folderTracks, it) }
        }
    } }
    val home = listOf(
        Entry("Musica locale", Icons.Rounded.MusicNote, color = Blue) { push("library", "Libreria"); requestAudio() },
        Entry("Google Drive", Icons.Rounded.AddToDrive, color = Color(0xFF34A853)) { push("drive", "Google Drive", "root") },
        Entry("Spotify", Icons.Rounded.GraphicEq, color = Color(0xFF1ED760)) { push("spotify", "Spotify") },
        Entry("Playlist", Icons.Rounded.QueueMusic) { push("playlists", "Playlist") },
        Entry("Album", Icons.Rounded.Album) { push("albums", "Album") },
        Entry("Artisti", Icons.Rounded.Person) { push("artists", "Artisti") },
        Entry("Preferiti", Icons.Rounded.Favorite, color = Color(0xFFF74C70)) { push("favorites", "Preferiti") },
        Entry("Riprodotti di recente", Icons.Rounded.History) { push("recent", "Recenti") },
        Entry("Ricerca", Icons.Rounded.Search) { push("search", "Ricerca") },
        Entry("Impostazioni", Icons.Rounded.Settings) { push("settings", "Impostazioni") }
    )
    val entries: List<Entry> = when(route.type) {
        "home" -> home
        "library" -> listOf(
            Entry("Tutti i brani", Icons.Rounded.MusicNote, "${local.size}") { push("tracks", "Musica locale") },
            Entry("Album", Icons.Rounded.Album, "${local.map { it.album to it.artist }.distinct().size}") { push("albums", "Album") },
            Entry("Artisti", Icons.Rounded.Person, "${local.map { it.artist }.distinct().size}") { push("artists", "Artisti") },
            Entry("Playlist", Icons.Rounded.QueueMusic, "${playlists.size}") { push("playlists", "Playlist") },
            Entry("Preferiti", Icons.Rounded.Favorite, "${tracks.count { it.favorite }}", Color(0xFFF74C70)) { push("favorites", "Preferiti") },
            Entry("Recenti", Icons.Rounded.History, "${tracks.count { it.lastPlayed > 0 }}") { push("recent", "Recenti") }
        )
        "tracks" -> songs(local)
        "favorites" -> songs(sorted.filter { it.favorite })
        "recent" -> songs(tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(100))
        "offline" -> songs(sorted.filter { it.offlinePath != null })
        "albums" -> sorted.distinctBy { it.album to it.artist }.map { t -> Entry(t.album, Icons.Rounded.Album, t.artist) { push("album", t.album, t.id) } }
        "album" -> tracks.find { it.id == route.arg }?.let { t -> songs(sorted.filter { it.album == t.album && it.artist == t.artist }) } ?: emptyList()
        "artists" -> sorted.map { it.artist }.distinct().sorted().map { artist -> Entry(artist, Icons.Rounded.Person) { push("artist", artist, artist) } }
        "artist" -> songs(sorted.filter { it.artist == route.arg })
        "search" -> songs(sorted.filter { query.isNotBlank() && listOf(it.title, it.artist, it.album).any { text -> text.contains(query, true) } })
        "playlists" -> listOf(Entry("Nuova playlist", Icons.Rounded.Add) { newPlaylist = true }) + playlists.map { p -> Entry(p.name, Icons.Rounded.QueueMusic,
            "${playlistEntries.count { it.playlistId == p.id }} brani") { push("playlist", p.name, p.id.toString()) } }
        "playlist" -> songs(playlistEntries.filter { it.playlistId.toString() == route.arg }.mapNotNull { entry -> tracks.find { it.id == entry.trackId } })
        "drive" -> when {
            settings.driveEmail.isNotBlank() -> driveItems
            keyMode && route.arg.ifBlank { "root" } == "root" ->
                savedFolders.map { f -> Entry(f.name, Icons.Rounded.Folder, "Cartella condivisa") { push("drive", f.name, f.id) } } +
                    Entry("Chiave API e cartelle", Icons.Rounded.VpnKey, "${savedFolders.size} cartelle salvate") { push("driveapi", "Drive con chiave API") }
            keyMode -> driveItems
            else -> listOf(
                Entry("Collega Google Drive", Icons.Rounded.AddToDrive, "Accedi al tuo account Google", Color(0xFF48B974)) { connectDrive() },
                Entry("Usa chiave API e cartelle condivise", Icons.Rounded.VpnKey, "Senza account: solo cartelle con link", Color(0xFF48B974)) { push("driveapi", "Drive con chiave API") }
            )
        }
        "driveapi" -> listOf(
            Entry("1 · Abilita Drive API", Icons.Rounded.OpenInNew, "Apre Google Cloud: premi Abilita") { openUrl("https://console.cloud.google.com/apis/library/drive.googleapis.com") },
            Entry("2 · Crea chiave API", Icons.Rounded.OpenInNew, "Crea credenziali → Chiave API") { openUrl("https://console.cloud.google.com/apis/credentials") },
            Entry("3 · Incolla chiave API", Icons.Rounded.VpnKey, maskedKey) { keyText = ""; keyDialog = true },
            Entry("4 · Aggiungi cartella condivisa", Icons.Rounded.Link, "Incolla il link della cartella Drive") { folderText = ""; folderDialog = true }
        ) + savedFolders.map { f -> Entry(f.name, Icons.Rounded.Folder, "Tocca per rimuovere") { removeFolder = f } } + listOf(
            Entry("Verifica collegamento", Icons.Rounded.CheckCircle, "Prova reale con chiave e prima cartella") { vm.testDriveApi() },
            Entry("Rimuovi chiave API", Icons.Rounded.Delete, "Cancella la chiave dal telefono") { vm.saveApiKey("") },
            Entry("Come funziona", Icons.Rounded.Info) { info = "Questa modalità non usa il tuo account Google: usa una chiave API tua e legge solo le cartelle che hai condiviso con «Chiunque abbia il link».\n\n1. Abilita la Drive API nel progetto Google Cloud.\n2. Crea una chiave API nello stesso progetto. Per sicurezza limitala alla sola Drive API; non limitarla ad «app Android», altrimenti Drive la rifiuta.\n3. Incolla la chiave qui.\n4. Su Drive condividi la cartella con «Chiunque abbia il link» (Visualizzatore) e incolla qui il link.\n\nLa chiave resta solo nella memoria privata di questo telefono. Se colleghi anche un account Google, l’app usa l’account.\n\nL’uso normale rientra nelle quote gratuite standard di Google. Questa modalità non è ancora stata provata con un tuo Drive: usa «Verifica collegamento» per vedere subito l’esito." }
        )
        "spotify" -> listOf(
            Entry("Apri app Spotify", Icons.Rounded.OpenInNew, "Riproduzione nell’app ufficiale", Color(0xFF1ED760)) { launchSpotify(true) },
            Entry("Apri Spotify nel browser", Icons.Rounded.Language, "Accesso tramite browser sicuro") { launchSpotify(false) },
            Entry("Come funziona", Icons.Rounded.Info) { info = "Spotify viene aperto nell’app ufficiale o in una scheda del browser. Login, riproduzione e abbonamento sono gestiti da Spotify.\n\nWebView non è tra i browser Android supportati dichiarati. Login e riproduzione non sono stati verificati con un account in questo ambiente.\n\nLa Click Wheel controlla solo i brani locali e Drive. Torna qui con Indietro e premi MENU per uscire dalla sezione." }
        )
        "settings" -> listOf(
            Entry("Aspetto iPod", Icons.Rounded.Palette, if (settings.midnight) "Midnight Black" else "Silver Classic") { push("appearance", "Aspetto iPod") },
            Entry("Sensibilità Click Wheel", Icons.Rounded.TouchApp, "${(settings.sensitivity * 100).toInt()}%") { push("sensitivity", "Click Wheel") },
            Entry("Alone neon", Icons.Rounded.AutoAwesome, if(settings.neon) "Attivo · reagisce a musica e ruota" else "Disattivato") { vm.preference(Preferences.neon, !settings.neon) },
            Entry("Feedback aptico", Icons.Rounded.Vibration, if(settings.haptics) "Attivo" else "Disattivato") { vm.preference(Preferences.haptics, !settings.haptics) },
            Entry("Account Google Drive", Icons.Rounded.AddToDrive, settings.driveEmail.ifBlank { "Non collegato" }) { push("account", "Account Google Drive") },
            Entry("Drive con chiave API", Icons.Rounded.VpnKey, if (settings.apiKey.isBlank()) "Non impostata" else "Chiave impostata · ${savedFolders.size} cartelle") { push("driveapi", "Drive con chiave API") },
            Entry("Gestione cache", Icons.Rounded.Storage, "Streaming e download offline") { push("cache", "Gestione cache") },
            Entry("Qualità audio", Icons.Rounded.GraphicEq, "Qualità originale") { info = "I file vengono riprodotti nella qualità originale, senza ricodifica. I formati disponibili dipendono dai decoder Android. La qualità Spotify si gestisce in Spotify." },
            Entry("Ordina musica", Icons.Rounded.Sort, settings.sort) { push("sort", "Ordinamento") },
            Entry(if(scanning) "Scansione in corso…" else "Scansiona musica locale", Icons.Rounded.Refresh) { requestAudio() },
            Entry("Informazioni", Icons.Rounded.Info, "Jarvis iPod · 1.0.0") { info = "Jarvis iPod 1.0.0\nLettore Android nativo con Click Wheel.\n\nInterfaccia ricreata autonomamente dal riferimento fornito. Nessuna affiliazione con Apple, Google o Spotify.\n\nRuota per navigare; al centro confermi. MENU torna indietro. In riproduzione, ruota per cercare nel brano. Tieni premuto precedente o successivo per riavvolgere o avanzare. Le opzioni ⋮ aggiungono i brani a coda e playlist." }
        )
        "appearance" -> listOf(
            Entry("Silver Classic", Icons.Rounded.LightMode, if (!settings.midnight) "Selezionato" else "Alluminio satinato") { vm.preference(Preferences.midnight, false) },
            Entry("Midnight Black", Icons.Rounded.DarkMode, if (settings.midnight) "Selezionato" else "Antracite metallizzato") { vm.preference(Preferences.midnight, true) }
        )
        "account" -> listOf(Entry("Collega / cambia account", Icons.Rounded.AddToDrive, settings.driveEmail.ifBlank { "OAuth Google" }) { connectDrive() }) +
            if (settings.driveEmail.isNotBlank()) listOf(Entry("Scollega account", Icons.Rounded.Logout) { vm.disconnectDrive() }) else emptyList()
        "cache" -> listOf(
            Entry("Svuota cache streaming", Icons.Rounded.CleaningServices, "Limite automatico: 256 MB") { vm.clearCache() },
            Entry("Brani offline", Icons.Rounded.DownloadDone, "${tracks.count { it.offlinePath != null }} file salvati") { push("offline", "Brani offline") },
            Entry("Come scaricare", Icons.Rounded.Info) { info = "Apri le opzioni ⋮ di un brano Drive e scegli Scarica offline. Il file rimane nello spazio privato dell’app.\n\nI download avviati richiedono che l’app resti aperta. Se il processo viene interrotto, ripeti il download. Per rimuovere un file, apri le sue opzioni.\n\nLa cache streaming si pulisce automaticamente; i download si rimuovono manualmente." }
        )
        "sort" -> listOf("Titolo", "Artista", "Album", "Data").map { value -> Entry(value, if(settings.sort == value) Icons.Rounded.Check else Icons.Rounded.Sort) { vm.preference(Preferences.sort, value) } }
        "sensitivity" -> listOf(Entry("Meno sensibile", Icons.Rounded.Remove, "${(settings.sensitivity * 100).toInt()}%") { vm.preference(Preferences.sensitivity, (settings.sensitivity - .25f).coerceAtLeast(.5f)) },
            Entry("Più sensibile", Icons.Rounded.Add, "${(settings.sensitivity * 100).toInt()}%") { vm.preference(Preferences.sensitivity, (settings.sensitivity + .25f).coerceAtMost(2f)) })
        "queue" -> playing.queue.mapIndexed { index, item -> Entry(item.mediaMetadata.title?.toString() ?: "Brano", Icons.Rounded.QueueMusic, item.mediaMetadata.artist?.toString(),
            track = tracks.find { it.id == item.mediaId }) { vm.playQueue(index) } }
        else -> emptyList()
    }
    LaunchedEffect(entries.size) { selected = selected.coerceIn(0, (entries.size - 1).coerceAtLeast(0)) }
    fun haptic() { if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    val colors = darkColorScheme(primary = Blue, background = DisplayBlack, surface = Color(0xFF172131), onSurface = Ice)
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF293C4C), Color(0xFF101820)))).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            IPodShell(settings, display = { scale ->
                Column(Modifier.fillMaxSize()) {
                    StatusBar(route.title, scale)
                    when(route.type) {
                        "home" -> HomeScreen(entries, selected, scale, playing, onSelect = { selected = it; entries[it].run() }, onNow = { if (playing.id != null) push("playing", "In riproduzione") })
                        "library" -> Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp * scale, vertical = 4.dp * scale), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Chip("♫ Musica", true, scale) { push("tracks", "Musica locale") }
                                Chip("Drive", false, scale) { push("drive", "Google Drive", "root") }
                                Chip("Spotify", false, scale) { push("spotify", "Spotify") }
                            }
                            LibraryGrid(entries, selected, scale, Modifier.weight(1f)) { selected = it; entries[it].run() }
                            MiniPlayer(playing, scale, { push("playing", "In riproduzione") }, vm::togglePlay, vm::next)
                        }
                        "playing" -> NowPlaying(playing, current?.favorite == true, scale, vm, { current?.let { vm.favorite(it.id) } }, { push("queue", "Coda di riproduzione") }, { current?.let { songOptions = it } })
                        else -> {
                            if (route.type == "search" || (route.type == "drive" && settings.driveEmail.isNotBlank())) {
                                TextField(query, { query = it; selected = 0 }, placeholder = { Text(if (route.type == "drive") "Cerca in Drive" else "Brani, album, artisti", fontSize = (12 * scale).sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) }, singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = (13 * scale).sp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                            }
                            if (route.type == "playlist") {
                                Text("Elimina playlist", color = Muted, fontSize = (11 * scale).sp, modifier = Modifier.fillMaxWidth().clickable { deletePlaylist = playlists.find { it.id.toString() == route.arg } }.padding(8.dp * scale))
                            }
                            if (driveBusy && route.type == "drive") LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue)
                            if (driveError != null && route.type == "drive") {
                                Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.Center) {
                                    Text(driveError!!, color = Ice, fontSize = (13 * scale).sp)
                                    TextButton(onClick = { vm.loadDrive(route.arg.ifBlank { "root" }, query, route.title) }) { Text("Riprova") }
                                    if (keyMode) TextButton(onClick = { push("driveapi", "Drive con chiave API") }) { Text("Chiave API e cartelle") }
                                    else TextButton(onClick = connectDrive) { Text("Ricollega Google") }
                                }
                            } else if (entries.isEmpty() && !driveBusy) {
                                EmptyScreen(when(route.type) { "search" -> if(query.isBlank()) "Cerca nella tua libreria" else "Nessun risultato"; "drive" -> "Nessun file audio in questa cartella"; "tracks" -> "La tua musica inizia qui"; else -> "Nessun brano da mostrare" },
                                    if(route.type == "tracks") "Autorizza l’accesso alla musica e avvia una scansione." else "Aggiungi musica o esplora un’altra sezione.", scale, Modifier.weight(1f), if(route.type == "tracks") requestAudio else null)
                            } else {
                                EntryList(entries, selected, scale, Modifier.weight(1f), { index -> selected = index; entries[index].run() },
                                    onOptions = { index -> entries[index].track?.let { songOptions = it } },
                                    queue = route.type == "queue", onRemove = vm::removeQueue, onMove = { vm.moveQueue(it, it - 1) })
                            }
                            if (playing.id != null) MiniPlayer(playing, scale, { push("playing", "In riproduzione") }, vm::togglePlay, vm::next)
                        }
                    }
                }
            }, onStep = { delta ->
                if (route.type == "playing") vm.seekBy(delta * 5000L)
                else if (entries.isNotEmpty()) selected = (selected + delta).coerceIn(entries.indices)
                haptic()
            }, onButton = { key ->
                haptic()
                when(key) {
                    "menu" -> back()
                    "select" -> if(route.type == "playing") { current?.let { songOptions = it } } else entries.getOrNull(selected)?.run?.invoke()
                    "play" -> if (route.type != "spotify") {
                        if (playing.id == null) entries.getOrNull(selected)?.track?.let { play(entries.mapNotNull { it.track }, it) } else vm.togglePlay()
                    }
                    "next" -> if (route.type != "spotify") vm.next()
                    "previous" -> if (route.type != "spotify") vm.previous()
                }
            }, onHold = { key -> if (route.type != "spotify") vm.seekBy(if(key == "next") 5000L else -5000L) })
            if (message != null) {
                LaunchedEffect(message) { delay(5500); vm.message.value = null }
                Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).clickable { vm.message.value = null }, color = Color(0xF0202C3C), shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp) {
                    Text(message!!, Modifier.padding(14.dp), color = Ice, fontSize = 13.sp)
                }
            }
        }
        songOptions?.let { track ->
            AlertDialog(onDismissRequest = { songOptions = null }, title = { Text(track.title, maxLines = 2) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Option(if(track.favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") { vm.favorite(track.id); songOptions = null }
                    Option("Aggiungi alla coda") { vm.enqueue(track); songOptions = null }
                    Option("Aggiungi a playlist") { playlistTarget = track; songOptions = null }
                    if(route.type == "playlist") Option("Rimuovi dalla playlist") { vm.removeFromPlaylist(route.arg.toLong(), track.id); songOptions = null }
                    if(track.source == "drive") Option(when { track.id in downloads -> "Download in corso…"; track.offlinePath != null -> "Rimuovi download offline"; else -> "Scarica offline" }) {
                        if(track.offlinePath != null) vm.deleteOffline(track) else vm.download(track); songOptions = null
                    }
                }
            }, confirmButton = { TextButton(onClick = { songOptions = null }) { Text("Chiudi") } })
        }
        playlistTarget?.let { track -> AlertDialog(onDismissRequest = { playlistTarget = null }, title = { Text("Aggiungi a playlist") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if(playlists.isEmpty()) Text("Crea prima una playlist dalla sezione Playlist.")
                playlists.forEach { p -> Option(p.name) { vm.addToPlaylist(p.id, track.id); playlistTarget = null } }
            }
        }, confirmButton = { TextButton(onClick = { playlistTarget = null }) { Text("Chiudi") } }) }
        if(newPlaylist) AlertDialog(onDismissRequest = { newPlaylist = false }, title = { Text("Nuova playlist") }, text = {
            OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Nome") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { vm.createPlaylist(playlistName); playlistName = ""; newPlaylist = false }, enabled = playlistName.isNotBlank()) { Text("Crea") } },
            dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text("Annulla") } })
        deletePlaylist?.let { p -> AlertDialog(onDismissRequest = { deletePlaylist = null }, title = { Text("Elimina ${p.name}?") }, text = { Text("I brani originali vengono conservati.") },
            confirmButton = { TextButton(onClick = { vm.deletePlaylist(p.id); deletePlaylist = null; back() }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Annulla") } }) }
        if (keyDialog) AlertDialog(onDismissRequest = { keyDialog = false }, title = { Text("Chiave API") }, text = {
            OutlinedTextField(keyText, { keyText = it }, label = { Text("Incolla la chiave (inizia con AIza…)") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { vm.saveApiKey(keyText); keyDialog = false }, enabled = keyText.isNotBlank()) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { keyDialog = false }) { Text("Annulla") } })
        if (folderDialog) AlertDialog(onDismissRequest = { folderDialog = false }, title = { Text("Cartella condivisa") }, text = {
            OutlinedTextField(folderText, { folderText = it }, label = { Text("Link della cartella Drive") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { vm.addFolder(folderText); folderDialog = false }, enabled = folderText.isNotBlank()) { Text("Aggiungi") } },
            dismissButton = { TextButton(onClick = { folderDialog = false }) { Text("Annulla") } })
        removeFolder?.let { f -> AlertDialog(onDismissRequest = { removeFolder = null }, title = { Text("Rimuovere ${f.name}?") },
            text = { Text("La cartella resta su Drive; sparisce solo da questa app.") },
            confirmButton = { TextButton(onClick = { vm.removeFolder(f.id); removeFolder = null }) { Text("Rimuovi") } },
            dismissButton = { TextButton(onClick = { removeFolder = null }) { Text("Annulla") } }) }
        info?.let { text -> AlertDialog(onDismissRequest = { info = null }, title = { Text(route.title) }, text = { Text(text, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { info = null }) { Text("Chiudi") } }) }
    }
}

@Composable private fun Option(title: String, onClick: () -> Unit) { Text(title, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), fontSize = 15.sp) }

@Composable
private fun StatusBar(title: String, scale: Float) {
    var time by remember { mutableStateOf(LocalTime.now()) }
    val context = LocalContext.current
    var battery by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) { while(true) { time = LocalTime.now(); battery = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100); delay(15000) } }
    Row(Modifier.fillMaxWidth().height(25.dp * scale).background(Brush.verticalGradient(listOf(Color(0xFF151E29), DisplayBlack))).padding(horizontal = 8.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ice, fontSize = (11.5f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), color = Ice, fontSize = (11.5f * scale).sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(20.dp * scale, 10.dp * scale).border(1.dp, Color(0xFFB9C7C7), RoundedCornerShape(2.dp)).padding(2.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(battery / 100f).background(if(battery < 15) Color(0xFFF85B62) else Color(0xFF56D76B), RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun HomeScreen(entries: List<Entry>, selected: Int, scale: Float, playing: Playback, onSelect: (Int) -> Unit, onNow: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        EntryList(entries, selected, scale, Modifier.fillMaxHeight().weight(.59f).background(Color(0xFFF0F1EF)), onSelect, light = true)
        Column(Modifier.weight(.41f).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF0D263D), DisplayBlack))).padding(9.dp * scale).clickable(onClick = onNow), verticalArrangement = Arrangement.Center) {
            Artwork(playing.artwork, Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp * scale, bottom = 12.dp * scale))
            Text(if(playing.id != null) playing.title else "La tua musica", color = Ice, fontWeight = FontWeight.SemiBold, fontSize = (15 * scale).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if(playing.id != null) playing.artist else "Sempre con te.", color = Muted, fontSize = (12 * scale).sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp * scale))
            LinearProgressIndicator(progress = { if(playing.duration > 0) playing.position.toFloat() / playing.duration else 0f }, color = Blue, trackColor = Color(0xFF344251), modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(20.dp * scale))
        }
    }
}

@Composable
private fun EntryList(entries: List<Entry>, selected: Int, scale: Float, modifier: Modifier = Modifier, onSelect: (Int) -> Unit,
    light: Boolean = false, onOptions: ((Int) -> Unit)? = null, queue: Boolean = false, onRemove: (Int) -> Unit = {}, onMove: (Int) -> Unit = {}) {
    val state = rememberLazyListState()
    LaunchedEffect(selected, entries.size) { if(entries.isNotEmpty()) {
        val visible = state.layoutInfo.visibleItemsInfo
        if(visible.none { it.index == selected && it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }) state.animateScrollToItem(selected)
    } }
    val shape = RoundedCornerShape(12.dp * scale)
    val tile = (if(light) 28.dp else 40.dp) * scale
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = state,
            contentPadding = PaddingValues(horizontal = 6.dp * scale, vertical = 5.dp * scale), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            itemsIndexed(entries) { index, e ->
                val active = selected == index
                val text = if(active || !light) Color.White else Color(0xFF19232B)
                val rowBackground = when {
                    active -> Brush.horizontalGradient(listOf(Color(0xFF3AA3FF), Color(0xFF1262E0)))
                    light -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    else -> Brush.horizontalGradient(listOf(Color(0xFF16212E), Color(0xFF111A25)))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = (if(light) 42.dp else 56.dp) * scale).clip(shape).background(rowBackground)
                    .clickable { onSelect(index) }.padding(horizontal = 7.dp * scale, vertical = 6.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                    if(!queue) {
                        Box(Modifier.size(tile).clip(RoundedCornerShape(10.dp * scale))
                            .background(if(active) Color.White.copy(alpha = .2f) else if(light && e.color == Ice) Color(0xFF1F2A36).copy(alpha = .08f) else e.color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                            Icon(e.icon, null, tint = if(active) Color.White else if(light && e.color == Ice) Color(0xFF374552) else e.color, modifier = Modifier.size(tile * .56f))
                            if(!e.track?.artwork.isNullOrBlank()) coil.compose.AsyncImage(model = e.track?.artwork, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(10.dp * scale))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = text, fontSize = (if(light) 13f else 14f).times(scale).sp, fontWeight = if(active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if(e.subtitle != null) Text(e.subtitle, color = if(active) Color(0xFFDCEBFF) else Muted, fontSize = (11f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                    }
                    if(queue) {
                        Icon(Icons.Rounded.ArrowUpward, "Sposta prima", Modifier.size(30.dp * scale).clickable { onMove(index) }.padding(5.dp), tint = text)
                        Icon(Icons.Rounded.Close, "Rimuovi dalla coda", Modifier.size(30.dp * scale).clickable { onRemove(index) }.padding(5.dp), tint = text)
                    } else if(e.track != null && onOptions != null) Icon(Icons.Rounded.MoreVert, "Opzioni ${e.title}", Modifier.size(32.dp * scale).clickable { onOptions(index) }.padding(5.dp), tint = text)
                    else Icon(Icons.Rounded.ChevronRight, null, tint = if(active) Color.White else Color(0xFF87939E), modifier = Modifier.size(18.dp * scale))
                }
            }
        }
        // Indicatore di posizione discreto, visibile solo quando la lista scorre.
        val info = state.layoutInfo
        if(info.visibleItemsInfo.isNotEmpty() && entries.size > info.visibleItemsInfo.size) {
            val total = entries.size.toFloat()
            val start = state.firstVisibleItemIndex / total
            val length = (info.visibleItemsInfo.size / total).coerceAtLeast(.08f)
            Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(3.dp * scale).padding(vertical = 8.dp)) {
                drawRoundRect(Color.White.copy(alpha = .07f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width))
                drawRoundRect(Color(0xFF3AA3FF), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * start.coerceAtMost(1f - length)),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * length), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width))
            }
        }
    }
}

@Composable
private fun Chip(text: String, active: Boolean, scale: Float, onClick: () -> Unit) {
    Text(text, Modifier.clip(RoundedCornerShape(10.dp)).background(if(active) Blue else Color(0xFF182330)).clickable(onClick = onClick).padding(horizontal = 12.dp * scale, vertical = 8.dp * scale), color = Ice, fontSize = (12 * scale).sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun LibraryGrid(entries: List<Entry>, selected: Int, scale: Float, modifier: Modifier, onSelect: (Int) -> Unit) {
    val gradients = listOf(Color(0xFF33658C), Color(0xFF424B61), Color(0xFF6E326B), Color(0xFF573B93), Color(0xFF902447), Color(0xFF9B574D))
    Column(modifier.padding(6.dp * scale), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        repeat(2) { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            repeat(3) { col -> val index = row * 3 + col; val e = entries[index]
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp * scale))
                    .background(Brush.verticalGradient(listOf(gradients[index], Color(0xFF070E1A))))
                    .border(if(selected == index) 1.5.dp else .5.dp, if(selected == index) Blue else Color(0xFF384457), RoundedCornerShape(10.dp * scale))
                    .clickable { onSelect(index) }.padding(4.dp * scale), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(e.icon, null, tint = if(index == 4) Color(0xFFFF577B) else Ice, modifier = Modifier.size(33.dp * scale))
                    Spacer(Modifier.height(7.dp * scale))
                    Text(e.title, color = Ice, fontSize = (11 * scale).sp, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text(e.subtitle ?: "", color = Muted, fontSize = (10 * scale).sp)
                }
            }
        } }
    }
}

@Composable
private fun MiniPlayer(playing: Playback, scale: Float, open: () -> Unit, toggle: () -> Unit, next: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1B2736), Color(0xFF0B121B))))) {
        LinearProgressIndicator(progress = { if(playing.duration > 0) (playing.position.toFloat() / playing.duration).coerceIn(0f, 1f) else 0f },
            color = Blue, trackColor = Color(0xFF263241), modifier = Modifier.fillMaxWidth().height(2.dp))
        // Altezza minima, non fissa: se il testo cresce il riquadro si adatta invece di tagliarsi.
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp * scale).clickable(onClick = open).padding(horizontal = 8.dp * scale, vertical = 7.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            Artwork(playing.artwork, Modifier.size(38.dp * scale))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp * scale)) {
                Text(playing.title, color = Ice, fontSize = (12.5f * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playing.artist, color = Muted, fontSize = (10.5f * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if(playing.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play o pausa", Modifier.size(38.dp * scale).clickable(onClick = toggle).padding(6.dp * scale), tint = Ice)
            Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(38.dp * scale).clickable(onClick = next).padding(6.dp * scale), tint = Ice)
        }
    }
}

@Composable
private fun EmptyScreen(title: String, subtitle: String, scale: Float, modifier: Modifier, action: (() -> Unit)?) {
    Column(modifier.fillMaxWidth().padding(20.dp * scale), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = Blue, modifier = Modifier.size(36.dp * scale))
        Spacer(Modifier.height(14.dp * scale))
        Text(title, color = Ice, fontSize = (16 * scale).sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = (12 * scale).sp, modifier = Modifier.padding(top = 8.dp * scale))
        if(action != null) TextButton(onClick = action) { Text("Scansiona musica") }
    }
}

private fun time(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun NowPlaying(p: Playback, favorite: Boolean, scale: Float, vm: PlayerViewModel, onFavorite: () -> Unit, queue: () -> Unit, options: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF142941), Color(0xFF201325), DisplayBlack)))) {
        val cover = minOf(maxWidth * .72f, maxHeight * .53f)
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp * scale, vertical = 5.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(p.artwork, Modifier.size(cover), p.title)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(top = 5.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, color = Ice, fontSize = (20 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.artist, color = Color(0xFFBBC5D4), fontSize = (13 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.album, color = Muted, fontSize = (11 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if(favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Preferito", tint = if(favorite) Color(0xFFFF537B) else Ice,
                    modifier = Modifier.size(36.dp * scale).clickable(onClick = onFavorite).padding(6.dp * scale))
                Icon(Icons.Rounded.MoreVert, "Opzioni brano", tint = Ice, modifier = Modifier.size(30.dp * scale).clickable(onClick = options).padding(5.dp * scale))
            }
            if(p.error != null) Text(p.error, color = Color(0xFFFFA7A0), fontSize = (10 * scale).sp, maxLines = 2)
            if(p.buffering) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Blue)
            var scrub by remember { mutableStateOf<Float?>(null) }
            Slider(value = scrub ?: (if(p.duration > 0) p.position.toFloat() / p.duration else 0f).coerceIn(0f, 1f), onValueChange = { scrub = it },
                onValueChangeFinished = { scrub?.let { vm.seekTo((it * p.duration).toLong()) }; scrub = null },
                modifier = Modifier.fillMaxWidth().height(27.dp * scale), colors = SliderDefaults.colors(thumbColor = Ice, activeTrackColor = Blue, inactiveTrackColor = Color(0xFF4C4059)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time(p.position), color = Ice, fontSize = (11 * scale).sp)
                Text("−${time(p.duration - p.position)}", color = Muted, fontSize = (11 * scale).sp)
            }
            Row(Modifier.fillMaxWidth().height(37.dp * scale), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlayerIcon(Icons.Rounded.Shuffle, "Casuale", if(p.shuffle) Blue else Muted, scale, vm::shuffle)
                PlayerIcon(Icons.Rounded.SkipPrevious, "Precedente", Ice, scale, vm::previous)
                PlayerIcon(if(p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play o pausa", Ice, scale, vm::togglePlay)
                PlayerIcon(Icons.Rounded.SkipNext, "Successivo", Ice, scale, vm::next)
                PlayerIcon(if(p.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ripeti", if(p.repeat != 0) Blue else Muted, scale, vm::repeat)
                PlayerIcon(Icons.Rounded.QueueMusic, "Coda", Ice, scale, queue)
            }
        }
    }
}

@Composable private fun PlayerIcon(icon: ImageVector, description: String, color: Color, scale: Float, action: () -> Unit) {
    Icon(icon, description, Modifier.size(35.dp * scale).clickable(onClick = action).padding(6.dp * scale), tint = color)
}
