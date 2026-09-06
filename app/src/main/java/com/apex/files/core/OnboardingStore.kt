package com.apex.files.core

import android.content.Context
import android.content.SharedPreferences

/**
 * First-run flags (welcome tour). Kept in their own preferences file so
 * resetting the app settings never touches them unless explicitly asked
 * (Settings → "Restablecer ajustes" resets both).
 */
class OnboardingStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apex_onboarding", Context.MODE_PRIVATE)

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