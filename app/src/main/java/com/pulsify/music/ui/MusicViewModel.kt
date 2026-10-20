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
    val index: Int = -1, val speed: Float = 1f, val sleep: String = "Off", val loopA: Long = -1, val loopB: Long = -1, val volume: Float = 1f)
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulsifyApplication
    val dao = app.database.music()
    val settings = app.preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val count = dao.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val filter = MutableStateFlow(LibraryFilter())
    val tracks = filter.debounce(180).flatMapLatest { f -> Pager(PagingConfig(pageSize = 50, initialLoadSize = 100)) { dao.paged(LibraryQuery.build(f)) }.flow }.cachedIn(viewModelScope)
    val home = MutableStateFlow<List<Track>>(emptyList())
    val history = dao.observeTracks(LibraryQuery.build(LibraryFilter(section = "Recently played"), 12)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val liked = dao.observeTracks(LibraryQuery.build(LibraryFilter(section = "Favorites"), 12)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchHistory = app.preferences.searchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val effects = MutableStateFlow(EffectsState())
    private var undoItem: Pair<Int, MediaItem>? = null
    val supportPrompt = MutableStateFlow(false)
    fun checkSupportPrompt() = action {
        if(!supportPrompt.value && app.preferences.claimSupportPrompt(System.currentTimeMillis())) supportPrompt.value = true
    }
    fun dismissSupportPrompt() { supportPrompt.value = false }
    val scanning = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val playback = MutableStateFlow(Playback())
    val current = MutableStateFlow<Track?>(null)
    val groups = filter.map { it.section }.distinctUntilChanged().flatMapLatest { section ->
        val column = when(section) { "Albums" -> "album"; "Artists" -> "artist"; "Genres" -> "genre"; else -> "folder" }
        dao.groups(SimpleSQLiteQuery("SELECT $column AS name, COUNT(*) AS count, MIN(artwork) AS artwork FROM songs GROUP BY $column ORDER BY $column COLLATE NOCASE"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var lyricsFollowing = false
    fun followLyrics(visible: Boolean) { lyricsFollowing = visible }
    private var controller: MediaController? = null
    private var scanJob: Job? = null
    private var scanPending = false
    private var scanPendingManual = false
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
                updatePlayer(true); refreshEffects()
            } catch (_: Exception) { message.value = "Playback service could not connect. Please reopen Pulsify." }
        }, ContextCompat.getMainExecutor(app))
        try { app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) } catch (_: SecurityException) { /* Folder mode does not require MediaStore access. */ }
        viewModelScope.launch {
            var nextStatus = 0L
            while(isActive) {
                updatePlayer(false)
                val now = android.os.SystemClock.elapsedRealtime()
                if(controller != null && now >= nextStatus) {
                    nextStatus = now + 1000
                    val future = controller!!.sendCustomCommand(SessionCommand(MusicService.SLEEP_STATUS, Bundle.EMPTY), Bundle.EMPTY)
                    future.addListener({
                        runCatching { future.get() }.getOrNull()?.extras?.let { extras ->
                            val remaining = extras.getLong("remaining")
                            val sleep = when { remaining > 0 -> "${(remaining / 60000) + 1} min left"; extras.getInt("tracks") > 0 -> "${extras.getInt("tracks")} song(s) left"; extras.getBoolean("end") -> "End of queue"; else -> "Off" }
                            playback.value = playback.value.copy(sleep = sleep, loopA = extras.getLong("loopA", -1), loopB = extras.getLong("loopB", -1), volume = extras.getFloat("volume", 1f))
                        }
                    }, ContextCompat.getMainExecutor(app))
                }
                delay(if(lyricsFollowing) 100 else 1000)
            }
        }
        viewModelScope.launch { dao.observeTracks(LibraryQuery.build(LibraryFilter(section = "Recently added"), 12)).collect { home.value = it } }
    }
    private fun updatePlayer(queue: Boolean) {
        val c = controller ?: return
        val old = playback.value
        playback.value = Playback(c.currentMediaItem, c.isPlaying, c.currentPosition.coerceAtLeast(0),
            c.duration.coerceAtLeast(0), c.shuffleModeEnabled, c.repeatMode,
            if(queue) (0 until c.mediaItemCount).map { c.getMediaItemAt(it) } else old.queue,
            c.currentMediaItemIndex, c.playbackParameters.speed, old.sleep, old.loopA, old.loopB, old.volume)
        if(current.value?.song?.id != c.currentMediaItem?.mediaId) viewModelScope.launch { current.value = c.currentMediaItem?.mediaId?.let { dao.track(it) } }
    }
    fun scan(manual: Boolean = false): Job = viewModelScope.launch {
        if(scanning.value) { scanPending = true; scanPendingManual = scanPendingManual || manual; return@launch }
        scanning.value = true
        try { val found = app.scanner.scan(); if(manual) message.value = "$found songs in your library"; refreshHome() }
        catch(e: Exception) { message.value = e.message ?: "Unable to scan this library. Check folder access in Settings." }
        finally { scanning.value = false; if(scanPending) { val report = scanPendingManual; scanPending = false; scanPendingManual = false; scan(report) } }
    }
    suspend fun refreshHome() { home.value = withContext(Dispatchers.IO) { dao.tracks(LibraryQuery.build(LibraryFilter(section = "Recently added"), 12)) } }
    fun playPlaylist(id: Long, shuffle: Boolean = false) = action {
        val songs = withContext(Dispatchers.IO) { dao.tracks(LibraryQuery.build(LibraryFilter(playlist = id), 2000)).map { it.song } }
        if(songs.isEmpty()) { message.value = "Add a few songs to this playlist first"; return@action }
        play(if(shuffle) songs.random() else songs.first(), songs, shuffle)
    }
    fun playMix() = action {
        val songs = withContext(Dispatchers.IO) { dao.tracks(SimpleSQLiteQuery("SELECT songs.*, COALESCE(stats.favorite,0) AS favorite, COALESCE(stats.playCount,0) AS playCount, COALESCE(stats.lastPlayed,0) AS lastPlayed FROM songs LEFT JOIN stats ON songs.id=stats.songId ORDER BY COALESCE(stats.favorite,0) DESC, RANDOM() LIMIT 50")).map { it.song } }
        if(songs.isEmpty()) { message.value = "Choose a music folder to create your mix"; return@action }
        play(songs.first(), songs, true)
    }
    fun enqueueSelection(ids: List<String>) = action {
        val items = withContext(Dispatchers.IO) { ids.mapNotNull { dao.song(it)?.mediaItem() } }
        val c = controller ?: error("Playback is still connecting")
        c.addMediaItems(items); if(c.playbackState == Player.STATE_IDLE) c.prepare(); message.value = "Added ${items.size} songs to queue"
    }
    fun playSelection(ids: List<String>) = action { val songs = withContext(Dispatchers.IO) { ids.mapNotNull { dao.song(it) } }; songs.firstOrNull()?.let { play(it, songs) } }
    fun rememberSearch(query: String) = action { app.preferences.rememberSearch(query) }
    fun clearSearchHistory() = action { app.preferences.text("searchHistory", "[]") }
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
    fun repeat() { controller?.let { it.repeatMode = when(it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun speed(value: Float) { controller?.setPlaybackSpeed(value) }
    fun addQueue(song: Song, next: Boolean) {
        val c = controller ?: run { message.value = "Playback is still connecting. Try again shortly."; return }
        if(next) c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtLeast(0), song.mediaItem()) else c.addMediaItem(song.mediaItem())
        if(c.playbackState == Player.STATE_IDLE) c.prepare()
        message.value = if(next) "Playing next" else "Added to queue"
    }
    fun removeQueue(index: Int) { controller?.let { c -> if(index in 0 until c.mediaItemCount) { undoItem = index to c.getMediaItemAt(index); c.removeMediaItem(index); message.value = "Removed from queue" } } }
    fun undoRemove() { val saved = undoItem ?: return; controller?.let { it.addMediaItem(saved.first.coerceIn(0,it.mediaItemCount), saved.second) }; undoItem = null }
    fun clearPlayedQueue() { controller?.let { if(it.currentMediaItemIndex > 0) it.removeMediaItems(0,it.currentMediaItemIndex) } }
    fun moveQueue(from: Int, to: Int) { controller?.let { if(from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun jumpQueue(index: Int) { controller?.let { it.seekTo(index, 0); it.play() } }
    fun clearQueue() { controller?.clearMediaItems() }
    fun favorite(song: Song) = action {
        dao.favorite(song.id)
        if(current.value?.song?.id == song.id) current.value = dao.track(song.id)
        refreshHome()
    }
    fun createPlaylist(name: String, ids: List<String> = emptyList(), fromSelection: Boolean = false) = action {
        val id = dao.create(Playlist(name = validName(name)))
        val songs = if(fromSelection) dao.tracks(LibraryQuery.build(filter.value)).map { it.song.id } else ids
        if(songs.isNotEmpty()) dao.addToPlaylist(id, songs)
        message.value = "Playlist created" + if(songs.isEmpty()) "" else " · ${songs.size} songs added"
    }
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
    fun skipSetting(value: Int) = action { app.preferences.number("skip", value.coerceIn(5,60)) }
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
    private fun effectsCommand(action: String, args: Bundle = Bundle.EMPTY) {
        val c = controller ?: run { message.value = "Playback is still connecting"; return }
        val future = c.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
        future.addListener({ runCatching { future.get() }.onSuccess { effects.value = EffectsState.fromBundle(it.extras) }.onFailure { message.value = "Unable to update audio effects. Please try again." } }, ContextCompat.getMainExecutor(app))
    }
    fun refreshEffects() = effectsCommand(MusicService.EFFECTS_STATUS)
    fun updateEffects(config: EffectsConfig) {
        effects.value = effects.value.copy(config = config.safe())
        effectsCommand(MusicService.EFFECTS, Bundle().apply { putString("config", config.safe().encode()) })
    }
    fun markLoop(clear: Boolean = false) {
        val future = controller?.sendCustomCommand(SessionCommand(MusicService.SEGMENT, Bundle.EMPTY), Bundle().apply { putBoolean("clear", clear) }) ?: return
        future.addListener({ runCatching { future.get() }.getOrNull()?.extras?.let { playback.value = playback.value.copy(loopA = it.getLong("loopA", -1), loopB = it.getLong("loopB", -1)) } }, ContextCompat.getMainExecutor(app))
    }
    fun volume(value: Float) { controller?.sendCustomCommand(SessionCommand(MusicService.VOLUME, Bundle.EMPTY), Bundle().apply { putFloat("volume", value.coerceIn(0f,1f)) }); playback.value = playback.value.copy(volume = value.coerceIn(0f,1f)) }
    val lyrics = MutableStateFlow<LyricsDocument?>(null)
    private var lyricsSongId: String? = null
    private var lyricsLoad: Job? = null
    private val lyricsSettings by lazy { app.getSharedPreferences("lyrics-timing", android.content.Context.MODE_PRIVATE) }
    private fun lyricKey(id: String) = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun lyricFile(id: String) = java.io.File(app.filesDir, "lyrics/" + lyricKey(id) + ".txt")
    fun loadLyrics(id: String) {
        lyricsSongId = id; lyricsLoad?.cancel(); lyrics.value = null
        lyricsLoad = action {
            val document = withContext(Dispatchers.IO) { lyricFile(id).takeIf { it.exists() }?.let { LyricsDocument(id, it.readText(), lyricsSettings.getLong(lyricKey(id), 0)) } }
            if(lyricsSongId == id) lyrics.value = document
        }
    }
    fun attachLyrics(id: String, uri: Uri) = action {
        val document = withContext(Dispatchers.IO) {
            val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if(cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            require(isLyricsFilename(name)) { "Choose a .txt or .lrc lyrics file" }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val text = app.contentResolver.openInputStream(uri)?.let { stream -> java.io.InputStreamReader(stream, decoder).buffered().use { reader ->
                val result = StringBuilder(); val buffer = CharArray(4096)
                while(true) { val n = reader.read(buffer); if(n < 0) break; result.append(buffer,0,n); require(result.length < 500_000) { "Lyrics file is too large" } }
                result.toString()
            } } ?: error("Cannot read lyrics")
            require(text.isNotBlank() && !text.contains('\u0000')) { "Choose a readable UTF-8 .txt or .lrc file" }
            lyricFile(id).apply { parentFile?.mkdirs(); writeText(text) }
            lyricsSettings.edit().remove(lyricKey(id)).apply()
            LyricsDocument(id, text)
        }
        if(lyricsSongId == id) { lyricsLoad?.cancel(); lyrics.value = document }
        message.value = "Lyrics attached"
    }
    fun lyricsDelay(id: String, delay: Long) {
        val value = delay.coerceIn(-30_000, 30_000)
        if(lyrics.value?.songId == id) lyrics.value = lyrics.value?.copy(delayMs = value)
        lyricsSettings.edit().putLong(lyricKey(id), value).apply()
    }
    fun removeLyrics(id: String) = action {
        withContext(Dispatchers.IO) { lyricFile(id).delete(); lyricsSettings.edit().remove(lyricKey(id)).apply() }
        if(lyricsSongId == id) { lyricsLoad?.cancel(); lyrics.value = null }
    }
    fun playFromLyric(position: Long) {
        controller?.let { c -> c.seekTo(position.coerceIn(0, c.duration.coerceAtLeast(0))); c.play() }
    }
    private fun validName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.length <= 80) { "Use a playlist name between 1 and 80 characters" } }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch(e: Exception) { if(e is CancellationException) throw e; message.value = if(e is android.database.sqlite.SQLiteConstraintException) "That playlist name already exists" else e.message ?: "Something went wrong. Please try again." }
    }
    override fun onCleared() { app.contentResolver.unregisterContentObserver(observer); MediaController.releaseFuture(controllerFuture); super.onCleared() }
}
