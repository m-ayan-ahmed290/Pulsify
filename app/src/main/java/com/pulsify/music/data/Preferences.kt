package com.pulsify.music.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("settings")
data class Settings(val theme: String = "Dark", val dynamic: Boolean = false, val folder: String = "",
    val resume: Boolean = true, val hideWhatsApp: Boolean = true, val showHidden: Boolean = false, val skip: Int = 10)
class Preferences(private val context: Context) {
    val flow = context.preferences.data.map { p -> Settings(p[stringPreferencesKey("theme")] ?: "Dark",
        p[booleanPreferencesKey("dynamic")] ?: false, p[stringPreferencesKey("folder")] ?: "",
        p[booleanPreferencesKey("resume")] ?: true, p[booleanPreferencesKey("hideWhatsApp")] ?: true,
        p[booleanPreferencesKey("showHidden")] ?: false, p[intPreferencesKey("skip")] ?: 10) }
    suspend fun text(key: String, value: String) { context.preferences.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun flag(key: String, value: Boolean) { context.preferences.edit { it[booleanPreferencesKey(key)] = value } }
}
