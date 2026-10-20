package com.pulsify.music.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pulsify.music.PulsifyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class LibraryScanner(private val context: Context, private val dao: MusicDao, private val preferences: Preferences) {
    private val lock = Mutex()
    suspend fun scan(): Int = withContext(Dispatchers.IO) { lock.withLock {
        val settings = preferences.flow.first()
        val token = System.currentTimeMillis()
        var count = 0
        val batch = ArrayList<Song>(128)
        suspend fun accept(song: Song) {
            batch.add(song); count++
            if(batch.size >= 128) { dao.upsert(batch.toList()); batch.clear() }
        }
        if(settings.folder.isNotEmpty()) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(settings.folder))
                ?: error("Choose your music folder again in Settings.")
            check(root.exists() && root.canRead()) { "Folder access was revoked. Choose your music folder again." }
            val pending = ArrayDeque<Pair<DocumentFile, String>>()
            val visited = HashSet<String>()
            pending.add(root to (root.name ?: "Music"))
            while(pending.isNotEmpty()) {
                val (directory, path) = pending.removeFirst()
                if(!visited.add(directory.uri.toString())) continue
                for(file in directory.listFiles()) {
                    val name = file.name ?: continue
                    if(!settings.showHidden && name.startsWith('.')) continue
                    if(settings.hideWhatsApp && (path + "/" + name).contains("whatsapp", true)) continue
                    if(file.isDirectory) { pending.add(file to "$path/$name"); continue }
                    if(file.type?.startsWith("audio/") != true && name.substringAfterLast('.').lowercase() !in audioExtensions) continue
                    val id = file.uri.toString()
                    val old = dao.song(id)
                    val song = if(old != null && old.modified == file.lastModified() && old.size == file.length()) old.copy(scanToken = token)
                    else metadata(file.uri, name.substringBeforeLast('.'), path, "folder", token, file.lastModified(), file.length(), old?.added ?: token)
                    accept(song)
                }
            }
            dao.upsert(batch)
            dao.removeMissing("folder", token)
            dao.removeOtherSources("folder")
        } else {
            val permission = if(Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
            check(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                "Allow audio access or choose a music folder to build your library."
            }
            val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DISPLAY_NAME)
            val cursor = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns, "${MediaStore.Audio.Media.IS_MUSIC}!=0", null, null)
                ?: error("The device music library is temporarily unavailable. Try scanning again.")
            cursor.use { c ->
                fun text(column: String) = c.getString(c.getColumnIndexOrThrow(column)).orEmpty()
                fun number(column: String) = c.getLong(c.getColumnIndexOrThrow(column))
                while(c.moveToNext()) {
                    val path = text(MediaStore.Audio.Media.RELATIVE_PATH).trimEnd('/')
                    if(settings.hideWhatsApp && path.contains("whatsapp", true)) continue
                    if(!settings.showHidden && path.split('/').any { it.startsWith('.') }) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, number(MediaStore.Audio.Media._ID))
                    val changed = number(MediaStore.Audio.Media.DATE_MODIFIED) * 1000
                    val size = number(MediaStore.Audio.Media.SIZE)
                    val old = dao.song(uri.toString())
                    val song = if(old != null && old.modified == changed && old.size == size) old.copy(scanToken = token)
                    else metadata(uri, text(MediaStore.Audio.Media.DISPLAY_NAME).substringBeforeLast('.'), path, "device", token, changed, size,
                        number(MediaStore.Audio.Media.DATE_ADDED) * 1000).copy(
                        title = text(MediaStore.Audio.Media.TITLE).ifBlank { "Untitled" },
                        artist = text(MediaStore.Audio.Media.ARTIST).ifBlank { "Unknown artist" },
                        album = text(MediaStore.Audio.Media.ALBUM).ifBlank { "Unknown album" },
                        duration = number(MediaStore.Audio.Media.DURATION), track = number(MediaStore.Audio.Media.TRACK).toInt() % 1000,
                        year = number(MediaStore.Audio.Media.YEAR).toInt())
                    accept(song)
                }
            }
            dao.upsert(batch)
            dao.removeMissing("device", token)
            dao.removeOtherSources("device")
        }
        count
    } }
    private fun metadata(uri: Uri, fallback: String, folder: String, source: String, token: Long, modified: Long, size: Long, added: Long): Song {
        var song = Song(uri.toString(), fallback, folder = folder, source = source, scanToken = token, modified = modified, size = size, added = added)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun get(key: Int) = retriever.extractMetadata(key).orEmpty()
            song = song.copy(title = get(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { fallback },
                artist = get(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { "Unknown artist" },
                album = get(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { "Unknown album" },
                albumArtist = get(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), genre = get(MediaMetadataRetriever.METADATA_KEY_GENRE).ifBlank { "Unknown genre" },
                year = get(MediaMetadataRetriever.METADATA_KEY_YEAR).toIntOrNull() ?: 0,
                track = get(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).substringBefore('/').toIntOrNull() ?: 0,
                duration = get(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0)
            val key = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            val art = File(context.filesDir, "artwork/$key.jpg")
            val bytes = retriever.embeddedPicture
            if(bytes != null && bytes.size <= 8 * 1024 * 1024) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while(bounds.outWidth / inSampleSize > 768 || bounds.outHeight / inSampleSize > 768) inSampleSize *= 2
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { bitmap ->
                    art.parentFile?.mkdirs()
                    art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    bitmap.recycle()
                    song = song.copy(artwork = art.absolutePath)
                }
            } else if(art.exists()) art.delete()
        } catch (_: Exception) {
            // A malformed track remains playable by URI with fallback metadata.
        } finally { runCatching { retriever.release() } }
        return song
    }
    companion object { private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr", "aiff") }
}
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PulsifyApplication).scanner.scan(); Result.success()
    } catch (_: SecurityException) { Result.failure() }
      catch (_: IllegalStateException) { Result.failure() }
      catch (_: Exception) { if(runAttemptCount < 2) Result.retry() else Result.failure() }
}
