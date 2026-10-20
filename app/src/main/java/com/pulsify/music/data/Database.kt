package com.pulsify.music.data

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "songs", indices = [Index("title"), Index("artist"), Index("album"), Index("folder"), Index("scanToken")])
data class Song(
    @PrimaryKey val id: String,
    val title: String, val artist: String = "Unknown artist", val album: String = "Unknown album",
    val albumArtist: String = "", val genre: String = "Unknown genre", val year: Int = 0,
    val track: Int = 0, val duration: Long = 0, val added: Long = 0, val modified: Long = 0,
    val folder: String = "Music", val artwork: String? = null, val source: String = "device",
    val scanToken: Long = 0, val size: Long = 0
)
@Entity(tableName = "stats")
data class SongStats(@PrimaryKey val songId: String, val favorite: Boolean = false, val playCount: Int = 0, val lastPlayed: Long = 0)
data class Track(@Embedded val song: Song, val favorite: Boolean = false, val playCount: Int = 0, val lastPlayed: Long = 0)
@Entity(tableName = "playlists", indices = [Index(value = ["name"], unique = true)])
data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val created: Long = System.currentTimeMillis())
@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"],
    foreignKeys = [ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("songId")])
data class PlaylistEntry(val playlistId: Long, val songId: String, val position: Int)
data class PlaylistSummary(@Embedded val playlist: Playlist, val count: Int, val artwork: String? = null)
data class LibraryGroup(val name: String, val count: Int, val artwork: String?)

@Dao
abstract class MusicDao {
    @RawQuery(observedEntities = [Song::class, SongStats::class, PlaylistEntry::class])
    abstract fun paged(query: SupportSQLiteQuery): PagingSource<Int, Track>
    @RawQuery(observedEntities = [Song::class, SongStats::class]) abstract fun observeTracks(query: SupportSQLiteQuery): Flow<List<Track>>
    @RawQuery abstract suspend fun tracks(query: SupportSQLiteQuery): List<Track>
    @Query("SELECT songs.*, COALESCE(stats.favorite,0) AS favorite, COALESCE(stats.playCount,0) AS playCount, COALESCE(stats.lastPlayed,0) AS lastPlayed FROM songs LEFT JOIN stats ON songs.id=stats.songId WHERE songs.id=:id")
    abstract suspend fun track(id: String): Track?
    @Query("SELECT * FROM songs WHERE id=:id") abstract suspend fun song(id: String): Song?
    @Upsert abstract suspend fun upsert(songs: List<Song>)
    @Query("DELETE FROM songs WHERE source=:source AND scanToken!=:token") abstract suspend fun removeMissing(source: String, token: Long)
    @Query("DELETE FROM songs WHERE source!=:source") abstract suspend fun removeOtherSources(source: String)
    @Query("SELECT COUNT(*) FROM songs") abstract fun count(): Flow<Int>
    @Query("SELECT * FROM stats WHERE songId=:id") abstract suspend fun stats(id: String): SongStats?
    @Upsert abstract suspend fun saveStats(stats: SongStats)
    @Transaction open suspend fun favorite(id: String) {
        val previous = stats(id) ?: SongStats(id)
        saveStats(previous.copy(favorite = !previous.favorite))
    }
    @Transaction open suspend fun recordPlay(id: String) {
        val previous = stats(id) ?: SongStats(id)
        saveStats(previous.copy(playCount = previous.playCount + 1, lastPlayed = System.currentTimeMillis()))
    }
    @Query("SELECT playlists.*, COUNT(playlist_songs.songId) AS count, MIN(songs.artwork) AS artwork FROM playlists LEFT JOIN playlist_songs ON playlists.id=playlist_songs.playlistId LEFT JOIN songs ON songs.id=playlist_songs.songId GROUP BY playlists.id ORDER BY name COLLATE NOCASE")
    abstract fun playlists(): Flow<List<PlaylistSummary>>
    @Insert abstract suspend fun create(playlist: Playlist): Long
    @Query("UPDATE playlists SET name=:name WHERE id=:id") abstract suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id=:id") abstract suspend fun deletePlaylist(id: Long)
    @Query("SELECT * FROM playlist_songs WHERE playlistId=:id ORDER BY position") abstract suspend fun entries(id: Long): List<PlaylistEntry>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertEntries(entries: List<PlaylistEntry>)
    @Query("DELETE FROM playlist_songs WHERE playlistId=:id AND songId=:song") abstract suspend fun removeEntry(id: Long, song: String)
    @Query("DELETE FROM playlist_songs WHERE playlistId=:id") abstract suspend fun clearEntries(id: Long)
    @Transaction open suspend fun addToPlaylist(id: Long, songs: List<String>) {
        val start = (entries(id).maxOfOrNull { it.position } ?: -1) + 1
        insertEntries(songs.distinct().mapIndexed { i, song -> PlaylistEntry(id, song, start + i) })
    }
    @Transaction open suspend fun reorderPlaylist(id: Long, ids: List<String>) {
        clearEntries(id)
        insertEntries(ids.distinct().mapIndexed { i, song -> PlaylistEntry(id, song, i) })
    }
    @RawQuery(observedEntities = [Song::class]) abstract fun groups(query: SupportSQLiteQuery): Flow<List<LibraryGroup>>
}
@Database(entities = [Song::class, SongStats::class, Playlist::class, PlaylistEntry::class], version = 1, exportSchema = true)
abstract class MusicDatabase : RoomDatabase() { abstract fun music(): MusicDao }
