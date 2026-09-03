package com.apex.files

import android.app.Application

/**
 * Deliberately empty: zero initialization on process start keeps cold start
 * minimal. All work is deferred to the first frame (see [ApexAppUi]).
 */
class ApexApp : Application()