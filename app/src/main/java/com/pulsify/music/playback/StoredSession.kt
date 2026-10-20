package com.pulsify.music.playback

import org.json.JSONArray
import org.json.JSONObject

/** Only durable IDs are stored; playback resumes paused after the IDs are resolved in Room. */
data class StoredSession(val queue: List<String>, val index: Int, val position: Long, val shuffle: Boolean, val repeat: Int) {
    fun encode(): String = JSONObject().put("queue", JSONArray(queue)).put("index", index)
        .put("position", position).put("shuffle", shuffle).put("repeat", repeat).toString()
    companion object {
        fun decode(value: String): StoredSession? = runCatching {
            val json = JSONObject(value)
            val array = json.getJSONArray("queue")
            val ids = (0 until array.length()).map { array.getString(it) }
            if(ids.isEmpty()) return null
            StoredSession(ids, json.optInt("index").coerceIn(ids.indices), json.optLong("position").coerceAtLeast(0),
                json.optBoolean("shuffle"), json.optInt("repeat").coerceIn(0, 2))
        }.getOrNull()
    }
}
