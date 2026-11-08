package com.example.pemutarmusik.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pemutar_musik", Context.MODE_PRIVATE)

    fun getExcludedFolders(): Set<String> =
        prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()

    fun saveExcludedFolders(folders: Set<String>) {
        prefs.edit().putStringSet("excluded_folders", folders).apply()
    }

    private fun readIds(key: String): List<Long> =
        (prefs.getString(key, "") ?: "").split(",").mapNotNull { it.trim().toLongOrNull() }

    private fun writeIds(key: String, ids: List<Long>) {
        prefs.edit().putString(key, ids.joinToString(",")).apply()
    }

    fun getFavorites(): List<Long> = readIds("favorites")
    fun saveFavorites(ids: List<Long>) = writeIds("favorites", ids)

    /** Riwayat putar, terbaru di depan. */
    fun getHistory(): List<Long> = readIds("history")
    fun saveHistory(ids: List<Long>) = writeIds("history", ids)
}
