package it.jarvis.ipod.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.jarvis.ipod.PlayerViewModel
import it.jarvis.ipod.data.DriveFolder
import it.jarvis.ipod.data.Playlist
import it.jarvis.ipod.data.Preferences
import it.jarvis.ipod.data.Track
import kotlinx.coroutines.delay
import kotlin.math.abs

private val HapticNames = listOf("Disattivata", "Leggera", "Normale", "Forte")
private val Green = Color(0xFF34A853)
private val SpotifyGreen = Color(0xFF1ED760)

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
    val routes = rememberSaveable(saver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Route>, String>(
        save = { it.flatMap { r -> listOf(r.type, r.title, r.arg) } },
        restore = { values -> mutableStateListOf<Route>().apply { values.chunked(3).forEach { add(Route(it[0], it[1], it[2])) } } }
    )) { mutableStateListOf(Route("home", "Jarvis"), Route("lock", "Jarvis")) }
    val route = routes.last()
    var selected by rememberSaveable(route) { mutableIntStateOf(0) }
    var query by rememberSaveable(route) { mutableStateOf("") }
    var songOptions by remember { mutableStateOf<Track?>(null) }
    var playlistTarget by remember { mutableStateOf<Track?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var trackSteps by remember(route) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val haptics = rememberHaptics(settings.hapticLevel)
    val controls = remember(vm) { PlayerControls(vm::seekTo, vm::shuffle, vm::previous, vm::togglePlay, vm::next, vm::repeat) }
    fun push(type: String, title: String, arg: String = "") { routes += Route(type, title, arg) }
    fun back() { if (routes.size > 1 && routes.last().type != "lock") routes.removeAt(routes.lastIndex) }
    fun unlock() { if (routes.last().type == "lock") routes.removeAt(routes.lastIndex) }
    BackHandler(routes.size > 1 && route.type != "lock") { back() }
    // "drive" con arg "root" è l'elenco delle cartelle musicali scelte; con un ID cartella è la navigazione al suo interno.
    // "drivepick" naviga tutto Drive soltanto per scegliere una cartella da aggiungere.
    val driveList = route.type == "drive" && (route.arg.isBlank() || route.arg == "root")
    val driveBrowse = route.type == "drive" && !driveList
    val drivePick = route.type == "drivepick"
    val driveLoading = driveBrowse || drivePick
    LaunchedEffect(route.type, route.arg, query, settings.driveEmail) {
        if (driveLoading && settings.driveEmail.isNotBlank()) {
            if (query.isNotBlank()) delay(350)
            vm.loadDrive(route.arg.ifBlank { "root" }, query, route.title, pick = drivePick)
        }
    }
    val sorted = remember(tracks, settings.sort) { when (settings.sort) {
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
    fun tick(active: Boolean) = if (active) "✓" else null
    val home = listOf(
        Entry("Musica", Icons.Rounded.MusicNote, color = Color(0xFFFF5C8A)) { push("library", "Libreria"); requestAudio() },
        Entry("Google Drive", Icons.Rounded.AddToDrive, color = Green) { push("drive", "Google Drive", "root") },
        Entry("Spotify", Icons.Rounded.GraphicEq, color = SpotifyGreen) { push("spotify", "Spotify") },
        Entry("Playlist", Icons.Rounded.QueueMusic, color = Color(0xFF3FA0FF)) { push("playlists", "Playlist") },
        Entry("Album", Icons.Rounded.Album, color = Color(0xFFFFB020)) { push("albums", "Album") },
        Entry("Artisti", Icons.Rounded.Person, color = Color(0xFF3DDC84)) { push("artists", "Artisti") },
        Entry("Preferiti", Icons.Rounded.Favorite, color = Color(0xFFFF4D6D)) { push("favorites", "Preferiti") },
        Entry("Recenti", Icons.Rounded.History, color = Color(0xFFBF7BFF)) { push("recent", "Recenti") },
        Entry("Ricerca", Icons.Rounded.Search, color = Color(0xFF7FD6FF)) { push("search", "Ricerca") },
        Entry("Impostazioni", Icons.Rounded.Settings, color = Color(0xFFC9D3E0)) { push("settings", "Impostazioni") }
    )
    val entries: List<Entry> = when (route.type) {
        "home" -> home
        "library" -> listOf(
            Entry("Brani", Icons.Rounded.MusicNote, "${local.size}") { push("tracks", "Musica locale") },
            Entry("Album", Icons.Rounded.Album, "${local.map { it.album to it.artist }.distinct().size}") { push("albums", "Album") },
            Entry("Artisti", Icons.Rounded.Person, "${local.map { it.artist }.distinct().size}") { push("artists", "Artisti") },
            Entry("Playlist", Icons.Rounded.QueueMusic, "${playlists.size}") { push("playlists", "Playlist") },
            Entry("Preferiti", Icons.Rounded.Favorite, "${tracks.count { it.favorite }}") { push("favorites", "Preferiti") },
            Entry("Recenti", Icons.Rounded.History, "${tracks.count { it.lastPlayed > 0 }}") { push("recent", "Recenti") }
        )
        "tracks" -> songs(local)
        "favorites" -> songs(sorted.filter { it.favorite })
        "recent" -> songs(tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(100))
        "offline" -> songs(sorted.filter { it.offlinePath != null })
        "albums" -> sorted.distinctBy { it.album to it.artist }.map { t -> Entry(t.album, Icons.Rounded.Album, t.artist, art = t.artwork) { push("album", t.album, t.id) } }
        "album" -> tracks.find { it.id == route.arg }?.let { t -> songs(sorted.filter { it.album == t.album && it.artist == t.artist }) } ?: emptyList()
        "artists" -> sorted.map { it.artist }.distinct().sorted().map { artist -> Entry(artist, Icons.Rounded.Person) { push("artist", artist, artist) } }
        "artist" -> songs(sorted.filter { it.artist == route.arg })
        "search" -> songs(sorted.filter { query.isNotBlank() && listOf(it.title, it.artist, it.album).any { text -> text.contains(query, true) } })
        "playlists" -> listOf(
            Entry("Preferiti", Icons.Rounded.Favorite, "${tracks.count { it.favorite }} brani", color = Color(0xFFFF4D6D), variant = 2) { push("favorites", "Preferiti") },
            Entry("Nuova playlist", Icons.Rounded.Add, color = GlowBlue) { newPlaylist = true }
        ) + playlists.mapIndexed { index, p -> Entry(p.name, Icons.Rounded.QueueMusic, "${playlistEntries.count { it.playlistId == p.id }} brani", variant = index % 3) { push("playlist", p.name, p.id.toString()) } }
        "playlist" -> songs(playlistEntries.filter { it.playlistId.toString() == route.arg }.mapNotNull { entry -> tracks.find { it.id == entry.trackId } })
        "drive" -> if (settings.driveEmail.isBlank()) listOf(Entry("Collega Google Drive", Icons.Rounded.AddToDrive, "Accedi al tuo account Google", Green) { connectDrive() })
            else if (driveList) {
                if (settings.driveFolders.isEmpty()) listOf(Entry("Scegli cartelle musicali", Icons.Rounded.CreateNewFolder, "Nessuna cartella selezionata", Green) { push("drivepick", "Scegli cartella", "root") })
                else settings.driveFolders.map { f -> Entry(f.name, Icons.Rounded.Folder, "Cartella") { push("drive", f.name, f.id) } } +
                    Entry("Gestisci cartelle", Icons.Rounded.Settings) { push("drivemanage", "Gestisci cartelle") }
            } else driveFiles.map { f -> Entry(f.name, if (f.folder) Icons.Rounded.Folder else Icons.Rounded.MusicNote,
                if (f.folder) "Cartella" else "Google Drive", track = if (f.folder) null else tracks.find { it.id == "drive:${f.id}" }) {
                if (f.folder) push("drive", f.name, f.id)
                else {
                    val folderTracks = driveFiles.filter { !it.folder }.mapNotNull { file -> tracks.find { it.id == "drive:${file.id}" } }
                    folderTracks.find { it.id == "drive:${f.id}" }?.let { play(folderTracks, it) }
                }
            } }
        "drivepick" -> (if (route.arg == "root" || route.arg.isBlank()) emptyList<Entry>()
            else listOf(Entry("Usa questa cartella", Icons.Rounded.Check, route.title, Green) {
                vm.addDriveFolder(DriveFolder(route.arg, route.title))
                while (routes.size > 1 && routes.last().type == "drivepick") routes.removeAt(routes.lastIndex)
            })) + driveFiles.filter { it.folder }.map { f -> Entry(f.name, Icons.Rounded.Folder, "Apri") { push("drivepick", f.name, f.id) } }
        "drivemanage" -> listOf(Entry("Aggiungi cartella", Icons.Rounded.CreateNewFolder) { push("drivepick", "Scegli cartella", "root") }) +
            settings.driveFolders.map { f -> Entry(f.name, Icons.Rounded.Delete, "Tocca per rimuovere dall’elenco") { vm.removeDriveFolder(f.id) } }
        "spotify" -> listOf(
            Entry("Apri app Spotify", Icons.Rounded.OpenInNew, "Riproduzione nell’app ufficiale", SpotifyGreen) { launchSpotify(true) },
            Entry("Apri Spotify nel browser", Icons.Rounded.Language, "Accesso tramite browser sicuro") { launchSpotify(false) },
            Entry("Come funziona", Icons.Rounded.Info) { info = "Spotify viene aperto nell’app ufficiale o in una scheda del browser. Login, riproduzione e abbonamento sono gestiti da Spotify.\n\nWebView non è tra i browser Android supportati dichiarati. Login e riproduzione non sono stati verificati con un account in questo ambiente.\n\nLa Click Wheel controlla solo i brani locali e Drive. Torna qui con Indietro e premi MENU per uscire dalla sezione." }
        )
        "settings" -> listOf(
            Entry("Aspetto iPod", Icons.Rounded.Palette, value = if (settings.midnight) "Midnight Black" else "Silver Classic") { push("appearance", "Aspetto iPod") },
            Entry("Comandi", Icons.Rounded.TouchApp, value = if (settings.controlMode == "buttons") "Solo tasti" else "Ruota e tasti") { push("controls", "Comandi") },
            Entry("Vibrazione", Icons.Rounded.Vibration, value = HapticNames[settings.hapticLevel]) { push("haptics", "Vibrazione") },
            Entry("Sensibilità ruota", Icons.Rounded.Tune, value = "${(settings.sensitivity * 100).toInt()}%") { push("sensitivity", "Sensibilità ruota") },
            Entry("Account Google Drive", Icons.Rounded.AddToDrive, value = settings.driveEmail.ifBlank { "Non collegato" }) { push("account", "Account Google Drive") },
            Entry("Gestione cache", Icons.Rounded.Storage) { push("cache", "Gestione cache") },
            Entry("Qualità audio", Icons.Rounded.GraphicEq, value = "Originale") { info = "I file vengono riprodotti nella qualità originale, senza ricodifica. I formati disponibili dipendono dai decoder Android. La qualità Spotify si gestisce in Spotify." },
            Entry("Ordina musica", Icons.Rounded.Sort, value = settings.sort) { push("sort", "Ordinamento") },
            Entry(if (scanning) "Scansione in corso…" else "Scansiona musica", Icons.Rounded.Refresh) { requestAudio() },
            Entry("Informazioni", Icons.Rounded.Info, value = "1.1") { info = "Jarvis 1.1\nLettore Android nativo con Click Wheel.\n\nInterfaccia ricreata autonomamente dal riferimento fornito. Nessuna affiliazione con Apple, Google o Spotify.\n\nRuota per navigare; il tasto centrale conferma, MENU torna indietro. Nel lettore la ruota cambia brano o cerca nel brano, a scelta in Impostazioni > Comandi. In modalità Solo tasti la ruota non gira e Precedente/Successivo spostano la selezione. Le opzioni ⋮ aggiungono i brani a coda e playlist." }
        )
        "appearance" -> listOf(
            Entry("Silver Classic", Icons.Rounded.LightMode, "Alluminio satinato", value = tick(!settings.midnight)) { vm.preference(Preferences.midnight, false) },
            Entry("Midnight Black", Icons.Rounded.DarkMode, "Antracite metallizzato", value = tick(settings.midnight)) { vm.preference(Preferences.midnight, true) }
        )
        "controls" -> listOf(
            Entry("Ruota e tasti", Icons.Rounded.TouchApp, "Gira il dito sull’anello per scorrere", value = tick(settings.controlMode != "buttons")) { vm.preference(Preferences.controlMode, "wheel") },
            Entry("Solo tasti", Icons.Rounded.Tune, "Rotazione disattivata: usa i quattro tasti", value = tick(settings.controlMode == "buttons")) { vm.preference(Preferences.controlMode, "buttons") },
            Entry("Ruota nel lettore: brano", Icons.Rounded.SkipNext, "Ogni tre scatti cambia canzone", value = tick(settings.playerWheel != "seek")) { vm.preference(Preferences.playerWheel, "track") },
            Entry("Ruota nel lettore: cerca", Icons.Rounded.FastForward, "Ogni scatto sposta di 5 secondi", value = tick(settings.playerWheel == "seek")) { vm.preference(Preferences.playerWheel, "seek") }
        )
        "haptics" -> HapticNames.mapIndexed { level, name ->
            Entry(name, Icons.Rounded.Vibration, value = tick(settings.hapticLevel == level)) { haptics.level = level; vm.preference(Preferences.hapticLevel, level); haptics.press() }
        } + Entry("Come funziona", Icons.Rounded.Info) {
            info = "Ogni scatto della ruota e ogni tasto premuto fanno vibrare il telefono come la tastiera.\n\nDa Android 13 la vibrazione segue Impostazioni > Suono e vibrazione > Feedback tattile: se è disattivato nel sistema, il telefono non vibra neanche qui."
        }
        "account" -> listOf(Entry("Collega / cambia account", Icons.Rounded.AddToDrive, settings.driveEmail.ifBlank { "OAuth Google" }) { connectDrive() }) +
            if (settings.driveEmail.isNotBlank()) listOf(Entry("Scollega account", Icons.Rounded.Logout) { vm.disconnectDrive() }) else emptyList()
        "cache" -> listOf(
            Entry("Svuota cache streaming", Icons.Rounded.CleaningServices, "Limite automatico: 256 MB") { vm.clearCache() },
            Entry("Brani offline", Icons.Rounded.DownloadDone, "${tracks.count { it.offlinePath != null }} file salvati") { push("offline", "Brani offline") },
            Entry("Come scaricare", Icons.Rounded.Info) { info = "Apri le opzioni ⋮ di un brano Drive e scegli Scarica offline. Il file rimane nello spazio privato dell’app.\n\nI download avviati richiedono che l’app resti aperta. Se il processo viene interrotto, ripeti il download. Per rimuovere un file, apri le sue opzioni.\n\nLa cache streaming si pulisce automaticamente; i download si rimuovono manualmente." }
        )
        "sort" -> listOf("Titolo", "Artista", "Album", "Data").map { value -> Entry(value, if (settings.sort == value) Icons.Rounded.Check else Icons.Rounded.Sort) { vm.preference(Preferences.sort, value) } }
        "sensitivity" -> listOf(Entry("Meno sensibile", Icons.Rounded.Remove, value = "${(settings.sensitivity * 100).toInt()}%") { vm.preference(Preferences.sensitivity, (settings.sensitivity - .25f).coerceAtLeast(.5f)) },
            Entry("Più sensibile", Icons.Rounded.Add, value = "${(settings.sensitivity * 100).toInt()}%") { vm.preference(Preferences.sensitivity, (settings.sensitivity + .25f).coerceAtMost(2f)) })
        "queue" -> playing.queue.mapIndexed { index, item -> Entry(item.mediaMetadata.title?.toString() ?: "Brano", Icons.Rounded.QueueMusic, item.mediaMetadata.artist?.toString(),
            track = tracks.find { it.id == item.mediaId }) { vm.playQueue(index) } }
        else -> emptyList()
    }
    LaunchedEffect(entries.size) { selected = selected.coerceIn(0, (entries.size - 1).coerceAtLeast(0)) }
    val listRoute = route.type != "playing" && route.type != "lock" && route.type != "spotify"
    val buttonsOnly = settings.controlMode == "buttons"
    fun move(delta: Int) { if (entries.isNotEmpty()) selected = (selected + delta).coerceIn(0, entries.lastIndex) }
    // Un tocco sullo schermo vibra come un tasto: lo scatto della ruota ha invece il suo colpetto leggero.
    fun choose(index: Int) { haptics.press(); selected = index; entries.getOrNull(index)?.run?.invoke() }
    val thumbs: List<String?> = run {
        val recent = tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.mapNotNull { it.artwork }
        (listOf(playing.artwork) + recent).filter { !it.isNullOrBlank() }.distinct().take(3).let { it + List(3 - it.size) { null } }
    }
    val colors = darkColorScheme(primary = Blue, background = DisplayBlack, surface = Color(0xFF172131), onSurface = Ice)
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF293C4C), Color(0xFF101820)))).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            IPodShell(settings, haptics, display = { scale ->
                Column(Modifier.fillMaxSize().background(DisplayBlack)) {
                    if (route.type != "lock") StatusBar(scale)
                    if (route.type !in listOf("home", "lock", "playing", "library")) {
                        NavBar(route.title, routes.getOrNull(routes.lastIndex - 1)?.title, scale) { haptics.press(); back() }
                    }
                    when (route.type) {
                        "lock" -> LockScreen(scale) { haptics.press(); unlock() }
                        "home" -> HomeScreen(entries, selected, scale, thumbs, onSelect = ::choose, onNow = { if (playing.id != null) push("playing", "In riproduzione") })
                        "library" -> Column(Modifier.weight(1f)) {
                            IconGrid(entries, selected, scale, Modifier.weight(1f), ::choose)
                            if (playing.id != null) MiniPlayer(playing, scale, null, { push("playing", "In riproduzione") }, vm::togglePlay, vm::next)
                        }
                        "playing" -> NowPlaying(playing, current?.favorite == true, scale, controls, { current?.let { vm.favorite(it.id) } },
                            { push("queue", "Coda di riproduzione") }, { current?.let { songOptions = it } })
                        else -> {
                            if (route.type == "search" || (driveBrowse && settings.driveEmail.isNotBlank())) {
                                TextField(query, { query = it; selected = 0 }, placeholder = { Text(if (route.type == "drive") "Cerca in questa cartella" else "Brani, album, artisti", fontSize = (12 * scale).sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) }, singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = (13 * scale).sp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                            }
                            if (route.type == "playlist") {
                                Text("Elimina playlist", color = Muted, fontSize = (11 * scale).sp, modifier = Modifier.fillMaxWidth().clickable { deletePlaylist = playlists.find { it.id.toString() == route.arg } }.padding(8.dp * scale))
                            }
                            if (driveBusy && driveLoading) BusyBar()
                            if (driveError != null && driveLoading) {
                                ErrorBox(driveError!!, scale, Modifier.weight(1f), listOf(
                                    "Riprova" to { vm.loadDrive(route.arg.ifBlank { "root" }, query, route.title, pick = drivePick) },
                                    "Ricollega Google" to connectDrive))
                            } else if (entries.isEmpty() && !(driveBusy && driveLoading)) {
                                EmptyScreen(when (route.type) { "search" -> if (query.isBlank()) "Cerca nella tua libreria" else "Nessun risultato"; "drive" -> "Nessun file audio in questa cartella"; "tracks" -> "La tua musica inizia qui"; else -> "Nessun brano da mostrare" },
                                    if (route.type == "tracks") "Autorizza l’accesso alla musica e avvia una scansione." else "Aggiungi musica o esplora un’altra sezione.", scale, Modifier.weight(1f), if (route.type == "tracks") requestAudio else null)
                            } else if (route.type == "albums") {
                                AlbumGrid(entries, selected, scale, Modifier.weight(1f), ::choose)
                            } else {
                                EntryList(entries, selected, scale, Modifier.weight(1f), ::choose,
                                    tiles = route.type in setOf("settings", "appearance", "controls", "haptics", "account", "cache", "sort", "sensitivity", "drive", "drivemanage", "drivepick", "playlists"),
                                    onOptions = { index -> entries[index].track?.let { songOptions = it } },
                                    queue = route.type == "queue", onRemove = vm::removeQueue, onMove = { vm.moveQueue(it, it - 1) })
                            }
                            if (playing.id != null) MiniPlayer(playing, scale, null, { push("playing", "In riproduzione") }, vm::togglePlay, vm::next)
                        }
                    }
                }
            }, onStep = { delta ->
                when (route.type) {
                    "lock" -> Unit
                    "playing" -> if (settings.playerWheel == "seek") vm.seekBy(delta * 5000L) else {
                        trackSteps += delta
                        if (abs(trackSteps) >= 3) {
                            if (trackSteps > 0) vm.next() else vm.previous()
                            trackSteps = 0
                            haptics.press()
                        }
                    }
                    else -> move(delta)
                }
            }, onButton = { key ->
                when (key) {
                    "menu" -> back()
                    "select" -> when (route.type) {
                        "lock" -> unlock()
                        "playing" -> current?.let { songOptions = it }
                        else -> entries.getOrNull(selected)?.run?.invoke()
                    }
                    "play" -> if (route.type == "lock") unlock() else if (route.type != "spotify") {
                        if (playing.id == null) entries.getOrNull(selected)?.track?.let { play(entries.mapNotNull { it.track }, it) } else vm.togglePlay()
                    }
                    "next" -> if (buttonsOnly && listRoute) move(1) else if (route.type != "spotify") vm.next()
                    "previous" -> if (buttonsOnly && listRoute) move(-1) else if (route.type != "spotify") vm.previous()
                }
            }, onHold = { key ->
                val direction = if (key == "next") 1 else -1
                if (buttonsOnly && listRoute) move(direction) else if (route.type != "spotify" && route.type != "lock") vm.seekBy(direction * 5000L)
            })
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
                    Option(if (track.favorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") { vm.favorite(track.id); songOptions = null }
                    Option("Aggiungi alla coda") { vm.enqueue(track); songOptions = null }
                    Option("Aggiungi a playlist") { playlistTarget = track; songOptions = null }
                    if (route.type == "playlist") Option("Rimuovi dalla playlist") { vm.removeFromPlaylist(route.arg.toLong(), track.id); songOptions = null }
                    if (track.source == "drive") Option(when { track.id in downloads -> "Download in corso…"; track.offlinePath != null -> "Rimuovi download offline"; else -> "Scarica offline" }) {
                        if (track.offlinePath != null) vm.deleteOffline(track) else vm.download(track); songOptions = null
                    }
                }
            }, confirmButton = { TextButton(onClick = { songOptions = null }) { Text("Chiudi") } })
        }
        playlistTarget?.let { track -> AlertDialog(onDismissRequest = { playlistTarget = null }, title = { Text("Aggiungi a playlist") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (playlists.isEmpty()) Text("Crea prima una playlist dalla sezione Playlist.")
                playlists.forEach { p -> Option(p.name) { vm.addToPlaylist(p.id, track.id); playlistTarget = null } }
            }
        }, confirmButton = { TextButton(onClick = { playlistTarget = null }) { Text("Chiudi") } }) }
        if (newPlaylist) AlertDialog(onDismissRequest = { newPlaylist = false }, title = { Text("Nuova playlist") }, text = {
            OutlinedTextField(playlistName, { playlistName = it }, label = { Text("Nome") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { vm.createPlaylist(playlistName); playlistName = ""; newPlaylist = false }, enabled = playlistName.isNotBlank()) { Text("Crea") } },
            dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text("Annulla") } })
        deletePlaylist?.let { p -> AlertDialog(onDismissRequest = { deletePlaylist = null }, title = { Text("Elimina ${p.name}?") }, text = { Text("I brani originali vengono conservati.") },
            confirmButton = { TextButton(onClick = { vm.deletePlaylist(p.id); deletePlaylist = null; back() }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Annulla") } }) }
        info?.let { text -> AlertDialog(onDismissRequest = { info = null }, title = { Text(route.title) }, text = { Text(text, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { info = null }) { Text("Chiudi") } }) }
    }
}

@Composable private fun Option(title: String, onClick: () -> Unit) { Text(title, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), fontSize = 15.sp) }
