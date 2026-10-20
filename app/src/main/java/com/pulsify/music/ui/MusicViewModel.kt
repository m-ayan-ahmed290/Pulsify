@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
package com.pulsify.music.ui

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.paging.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.pulsify.music.PulsifyApplication
import com.pulsify.music.data.*
import com.pulsify.music.domain.*
import com.pulsify.music.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

 data class Playback(val item: MediaItem? = null, val playing: Boolean = false, val position: Long = 0,
    val duration: Long = 0, val shuffle: Boolean = false, val repeat: Int = 0, val queue: List<MediaItem> = emptyList(),
    val index: Int = -1, val speed: Float = 1f, val sleep: String = "Off")
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulsifyApplication
    val dao = app.database.music()
    val settings = app.preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val count = dao.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val filter = MutableStateFlow(LibraryFilter())
    val tracks = filter.debounce(180).flatMapLatest { f -> Pager(PagingConfig(pageSize = 50, initialLoadSize = 100)) { dao.paged(LibraryQuery.build(f)) }.flow }.cachedIn(viewModelScope)
    val home = MutableStateFlow<List<Track>>(emptyList())
    val scanning = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val playback = MutableStateFlow(Playback())
    val current = MutableStateFlow<Track?>(null)
    val groups = filter.map { it.section }.distinctUntilChanged().flatMapLatest { section ->
        val column = when(section) { "Albums" -> "album"; "Artists" -> "artist"; "Genres" -> "genre"; else -> "folder" }
        dao.groups(SimpleSQLiteQuery("SELECT $column AS name, COUNT(*) AS count, MIN(artwork) AS artwork FROM songs GROUP BY $column ORDER BY $column COLLATE NOCASE"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var controller: MediaController? = null
    private var scanJob: Job? = null
    private var scanPending = false
    private val controllerFuture = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scanJob?.cancel(); scanJob = viewModelScope.launch { delay(1500); scan() }
        }
    }
    init {
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                controller?.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { updatePlayer(true) }
                    override fun onPlayerError(error: PlaybackException) { message.value = "Unable to play this file. It may be missing, inaccessible, or unsupported." }
                })
                updatePlayer(true)
            } catch (_: Exception) { message.value = "Playback service could not connect. Please reopen Pulsify." }
        }, ContextCompat.getMainExecutor(app))
        try { app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) } catch (_: SecurityException) { /* Folder mode does not require MediaStore access. */ }
        viewModelScope.launch {
            while(isActive) {
                updatePlayer(false)
                if(controller != null) {
                    val future = controller!!.sendCustomCommand(SessionCommand(MusicService.SLEEP_STATUS, Bundle.EMPTY), Bundle.EMPTY)
                    future.addListener({
                        runCatching { future.get() }.getOrNull()?.extras?.let { extras ->
                            val remaining = extras.getLong("remaining")
                            val sleep = when { remaining > 0 -> "${(remaining / 60000) + 1} min left"; extras.getInt("tracks") > 0 -> "${extras.getInt("tracks")} song(s) left"; extras.getBoolean("end") -> "End of queue"; else -> "Off" }
                            playback.value = playback.value.copy(sleep = sleep)
                        }
                    }, ContextCompat.getMainExecutor(app))
                }
                delay(1000)
            }
        }
        viewModelScope.launch { dao.count().collect { refreshHome() } }
    }
    private fun updatePlayer(queue: Boolean) {
        val c = controller ?: return
        val old = playback.value
        playback.value = Playback(c.currentMediaItem, c.isPlaying, c.currentPosition.coerceAtLeast(0),
            c.duration.coerceAtLeast(0), c.shuffleModeEnabled, c.repeatMode,
            if(queue) (0 until c.mediaItemCount).map { c.getMediaItemAt(it) } else old.queue,
            c.currentMediaItemIndex, c.playbackParameters.speed, old.sleep)
        if(current.value?.song?.id != c.currentMediaItem?.mediaId) viewModelScope.launch { current.value = c.currentMediaItem?.mediaId?.let { dao.track(it) } }
    }
    fun scan(): Job = viewModelScope.launch {
        if(scanning.value) { scanPending = true; return@launch }
        scanning.value = true
        try { val found = app.scanner.scan(); message.value = "$found songs in your library"; refreshHome() }
        catch(e: Exception) { message.value = e.message ?: "Unable to scan this library. Check folder access in Settings." }
        finally { scanning.value = false; if(scanPending) { scanPending = false; scan() } }
    }
    suspend fun refreshHome() { home.value = withContext(Dispatchers.IO) { dao.tracks(LibraryQuery.build(LibraryFilter(section = "Recently added"), 12)) } }
    fun play(song: Song, list: List<Song>? = null, shuffle: Boolean = false) = action {
        val songs = list ?: withContext(Dispatchers.IO) { dao.tracks(LibraryQuery.build(filter.value, 2000)).map { it.song } }
        val queue = if(songs.any { it.id == song.id }) songs else listOf(song)
        val c = controller ?: error("Playback is still connecting. Try again shortly.")
        c.setMediaItems(queue.map { it.mediaItem() }, queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0), 0)
        c.shuffleModeEnabled = shuffle; c.prepare(); c.play()
    }
    fun playAll(shuffle: Boolean = false) = action {
        val songs = withContext(Dispatchers.IO) { dao.tracks(LibraryQuery.build(filter.value, 2000)).map { it.song } }
        if(songs.isEmpty()) { message.value = "Add some music to start listening"; return@action }
        play(if(shuffle) songs.random() else songs.first(), songs, shuffle)
    }
    fun toggle() { controller?.let { if(it.isPlaying) it.pause() else { if(it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seek(position: Long) { controller?.seekTo(position) }
    fun skip(direction: Int) { controller?.let { it.seekTo((it.currentPosition + direction * settings.value.skip * 1000).coerceIn(0, it.duration.coerceAtLeast(0))) } }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun speed(value: Float) { controller?.setPlaybackSpeed(value) }
    fun addQueue(song: Song, next: Boolean) {
        val c = controller ?: run { message.value = "Playback is still connecting. Try again shortly."; return }
        if(next) c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtLeast(0), song.mediaItem()) else c.addMediaItem(song.mediaItem())
        if(c.playbackState == Player.STATE_IDLE) c.prepare()
        message.value = if(next) "Playing next" else "Added to queue"
    }
    fun removeQueue(index: Int) { controller?.removeMediaItem(index) }
    fun moveQueue(from: Int, to: Int) { controller?.let { if(from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun jumpQueue(index: Int) { controller?.let { it.seekTo(index, 0); it.play() } }
    fun clearQueue() { controller?.clearMediaItems() }
    fun favorite(song: Song) = action {
        dao.favorite(song.id)
        if(current.value?.song?.id == song.id) current.value = dao.track(song.id)
        refreshHome()
    }
    fun createPlaylist(name: String) = action { dao.create(Playlist(name = validName(name))); message.value = "Playlist created" }
    fun renamePlaylist(id: Long, name: String) = action { dao.rename(id, validName(name)) }
    fun deletePlaylist(id: Long) = action { dao.deletePlaylist(id) }
    fun addPlaylist(id: Long, songs: List<String>) = action { dao.addToPlaylist(id, songs); message.value = "Added to playlist" }
    fun addSelection(id: Long) = action { val songs = dao.tracks(LibraryQuery.build(filter.value)); dao.addToPlaylist(id, songs.map { it.song.id }); message.value = "Added ${songs.size} songs" }
    fun removePlaylistSong(id: Long, song: String) = action { dao.removeEntry(id, song) }
    fun movePlaylistSong(id: Long, song: String, direction: Int) = action {
        val ids = dao.entries(id).map { it.songId }; val index = ids.indexOf(song)
        dao.reorderPlaylist(id, moved(ids, index, index + direction))
    }
    fun folder(uri: Uri) = action { app.preferences.text("folder", uri.toString()); scan() }
    fun useDevice() = action { app.preferences.text("folder", ""); scan() }
    fun textSetting(key: String, value: String) = action { app.preferences.text(key, value) }
    fun flagSetting(key: String, value: Boolean) = action { app.preferences.flag(key, value); if(key in listOf("hideWhatsApp", "showHidden")) scan() }
    fun sleep(minutes: Int = 0, tracks: Int = 0, end: Boolean = false) {
        controller?.sendCustomCommand(SessionCommand(MusicService.SLEEP, Bundle.EMPTY), Bundle().apply { putInt("minutes", minutes); putInt("tracks", tracks); putBoolean("end", end) })
    }
    fun exportPlaylist(id: Long, name: String, uri: Uri) = action {
        withContext(Dispatchers.IO) {
            val songs = dao.tracks(LibraryQuery.build(LibraryFilter(playlist = id)))
            val data = JSONObject().put("format", "pulsify-playlist-v1").put("name", name).put("songs", JSONArray().apply {
                songs.forEach { put(JSONObject().put("uri", it.song.id).put("title", it.song.title).put("artist", it.song.artist)) }
            }).toString(2)
            app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(data) } ?: error("Cannot write to this document")
        }; message.value = "Playlist exported"
    }
    fun importPlaylist(uri: Uri) = action {
        val result = withContext(Dispatchers.IO) {
            val data = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val text = StringBuilder(); val buffer = CharArray(4096)
                while(true) { val n = reader.read(buffer); if(n < 0) break; text.append(buffer,0,n); check(text.length <= 2_000_000) { "Playlist file is too large" } }; text.toString()
            } ?: error("Cannot read this document")
            val json = JSONObject(data); require(json.optString("format") == "pulsify-playlist-v1") { "Select a Pulsify JSON playlist" }
            val songs = json.getJSONArray("songs"); val ids = ArrayList<String>()
            for(i in 0 until songs.length()) {
                val item = songs.getJSONObject(i)
                val direct = dao.song(item.optString("uri"))
                val matched = direct ?: dao.tracks(LibraryQuery.build(LibraryFilter(query = item.optString("title")), 100))
                    .firstOrNull { it.song.title.equals(item.optString("title"), true) && it.song.artist.equals(item.optString("artist"), true) }?.song
                matched?.let { ids.add(it.id) }
            }
            val id = dao.create(Playlist(name = validName(json.optString("name", "Imported")) + " · " + System.currentTimeMillis().toString().takeLast(6)))
            dao.addToPlaylist(id, ids)
            "Imported ${ids.size} of ${songs.length()} tracks. Missing files must be added to your library first."
        }; message.value = result
    }
    fun clearArtworkCache() = action {
        withContext(Dispatchers.IO) { app.cacheDir.deleteRecursively() }; message.value = "Temporary image cache cleared"
    }
    fun audioSession(onReady: (Int) -> Unit) {
        val future = controller?.sendCustomCommand(SessionCommand(MusicService.SLEEP_STATUS, Bundle.EMPTY), Bundle.EMPTY) ?: return
        future.addListener({ runCatching { future.get() }.getOrNull()?.let { onReady(it.extras.getInt("audioSessionId")) } }, ContextCompat.getMainExecutor(app))
    }
    val lyrics = MutableStateFlow<String?>(null)
    private fun lyricFile(id: String) = java.io.File(app.filesDir, "lyrics/" + java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) } + ".txt")
    fun loadLyrics(id: String) = action { lyrics.value = withContext(Dispatchers.IO) { lyricFile(id).takeIf { it.exists() }?.readText() } }
    fun attachLyrics(id: String, uri: Uri) = action {
        withContext(Dispatchers.IO) {
            val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val result = StringBuilder(); val buffer = CharArray(4096)
                while(true) { val n = reader.read(buffer); if(n < 0) break; result.append(buffer,0,n); require(result.length < 500_000) { "Lyrics file is too large" } }
                result.toString()
            } ?: error("Cannot read lyrics")
            lyricFile(id).apply { parentFile?.mkdirs(); writeText(text) }; lyrics.value = text
        }
    }
    private fun validName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= 80) { "Use a playlist name between 1 and 80 characters" } }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch(e: Exception) { message.value = if(e is android.database.sqlite.SQLiteConstraintException) "That playlist name already exists" else e.message ?: "Something went wrong. Please try again." }
    }
    override fun onCleared() { app.contentResolver.unregisterContentObserver(observer); MediaController.releaseFuture(controllerFuture); super.onCleared() }
}
