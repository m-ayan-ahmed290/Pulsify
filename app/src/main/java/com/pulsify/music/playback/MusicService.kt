package com.pulsify.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.pulsify.music.MainActivity
import com.pulsify.music.PulsifyApplication
import com.pulsify.music.domain.LibraryFilter
import com.pulsify.music.domain.LibraryQuery
import com.pulsify.music.domain.qualifiesForPlay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as PulsifyApplication
    private var sleepDeadline = 0L
    private var sleepTracks = 0
    private var sleepAtEnd = false
    private var listened = 0L
    private var counted = false
    private var lastTick = 0L
    private var saveTicks = 0
    private var restoring = true
    private var lastQueue = emptyList<String>()
    private val store by lazy { getSharedPreferences("playback", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build()
        session = MediaLibrarySession.Builder(this, player, Callback()).setSessionActivity(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                listened = 0; counted = false
                if(reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && sleepTracks > 0 && --sleepTracks == 0) stopForSleep()
                snapshot()
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { snapshot(queueChanged = true) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { lastTick = SystemClock.elapsedRealtime(); snapshot() }
            override fun onRepeatModeChanged(repeatMode: Int) { snapshot() }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { snapshot() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if(playbackState == Player.STATE_ENDED && (sleepAtEnd || sleepTracks > 0)) stopForSleep()
            }
        })
        scope.launch {
            restore()
            restoring = false
            snapshot(queueChanged = true)
            while(isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if(player.isPlaying) {
                    listened += (now - lastTick).coerceIn(0, 1500)
                    val id = player.currentMediaItem?.mediaId
                    if(!counted && id != null && qualifiesForPlay(listened, player.duration.coerceAtLeast(0))) {
                        counted = true
                        withContext(Dispatchers.IO) { app.database.music().recordPlay(id) }
                    }
                }
                lastTick = now
                if(sleepDeadline > 0 && now >= sleepDeadline) stopForSleep()
                if(++saveTicks % 5 == 0) snapshot()
            }
        }
    }
    private fun stopForSleep() {
        player.pause(); sleepDeadline = 0; sleepTracks = 0; sleepAtEnd = false
    }
    private fun snapshot(queueChanged: Boolean = false) {
        if(restoring) return
        if(queueChanged) lastQueue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val value = StoredSession(lastQueue, player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0), player.shuffleModeEnabled, player.repeatMode).encode()
        store.edit().putString("snapshot", value).apply()
    }
    private suspend fun restore() {
        if (!app.preferences.flow.first().resume) return
        try {
            val saved = StoredSession.decode(store.getString("snapshot", "{}") ?: "{}") ?: return
            val originalId = saved.queue[saved.index]
            val songs = withContext(Dispatchers.IO) { saved.queue.mapNotNull { app.database.music().song(it) } }
            if(songs.isEmpty() || player.mediaItemCount != 0) return
            player.setMediaItems(songs.map { it.mediaItem() }, songs.indexOfFirst { it.id == originalId }.coerceAtLeast(0), saved.position)
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeat
            player.prepare()
            lastQueue = songs.map { it.id }
        } catch (_: Exception) { store.edit().remove("snapshot").apply() }
    }
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val job = scope.launch { try { result.set(block()) } catch(e: Exception) { result.setException(e) } }
        result.addListener({ if(result.isCancelled) job.cancel() }, { it.run() })
        return result
    }
    private inner class Callback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(SLEEP, Bundle.EMPTY)).add(SessionCommand(SLEEP_STATUS, Bundle.EMPTY)).build()).build()
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            if(customCommand.customAction == SLEEP) {
                sleepDeadline = 0; sleepTracks = 0; sleepAtEnd = false
                val minutes = args.getInt("minutes")
                if(minutes > 0) sleepDeadline = SystemClock.elapsedRealtime() + minutes * 60_000L
                sleepTracks = args.getInt("tracks").coerceAtLeast(0)
                sleepAtEnd = args.getBoolean("end")
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                putLong("remaining", (sleepDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                putInt("tracks", sleepTracks); putBoolean("end", sleepAtEnd); putInt("audioSessionId", player.audioSessionId)
            }))
        }
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaItem.Builder().setMediaId("root").setMediaMetadata(
                MediaMetadata.Builder().setTitle("Pulsify").setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build(), params))
        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val tracks = withContext(Dispatchers.IO) {
                if(parentId != "root") emptyList() else app.database.music().tracks(
                    androidx.sqlite.db.SimpleSQLiteQuery(LibraryQuery.build(LibraryFilter()).sql + " LIMIT ? OFFSET ?", arrayOf(pageSize.coerceIn(1,500), page.coerceAtLeast(0) * pageSize.coerceIn(1,500))))
            }
            LibraryResult.ofItemList(tracks.map { it.song.mediaItem() }, params)
        }
        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = future {
            val song = withContext(Dispatchers.IO) { app.database.music().song(mediaId) }
            if(song == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(song.mediaItem(), null)
        }
        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = future {
            val count = withContext(Dispatchers.IO) { app.database.music().tracks(LibraryQuery.build(LibraryFilter(query = query), 500)).size }
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }
        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val found = withContext(Dispatchers.IO) { app.database.music().tracks(LibraryQuery.build(LibraryFilter(query = query), 500)) }
            val size = pageSize.coerceIn(1, 100)
            LibraryResult.ofItemList(found.drop((page.toLong().coerceAtLeast(0) * size).coerceAtMost(500).toInt()).take(size).map { it.song.mediaItem() }, params)
        }
        override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> = future {
            withContext(Dispatchers.IO) {
                mediaItems.flatMap { item ->
                    val query = item.requestMetadata.searchQuery
                    if(query != null) app.database.music().tracks(LibraryQuery.build(LibraryFilter(query = query), 100)).map { it.song.mediaItem() }
                    else listOfNotNull(app.database.music().song(item.mediaId)?.mediaItem())
                }.toMutableList()
            }
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session
    override fun onTaskRemoved(rootIntent: Intent?) { if(!player.playWhenReady) stopSelf() }
    override fun onDestroy() {
        snapshot(queueChanged = true); scope.cancel(); session.release(); player.release(); super.onDestroy()
    }
    companion object { const val SLEEP = "com.pulsify.SLEEP"; const val SLEEP_STATUS = "com.pulsify.SLEEP_STATUS" }
}
