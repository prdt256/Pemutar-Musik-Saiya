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
}
