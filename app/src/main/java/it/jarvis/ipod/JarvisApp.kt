package it.jarvis.ipod

import android.app.Application
import androidx.room.Room
import it.jarvis.ipod.data.*
import it.jarvis.ipod.drive.*
import it.jarvis.ipod.spotify.SpotifyBridge
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class JarvisApp : Application() {
    val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, LibraryDatabase::class.java, "library.db").build() }
    val dao get() = database.dao()
    val preferences by lazy { Preferences(this) }
    val auth by lazy { DriveAuth(this, preferences) }
    val drive by lazy { DriveRepository(this, auth, dao, preferences) }
    val local by lazy { LocalLibrary(this, dao) }
    val spotify by lazy { SpotifyBridge(this) }
    val cacheEvictor by lazy { LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024) }
    val audioCache by lazy { SimpleCache(File(cacheDir, "stream"), cacheEvictor, StandaloneDatabaseProvider(this)) }
}
