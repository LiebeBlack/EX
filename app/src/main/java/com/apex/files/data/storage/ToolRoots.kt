package com.apex.files.data.storage

import android.content.SharedPreferences
/**
 * Persists the scan root (a [DrivesRepository.Volume.key]) chosen per tool
 * (Cleaner / Duplicates / APK). Falling back to the internal volume keeps
 * behavior unchanged for existing users.
 * Now uses EncryptedSharedPreferences for secure storage.
 */
class ToolRoots(
    encryptedPrefs: SharedPreferences
) {

    private val prefs: SharedPreferences = encryptedPrefs

    fun get(tool: String): String? = prefs.getString("root_$tool", null)

    fun set(tool: String, key: String) {
        prefs.edit().putString("root_$tool", key).apply()
    }
}
