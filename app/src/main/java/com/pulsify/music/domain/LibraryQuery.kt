package com.pulsify.music.domain

import androidx.sqlite.db.SimpleSQLiteQuery

enum class Sort(val label: String, val sql: String) {
    TITLE("Title", "songs.title COLLATE NOCASE"), ARTIST("Artist", "songs.artist COLLATE NOCASE"),
    ALBUM("Album", "songs.album COLLATE NOCASE"), ADDED("Date added", "songs.added"),
    DURATION("Duration", "songs.duration"), TRACK("Track number", "songs.track"), YEAR("Year", "songs.year"), PLAYS("Play count", "playCount")
}
data class LibraryFilter(val query: String = "", val section: String = "Songs", val group: String? = null,
    val groupKind: String = "", val playlist: Long? = null, val sort: Sort = Sort.TITLE, val descending: Boolean = false)
object LibraryQuery {
    fun build(filter: LibraryFilter, limit: Int? = null): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()
        var join = ""
        filter.playlist?.let { join = " JOIN playlist_songs ps ON ps.songId=songs.id AND ps.playlistId=?"; args.add(it) }
        if (filter.query.isNotBlank()) {
            val term = "%" + filter.query.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
            where += "(songs.title LIKE ? ESCAPE '!' OR songs.artist LIKE ? ESCAPE '!' OR songs.album LIKE ? ESCAPE '!' OR songs.genre LIKE ? ESCAPE '!' OR songs.folder LIKE ? ESCAPE '!')"
            repeat(5) { args.add(term) }
        }
        when (filter.section) {
            "Favorites" -> where += "stats.favorite=1"
            "Recently played", "Most played" -> where += "stats.playCount>0"
        }
        filter.group?.let {
            val col = when(filter.groupKind) { "Albums" -> "album"; "Artists" -> "artist"; "Genres" -> "genre"; else -> "folder" }
            where += "songs.$col=?"; args.add(it)
        }
        val sort = when {
            filter.playlist != null -> "ps.position ASC"
            filter.section == "Recently added" -> "songs.added DESC"
            filter.section == "Recently played" -> "lastPlayed DESC"
            filter.section == "Most played" -> "playCount DESC"
            else -> filter.sort.sql + if (filter.descending) " DESC" else " ASC"
        }
        val sql = "SELECT songs.*, COALESCE(stats.favorite,0) AS favorite, COALESCE(stats.playCount,0) AS playCount, COALESCE(stats.lastPlayed,0) AS lastPlayed FROM songs LEFT JOIN stats ON songs.id=stats.songId" + join +
            (if(where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) + " ORDER BY $sort, songs.id" + (limit?.let { " LIMIT ${it.coerceIn(1,10000)}" } ?: "")
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
/** Deterministic queue operations used by both UI and tests. */
fun <T> moved(items: List<T>, from: Int, to: Int): List<T> = items.toMutableList().apply {
    if (from in indices && to in indices) add(to, removeAt(from))
}
fun qualifiesForPlay(listenedMs: Long, duration: Long): Boolean = listenedMs >= if(duration <= 0) 30_000L else minOf(30_000L, (duration / 2).coerceAtLeast(1_000L))
