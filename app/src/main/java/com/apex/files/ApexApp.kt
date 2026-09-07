package com.apex.files

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Deliberately empty: zero initialization on process start keeps cold start
 * minimal. All work is deferred to the first frame (see [ApexAppUi]).
 * Hilt is enabled for dependency injection with proper lifecycle scoping.
 */
@HiltAndroidApp
class ApexApp : Application()