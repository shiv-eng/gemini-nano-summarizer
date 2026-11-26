package com.nanodigest

import android.app.Application
import com.nanodigest.core.engine.ExtractiveEngine
import com.nanodigest.core.engine.GeminiNanoEngine
import com.nanodigest.core.engine.ModelManager
import com.nanodigest.core.engine.SummaryPipeline
import com.nanodigest.core.lang.LanguageDetector
import com.nanodigest.data.AppDatabase
import com.nanodigest.data.SettingsStore
import com.nanodigest.data.SharedTextBus
import com.nanodigest.data.SummaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container — small app, no DI framework needed. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val repository = SummaryRepository(AppDatabase.create(app).summaryDao())
    val nano = GeminiNanoEngine(app)
    val extractive = ExtractiveEngine()
    val pipeline = SummaryPipeline(nano, extractive)
    val modelManager = ModelManager(nano, appScope)
    val languageDetector = LanguageDetector()
    val sharedText = SharedTextBus()
}

class NanoDigestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
