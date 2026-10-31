package it.jarvis.ipod.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import it.jarvis.ipod.data.Settings
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

val Ice = Color(0xFFE9EEF5)
val Muted = Color(0xFF9CAAC0)
val Blue = Color(0xFF1788FF)
val DisplayBlack = Color(0xFF060C14)
val Violet = Color(0xFF6C4DF5)
val GlowBlue = Color(0xFF45B0FF)

private val TwoPi = (2 * PI).toFloat()
private fun degrees(radians: Float) = radians * 180f / PI.toFloat()

@Composable
fun IPodShell(settings: Settings, haptics: Haptics, modifier: Modifier = Modifier, display: @Composable (Float) -> Unit,
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
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth - 16.dp, (maxHeight - 12.dp) / 1.82f)
        val scale = width.value / 400f
        val height = width * 1.82f
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
                Box(Modifier.fillMaxWidth().height(height * .535f).shadow(3.dp, RoundedCornerShape(13.dp * scale))
                    .background(Brush.linearGradient(listOf(Color(0xFF35393D), Color.Black, Color(0xFF74777B))), RoundedCornerShape(13.dp * scale))
                    .border(1.dp, Color(0xFF83888A), RoundedCornerShape(13.dp * scale)).padding(5.dp * scale)
                    .clip(RoundedCornerShape(8.dp * scale)).background(DisplayBlack)) { display(scale) }
                Spacer(Modifier.weight(1f))
                ClickWheel(width * .608f, settings, haptics, onStep, onButton, onHold)
                Spacer(Modifier.height(width * .035f))
            }
        }
    }
}

/**
 * Ruota scura con anello luminoso. Con il dito sull'anello compare una scia che segue la rotazione e le tacche
 * vicine si accendono; ogni pressione lascia un'onda e il tasto centrale si abbassa. In modalità "solo tasti"
 * la rotazione è disattivata e le quattro zone dell'anello funzionano come tasti normali.
 */
