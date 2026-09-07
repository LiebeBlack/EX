package com.apex.files.core

import android.content.Context
import coil.ImageLoader
import com.apex.files.data.fs.ArchiveRepository
import com.apex.files.data.fs.ConflictController
import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.IndexStore
import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.fs.SqliteRepository
import com.apex.files.data.fs.TrashManager
import com.apex.files.data.media.MediaStoreRepository
import com.apex.files.data.search.ContentIndex
import com.apex.files.data.search.ContentIndexer
import com.apex.files.data.search.OcrEngine
import com.apex.files.data.search.PdfTextExtractor
import com.apex.files.data.storage.DrivesRepository
import com.apex.files.data.storage.ToolRoots
import com.apex.files.tools.ApkScanner
import com.apex.files.tools.DuplicateFinder
import com.apex.files.tools.EmptyCleaner
import com.apex.files.tools.JunkAnalyzer
import com.apex.files.tools.SpaceAnalyzer
import com.apex.files.tools.StorageBenchmark
import javax.inject.Inject

/**
 * Dependency container now managed by Hilt with proper lifecycle scoping.
 * All dependencies are injected via constructor injection to avoid memory leaks.
 * Created once per process in [MainActivity] and exposed to composables via [LocalContainer].
 */
class AppContainer @Inject constructor(
    val appContext: Context,
    val settings: SettingsRepository,
    val conflicts: ConflictController,
    val fs: FsRepository,
    val index: MemoryIndex,
    val indexStore: IndexStore,
    val sqlite: SqliteRepository,
    val recents: RecentStore,
    val favorites: FavoritesStore,
    val onboarding: OnboardingStore,
    val mediaStore: MediaStoreRepository,
    val drives: DrivesRepository,
    val toolRoots: ToolRoots,
    val trash: TrashManager,
    val contentIndex: ContentIndex,
    val ocr: OcrEngine,
    val pdfText: PdfTextExtractor,
    val contentIndexer: ContentIndexer,
    val junkAnalyzer: JunkAnalyzer,
    val archive: ArchiveRepository,
    val cleaner: EmptyCleaner,
    val duplicateFinder: DuplicateFinder,
    val apkScanner: ApkScanner,
    val spaceAnalyzer: SpaceAnalyzer,
    val benchmark: StorageBenchmark,
    val imageLoader: ImageLoader
)