package it.jarvis.ipod.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.jarvisDataStore by preferencesDataStore(name = "jarvis")
data class Settings(val midnight: Boolean = false, val sensitivity: Float = 1f, val haptics: Boolean = true,
    val driveEmail: String = "", val sort: String = "Titolo", val cacheMb: Int = 256,
    val apiKey: String = "", val folders: String = "[]")

class Preferences(context: Context) {
    private val store = context.jarvisDataStore
    companion object {
        val midnight = booleanPreferencesKey("midnight")
        val sensitivity = floatPreferencesKey("sensitivity")
        val haptics = booleanPreferencesKey("haptics")
        val email = stringPreferencesKey("drive_email")
        val sort = stringPreferencesKey("sort")
        val cache = intPreferencesKey("cache_mb")
        val apiKey = stringPreferencesKey("drive_api_key")
        val folders = stringPreferencesKey("drive_folders")
        val queue = stringPreferencesKey("queue")
        val queueIndex = intPreferencesKey("queue_index")
        val position = longPreferencesKey("position")
        val shuffle = booleanPreferencesKey("shuffle")
        val repeat = intPreferencesKey("repeat")
    }
    val data = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val settings = data.map { Settings(it[midnight] ?: false, it[sensitivity] ?: 1f, it[haptics] ?: true,
        it[email] ?: "", it[sort] ?: "Titolo", it[cache] ?: 256, it[apiKey] ?: "", it[folders] ?: "[]") }
    suspend fun <T> set(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) { store.edit { it[key] = value } }
    suspend fun savePlayback(ids: List<String>, index: Int, pos: Long, shuffled: Boolean, repeated: Int) {
        store.edit {
            it[queue] = org.json.JSONArray(ids).toString(); it[queueIndex] = index; it[position] = pos
            it[shuffle] = shuffled; it[repeat] = repeated
        }
    }
}
