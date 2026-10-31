package it.jarvis.ipod.ui

import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.jarvis.ipod.Playback
import it.jarvis.ipod.data.Track
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

internal data class Route(val type: String, val title: String, val arg: String = "")

/**
 * Una riga di menu. [value] è il valore mostrato a destra (impostazioni); [art]/[variant] attivano la miniatura
 * (variant >= 0), con copertina reale se [art] o la track ne hanno una, altrimenti un paesaggio disegnato.
 */
internal data class Entry(val title: String, val icon: ImageVector = Icons.Rounded.ChevronRight,
    val subtitle: String? = null, val color: Color = Ice, val track: Track? = null, val value: String? = null,
    val art: String? = null, val variant: Int = -1, val run: () -> Unit)

/** Azioni del lettore: le stesse schermate servono sia per la musica del telefono sia per Spotify. */
internal class PlayerControls(val seekTo: (Long) -> Unit, val shuffle: () -> Unit, val previous: () -> Unit,
    val toggle: () -> Unit, val next: () -> Unit, val repeat: () -> Unit)

private val PillBrush = Brush.horizontalGradient(listOf(Color(0xFF2F86FF), Violet))

private fun sceneFor(text: String) = abs(text.hashCode()) % 3

private fun time(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
internal fun StatusBar(scale: Float) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    val context = LocalContext.current
    var battery by remember { mutableIntStateOf(100) }
    var headphones by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            battery = runCatching { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100) }.getOrDefault(100)
            headphones = runCatching {
                context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
            }.getOrDefault(false)
            delay(15000)
        }
    }
    Row(Modifier.fillMaxWidth().height(25.dp * scale).background(Brush.verticalGradient(listOf(Color(0xFF0E1622), DisplayBlack))).padding(horizontal = 9.dp * scale),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Jarvis", color = Color.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (headphones) Icon(Icons.Rounded.Headphones, "Cuffie collegate", tint = Color.White, modifier = Modifier.size(13.dp * scale))
            Text("$battery%", color = Color.White, fontSize = (11 * scale).sp, modifier = Modifier.padding(start = 3.dp * scale, end = 4.dp * scale))
            Box(Modifier.size(20.dp * scale, 10.dp * scale).border(1.dp, Color(0xFFB9C7C7), RoundedCornerShape(2.dp)).padding(1.5.dp)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(battery / 100f).background(if (battery < 15) Color(0xFFF85B62) else Color.White, RoundedCornerShape(1.dp)))
            }
        }
    }
}

