package com.pulsify.music

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pulsify.music.data.*
import com.pulsify.music.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class LibraryDatabaseTest {
    private lateinit var database: MusicDatabase
    private lateinit var dao: MusicDao
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MusicDatabase::class.java).build()
        dao = database.music()
        runBlocking {
            dao.upsert(listOf(Song("content://1", "100% Real", "Aria", "Sunrise", genre = "Jazz", folder = "Music/Jazz", duration = 100000, source = "device", scanToken = 1),
                Song("content://2", "Night", "Nova", "Moon", duration = 200000, source = "device", scanToken = 1),
                Song("content://3", "Dawn", "Aria", "Sunrise", duration = 300000, source = "device", scanToken = 2)))
        }
    }
    @After fun tearDown() { database.close() }
    @Test fun createRenameAndDeletePlaylist() = runBlocking {
        val id = dao.create(Playlist(name = "Morning"))
        dao.rename(id, "Evening")
        assertEquals("Evening", dao.playlists().first().single().playlist.name)
        dao.addToPlaylist(id, listOf("content://1"))
        dao.deletePlaylist(id)
        assertTrue(dao.playlists().first().isEmpty())
        assertTrue(dao.entries(id).isEmpty())
        assertNotNull(dao.song("content://1"))
    }
    @Test fun addDeduplicateReorderAndRemoveSongs() = runBlocking {
        val id = dao.create(Playlist(name = "Mix"))
        dao.addToPlaylist(id, listOf("content://1", "content://2", "content://1"))
        dao.addToPlaylist(id, listOf("content://1", "content://3"))
        assertEquals(3, dao.entries(id).size)
        dao.reorderPlaylist(id, listOf("content://3", "content://2", "content://1"))
        assertEquals(listOf("content://3", "content://2", "content://1"), dao.tracks(LibraryQuery.build(LibraryFilter(playlist = id))).map { it.song.id })
        dao.removeEntry(id, "content://2")
        assertEquals(2, dao.entries(id).size)
    }
    @Test fun searchEscapesWildcardsAndMatchesMetadata() = runBlocking {
        assertEquals(listOf("100% Real"), dao.tracks(LibraryQuery.build(LibraryFilter(query = "%"))).map { it.song.title })
        assertEquals(2, dao.tracks(LibraryQuery.build(LibraryFilter(query = "aria"))).size)
        assertEquals(1, dao.tracks(LibraryQuery.build(LibraryFilter(query = "jazz"))).size)
        assertTrue(dao.tracks(LibraryQuery.build(LibraryFilter(query = "' OR 1=1 --"))).isEmpty())
    }
    @Test fun favoritesHistoryAndFilteringPersistIndependentlyOfScan() = runBlocking {
        dao.favorite("content://1"); dao.recordPlay("content://1"); dao.recordPlay("content://1")
        val song = dao.song("content://1")!!
        dao.upsert(listOf(song.copy(title = "Updated title", scanToken = 9)))
        assertEquals(2, dao.tracks(LibraryQuery.build(LibraryFilter(section = "Favorites"))).single().playCount)
        assertEquals("Updated title", dao.tracks(LibraryQuery.build(LibraryFilter(section = "Most played"))).single().song.title)
        assertEquals(2, dao.tracks(LibraryQuery.build(LibraryFilter(group = "Sunrise", groupKind = "Albums"))).size)
        dao.favorite("content://1")
        assertTrue(dao.tracks(LibraryQuery.build(LibraryFilter(section = "Favorites"))).isEmpty())
    }
    @Test fun deletionCascadesPlaylistMembershipButRetainsOtherSongs() = runBlocking {
        val id = dao.create(Playlist(name = "Keep")); dao.addToPlaylist(id, listOf("content://1", "content://3"))
        dao.removeMissing("device", 2)
        assertEquals(listOf("content://3"), dao.entries(id).map { it.songId })
    }
    @Test fun sortingUsesNumericDurationAndDescendingOrder() = runBlocking {
        val songs = dao.tracks(LibraryQuery.build(LibraryFilter(sort = Sort.DURATION, descending = true)))
        assertEquals(listOf(300000L,200000L,100000L), songs.map { it.song.duration })
    }
}
