package it.jarvis.ipod.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val artist: String = "Artista sconosciuto",
    val album: String = "Album sconosciuto",
    val duration: Long = 0,
    val artwork: String? = null,
    val source: String = "local",
    val added: Long = 0,
    val favorite: Boolean = false,
    val lastPlayed: Long = 0,
    val offlinePath: String? = null
) {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun mediaItem() = MediaItem.Builder().setMediaId(id).setUri(uri)
        .setCustomCacheKey(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setAlbumTitle(album).setArtworkUri(artwork?.let(Uri::parse))
            .setIsBrowsable(false).setIsPlayable(true).build()).build()
}

@Entity(tableName = "playlists")
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId")])
data class PlaylistEntry(val playlistId: Long, val trackId: String, val position: Long = System.currentTimeMillis())

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE") fun observeTracks(): Flow<List<Track>>
    @Query("SELECT * FROM tracks") suspend fun allTracks(): List<Track>
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun track(id: String): Track?
    @Upsert suspend fun save(track: Track)
    @Upsert suspend fun saveAll(tracks: List<Track>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfMissing(track: Track)
    @Query("UPDATE tracks SET uri=:uri, title=:title, artist=:artist, album=:album, duration=:duration, artwork=:artwork, source=:source, added=:added WHERE id=:id")
    suspend fun updateMetadata(id: String, uri: String, title: String, artist: String, album: String,
        duration: Long, artwork: String?, source: String, added: Long)
    @Transaction suspend fun syncMetadata(tracks: List<Track>) {
        tracks.forEach { t ->
            insertIfMissing(t)
            // A scan must never overwrite a favorite, play event or offline download.
            updateMetadata(t.id, t.uri, t.title, t.artist, t.album, t.duration, t.artwork, t.source, t.added)
        }
    }
    @Query("UPDATE tracks SET favorite = NOT favorite WHERE id = :id") suspend fun toggleFavorite(id: String)
    @Query("UPDATE tracks SET lastPlayed = :time WHERE id = :id") suspend fun played(id: String, time: Long)
    @Query("UPDATE tracks SET offlinePath = :path WHERE id = :id") suspend fun setOffline(id: String, path: String?)
    @Query("DELETE FROM tracks WHERE source = 'local' AND id IN (:ids)") suspend fun deleteLocalRows(ids: List<String>)
    @Query("DELETE FROM playlist_entries WHERE trackId IN (:ids)") suspend fun deleteTrackEntries(ids: List<String>)
    @Transaction suspend fun pruneLocal(ids: List<String>) { deleteTrackEntries(ids); deleteLocalRows(ids) }
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") fun observePlaylists(): Flow<List<Playlist>>
    @Insert suspend fun createPlaylist(playlist: Playlist): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addEntry(entry: PlaylistEntry)
    @Query("SELECT * FROM playlist_entries ORDER BY position") fun observeEntries(): Flow<List<PlaylistEntry>>
    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlist AND trackId = :track") suspend fun removeEntry(playlist: Long, track: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
}

@Database(entities = [Track::class, Playlist::class, PlaylistEntry::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() { abstract fun dao(): LibraryDao }

class LocalLibrary(private val context: Context, private val dao: LibraryDao) {
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        val existing = dao.allTracks().associateBy { it.id }
        val result = mutableListOf<Track>()
        val columns = mutableListOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATE_ADDED)
        if (Build.VERSION.SDK_INT >= 29) columns += MediaStore.Audio.Media.VOLUME_NAME
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(0)
                val volume = if (Build.VERSION.SDK_INT >= 29) cursor.getString(7) ?: "external" else "external"
                val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(volume) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val id = "local:$volume:$mediaId"
                val old = existing[id]
                result += Track(id, ContentUris.withAppendedId(collection, mediaId).toString(),
                    cursor.getString(1) ?: "Senza titolo", cursor.getString(2)?.takeUnless { it == "<unknown>" } ?: "Artista sconosciuto",
                    cursor.getString(3) ?: "Album sconosciuto", cursor.getLong(4),
                    ContentUris.withAppendedId(Uri.parse("content://media/$volume/audio/albumart"), cursor.getLong(5)).toString(),
                    added = cursor.getLong(6) * 1000, favorite = old?.favorite ?: false, lastPlayed = old?.lastPlayed ?: 0)
            }
        }
        dao.syncMetadata(result)
        val liveIds = result.mapTo(HashSet()) { it.id }
        // Stay below SQLite's bind limit on older Android versions and large libraries.
        existing.values.filter { it.source == "local" && it.id !in liveIds }.map { it.id }
            .chunked(400).forEach { dao.pruneLocal(it) }
        result.size
    }
}