@Composable
internal fun NavBar(title: String, parent: String?, scale: Float, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(26.dp * scale).padding(horizontal = 6.dp * scale)) {
        if (parent != null) {
            Row(Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(end = 6.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChevronLeft, "Indietro", tint = GlowBlue, modifier = Modifier.size(20.dp * scale))
                Text(parent, color = GlowBlue, fontSize = (12 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 78.dp * scale))
            }
        }
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = 84.dp * scale), color = Color.White, fontSize = (13 * scale).sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Schermata iniziale: sfondo disegnato, ora e data; il tasto centrale (o un tocco) entra nel menu. */
@Composable
internal fun LockScreen(scale: Float, onUnlock: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(10000) } }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
    Box(Modifier.fillMaxSize().clickable(onClick = onUnlock)) {
        Artwork(null, Modifier.fillMaxSize(), variant = 0, corner = 0.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent, Color.Black.copy(alpha = .55f)))))
        Column(Modifier.fillMaxSize().padding(top = 26.dp * scale, bottom = 18.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontSize = (60 * scale).sp, fontWeight = FontWeight.Light)
            Text(date, color = Color.White.copy(alpha = .9f), fontSize = (14 * scale).sp)
            Spacer(Modifier.weight(1f))
            Text("Premi il tasto centrale", color = Color.White, fontSize = (13 * scale).sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun HomeScreen(entries: List<Entry>, selected: Int, scale: Float, thumbs: List<String?>, onSelect: (Int) -> Unit, onNow: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        EntryList(entries, selected, scale, Modifier.fillMaxHeight().weight(.66f), onSelect)
        Column(Modifier.weight(.34f).fillMaxHeight().padding(end = 8.dp * scale, top = 4.dp * scale, bottom = 6.dp * scale), verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            thumbs.take(3).forEachIndexed { index, art ->
                Artwork(art, Modifier.fillMaxWidth().weight(1f).clickable(onClick = onNow), "Copertina", variant = index, corner = 12.dp * scale)
            }
        }
    }
}

@Composable
internal fun EntryList(entries: List<Entry>, selected: Int, scale: Float, modifier: Modifier = Modifier, onSelect: (Int) -> Unit,
    tiles: Boolean = false, onOptions: ((Int) -> Unit)? = null, queue: Boolean = false, onRemove: (Int) -> Unit = {}, onMove: (Int) -> Unit = {}) {
    val state = rememberLazyListState()
    LaunchedEffect(selected, entries.size) { if (entries.isNotEmpty()) {
        val visible = state.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == selected && it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }) state.animateScrollToItem(selected)
    } }
    LazyColumn(modifier, state = state, contentPadding = PaddingValues(horizontal = 6.dp * scale, vertical = 2.dp * scale)) {
        itemsIndexed(entries) { index, e ->
            val active = selected == index
            val thumb = e.track != null || e.variant >= 0
            Row(Modifier.fillMaxWidth().heightIn(min = (if (thumb) 48.dp else 41.dp) * scale).clip(RoundedCornerShape(12.dp * scale))
                .then(if (active) Modifier.background(PillBrush) else Modifier)
                .clickable { onSelect(index) }.padding(horizontal = 8.dp * scale, vertical = 4.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                if (!queue) {
                    if (thumb) {
                        Artwork(e.track?.artwork ?: e.art, Modifier.size(36.dp * scale), e.title,
                            variant = if (e.variant >= 0) e.variant else sceneFor(e.title), corner = 8.dp * scale)
                    } else if (tiles) {
                        Box(Modifier.size(26.dp * scale).clip(RoundedCornerShape(7.dp * scale)).background(if (active) Color.White.copy(alpha = .22f) else e.color.copy(alpha = .9f)),
                            contentAlignment = Alignment.Center) {
                            Icon(e.icon, null, tint = if (active || e.color == Ice) Color.White else Color(0xFF08121D), modifier = Modifier.size(17.dp * scale))
                        }
                    } else {
                        Icon(e.icon, null, tint = if (active) Color.White else e.color, modifier = Modifier.size(23.dp * scale))
                    }
                    Spacer(Modifier.width(10.dp * scale))
                }
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = Color.White, fontSize = (14 * scale).sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.subtitle != null) Text(e.subtitle, color = if (active) Color(0xFFD3E3FF) else Muted, fontSize = (11 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (e.value != null) Text(e.value, color = if (active) Color(0xFFD3E3FF) else Muted, fontSize = (12 * scale).sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp * scale, end = 2.dp * scale))
                if (queue) {
                    Icon(Icons.Rounded.ArrowUpward, "Sposta prima", Modifier.size(30.dp * scale).clickable { onMove(index) }.padding(5.dp), tint = Color.White)
                    Icon(Icons.Rounded.Close, "Rimuovi dalla coda", Modifier.size(30.dp * scale).clickable { onRemove(index) }.padding(5.dp), tint = Color.White)
                } else if (e.track != null && onOptions != null) {
                    Icon(Icons.Rounded.MoreVert, "Opzioni ${e.title}", Modifier.size(32.dp * scale).clickable { onOptions(index) }.padding(5.dp), tint = Color.White)
                } else {
                    Icon(Icons.Rounded.ChevronRight, null, tint = if (active) Color.White else Color(0xFF7C8AA0), modifier = Modifier.size(17.dp * scale))
                }
            }
            if (!active && index < entries.lastIndex && selected != index + 1) HorizontalDivider(color = Color.White.copy(alpha = .05f), modifier = Modifier.padding(horizontal = 10.dp * scale))
        }
    }
}

