package com.pulsify.music.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import com.pulsify.music.domain.rememberedSearches
import com.pulsify.music.domain.supportPromptDue

private val Context.preferences by preferencesDataStore("settings")
data class Settings(val theme: String = "Dark", val dynamic: Boolean = false, val folder: String = "",
    val resume: Boolean = true, val hideWhatsApp: Boolean = true, val showHidden: Boolean = false, val skip: Int = 10)
class Preferences(private val context: Context) {
    val flow = context.preferences.data.map { p -> Settings(p[stringPreferencesKey("theme")] ?: "Dark",
        p[booleanPreferencesKey("dynamic")] ?: false, p[stringPreferencesKey("folder")] ?: "",
        p[booleanPreferencesKey("resume")] ?: true, p[booleanPreferencesKey("hideWhatsApp")] ?: true,
        p[booleanPreferencesKey("showHidden")] ?: false, p[intPreferencesKey("skip")] ?: 10) }
    val searchHistory = context.preferences.data.map { p ->
        runCatching { val data = JSONArray(p[stringPreferencesKey("searchHistory")] ?: "[]"); (0 until data.length()).map { data.getString(it) }.take(8) }.getOrDefault(emptyList())
    }
    suspend fun rememberSearch(query: String) {
        context.preferences.edit { p ->
            val old = runCatching { val data = JSONArray(p[stringPreferencesKey("searchHistory")] ?: "[]"); (0 until data.length()).map { data.getString(it) } }.getOrDefault(emptyList())
            p[stringPreferencesKey("searchHistory")] = JSONArray(rememberedSearches(old, query)).toString()
        }
    }
    /** Claim atomically before display so reopening or concurrent checks cannot repeat the prompt. */
    suspend fun claimSupportPrompt(now: Long): Boolean {
        var claimed = false
        context.preferences.edit { p ->
            val key = longPreferencesKey("supportLastShown")
            if(supportPromptDue(now, p[key])) { p[key] = now; claimed = true }
        }
        return claimed
    }
    suspend fun number(key: String, value: Int) { context.preferences.edit { it[intPreferencesKey(key)] = value } }
    suspend fun text(key: String, value: String) { context.preferences.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun flag(key: String, value: Boolean) { context.preferences.edit { it[booleanPreferencesKey(key)] = value } }
}
