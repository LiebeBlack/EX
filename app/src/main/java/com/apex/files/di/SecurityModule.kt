package com.apex.files.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val ENCRYPTED_PREFS_FILE = "apex_encrypted_prefs"

    @Provides
    @Singleton
    fun provideMasterKey(@ApplicationContext context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun getSecurePrefs(
        context: Context,
        fileName: String,
        masterKey: MasterKey
    ): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
    }

    @Provides
    @Singleton
    @Named("EncryptedSettings")
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey
    ): SharedPreferences {
        return getSecurePrefs(context, ENCRYPTED_PREFS_FILE, masterKey)
    }

    @Provides
    @Singleton
    @Named("EncryptedOnboarding")
    fun provideEncryptedOnboardingSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey
    ): SharedPreferences {
        return getSecurePrefs(context, "apex_encrypted_onboarding", masterKey)
    }

    @Provides
    @Singleton
    @Named("EncryptedToolRoots")
    fun provideEncryptedToolRootsSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey
    ): SharedPreferences {
        return getSecurePrefs(context, "apex_encrypted_tool_roots", masterKey)
    }

    @Provides
    @Singleton
    @Named("EncryptedDrives")
    fun provideEncryptedDrivesSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey
    ): SharedPreferences {
        return getSecurePrefs(context, "apex_encrypted_drives", masterKey)
    }

    @Provides
    @Singleton
    @Named("EncryptedRecents")
    fun provideEncryptedRecentsSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey
    ): SharedPreferences {
        return getSecurePrefs(context, "apex_encrypted_recents", masterKey)
    }

    @Provides
    @Singleton
    @Named("EncryptedFavorites")
    fun provideEncryptedFavoritesSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey
    ): SharedPreferences {
        return getSecurePrefs(context, "apex_encrypted_favorites", masterKey)
    }
}