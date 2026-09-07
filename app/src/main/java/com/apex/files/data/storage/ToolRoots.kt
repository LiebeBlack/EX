package com.apex.files.data.storage

import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Persists the scan root (a [DrivesRepository.Volume.key]) chosen per tool
 * (Cleaner / Duplicates / APK). Falling back to the internal volume keeps
 * behavior unchanged for existing users.
 * Now uses EncryptedSharedPreferences for secure storage.
 */
@Singleton
class ToolRoots @Inject constructor(
    @Named("EncryptedToolRoots") encryptedPrefs: SharedPreferences
) {

    private val prefs: SharedPreferences = encryptedPrefs

    fun get(tool: String): String? = prefs.getString("root_$tool", null)

    fun set(tool: String, key: String) {
        prefs.edit().putString("root_$tool", key).apply()
    }
}
