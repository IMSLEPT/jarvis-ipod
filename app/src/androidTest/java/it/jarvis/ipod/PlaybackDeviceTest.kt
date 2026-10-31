package it.jarvis.ipod

import android.content.ContentValues
import android.content.ComponentName
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import it.jarvis.ipod.data.Preferences
import it.jarvis.ipod.data.Track
import it.jarvis.ipod.playback.PlaybackService
import it.jarvis.ipod.playback.AudioReactive
import android.os.SystemClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrument.targetContext
    private val app get() = context.applicationContext as JarvisApp

    @Test fun mediaStorePlaybackBackgroundAndResume() {
        runBlocking { app.preferences.set(Preferences.neon, true) }
        val permission = if(Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        instrument.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").use { p -> java.io.FileInputStream(p.fileDescriptor).readBytes() }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Jarvis_Test.wav")
            put(MediaStore.Audio.Media.TITLE, "Jarvis Test")
            put(MediaStore.Audio.Media.ARTIST, "Test audio sintetico")
            put(MediaStore.Audio.Media.ALBUM, "Verifica locale")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if(Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/JarvisTest")
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        val offlineFile = java.io.File(context.filesDir, "jarvis-test-offline.wav")
        var controller: MediaController? = null
        try {
            val sampleRate = 16000
            val samples = sampleRate * 180
            val period = ByteBuffer.allocate(sampleRate * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                repeat(sampleRate) { i -> putShort((sin(2 * PI * 440 * i / sampleRate) * 1200).toInt().toShort()) }
            }.array()
            val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray()); putInt(16)
                putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(samples * 2)
                repeat(180) { put(period) }
            }.array()
            context.contentResolver.openOutputStream(uri)!!.use { it.write(wav) }
            val count = runBlocking { app.local.scan() }
            assertTrue(count > 0)
            val audioId = android.content.ContentUris.parseId(uri)
            val track = runBlocking { app.dao.allTracks().first { it.source == "local" && it.id.endsWith(":$audioId") } }
            assertTrue(track.title.isNotBlank())
            runBlocking { app.dao.toggleFavorite(track.id) }
            runBlocking { app.local.scan() }
            assertTrue(runBlocking { app.dao.track(track.id)!!.favorite })
            val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            val p = future.get(30, TimeUnit.SECONDS)
            controller = p
            instrument.runOnMainSync { p.setMediaItem(track.mediaItem()); p.prepare(); p.play() }
            var position = 0L
            val until = System.currentTimeMillis() + 30000
            while(position < 600 && System.currentTimeMillis() < until) {
                Thread.sleep(300)
                instrument.runOnMainSync { position = p.currentPosition }
            }
            assertTrue("Audio position must actually advance", position >= 600)
            val meterDeadline = SystemClock.uptimeMillis() + 5000
            while (AudioReactive.level(SystemClock.uptimeMillis()) == 0f && SystemClock.uptimeMillis() < meterDeadline) Thread.sleep(30)
            assertTrue("Decoded PCM must feed the neon meter", AudioReactive.level(SystemClock.uptimeMillis()) > 0f)
            val stopped = CountDownLatch(1)
            activityRule.scenario.onActivity { activity ->
                activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) stopped.countDown()
                })
                assertTrue("The actual Android task must move into the background", activity.moveTaskToBack(true))
            }
            assertTrue("Activity must actually reach ON_STOP", stopped.await(30, TimeUnit.SECONDS))
            Thread.sleep(1800)
            var after = 0L
            instrument.runOnMainSync {
                after = p.currentPosition
                assertTrue("Background state=${p.playbackState}, error=${p.playerError}", p.isPlaying)
                p.pause()
            }
            assertTrue("Background playback must advance", after > position + 500)
            assertEquals(0f, AudioReactive.level(SystemClock.uptimeMillis()))
            Thread.sleep(3500)
            val snapshot = runBlocking { app.preferences.data.first() }
            assertTrue(snapshot[Preferences.queue]!!.contains(track.id))
            assertTrue(snapshot[Preferences.position]!! > 0)
            // Exercise the real Drive URI -> private file path, with no OAuth grant or network.
            offlineFile.writeBytes(wav)
            val offline = Track("drive:jarvis-test-offline", "drive://jarvis-test-offline", "Offline Drive test",
                source = "drive", offlinePath = offlineFile.absolutePath)
            runBlocking { app.dao.save(offline) }
            instrument.runOnMainSync { p.setMediaItem(offline.mediaItem()); p.prepare(); p.play() }
            position = 0
            val offlineUntil = System.currentTimeMillis() + 30000
            while(position < 500 && System.currentTimeMillis() < offlineUntil) {
                Thread.sleep(300)
                instrument.runOnMainSync { position = p.currentPosition }
            }
            assertTrue("Offline Drive must play without Google authorization", position >= 500)
        } finally {
            controller?.let { p -> instrument.runOnMainSync { p.stop(); p.clearMediaItems(); p.release() } }
            context.contentResolver.delete(uri, null, null)
            offlineFile.delete()
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM tracks WHERE id = ?", arrayOf("drive:jarvis-test-offline"))
            runBlocking { app.local.scan() }
        }
    }
}
