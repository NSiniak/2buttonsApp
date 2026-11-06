package com.example.twobuttonsapp

import android.content.Context
import android.content.SharedPreferences

data class HistoryEntry(
    val id: Long,
    val isYes: Boolean,
    val timestamp: Long,
    val note: String = ""
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("two_buttons_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_YES_SOUND_TYPE = "yes_sound_type" // "default", "recorded", "custom"
        private const val KEY_NO_SOUND_TYPE = "no_sound_type"   // "default", "recorded", "custom"
        private const val KEY_YES_RECORDED_PATH = "yes_recorded_path"
        private const val KEY_NO_RECORDED_PATH = "no_recorded_path"
        private const val KEY_YES_CUSTOM_URI = "yes_custom_uri"
        private const val KEY_NO_CUSTOM_URI = "no_custom_uri"
        private const val KEY_YES_PITCH = "yes_pitch"
        private const val KEY_NO_PITCH = "no_pitch"
        private const val KEY_YES_COUNT = "yes_count"
        private const val KEY_NO_COUNT = "no_count"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_PARTICLES_ENABLED = "particles_enabled"
        private const val KEY_HISTORY = "history"
    }

    var yesSoundType: String
        get() = prefs.getString(KEY_YES_SOUND_TYPE, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_YES_SOUND_TYPE, value).apply()

    var noSoundType: String
        get() = prefs.getString(KEY_NO_SOUND_TYPE, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_NO_SOUND_TYPE, value).apply()

    var yesRecordedPath: String?
        get() = prefs.getString(KEY_YES_RECORDED_PATH, null)
        set(value) = prefs.edit().putString(KEY_YES_RECORDED_PATH, value).apply()

    var noRecordedPath: String?
        get() = prefs.getString(KEY_NO_RECORDED_PATH, null)
        set(value) = prefs.edit().putString(KEY_NO_RECORDED_PATH, value).apply()

    var yesCustomUri: String?
        get() = prefs.getString(KEY_YES_CUSTOM_URI, null)
        set(value) = prefs.edit().putString(KEY_YES_CUSTOM_URI, value).apply()

    var noCustomUri: String?
        get() = prefs.getString(KEY_NO_CUSTOM_URI, null)
        set(value) = prefs.edit().putString(KEY_NO_CUSTOM_URI, value).apply()

    var yesPitch: Float
        get() = prefs.getFloat(KEY_YES_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_YES_PITCH, value).apply()

    var noPitch: Float
        get() = prefs.getFloat(KEY_NO_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_NO_PITCH, value).apply()

    var yesCount: Int
        get() = prefs.getInt(KEY_YES_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_YES_COUNT, value).apply()

    var noCount: Int
        get() = prefs.getInt(KEY_NO_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_NO_COUNT, value).apply()

    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    var isParticlesEnabled: Boolean
        get() = prefs.getBoolean(KEY_PARTICLES_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PARTICLES_ENABLED, value).apply()

    fun incrementYes() {
        yesCount = yesCount + 1
    }

    fun incrementNo() {
        noCount = noCount + 1
    }

    fun clearCounts() {
        yesCount = 0
        noCount = 0
    }

    fun getHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split(";;;")
            if (parts.size >= 3) {
                try {
                    val id = parts[0].toLong()
                    val isYes = parts[1].toBoolean()
                    val timestamp = parts[2].toLong()
                    val note = if (parts.size > 3) parts[3] else ""
                    HistoryEntry(id, isYes, timestamp, note)
                } catch (e: Exception) {
                    null
                }
            } else null
        }.sortedByDescending { it.timestamp }
    }

    fun addHistoryEntry(isYes: Boolean, note: String = "") {
        val currentHistory = getHistory().toMutableList()
        val newEntry = HistoryEntry(
            id = System.currentTimeMillis(),
            isYes = isYes,
            timestamp = System.currentTimeMillis(),
            note = note.replace("\n", " ").replace(";;;", " ")
        )
        currentHistory.add(newEntry)
        saveHistory(currentHistory)
    }

    fun deleteHistoryEntry(id: Long) {
        val currentHistory = getHistory().filter { it.id != id }
        saveHistory(currentHistory)
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(history: List<HistoryEntry>) {
        val serialized = history.joinToString("\n") { entry ->
            "${entry.id};;;${entry.isYes};;;${entry.timestamp};;;${entry.note}"
        }
        prefs.edit().putString(KEY_HISTORY, serialized).apply()
    }
}