@Composable
private fun ClickWheel(diameter: Dp, settings: Settings, haptics: Haptics, onStep: (Int) -> Unit, onButton: (String) -> Unit, onHold: (String) -> Unit) {
    val step by rememberUpdatedState(onStep)
    val button by rememberUpdatedState(onButton)
    val hold by rememberUpdatedState(onHold)
    val sensitivity by rememberUpdatedState(settings.sensitivity)
    val wheelMode by rememberUpdatedState(settings.controlMode != "buttons")
    val ink = Color(0xFFE6EBF2)
    var pressed by remember { mutableStateOf<String?>(null) }
    var touching by remember { mutableStateOf(false) }
    var angle by remember { mutableFloatStateOf(0f) }
    var ripplePos by remember { mutableStateOf(Offset.Zero) }
    var rippleKey by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(1f) }
    val trail = remember { Animatable(0f) }
    LaunchedEffect(rippleKey) {
        if (rippleKey > 0) { ripple.snapTo(0f); ripple.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
    }
    LaunchedEffect(touching) { if (touching) trail.snapTo(angle) }
    LaunchedEffect(angle) { if (touching) trail.animateTo(angle, spring(dampingRatio = 0.9f, stiffness = 110f)) }
    val glow by animateFloatAsState(if (touching) 1f else 0f, tween(280), label = "glow")
    val centerSize by animateFloatAsState(if (pressed == "select") 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "center")
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "breathe")
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
            val startAngle = atan2(start.y, start.x)
            var dragged = false
            var held = false
            var cancelled = false
            var active = true
            pressed = key
            ripplePos = down.position
            rippleKey++
            // Come sulla tastiera: il tasto centrale vibra subito alla pressione.
            if (key == "select") haptics.press()
            while (active) {
                val event = if (!dragged && (key == "next" || key == "previous")) {
                    withTimeoutOrNull(if (held) 180L else 500L) { awaitPointerEvent() }
                } else awaitPointerEvent()
                if (event == null) { held = true; haptics.step(); hold(key); continue }
                // Cancel any multi-finger gesture; never convert it into an accidental click.
                if (event.changes.count { it.pressed } > 1) { cancelled = true; dragged = true; pressed = null; touching = false }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) { active = false; continue }
                val offset = change.position - Offset(radius, radius)
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { dragged = true; pressed = null }
                if (dragged && ring && !held && !cancelled && wheelMode && WheelAccumulator.onRing(offset.x, offset.y, radius * 1.06f)) {
                    val count = wheel.move(offset.x, offset.y)
                    angle = startAngle + wheel.total.toFloat()
                    touching = true
                    if (count != 0) {
                        haptics.step()
                        step(count)
                    }
                }
                change.consume()
            }
            if (!dragged && !held && !cancelled) {
                if (key != "select") haptics.press()
                button(key)
            }
            pressed = null; touching = false; wheel.end()
        }
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width / 2
            val c = center
            val u = size.width / 244f
            // Disco scuro con bordo appena illuminato.
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1F232A), Color(0xFF0B0D11)), center = c, radius = r), radius = r)
            drawCircle(Color.White.copy(alpha = .10f), radius = r - 1f * u, style = Stroke(1.2f * u))
            // Anello luminoso che "respira"; si accende quando lo si tocca.
            val ringRadius = r - 3.5f * u
            val energy = (0.7f + 0.3f * breathe) * (1f + 0.55f * glow)
            for ((width, alpha) in listOf(13f to .06f, 8f to .10f, 4.5f to .20f, 1.8f to .85f)) {
                drawCircle(GlowBlue.copy(alpha = (alpha * energy).coerceAtMost(1f)), radius = ringRadius, style = Stroke(width * u))
            }
            drawCircle(Color.Black.copy(alpha = .5f), radius = ringRadius - 7f * u, style = Stroke(3f * u))
            // Tacche: quelle vicine al dito si accendono.
            val finger = angle
            for (i in 0 until 48) {
                val tick = i * TwoPi / 48f
                var distance = (tick - finger) % TwoPi
                if (distance > PI.toFloat()) distance -= TwoPi else if (distance < -PI.toFloat()) distance += TwoPi
                val near = (1f - abs(distance) / (PI.toFloat() / 4f)).coerceIn(0f, 1f) * glow
                val dx = cos(tick); val dy = sin(tick)
                drawLine(
                    color = if (near > 0f) GlowBlue.copy(alpha = .15f + .8f * near) else Color.White.copy(alpha = .10f),
                    start = Offset(c.x + dx * r * .60f, c.y + dy * r * .60f),
                    end = Offset(c.x + dx * r * .66f, c.y + dy * r * .66f),
                    strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            }
            // Scia a cometa dietro al dito.
            if (glow > 0.01f) {
                val head = angle
                val sweep = (head - trail.value).coerceIn(-2.2f, 2.2f)
                if (abs(sweep) > 0.02f) {
                    val segments = 26
                    val diameterArc = ringRadius * 2
                    for (i in 0 until segments) {
                        val t = i / segments.toFloat()
                        val begin = head - sweep * (1f - t)
                        drawArc(
                            color = GlowBlue.copy(alpha = (t * t * glow * .95f).coerceIn(0f, 1f)),
                            startAngle = degrees(begin), sweepAngle = degrees(sweep / segments * 1.15f), useCenter = false,
                            topLeft = Offset(c.x - ringRadius, c.y - ringRadius), size = Size(diameterArc, diameterArc),
                            style = Stroke(width = 4.4f * u, cap = StrokeCap.Butt))
                    }
                }
                val head2 = Offset(c.x + cos(head) * ringRadius, c.y + sin(head) * ringRadius)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFBFE5FF).copy(alpha = .9f * glow), Color.Transparent), center = head2, radius = 15f * u),
                    radius = 15f * u, center = head2)
                drawCircle(Color.White.copy(alpha = .95f * glow), radius = 2.6f * u, center = head2)
            }
            // Tasto centrale: si abbassa e si illumina quando premuto.
            val buttonRadius = r * .387f * centerSize
            drawCircle(Color.Black.copy(alpha = .45f), radius = r * .387f + 3f * u)
            drawCircle(Brush.linearGradient(listOf(Color(0xFF5A5E66), Color(0xFF30333A), Color(0xFF454951)),
                start = Offset(c.x - r * .39f, c.y - r * .39f), end = Offset(c.x + r * .39f, c.y + r * .39f)), radius = buttonRadius)
            drawCircle(Color.White.copy(alpha = .22f), radius = buttonRadius - 1.2f * u, style = Stroke(.8f * u))
            if (pressed == "select") {
                drawCircle(Color.Black.copy(alpha = .12f), radius = buttonRadius)
                drawCircle(GlowBlue.copy(alpha = .65f), radius = buttonRadius, style = Stroke(2.4f * u))
            }
            // Onda nel punto toccato.
            val wave = ripple.value
            if (wave < 1f) {
                val waveRadius = 6f * u + wave * r * .5f
                drawCircle(GlowBlue.copy(alpha = (1f - wave) * .12f), radius = waveRadius, center = ripplePos)
                drawCircle(GlowBlue.copy(alpha = (1f - wave) * .55f), radius = waveRadius, center = ripplePos, style = Stroke(2f * u))
            }
        }
        fun Modifier.control(name: String, label: String) = semantics {
            contentDescription = label; role = Role.Button
            onClick { button(name); true }
        }
        fun tone(name: String) = if (pressed == name) GlowBlue else ink
        Text("MENU", color = tone("menu"), fontSize = (17f * scale).sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = diameter * .045f).control("menu", "MENU, indietro"))
        SkipGlyph(false, tone("previous"), Modifier.align(Alignment.CenterStart).padding(start = diameter * .045f).size(diameter * .12f).control("previous", "Brano precedente"))
        SkipGlyph(true, tone("next"), Modifier.align(Alignment.CenterEnd).padding(end = diameter * .045f).size(diameter * .12f).control("next", "Brano successivo"))
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = diameter * .045f).control("play", "Play o pausa"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, "", tint = tone("play"), modifier = Modifier.size(diameter * .075f))
            Icon(Icons.Rounded.Pause, "", tint = tone("play"), modifier = Modifier.size(diameter * .075f))
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

