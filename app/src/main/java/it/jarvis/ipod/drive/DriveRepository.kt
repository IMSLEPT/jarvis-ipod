package it.jarvis.ipod.drive

import android.content.Context
import it.jarvis.ipod.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class DriveFile(val id: String, val name: String, val folder: Boolean, val downloadable: Boolean = true) {
    fun track(folderName: String) = Track("drive:$id", "drive://$id", name.substringBeforeLast('.', name),
        "Google Drive", folderName, source = "drive", added = System.currentTimeMillis())
}

enum class Via { AUTO, OAUTH, KEY }

class DriveRepository(private val context: Context, private val auth: DriveAuth, private val dao: LibraryDao,
    private val preferences: Preferences) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    // Resource key dei file elencati in questa sessione (solo link vecchi di Drive ne hanno bisogno).
    private val resourceKeys = ConcurrentHashMap<String, String>()

    /** Costruisce la richiesta: token OAuth se l'account è collegato, altrimenti chiave API + resource key. */
    private suspend fun prepare(url: String, via: Via = Via.AUTO): Request {
        val s = preferences.settings.first()
        val oauth = when (via) { Via.OAUTH -> true; Via.KEY -> false; Via.AUTO -> s.driveEmail.isNotBlank() }
        if (oauth) return Request.Builder().url(url).header("Authorization", "Bearer ${auth.accessToken()}").build()
        if (s.apiKey.isBlank()) throw IOException("Collega Google Drive oppure imposta una chiave API dalle impostazioni.")
        val keys = DriveLinks.resourceKeyHeader(DriveLinks.decode(s.folders).associate { it.id to it.resourceKey }, resourceKeys)
        return Request.Builder().url(url.toHttpUrl().newBuilder().addQueryParameter("key", s.apiKey).build())
            .apply { if (keys.isNotEmpty()) header("X-Goog-Drive-Resource-Keys", keys) }.build()
    }
    private fun describe(code: Int, body: String?, bearer: Boolean): String {
        val detail = runCatching { JSONObject(body.orEmpty()).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        val base = if (bearer) when (code) {
            401 -> "Sessione Google scaduta. Ricollega Drive."
            403 -> "Accesso negato da Drive. Verifica autorizzazioni, quota e possibilità di scaricare il file."
            404 -> "File non più disponibile su Drive."
            else -> "Drive non disponibile (HTTP $code). Riprova."
        } else when (code) {
            400 -> "Chiave API non valida. Controlla di averla copiata per intero."
            401, 403 -> "Accesso negato. Controlla che la Drive API sia abilitata nel progetto della chiave, che la chiave non sia limitata ad altre API o app e che la cartella sia condivisa con «Chiunque abbia il link»."
            404 -> "Cartella o file non trovato. Controlla il link e che sia condiviso con «Chiunque abbia il link»."
            429 -> "Troppe richieste a Drive. Riprova tra poco."
            else -> "Drive non disponibile (HTTP $code). Riprova."
        }
        return if (!bearer && detail.isNotBlank() && code != 429) "$base ($detail)" else base
    }
    private suspend fun get(url: String, via: Via = Via.AUTO): String = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            val request = prepare(url, via)
            val bearer = request.header("Authorization") != null
            client.newCall(request).execute().use {
                if (it.code == 401 && bearer && attempt == 0) { auth.invalidate(); return@use }
                if (!it.isSuccessful) throw IOException(describe(it.code, it.body?.string(), bearer))
                return@withContext it.body?.string() ?: throw IOException("Risposta Drive vuota")
            }
        }
        throw IOException("Ricollega Google Drive.")
    }
    /** URL e header per lo streaming di un file; usato dal servizio di riproduzione. */
    suspend fun streamRequest(id: String): Pair<String, Map<String, String>> {
        val request = prepare("https://www.googleapis.com/drive/v3/files/$id?alt=media&supportsAllDrives=true")
        return request.url.toString() to request.headers.toMultimap().mapValues { it.value.first() }
    }
    suspend fun folderName(id: String, resourceKey: String = ""): String {
        if (resourceKey.isNotBlank()) resourceKeys[id] = resourceKey
        return JSONObject(get("https://www.googleapis.com/drive/v3/files/$id?fields=name&supportsAllDrives=true", Via.KEY)).getString("name")
    }
    suspend fun accountEmail(): String {
        val url = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)"
        return JSONObject(get(url, Via.OAUTH)).getJSONObject("user").getString("emailAddress")
    }
    suspend fun list(folderId: String, search: String = "", via: Via = Via.AUTO): List<DriveFile> {
        fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
        val query = if (search.isBlank()) "'${escape(folderId)}' in parents" else "name contains '${escape(search)}'"
        val files = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "trashed = false and ($query) and (mimeType = 'application/vnd.google-apps.folder' or mimeType contains 'audio/' or mimeType = 'application/ogg')")
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,resourceKey,capabilities(canDownload))")
                .addQueryParameter("pageSize", "1000").addQueryParameter("orderBy", "folder,name_natural")
                .addQueryParameter("supportsAllDrives", "true").addQueryParameter("includeItemsFromAllDrives", "true")
                .apply { page?.let { addQueryParameter("pageToken", it) } }.build()
            val json = JSONObject(get(url.toString(), via))
            val values = json.getJSONArray("files")
            for (i in 0 until values.length()) {
                val f = values.getJSONObject(i)
                f.optString("resourceKey").takeIf { it.isNotBlank() }?.let { resourceKeys[f.getString("id")] = it }
                files += DriveFile(f.getString("id"), f.getString("name"),
                    f.getString("mimeType") == "application/vnd.google-apps.folder",
                    f.optJSONObject("capabilities")?.optBoolean("canDownload", true) ?: true)
            }
            page = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (page != null)
        return files
    }
    suspend fun remember(files: List<DriveFile>, folder: String): List<Track> {
        val tracks = files.filter { !it.folder }.map { file ->
            val old = dao.track("drive:${file.id}")
            file.track(folder).copy(favorite = old?.favorite ?: false, lastPlayed = old?.lastPlayed ?: 0, offlinePath = old?.offlinePath)
        }
        dao.syncMetadata(tracks)
        return tracks
    }
    suspend fun download(track: Track) = withContext(Dispatchers.IO) {
        require(track.source == "drive")
        val id = track.id.removePrefix("drive:")
        // Fetch capability immediately before download; honor owner restrictions.
        val metadata = JSONObject(get("https://www.googleapis.com/drive/v3/files/$id?fields=capabilities(canDownload),size&supportsAllDrives=true"))
        if (!metadata.optJSONObject("capabilities")!!.optBoolean("canDownload", false)) throw IOException("Il proprietario non consente il download.")
        val directory = File(context.filesDir, "offline").apply { mkdirs() }
        val bytes = metadata.optString("size").toLongOrNull() ?: 0L
        if (bytes > directory.usableSpace - 64L * 1024 * 1024) throw IOException("Spazio insufficiente sul dispositivo.")
        val destination = File(directory, id.replace(Regex("[^A-Za-z0-9_-]"), "_"))
        val temp = File(directory, "${destination.name}.part")
        try {
            client.newCall(prepare("https://www.googleapis.com/drive/v3/files/$id?alt=media&supportsAllDrives=true")).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download interrotto (HTTP ${response.code}). Riprova.")
                val body = response.body ?: throw IOException("File vuoto")
                body.byteStream().use { input -> temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { coroutineContext.ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                } }
            }
            if (bytes > 0 && temp.length() != bytes) throw IOException("Download incompleto. Riprova.")
            if (!temp.renameTo(destination)) throw IOException("Impossibile salvare il download.")
            dao.setOffline(track.id, destination.absolutePath)
        } finally { temp.delete() }
    }
    suspend fun deleteOffline(track: Track) = withContext(Dispatchers.IO) {
        track.offlinePath?.let { File(it).delete() }; dao.setOffline(track.id, null)
    }
}
