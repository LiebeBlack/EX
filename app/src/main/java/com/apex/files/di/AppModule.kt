package com.apex.files.di

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.apex.files.core.AppContainer
import com.apex.files.core.FavoritesStore
import com.apex.files.core.OnboardingStore
import com.apex.files.core.RecentStore
import com.apex.files.core.SettingsRepository
import com.apex.files.data.fs.ArchiveRepository
import com.apex.files.data.fs.ConflictController
import com.apex.files.data.fs.FsRepository
import com.apex.files.data.fs.IndexStore
import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.fs.Paths
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        @Named("EncryptedSettings") encryptedPrefs: SharedPreferences
    ): SettingsRepository {
        return SettingsRepository(context, encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideConflictController(): ConflictController {
        return ConflictController()
    }

    @Provides
    @Singleton
    fun provideFsRepository(
        @ApplicationContext context: Context
    ): FsRepository {
        return FsRepository(context)
    }

    @Provides
    @Singleton
    fun provideMemoryIndex(): MemoryIndex {
        return MemoryIndex()
    }

    @Provides
    @Singleton
    fun provideIndexStore(
        @ApplicationContext context: Context
    ): IndexStore {
        return IndexStore(context)
    }

    @Provides
    @Singleton
    fun provideSqliteRepository(
        @ApplicationContext context: Context,
        fsRepository: FsRepository
    ): SqliteRepository {
        return SqliteRepository(context, fsRepository)
    }

    @Provides
    @Singleton
    fun provideRecentStore(
        @ApplicationContext context: Context,
        @Named("EncryptedRecents") encryptedPrefs: SharedPreferences
    ): RecentStore {
        return RecentStore(context, encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideFavoritesStore(
        @ApplicationContext context: Context,
        @Named("EncryptedFavorites") encryptedPrefs: SharedPreferences
    ): FavoritesStore {
        return FavoritesStore(context, encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideOnboardingStore(
        @ApplicationContext context: Context,
        @Named("EncryptedOnboarding") encryptedPrefs: SharedPreferences
    ): OnboardingStore {
        return OnboardingStore(context, encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideMediaStoreRepository(
        @ApplicationContext context: Context
    ): MediaStoreRepository {
        return MediaStoreRepository(context)
    }

    @Provides
    @Singleton
    fun provideDrivesRepository(
        @ApplicationContext context: Context,
        @Named("EncryptedDrives") encryptedPrefs: SharedPreferences
    ): DrivesRepository {
        return DrivesRepository(context, encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideToolRoots(
        @Named("EncryptedToolRoots") encryptedPrefs: SharedPreferences
    ): ToolRoots {
        return ToolRoots(encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideTrashManager(
        @ApplicationContext context: Context
    ): TrashManager {
        return TrashManager(
            rootForPath = { path -> trashVolumeRoot(path) },
            allRoots = {
                buildList {
                    val internal = Paths.internalRoot()
                    if (internal.exists()) add(internal)
                    addAll(Paths.removableRoots())
                }
            },
        )
    }

    private fun trashVolumeRoot(path: String): File {
        for (root in Paths.removableRoots()) {
            val rootPath = root.absolutePath
            if (path == rootPath || path.startsWith("$rootPath/")) return root
        }
        return Paths.internalRoot()
    }

    @Provides
    @Singleton
    fun provideContentIndex(
        @ApplicationContext context: Context
    ): ContentIndex {
        return ContentIndex(context)
    }

    @Provides
    @Singleton
    fun provideOcrEngine(
        @ApplicationContext context: Context
    ): OcrEngine {
        return OcrEngine(context)
    }

    @Provides
    @Singleton
    fun providePdfTextExtractor(
        ocrEngine: OcrEngine
    ): PdfTextExtractor {
        return PdfTextExtractor(ocrEngine)
    }

    @Provides
    @Singleton
    fun provideContentIndexer(
        contentIndex: ContentIndex,
        ocrEngine: OcrEngine,
        pdfTextExtractor: PdfTextExtractor
    ): ContentIndexer {
        return ContentIndexer(contentIndex, ocrEngine, pdfTextExtractor)
    }

    @Provides
    @Singleton
    fun provideJunkAnalyzer(
        @ApplicationContext context: Context
    ): JunkAnalyzer {
        return JunkAnalyzer(context)
    }

    @Provides
    @Singleton
    fun provideArchiveRepository(
        @ApplicationContext context: Context,
        fsRepository: FsRepository
    ): ArchiveRepository {
        return ArchiveRepository(context, fsRepository)
    }

    @Provides
    @Singleton
    fun provideEmptyCleaner(
        fsRepository: FsRepository
    ): EmptyCleaner {
        return EmptyCleaner(fsRepository)
    }

    @Provides
    @Singleton
    fun provideDuplicateFinder(
        fsRepository: FsRepository
    ): DuplicateFinder {
        return DuplicateFinder(fsRepository)
    }

    @Provides
    @Singleton
    fun provideApkScanner(
        @ApplicationContext context: Context,
        fsRepository: FsRepository
    ): ApkScanner {
        return ApkScanner(context, fsRepository)
    }

    @Provides
    @Singleton
    fun provideSpaceAnalyzer(
        fsRepository: FsRepository
    ): SpaceAnalyzer {
        return SpaceAnalyzer(fsRepository)
    }

    @Provides
    @Singleton
    fun provideStorageBenchmark(
        @ApplicationContext context: Context,
        fsRepository: FsRepository
    ): StorageBenchmark {
        return StorageBenchmark(context, fsRepository)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.08)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "apex_coil"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .logger(DebugLogger())
            .build()
    }

    @Provides
    @Singleton
    fun provideAppContainer(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        conflictController: ConflictController,
        fsRepository: FsRepository,
        memoryIndex: MemoryIndex,
        indexStore: IndexStore,
        sqliteRepository: SqliteRepository,
        recentStore: RecentStore,
        favoritesStore: FavoritesStore,
        onboardingStore: OnboardingStore,
        mediaStoreRepository: MediaStoreRepository,
        drivesRepository: DrivesRepository,
        toolRoots: ToolRoots,
        trashManager: TrashManager,
        contentIndex: ContentIndex,
        ocrEngine: OcrEngine,
        pdfTextExtractor: PdfTextExtractor,
        contentIndexer: ContentIndexer,
        junkAnalyzer: JunkAnalyzer,
        archiveRepository: ArchiveRepository,
        emptyCleaner: EmptyCleaner,
        duplicateFinder: DuplicateFinder,
        apkScanner: ApkScanner,
        spaceAnalyzer: SpaceAnalyzer,
        storageBenchmark: StorageBenchmark,
        imageLoader: ImageLoader
    ): AppContainer {
        // Initialize PDFBox resource loader
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context) }

        return AppContainer(
            appContext = context,
            settings = settingsRepository,
            conflicts = conflictController,
            fs = fsRepository,
            index = memoryIndex,
            indexStore = indexStore,
            sqlite = sqliteRepository,
            recents = recentStore,
            favorites = favoritesStore,
            onboarding = onboardingStore,
            mediaStore = mediaStoreRepository,
            drives = drivesRepository,
            toolRoots = toolRoots,
            trash = trashManager,
            contentIndex = contentIndex,
            ocr = ocrEngine,
            pdfText = pdfTextExtractor,
            contentIndexer = contentIndexer,
            junkAnalyzer = junkAnalyzer,
            archive = archiveRepository,
            cleaner = emptyCleaner,
            duplicateFinder = duplicateFinder,
            apkScanner = apkScanner,
            spaceAnalyzer = spaceAnalyzer,
            benchmark = storageBenchmark,
            imageLoader = imageLoader
        )
    }
}