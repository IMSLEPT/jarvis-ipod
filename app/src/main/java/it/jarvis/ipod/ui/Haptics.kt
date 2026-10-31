package it.jarvis.ipod.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Vibrazione "da tastiera": un colpetto secco a ogni scatto della ruota e uno più marcato quando si preme un tasto.
 *
 * Usa gli effetti predefiniti del telefono (TICK, CLICK, HEAVY_CLICK, gli stessi della tastiera su Pixel) da Android 10;
 * prima usa impulsi brevi con ampiezza regolata. Da Android 13 la vibrazione è dichiarata come "tocco": segue quindi
 * l'interruttore di sistema Impostazioni > Suono e vibrazione > Feedback tattile. Se il telefono non ha un vibratore
 * ricade sul feedback della view.
 *
 * Livelli: 0 spento, 1 leggero, 2 normale, 3 forte.
 */
class Haptics(context: Context, private val view: View) {
    @Volatile var level: Int = 2
    private var lastMs = 0L
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    /** Scatto della ruota o spostamento di selezione. */
    fun step() = play(if (level >= 3) Kind.CLICK else Kind.TICK, minGapMs = 18)

    /** Pressione di un tasto: indietro, avanti, play, selezione. */
    fun press() = play(when (level) { 1 -> Kind.TICK; 2 -> Kind.CLICK; else -> Kind.HEAVY }, minGapMs = 0)

    private enum class Kind { TICK, CLICK, HEAVY }

    private fun play(kind: Kind, minGapMs: Long) {
        if (level <= 0) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastMs < minGapMs) return
        lastMs = now
        val v = vibrator
        if (v == null || !v.hasVibrator()) { fallback(kind); return }
        runCatching {
            val effect = effect(kind, v)
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                v.vibrate(effect)
            }
        }.onFailure { fallback(kind) }
    }

    private fun effect(kind: Kind, v: Vibrator): VibrationEffect {
        if (Build.VERSION.SDK_INT >= 29) {
            return VibrationEffect.createPredefined(when (kind) {
                Kind.TICK -> VibrationEffect.EFFECT_TICK
                Kind.CLICK -> VibrationEffect.EFFECT_CLICK
                Kind.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
            })
        }
        val millis = when (kind) { Kind.TICK -> 9L; Kind.CLICK -> 16L; Kind.HEAVY -> 28L }
        val amplitude = if (v.hasAmplitudeControl()) when (kind) { Kind.TICK -> 90; Kind.CLICK -> 170; Kind.HEAVY -> 255 }
            else VibrationEffect.DEFAULT_AMPLITUDE
        return VibrationEffect.createOneShot(millis, amplitude)
    }

    private fun fallback(kind: Kind) {
        view.performHapticFeedback(if (kind == Kind.TICK) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

@Composable
fun rememberHaptics(level: Int): Haptics {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context, view) { Haptics(context, view) }
    haptics.level = level
    return haptics
}
