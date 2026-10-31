package it.jarvis.ipod.ui

import android.graphics.Bitmap
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import it.jarvis.ipod.data.Settings
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlin.math.hypot
import kotlin.random.Random

val Ice = Color(0xFFE9EEF5)
val Muted = Color(0xFF9CAAC0)
val Blue = Color(0xFF1788FF)
val DisplayBlack = Color(0xFF060C14)

@Composable
fun IPodShell(settings: Settings, modifier: Modifier = Modifier, display: @Composable (Float) -> Unit,
    onStep: (Int) -> Unit, onButton: (String) -> Unit, onHold: (String) -> Unit) {
    // Generate once and draw once per frame; thousands of individual grain strokes
    // inside the draw callback made cold software rendering unnecessarily expensive.
    val grainBrush = remember {
        var seed = 53
        var color = 0
        val pixels = IntArray(64 * 64) { index ->
            if (index % 2 == 0) {
                seed = seed * 1664525 + 1013904223
                color = if ((seed ushr 28) > 7) 0x02FFFFFF else 0x03000000
            }
            color
        }
        val bitmap = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888).asImageBitmap()
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
    val neon = remember { NeonState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audioManager = LocalContext.current.getSystemService(AudioManager::class.java)
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(settings.neon, lifecycleState) {
        if (!settings.neon || !lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            neon.energy = 0f
            return@LaunchedEffect
        }
        // Keep Compose's animation clock/policy: a separate lifecycle scope loses the test clock.
        var volume = 0f
        var lastVolumeCheck = 0L
        while (true) withInfiniteAnimationFrameNanos { frame ->
            if (frame - lastVolumeCheck >= 150_000_000L) {
                val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                volume = if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) 0f
                    else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
                lastVolumeCheck = frame
            }
            neon.tick(frame, volume)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Quasi a tutto schermo: margini minimi; sui telefoni più alti il corpo si allunga e il display guadagna spazio.
        val width = minOf(maxWidth - 8.dp, (maxHeight - 8.dp) / 1.82f)
        val scale = width.value / 400f
        val height = minOf(maxHeight - 8.dp, width * 2.12f)
        val shape = RoundedCornerShape(width * .105f)
        Box(Modifier.size(width, height).shadow(18.dp, shape).clip(shape)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val unit = w / 400f
                val black = settings.midnight
                val metal = if (black) listOf(Color(0xFF6A6D70), Color(0xFF292C30), Color(0xFF3C3E42), Color(0xFF202328))
                    else listOf(Color(0xFFF0F0ED), Color(0xFFC3C5C6), Color(0xFFE1E1DE), Color(0xFFAAADAF))
                drawRoundRect(Brush.linearGradient(metal, Offset(0f, 0f), Offset(w, h * .35f)), cornerRadius = CornerRadius(w * .105f))
                drawRect(grainBrush)
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .65f), Color.Transparent, Color.Black.copy(alpha = .45f))),
                    topLeft = Offset(2f * unit, 2f * unit), size = Size(w - 4 * unit, h - 4 * unit), cornerRadius = CornerRadius(w * .105f), style = Stroke(4f * unit))
                drawRoundRect(Color.Black.copy(alpha = .24f), topLeft = Offset(5f * unit, 5f * unit), size = Size(w - 10 * unit, h - 10 * unit),
                    cornerRadius = CornerRadius(w * .10f), style = Stroke(1.3f * unit))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = width * .066f, vertical = width * .073f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f).shadow(3.dp, RoundedCornerShape(13.dp * scale))
                    .background(Brush.linearGradient(listOf(Color(0xFF35393D), Color.Black, Color(0xFF74777B))), RoundedCornerShape(13.dp * scale))
                    .border(1.dp, Color(0xFF83888A), RoundedCornerShape(13.dp * scale)).padding(5.dp * scale)
                    .clip(RoundedCornerShape(8.dp * scale)).background(DisplayBlack)) {
                    // Il display ha misure proporzionali: ignora l'ingrandimento font di sistema, che tagliava i testi.
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1f))) { display(scale) }
                }
                Spacer(Modifier.height(width * .045f))
                ClickWheel(width * .608f, settings, neon, { if (settings.neon) neon.kick(it); onStep(it) }, { if (settings.neon) neon.kick(); onButton(it) }, { if (settings.neon) neon.kick(); onHold(it) })
                Spacer(Modifier.height(width * .035f))
            }
        }
    }
}

