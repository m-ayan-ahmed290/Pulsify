package com.pulsify.music

import android.app.Application
import androidx.room.Room
import androidx.work.*
import com.pulsify.music.data.*
import java.util.concurrent.TimeUnit

class PulsifyApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, MusicDatabase::class.java, "pulsify.db").build() }
    val preferences by lazy { Preferences(this) }
    val scanner by lazy { LibraryScanner(this, database.music(), preferences) }
    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("library-watch", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES).build())
    }
}
