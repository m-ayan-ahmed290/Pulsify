package com.pulsify.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.pulsify.music.data.Song
import java.io.File

fun Song.mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(id)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setAlbumArtist(albumArtist).setGenre(genre).setTrackNumber(track).setReleaseYear(year)
        .setArtworkUri(artwork?.let { Uri.fromFile(File(it)) }).setIsBrowsable(false).setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