@Composable
private fun ClickWheel(diameter: Dp, settings: Settings, neon: NeonState, onStep: (Int) -> Unit, onButton: (String) -> Unit, onHold: (String) -> Unit) {
    val step by rememberUpdatedState(onStep)
    val button by rememberUpdatedState(onButton)
    val hold by rememberUpdatedState(onHold)
    val sensitivity by rememberUpdatedState(settings.sensitivity)
    val ink = if (settings.midnight) Color(0xFFE0E3E7) else Color(0xFF838B93)
    var pressed by remember { mutableStateOf<String?>(null) }
    val scale = diameter.value / 244
    Box(Modifier.size(diameter).testTag("click-wheel").pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val radius = size.width / 2f
            val start = down.position - Offset(radius, radius)
            if (hypot(start.x, start.y) > radius) return@awaitEachGesture
            down.consume()
            val key = WheelAccumulator.button(start.x, start.y, radius)
            val wheel = WheelAccumulator(sensitivity)
            wheel.begin(start.x, start.y)
            val ring = WheelAccumulator.onRing(start.x, start.y, radius)
            var dragged = false
            var held = false
            var cancelled = false
            var active = true
            pressed = key
            while (active) {
                val event = if (!dragged && (key == "next" || key == "previous")) {
                    withTimeoutOrNull(if (held) 180L else 500L) { awaitPointerEvent() }
                } else awaitPointerEvent()
                if (event == null) { held = true; hold(key); continue }
                // Cancel any multi-finger gesture; never convert it into an accidental click.
                if (event.changes.count { it.pressed } > 1) { cancelled = true; dragged = true; pressed = null }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) { active = false; continue }
                val offset = change.position - Offset(radius, radius)
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { dragged = true; pressed = null }
                if (dragged && ring && !held && !cancelled && WheelAccumulator.onRing(offset.x, offset.y, radius * 1.06f)) {
                    val count = wheel.move(offset.x, offset.y)
                    if (count != 0) step(count)
                }
                change.consume()
            }
            if (!dragged && !held && !cancelled) button(key)
            pressed = null; wheel.end()
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width / 2
            val unit = size.width / 244f
            val glow = settings.neon
            val c1 = Color.hsv(neon.hue, .85f, 1f)
            val c2 = Color.hsv((neon.hue + 55f) % 360f, .85f, 1f)
            val e = if (glow) neon.energy else 0f
            if (glow) {
                // Alone esterno: anelli sempre più deboli verso fuori, che si allargano con la musica.
                clipRect(left = -r, top = -size.width * .06f, right = size.width + r, bottom = size.height + r) {
                    for (i in 0 until 10) {
                        val f = i / 10f
                        drawCircle(c1.copy(alpha = ((.10f + .24f * e) * (1f - f)).coerceIn(0f, 1f)),
                            radius = r + (3f + i * 1.6f * (1f + e * .5f)) * unit, style = Stroke(3.4f * unit))
                    }
                }
            }
            drawCircle(Brush.linearGradient(if (settings.midnight) listOf(Color(0xFF22262D), Color(0xFF0C1016)) else listOf(Color(0xFFF6F6F3), Color(0xFFE0E2E1))))
            drawCircle(Color.Black.copy(alpha = .28f), style = Stroke(unit))
            drawCircle(Color.White.copy(alpha = .17f), radius = r - 1.5f * unit, style = Stroke(unit))
            drawCircle(Brush.linearGradient(if (settings.midnight) listOf(Color(0xFF54565B), Color(0xFF303338), Color(0xFF44474A))
                else listOf(Color(0xFFB4B7B8), Color(0xFFDDDEDC), Color(0xFFC5C7C6))), radius = r * .387f)
            drawCircle(Color.Black.copy(alpha = .36f), radius = r * .387f, style = Stroke(unit))
            drawCircle(Color.White.copy(alpha = .36f), radius = r * .387f - 1.3f * unit, style = Stroke(.7f * unit))
            if (pressed == "select") drawCircle(Color.Black.copy(alpha = .09f), radius = r * .385f)
            if (glow) {
                // Filo neon sul bordo della ruota e attorno al tasto centrale, con riverbero.
                drawCircle(c1.copy(alpha = .62f + .38f * e), radius = r - .8f * unit, style = Stroke((1.5f + 1.8f * e) * unit))
                for (i in 0 until 5) {
                    drawCircle(c2.copy(alpha = ((.30f + .35f * e) * (1f - i / 5f)).coerceIn(0f, 1f)),
                        radius = r * .387f + (2f + i * 1.7f * (1f + e * .6f)) * unit, style = Stroke(2.2f * unit))
                }
                drawCircle(c2.copy(alpha = .75f + .25f * e), radius = r * .387f + 1.2f * unit, style = Stroke((1.3f + 1.4f * e) * unit))
                // Soft light behind the four existing glyphs; no extra buttons or layout changes.
                for ((index, point) in listOf(Offset(r, r * .15f), Offset(r * .15f, r),
                    Offset(r * 1.85f, r), Offset(r, r * 1.85f)).withIndex()) {
                    val tint = Color.hsv((neon.hue + index * 32f) % 360f, .85f, 1f)
                    val spread = r * (.14f + e * .10f)
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .16f + e * .45f),
                        tint.copy(alpha = .07f + e * .20f), Color.Transparent), center = point, radius = spread),
                        radius = spread, center = point)
                }
            }
        }
        fun Modifier.control(name: String, label: String) = semantics {
            contentDescription = label; role = Role.Button
            onClick { button(name); true }
        }
        Text("MENU", color = ink, fontSize = (17f * scale).sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = diameter * .045f).control("menu", "MENU, indietro"))
        SkipGlyph(false, ink, Modifier.align(Alignment.CenterStart).padding(start = diameter * .045f).size(diameter * .12f).control("previous", "Brano precedente"))
        SkipGlyph(true, ink, Modifier.align(Alignment.CenterEnd).padding(end = diameter * .045f).size(diameter * .12f).control("next", "Brano successivo"))
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = diameter * .045f).control("play", "Play o pausa"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, "", tint = ink, modifier = Modifier.size(diameter * .075f))
            Icon(Icons.Rounded.Pause, "", tint = ink, modifier = Modifier.size(diameter * .075f))
        }
        Box(Modifier.size(diameter * .38f).align(Alignment.Center).control("select", "Seleziona"))
    }
}

