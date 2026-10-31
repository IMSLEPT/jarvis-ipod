package it.jarvis.ipod.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis")

data class DriveFolder(val id: String, val name: String)

/**
 * [controlMode]: "wheel" = ruota e tasti, "buttons" = solo tasti (la rotazione è disattivata).
 * [playerWheel]: cosa fa la rotazione nella schermata del lettore, "track" = cambia brano, "seek" = cerca nel brano.
 * [hapticLevel]: 0 spento, 1 leggero, 2 normale, 3 forte.
 */
data class Settings(val midnight: Boolean = true, val sensitivity: Float = 1f, val hapticLevel: Int = 2,
    val driveEmail: String = "", val sort: String = "Titolo", val cacheMb: Int = 256,
    val driveFolders: List<DriveFolder> = emptyList(), val controlMode: String = "wheel",
    val playerWheel: String = "track", val spotifyClientId: String = "",
    val glow: String = "blue", val backupUri: String = "", val driveApiKey: String = "")

class Preferences(context: Context) {
    private val store = context.jarvisDataStore
    companion object {
        val midnight = booleanPreferencesKey("midnight")
        val sensitivity = floatPreferencesKey("sensitivity")
        /** Chiave storica on/off; resta per leggere la scelta fatta prima dei livelli di vibrazione. */
        val haptics = booleanPreferencesKey("haptics")
        val hapticLevel = intPreferencesKey("haptic_level")
        val controlMode = stringPreferencesKey("control_mode")
        val playerWheel = stringPreferencesKey("player_wheel")
        val spotifyClientId = stringPreferencesKey("spotify_client_id")
        val glow = stringPreferencesKey("glow")
        val backupUri = stringPreferencesKey("backup_uri")
        val email = stringPreferencesKey("drive_email")
        val sort = stringPreferencesKey("sort")
        val cache = intPreferencesKey("cache_mb")
        val driveFolders = stringPreferencesKey("drive_folders")
        /** Chiave API Google per leggere cartelle Drive condivise con link, senza collegare un account. */
        val driveApiKey = stringPreferencesKey("drive_api_key")
        fun decodeFolders(raw: String?): List<DriveFolder> = runCatching {
            val array = org.json.JSONArray(raw ?: "[]")
            (0 until array.length()).map { array.getJSONObject(it) }
                .map { DriveFolder(it.getString("id"), it.getString("name")) }
        }.getOrDefault(emptyList())
        fun encodeFolders(folders: List<DriveFolder>): String = org.json.JSONArray().apply {
            folders.forEach { put(org.json.JSONObject().put("id", it.id).put("name", it.name)) }
        }.toString()
        val queue = stringPreferencesKey("queue")
        val queueIndex = intPreferencesKey("queue_index")
        val position = longPreferencesKey("position")
        val shuffle = booleanPreferencesKey("shuffle")
        val repeat = intPreferencesKey("repeat")
    }
    val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val settings = data.map { prefs ->
        Settings(
            midnight = prefs[midnight] ?: true,
            sensitivity = prefs[sensitivity] ?: 1f,
            hapticLevel = (prefs[hapticLevel] ?: if (prefs[haptics] == false) 0 else 2).coerceIn(0, 3),
            driveEmail = prefs[email] ?: "",
            sort = prefs[sort] ?: "Titolo",
            cacheMb = prefs[cache] ?: 256,
            driveFolders = decodeFolders(prefs[driveFolders]),
            controlMode = prefs[controlMode] ?: "wheel",
            playerWheel = prefs[playerWheel] ?: "track",
            spotifyClientId = prefs[spotifyClientId] ?: "",
            glow = prefs[glow] ?: "blue",
            backupUri = prefs[backupUri] ?: "",
            driveApiKey = prefs[driveApiKey] ?: ""
        )
    }
    suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) { store.edit { it[key] = value } }
    /** Ripristina le impostazioni da un backup. Non tocca la posizione del file di backup automatico. */
    suspend fun restore(b: Settings) {
        store.edit {
            it[midnight] = b.midnight; it[sensitivity] = b.sensitivity; it[hapticLevel] = b.hapticLevel
            it[sort] = b.sort; it[controlMode] = b.controlMode; it[playerWheel] = b.playerWheel; it[glow] = b.glow
            it[spotifyClientId] = b.spotifyClientId; it[driveFolders] = encodeFolders(b.driveFolders); it[driveApiKey] = b.driveApiKey
        }
    }
    suspend fun savePlayback(ids: List<String>, index: Int, pos: Long, shuffled: Boolean, repeated: Int) {
        store.edit {
            it[queue] = org.json.JSONArray(ids).toString(); it[queueIndex] = index; it[position] = pos
            it[shuffle] = shuffled; it[repeat] = repeated
        }
    }
}