private class Scene(val sky: List<Color>, val glow: Color, val sun: Color, val layers: List<Color>)

private val scenes = listOf(
    Scene(listOf(Color(0xFF07385B), Color(0xFF385980), Color(0xFFC66B83), Color(0xFFFFB465), Color(0xFF203B53)),
        Color(0xFFFFBD71), Color(0xFFFFDC8E),
        listOf(Color(0xFF6F6485), Color(0xFF4A5271), Color(0xFF2D425F), Color(0xFF193448), Color(0xFF0B2334))),
    Scene(listOf(Color(0xFF050B1F), Color(0xFF102A57), Color(0xFF2D5FA8), Color(0xFF6EA8E8), Color(0xFF132A4A)),
        Color(0xFF9CC8FF), Color(0xFFDDEBFF),
        listOf(Color(0xFF3A5A8C), Color(0xFF2A4570), Color(0xFF1D3558), Color(0xFF122640), Color(0xFF0A1828))),
    Scene(listOf(Color(0xFF1A0B3A), Color(0xFF4B2A7B), Color(0xFFB5489B), Color(0xFFFF8F6B), Color(0xFF2A1840)),
        Color(0xFFFF9A7A), Color(0xFFFFD08A),
        listOf(Color(0xFF7C4F8F), Color(0xFF5B3A78), Color(0xFF3E2A60), Color(0xFF281C47), Color(0xFF160F2E)))
)

/** Copertina reale se presente; altrimenti un paesaggio disegnato proceduralmente ([variant] sceglie la tavolozza). */
@Composable
fun Artwork(uri: String?, modifier: Modifier = Modifier, title: String = "Copertina", variant: Int = 0, corner: Dp = 9.dp, bitmap: Bitmap? = null) {
    val scene = scenes[abs(variant) % scenes.size]
    Box(modifier.clip(RoundedCornerShape(corner))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(scene.sky))
            drawCircle(Brush.radialGradient(listOf(scene.sun, scene.glow.copy(alpha = .8f), Color.Transparent), center = Offset(w * .72f, h * .55f), radius = w * .28f), radius = w * .28f, center = Offset(w * .72f, h * .55f))
            drawCircle(scene.sun, w * .075f, Offset(w * .72f, h * .55f))
            for (layer in 0..4) {
                val path = Path().apply {
                    moveTo(0f, h); lineTo(0f, h * (.60f + layer * .065f))
                    for (i in 0..12) {
                        val x = w * i / 12
                        val noise = Random(layer * 71 + i + variant * 13).nextFloat()
                        lineTo(x, h * (.55f + layer * .07f + noise * .13f))
                    }
                    lineTo(w, h); close()
                }
                drawPath(path, scene.layers[layer])
            }
        }
        if (bitmap != null) androidx.compose.foundation.Image(bitmap = bitmap.asImageBitmap(), contentDescription = title,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (!uri.isNullOrBlank()) coil.compose.AsyncImage(model = uri, contentDescription = title,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