@Composable
private fun SkipGlyph(next: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        fun x(fraction: Float) = size.width * if (next) fraction else 1f - fraction
        for (left in listOf(.08f, .40f)) {
            drawPath(Path().apply {
                moveTo(x(left), size.height * .25f)
                lineTo(x(left + .32f), size.height * .5f)
                lineTo(x(left), size.height * .75f)
                close()
            }, color)
        }
        drawLine(color, Offset(x(.83f), size.height * .25f), Offset(x(.83f), size.height * .75f), size.width * .10f)
    }
}

@Composable
fun Artwork(uri: String?, modifier: Modifier = Modifier, title: String = "Copertina") {
    Box(modifier.clip(RoundedCornerShape(9.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF07385B), Color(0xFF385980), Color(0xFFC66B83), Color(0xFFFFB465), Color(0xFF203B53))))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A1), Color(0xFFFFBD71).copy(alpha = .8f), Color.Transparent), center = Offset(w * .72f, h * .55f), radius = w * .28f), radius = w * .28f, center = Offset(w * .72f, h * .55f))
            drawCircle(Color(0xFFFFDC8E), w * .075f, Offset(w * .72f, h * .55f))
            for (layer in 0..4) {
                val path = Path().apply {
                    moveTo(0f, h); lineTo(0f, h * (.60f + layer * .065f))
                    for (i in 0..12) {
                        val x = w * i / 12
                        val noise = Random(layer * 71 + i).nextFloat()
                        lineTo(x, h * (.55f + layer * .07f + noise * .13f))
                    }
                    lineTo(w, h); close()
                }
                drawPath(path, listOf(Color(0xFF6F6485), Color(0xFF4A5271), Color(0xFF2D425F), Color(0xFF193448), Color(0xFF0B2334))[layer])
            }
        }
        if (!uri.isNullOrBlank()) coil.compose.AsyncImage(model = uri, contentDescription = title,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
