package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * First-run flags (welcome tour). Kept in their own preferences file so
 * resetting the app settings never touches them unless explicitly asked
 * (Settings → "Restablecer ajustes" resets both).
 * Now uses EncryptedSharedPreferences for secure storage.
 */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext context: Context,
    @Named("EncryptedOnboarding") encryptedPrefs: SharedPreferences
) {

    private val prefs: SharedPreferences = encryptedPrefs

    val tourSeen: Boolean
        get() = prefs.getBoolean(KEY_TOUR_SEEN, false)

    fun markTourSeen() {
        prefs.edit().putBoolean(KEY_TOUR_SEEN, true).apply()
    }

    /** Re-arms the welcome tour (Settings → "Ver guía de nuevo"). */
    fun resetTour() {
        prefs.edit().putBoolean(KEY_TOUR_SEEN, false).apply()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOUR_SEEN = "tour_seen"
    }
}