package it.jarvis.ipod.drive

import android.content.Context
import it.jarvis.ipod.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import it.jarvis.ipod.spotify.SpotifyBridge
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class DriveFile(val id: String, val name: String, val folder: Boolean, val downloadable: Boolean = true) {
    fun track(folderName: String) = Track("drive:$id", "drive://$id", name.substringBeforeLast('.', name),
        "Google Drive", folderName, source = "drive", added = System.currentTimeMillis())
}

/** Come autenticare una richiesta Drive: token dell'account collegato oppure chiave API (solo cartelle condivise con link). */
data class DriveAccess(val url: String, val headers: Map<String, String>, val bearer: String?)

class DriveRepository(private val context: Context, private val auth: DriveAuth, private val dao: LibraryDao, private val preferences: Preferences) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    /**
     * Credenziali per [url]: se un account Google è collegato si usa il suo token; altrimenti la chiave API, con cui Drive
     * restituisce solo ciò che è condiviso con «Chiunque abbia il link». Con la chiave si inviano anche pacchetto e firma,
     * necessari se la chiave è limitata all'app Android.
     */
    suspend fun access(url: String): DriveAccess {
        val s = preferences.settings.first()
        if (s.driveEmail.isNotBlank()) return DriveAccess(url, emptyMap(), auth.accessToken())
        if (s.driveApiKey.isBlank()) throw IOException("Prima inserisci l’API di Google Drive (pulsante «API di Google Drive») oppure collega l’account.")
        val withKey = url.toHttpUrl().newBuilder().addQueryParameter("key", s.driveApiKey).build().toString()
        val headers = buildMap {
            put("X-Android-Package", context.packageName)
            SpotifyBridge.signingSha1(context)?.let { put("X-Android-Cert", it.replace(":", "")) }
        }
        return DriveAccess(withKey, headers, null)
    }
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            val access = access(url)
            val request = Request.Builder().url(access.url).apply {
                access.headers.forEach { (k, v) -> header(k, v) }
                access.bearer?.let { header("Authorization", "Bearer $it") }
            }.build()
            client.newCall(request).execute().use {
                if (it.code == 401 && attempt == 0 && access.bearer != null) { auth.invalidate(); return@use }
                if (!it.isSuccessful) throw IOException(explain(it.code, access.bearer == null))
                return@withContext it.body?.string() ?: throw IOException("Risposta Drive vuota")
            }
        }
        throw IOException("Ricollega Google Drive.")
    }
    private fun explain(code: Int, withKey: Boolean): String = if (withKey) when (code) {
        400 -> "Chiave API non valida. Controllala in Google Cloud e reinseriscila."
        401, 403 -> "Drive ha rifiutato la richiesta. Controlla che la chiave sia valida, che l'API Google Drive sia abilitata per il suo progetto e che la cartella sia condivisa con «Chiunque abbia il link»."
        404 -> "Cartella non trovata: deve essere condivisa con «Chiunque abbia il link»."
        else -> "Drive non disponibile (HTTP $code). Riprova."
    } else when (code) {
        401 -> "Sessione Google scaduta. Ricollega Drive."
        403 -> "Accesso negato da Drive. Verifica autorizzazioni, quota e possibilità di scaricare il file."
        404 -> "File non più disponibile su Drive."
        else -> "Drive non disponibile (HTTP $code). Riprova."
    }
    /** Nome di una cartella (anche condivisa con link). Fallisce se l'ID non è una cartella o non è raggiungibile. */
    suspend fun folderName(id: String): String {
        val json = JSONObject(get("https://www.googleapis.com/drive/v3/files/$id?fields=name,mimeType&supportsAllDrives=true"))
        if (json.optString("mimeType") != "application/vnd.google-apps.folder") throw IOException("Il link non è una cartella di Drive.")
        return json.getString("name")
    }
    suspend fun accountEmail(): String {
        val url = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)"
        return JSONObject(get(url)).getJSONObject("user").getString("emailAddress")
    }
    /**
     * Elenca il contenuto di una sola cartella. La ricerca resta dentro la cartella aperta (nessuna ricerca
     * su tutto Drive). Con [foldersOnly] restituisce solo sottocartelle: serve alla scelta delle cartelle musicali.
     */
    suspend fun list(folderId: String, search: String = "", foldersOnly: Boolean = false): List<DriveFile> {
        fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
        val parent = "'${escape(folderId)}' in parents"
        val query = if (search.isBlank()) parent else "$parent and name contains '${escape(search)}'"
        val types = if (foldersOnly) "mimeType = 'application/vnd.google-apps.folder'"
            else "mimeType = 'application/vnd.google-apps.folder' or mimeType contains 'audio/' or mimeType = 'application/ogg'"
        val files = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "trashed = false and ($query) and ($types)")
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,capabilities(canDownload))")
                .addQueryParameter("pageSize", "1000").addQueryParameter("orderBy", "folder,name_natural")
                .addQueryParameter("supportsAllDrives", "true").addQueryParameter("includeItemsFromAllDrives", "true")
                .apply { page?.let { addQueryParameter("pageToken", it) } }.build()
            val json = JSONObject(get(url.toString()))
            val values = json.getJSONArray("files")
            for (i in 0 until values.length()) {
                val f = values.getJSONObject(i)
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
        if (metadata.optJSONObject("capabilities")?.optBoolean("canDownload", false) == false) throw IOException("Il proprietario non consente il download.")
        val directory = File(context.filesDir, "offline").apply { mkdirs() }
        val bytes = metadata.optString("size").toLongOrNull() ?: 0L
        if (bytes > directory.usableSpace - 64L * 1024 * 1024) throw IOException("Spazio insufficiente sul dispositivo.")
        val destination = File(directory, id.replace(Regex("[^A-Za-z0-9_-]"), "_"))
        val temp = File(directory, "${destination.name}.part")
        try {
            val access = access("https://www.googleapis.com/drive/v3/files/$id?alt=media&supportsAllDrives=true")
            client.newCall(Request.Builder().url(access.url).apply {
                access.headers.forEach { (k, v) -> header(k, v) }
                access.bearer?.let { header("Authorization", "Bearer $it") }
            }.build()).execute().use { response ->
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
