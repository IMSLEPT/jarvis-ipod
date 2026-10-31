package it.jarvis.ipod

import android.content.ContentValues
import android.content.ComponentName
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.jarvis.ipod.data.Preferences
import it.jarvis.ipod.data.Track
import it.jarvis.ipod.playback.PlaybackService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class JarvisDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val app get() = context.applicationContext as JarvisApp

    @Test fun wheelNavigatesAndThemePersists() {
        runBlocking {
            app.preferences.set(Preferences.midnight, false)
            app.preferences.set(Preferences.neon, false)
        }
        compose.waitForIdle()
        // L'app parte dalla schermata iniziale: il tasto centrale entra nel menu.
        compose.onNodeWithContentDescription("Seleziona").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("click-wheel").performTouchInput {
            val r = width * .40f
            down(Offset(center.x + r, center.y))
            for(i in 1..48) {
                val a = i * PI / 24
                moveTo(Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r), 24)
            }
            up()
        }
        compose.onNodeWithContentDescription("Seleziona").performClick()
        compose.onNodeWithText("Aspetto iPod").assertIsDisplayed().performClick()
        compose.onNodeWithText("Midnight Black").assertIsDisplayed()
        // Confirm the second appearance entry through the original down/centre controls.
        compose.onNodeWithContentDescription("Giù, play o pausa").performClick()
        compose.onNodeWithContentDescription("Seleziona").performClick()
        compose.waitUntil(10000) { runBlocking { app.preferences.settings.first().midnight } }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertTrue(runBlocking { app.preferences.settings.first().midnight })
        // MENU sale nella lista (il ritorno indietro è la pressione prolungata): la schermata resta la stessa.
        compose.onNodeWithContentDescription("MENU, su (tieni premuto per tornare indietro)").performClick()
        compose.onNodeWithText("Aspetto iPod").assertIsDisplayed()
    }

    @Test fun neonTogglePersistsWithoutChangingExistingSettings() {
        val previous = runBlocking { app.preferences.settings.first() }
        runBlocking { app.preferences.set(Preferences.neon, false) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Seleziona").performClick()
        compose.onNodeWithTag("menu-list").performScrollToNode(hasText("Impostazioni"))
        compose.onNodeWithText("Impostazioni").performClick()
        compose.onNodeWithContentDescription("Alone neon").performScrollTo().assertIsOff().performClick()
        compose.waitUntil(10000) { runBlocking { app.preferences.settings.first().neon } }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Alone neon").performScrollTo().assertIsOn()
        val current = runBlocking { app.preferences.settings.first() }
        assertEquals(previous.copy(neon = true), current)
        compose.onNodeWithContentDescription("Alone neon").performClick()
        compose.waitUntil(10000) { runBlocking { !app.preferences.settings.first().neon } }
    }

}
