package it.jarvis.ipod.drive

import android.content.Context
import it.jarvis.ipod.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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

class DriveRepository(private val context: Context, private val auth: DriveAuth, private val dao: LibraryDao) {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            val token = auth.accessToken()
            client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use {
                if (it.code == 401 && attempt == 0) { auth.invalidate(); return@use }
                if (!it.isSuccessful) throw IOException(when(it.code) {
                    401 -> "Sessione Google scaduta. Ricollega Drive."
                    403 -> "Accesso negato da Drive. Verifica autorizzazioni, quota e possibilità di scaricare il file."
                    404 -> "File non più disponibile su Drive."
                    else -> "Drive non disponibile (HTTP ${it.code}). Riprova."
                })
                return@withContext it.body?.string() ?: throw IOException("Risposta Drive vuota")
            }
        }
        throw IOException("Ricollega Google Drive.")
    }
    suspend fun accountEmail(): String {
        val url = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)"
        return JSONObject(get(url)).getJSONObject("user").getString("emailAddress")
    }
    suspend fun list(folderId: String, search: String = ""): List<DriveFile> {
        fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
        val query = if (search.isBlank()) "'${escape(folderId)}' in parents" else "name contains '${escape(search)}'"
        val files = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "trashed = false and ($query) and (mimeType = 'application/vnd.google-apps.folder' or mimeType contains 'audio/' or mimeType = 'application/ogg')")
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
        if (!metadata.optJSONObject("capabilities")!!.optBoolean("canDownload", false)) throw IOException("Il proprietario non consente il download.")
        val directory = File(context.filesDir, "offline").apply { mkdirs() }
        val bytes = metadata.optString("size").toLongOrNull() ?: 0L
        if (bytes > directory.usableSpace - 64L * 1024 * 1024) throw IOException("Spazio insufficiente sul dispositivo.")
        val destination = File(directory, id.replace(Regex("[^A-Za-z0-9_-]"), "_"))
        val temp = File(directory, "${destination.name}.part")
        try {
            client.newCall(Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?alt=media&supportsAllDrives=true")
                .header("Authorization", "Bearer ${auth.accessToken()}").build()).execute().use { response ->
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
