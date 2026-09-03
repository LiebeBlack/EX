package com.apex.files.core

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.apex.files.data.fs.ArchiveRepository
import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.IndexStore
import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.media.MediaStoreRepository
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.tools.ApkScanner
import com.apex.files.tools.DuplicateFinder
import com.apex.files.tools.EmptyCleaner
import com.apex.files.tools.SpaceAnalyzer
import com.apex.files.tools.StorageBenchmark
import java.io.File

/**
 * Manual dependency container. Created once per process in [MainActivity]
 * and exposed to composables via [LocalContainer].
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val fs: FsRepository by lazy { FsRepository(appContext) }
    val index: MemoryIndex by lazy { MemoryIndex() }
    val indexStore: IndexStore by lazy { IndexStore(appContext) }
    val recents: RecentStore by lazy { RecentStore(appContext) }
    val favorites: FavoritesStore by lazy { FavoritesStore(appContext) }
    val mediaStore: MediaStoreRepository by lazy { MediaStoreRepository(appContext) }
    val drives: DrivesRepository by lazy { DrivesRepository(appContext) }

    // Algorithmic tools (100% local, zero dependencies).
    val archive: ArchiveRepository by lazy { ArchiveRepository(appContext, fs) }
    val cleaner: EmptyCleaner by lazy { EmptyCleaner(fs) }
    val duplicateFinder: DuplicateFinder by lazy { DuplicateFinder(fs) }
    val apkScanner: ApkScanner by lazy { ApkScanner(appContext, fs) }
    val spaceAnalyzer: SpaceAnalyzer by lazy { SpaceAnalyzer(fs) }
    val benchmark: StorageBenchmark by lazy { StorageBenchmark(appContext, fs) }

    /** Bounded Coil caches: 64 MB disk, small memory cache. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.08)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "apex_coil"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .logger(DebugLogger())
            .build()
    }
}