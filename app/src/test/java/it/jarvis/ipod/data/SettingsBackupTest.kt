package it.jarvis.ipod.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupTest {
    private val full = Settings(
        midnight = false, sensitivity = 1.5f, hapticLevel = 3, sort = "Artista", controlMode = "buttons", playerWheel = "seek",
        glow = "violet", neon = false, spotifyClientId = "abc123clientid", driveApiKey = "AIzaSyExample", driveEmail = "x@y.z", backupUri = "content://file",
        driveFolders = listOf(DriveFolder("id1", "Musica"), DriveFolder("id2", "Dischi"))
    )

    @Test fun roundTripKeepsEverythingExceptAccountAndBackupLocation() {
        val back = SettingsBackup.fromJson(SettingsBackup.toJson(full))!!
        assertEquals(full.copy(driveEmail = "", backupUri = ""), back)
    }

    @Test fun rejectsFilesThatAreNotJarvisBackups() {
        assertNull(SettingsBackup.fromJson("non è json"))
        assertNull(SettingsBackup.fromJson("{\"app\":\"Altro\"}"))
    }

    @Test fun invalidValuesFallBackToDefaults() {
        val b = SettingsBackup.fromJson("{\"app\":\"Jarvis\",\"hapticLevel\":99,\"controlMode\":\"boh\",\"sensitivity\":9,\"driveFolders\":[{\"id\":\"\"},{\"id\":\"k\"}]}")!!
        assertEquals(3, b.hapticLevel)
        assertEquals("wheel", b.controlMode)
        assertEquals(2f, b.sensitivity, 0f)
        assertEquals(listOf(DriveFolder("k", "Cartella")), b.driveFolders)
    }
}