/** Copertine degli album su tre colonne; la ruota scorre in ordine di lettura. */
@Composable
internal fun AlbumGrid(entries: List<Entry>, selected: Int, scale: Float, modifier: Modifier, onSelect: (Int) -> Unit) {
    val state = rememberLazyGridState()
    LaunchedEffect(selected, entries.size) {
        if (entries.isNotEmpty() && state.layoutInfo.visibleItemsInfo.none { it.index == selected }) state.animateScrollToItem(selected)
    }
    LazyVerticalGrid(columns = GridCells.Fixed(3), state = state, modifier = modifier,
        contentPadding = PaddingValues(horizontal = 9.dp * scale, vertical = 6.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(9.dp * scale), verticalArrangement = Arrangement.spacedBy(9.dp * scale)) {
        gridItemsIndexed(entries) { index, e ->
            val active = index == selected
            Column(Modifier.clickable { onSelect(index) }) {
                Artwork(e.art, Modifier.fillMaxWidth().aspectRatio(1f).border(if (active) 2.dp else 0.dp, if (active) GlowBlue else Color.Transparent, RoundedCornerShape(10.dp * scale)),
                    e.title, variant = if (e.variant >= 0) e.variant else sceneFor(e.title), corner = 10.dp * scale)
                Text(e.title, color = Color.White, fontSize = (11 * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp * scale))
                Text(e.subtitle ?: "", color = if (active) GlowBlue else Muted, fontSize = (10 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Icone grandi, come la schermata delle app: libreria locale più Drive e Spotify. Si adatta all'altezza disponibile. */
@Composable
internal fun IconGrid(entries: List<Entry>, selected: Int, scale: Float, modifier: Modifier, onSelect: (Int) -> Unit) {
    val tiles = listOf(
        listOf(Color(0xFFFF5C7A), Color(0xFFE0245E)), listOf(Color(0xFFB7BCC6), Color(0xFF7D8491)), listOf(Color(0xFF6C7A8E), Color(0xFF414B5C)),
        listOf(Color(0xFF3F8CFF), Color(0xFF1E4FBF)), listOf(Color(0xFFFF4D6D), Color(0xFFB0194C)), listOf(Color(0xFFFFA83C), Color(0xFFE06A12)),
        listOf(Color(0xFF3FBF6B), Color(0xFF1E8A48)), listOf(Color(0xFF1ED760), Color(0xFF12A04A)))
    val rows = ((entries.size + 2) / 3).coerceAtLeast(1)
    BoxWithConstraints(modifier) {
        val rowHeight = (maxHeight - 16.dp * scale - 10.dp * scale * (rows - 1)) / rows
        val icon = (rowHeight - 40.dp * scale).coerceAtMost(54.dp * scale).coerceAtLeast(26.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp * scale, vertical = 8.dp * scale), verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            repeat(rows) { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                    repeat(3) { col ->
                        val index = row * 3 + col
                        val e = entries.getOrNull(index)
                        if (e == null) Spacer(Modifier.weight(1f)) else {
                            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp * scale))
                                .background(if (selected == index) Color.White.copy(alpha = .09f) else Color.Transparent)
                                .border(if (selected == index) 1.5.dp else 0.dp, if (selected == index) GlowBlue else Color.Transparent, RoundedCornerShape(14.dp * scale))
                                .clickable { onSelect(index) }.padding(4.dp * scale), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(icon).clip(RoundedCornerShape(icon * .26f)).background(Brush.verticalGradient(tiles[index % tiles.size])), contentAlignment = Alignment.Center) {
                                    Icon(e.icon, null, tint = Color.White, modifier = Modifier.size(icon * .56f))
                                }
                                Spacer(Modifier.height(5.dp * scale))
                                Text(e.title, color = Color.White, fontSize = (11 * scale).sp, maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(e.subtitle ?: "", color = Muted, fontSize = (10 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MiniPlayer(playing: Playback, scale: Float, cover: Bitmap? = null, open: () -> Unit, toggle: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 49.dp * scale).background(Brush.verticalGradient(listOf(Color(0xFF14202F), DisplayBlack))).clickable(onClick = open).padding(6.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Artwork(playing.artwork, Modifier.size(36.dp * scale), corner = 8.dp * scale, bitmap = cover)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp * scale)) {
            Text(playing.title, color = Color.White, fontSize = (12 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playing.artist, color = Muted, fontSize = (10 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (playing.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play o pausa", Modifier.size(36.dp * scale).clickable(onClick = toggle).padding(6.dp * scale), tint = Color.White)
        Icon(Icons.Rounded.SkipNext, "Successivo", Modifier.size(36.dp * scale).clickable(onClick = next).padding(6.dp * scale), tint = Color.White)
    }
}

@Composable
internal fun EmptyScreen(title: String, subtitle: String, scale: Float, modifier: Modifier, action: (() -> Unit)?, actionLabel: String = "Scansiona musica") {
    Column(modifier.fillMaxWidth().padding(20.dp * scale), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = GlowBlue, modifier = Modifier.size(36.dp * scale))
        Spacer(Modifier.height(14.dp * scale))
        Text(title, color = Color.White, fontSize = (16 * scale).sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(subtitle, color = Muted, fontSize = (12 * scale).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp * scale))
        if (action != null) TextButton(onClick = action) { Text(actionLabel) }
    }
}

@Composable
internal fun ErrorBox(message: String, scale: Float, modifier: Modifier, actions: List<Pair<String, () -> Unit>>) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
        Text(message, color = Ice, fontSize = (13 * scale).sp)
        actions.forEach { (label, run) -> TextButton(onClick = run) { Text(label) } }
    }
}

@Composable
internal fun BusyBar() { LinearProgressIndicator(Modifier.fillMaxWidth(), color = GlowBlue) }

/** Lettore: copertina grande, titolo centrato, barra di avanzamento con cursore e comandi. */
@Composable
internal fun NowPlaying(p: Playback, favorite: Boolean, scale: Float, controls: PlayerControls, onFavorite: (() -> Unit)?,
    queue: (() -> Unit)?, options: (() -> Unit)?, cover: Bitmap? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F1D31), Color(0xFF0A1322), DisplayBlack)))) {
        val coverSize = minOf(maxWidth * .56f, maxHeight * .40f)
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp * scale, vertical = 4.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(p.artwork, Modifier.size(coverSize).shadow(10.dp, RoundedCornerShape(14.dp * scale)), p.title, corner = 14.dp * scale, bitmap = cover)
            Spacer(Modifier.height(7.dp * scale))
            Text(p.title, color = Color.White, fontSize = (20 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(p.artist, color = Ice.copy(alpha = .88f), fontSize = (14 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (p.album.isNotBlank()) Text(p.album, color = Muted, fontSize = (12 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (p.error != null) Text(p.error, color = Color(0xFFFFA7A0), fontSize = (10 * scale).sp, maxLines = 2, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            if (p.buffering) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = GlowBlue)
            ProgressBar(if (p.duration > 0) (p.position.toFloat() / p.duration).coerceIn(0f, 1f) else 0f, scale) { controls.seekTo((it * p.duration).toLong()) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time(p.position), color = Ice, fontSize = (11 * scale).sp)
                Text("−${time(p.duration - p.position)}", color = Muted, fontSize = (11 * scale).sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp * scale), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlayerIcon(Icons.Rounded.Shuffle, "Casuale", if (p.shuffle) GlowBlue else Muted, scale, controls.shuffle)
                PlayerIcon(Icons.Rounded.SkipPrevious, "Precedente", Color.White, scale, controls.previous, 40)
                Box(Modifier.size(52.dp * scale).border(2.dp, Color.White.copy(alpha = .85f), CircleShape).clip(CircleShape).clickable(onClick = controls.toggle), contentAlignment = Alignment.Center) {
                    Icon(if (p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play o pausa", tint = Color.White, modifier = Modifier.size(30.dp * scale))
                }
                PlayerIcon(Icons.Rounded.SkipNext, "Successivo", Color.White, scale, controls.next, 40)
                PlayerIcon(if (p.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ripeti", if (p.repeat != 0) GlowBlue else Muted, scale, controls.repeat)
            }
            if (onFavorite != null || queue != null || options != null) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp * scale, bottom = 2.dp * scale), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (onFavorite != null) PlayerIcon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Preferito", if (favorite) Color(0xFFFF537B) else Ice, scale, onFavorite, 30)
                    if (queue != null) PlayerIcon(Icons.Rounded.QueueMusic, "Coda", Ice, scale, queue, 30)
                    if (options != null) PlayerIcon(Icons.Rounded.MoreVert, "Opzioni brano", Ice, scale, options, 30)
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, scale: Float, onSeek: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: fraction
    Box(Modifier.fillMaxWidth().height(22.dp * scale)
        .pointerInput(Unit) { detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> drag = (offset.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { drag?.let(onSeek); drag = null },
                onDragCancel = { drag = null }
            ) { change, _ -> change.consume(); drag = (change.position.x / size.width).coerceIn(0f, 1f) }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val track = 4.dp.toPx()
            val knob = 6.5.dp.toPx()
            val x = knob + (size.width - 2 * knob) * shown
            drawLine(Color.White.copy(alpha = .22f), Offset(knob, y), Offset(size.width - knob, y), track, StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(Color(0xFF8FD0FF), GlowBlue)), Offset(knob, y), Offset(x, y), track, StrokeCap.Round)
            drawCircle(Color.White, knob, Offset(x, y))
        }
    }
}

@Composable
private fun PlayerIcon(icon: ImageVector, description: String, color: Color, scale: Float, action: () -> Unit, size: Int = 34) {
    Icon(icon, description, Modifier.size(size.dp * scale).clip(CircleShape).clickable(onClick = action).padding((size / 6).dp * scale), tint = color)
}
