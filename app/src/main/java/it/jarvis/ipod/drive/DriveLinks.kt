package it.jarvis.ipod.drive

import org.json.JSONArray
import org.json.JSONObject

/** Cartella Drive condivisa con «Chiunque abbia il link», usata con la chiave API. */
data class DriveFolder(val id: String, val name: String, val resourceKey: String = "")

object DriveLinks {
    private val folderPath = Regex("""/folders/([A-Za-z0-9_-]{10,})""")
    private val idParam = Regex("""[?&]id=([A-Za-z0-9_-]{10,})""")
    private val bareId = Regex("""^[A-Za-z0-9_-]{15,}$""")
    private val resourceKeyParam = Regex("""[?&]resourcekey=([A-Za-z0-9_-]+)""")

    /** Estrae (idCartella, resourceKey) da un link Drive o da un ID incollato da solo. */
    fun parse(text: String): Pair<String, String>? {
        val value = text.trim()
        if (value.isEmpty()) return null
        val id = folderPath.find(value)?.groupValues?.get(1)
            ?: idParam.find(value)?.groupValues?.get(1)
            ?: bareId.find(value)?.value
            ?: return null
        val key = resourceKeyParam.find(value)?.groupValues?.get(1).orEmpty()
        return id to key
    }

    fun encode(folders: List<DriveFolder>): String = JSONArray().apply {
        folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("rk", it.resourceKey)) }
    }.toString()

    fun decode(json: String): List<DriveFolder> = runCatching {
        val array = JSONArray(json.ifBlank { "[]" })
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            DriveFolder(o.getString("id"), o.optString("name", "Cartella condivisa"), o.optString("rk", ""))
        }
    }.getOrDefault(emptyList())

    /** Valore dell'header X-Goog-Drive-Resource-Keys: «idFile/resourceKey,idFile/resourceKey». */
    fun resourceKeyHeader(vararg sources: Map<String, String>): String =
        sources.flatMap { it.entries }.filter { it.value.isNotBlank() }
            .distinctBy { it.key }.joinToString(",") { "${it.key}/${it.value}" }
}
