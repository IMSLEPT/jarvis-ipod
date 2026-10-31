package it.jarvis.ipod.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia delle impostazioni da conservare fuori dall'app (un file scelto dall'utente): Client ID di Spotify, chiave API Google,
 * cartelle musicali di Drive e preferenze. Non contiene token né l'account Google: dopo un ripristino basta ricollegare Google.
 * Il Client ID di Spotify è un identificativo pubblico; la chiave API Google è una credenziale: il file va tenuto in un posto privato.
 */
object SettingsBackup {
    private const val VERSION = 1

    fun toJson(s: Settings): String = JSONObject()
        .put("app", "Jarvis")
        .put("version", VERSION)
        .put("midnight", s.midnight)
        .put("sensitivity", s.sensitivity.toDouble())
        .put("hapticLevel", s.hapticLevel)
        .put("sort", s.sort)
        .put("controlMode", s.controlMode)
        .put("playerWheel", s.playerWheel)
        .put("glow", s.glow)
        .put("spotifyClientId", s.spotifyClientId)
        .put("driveApiKey", s.driveApiKey)
        .put("driveFolders", JSONArray().apply { s.driveFolders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        .toString(2)

    /** Legge un backup; i campi mancanti o non validi tornano ai valori predefiniti. Restituisce null se il testo non è un backup Jarvis. */
    fun fromJson(text: String): Settings? {
        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        if (o.optString("app") != "Jarvis") return null
        val base = Settings()
        val folders = o.optJSONArray("driveFolders")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val f = array.optJSONObject(i) ?: return@mapNotNull null
                val id = f.optString("id"); val name = f.optString("name")
                if (id.isBlank()) null else DriveFolder(id, name.ifBlank { "Cartella" })
            }
        } ?: emptyList()
        return base.copy(
            midnight = o.optBoolean("midnight", base.midnight),
            sensitivity = o.optDouble("sensitivity", base.sensitivity.toDouble()).toFloat().coerceIn(0.5f, 2f),
            hapticLevel = o.optInt("hapticLevel", base.hapticLevel).coerceIn(0, 3),
            sort = o.optString("sort", base.sort).ifBlank { base.sort },
            controlMode = o.optString("controlMode", base.controlMode).takeIf { it == "wheel" || it == "buttons" } ?: base.controlMode,
            playerWheel = o.optString("playerWheel", base.playerWheel).takeIf { it == "track" || it == "seek" } ?: base.playerWheel,
            glow = o.optString("glow", base.glow).ifBlank { base.glow },
            spotifyClientId = o.optString("spotifyClientId", "").trim(),
            driveApiKey = o.optString("driveApiKey", "").trim(),
            driveFolders = folders
        )
    }
}
