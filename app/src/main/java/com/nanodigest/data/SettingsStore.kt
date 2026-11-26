package com.nanodigest.data

import android.content.Context
import com.nanodigest.core.engine.EngineMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Settings(
    val engineMode: EngineMode = EngineMode.AUTO,
    val defaultBullets: Int = 3,
    val autoDownloadModel: Boolean = true,
    val warmUpOnLaunch: Boolean = true,
    val saveHistory: Boolean = true,
    val cleanChatExports: Boolean = true,
    val replaceLinks: Boolean = true,
    val autoRunSharedText: Boolean = true,
)

/** Small, synchronous preferences store exposed as a StateFlow. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
        save(_settings.value)
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            engineMode = runCatching { EngineMode.valueOf(prefs.getString("engineMode", null) ?: d.engineMode.name) }
                .getOrDefault(d.engineMode),
            defaultBullets = prefs.getInt("defaultBullets", d.defaultBullets).coerceIn(1, 3),
            autoDownloadModel = prefs.getBoolean("autoDownloadModel", d.autoDownloadModel),
            warmUpOnLaunch = prefs.getBoolean("warmUpOnLaunch", d.warmUpOnLaunch),
            saveHistory = prefs.getBoolean("saveHistory", d.saveHistory),
            cleanChatExports = prefs.getBoolean("cleanChatExports", d.cleanChatExports),
            replaceLinks = prefs.getBoolean("replaceLinks", d.replaceLinks),
            autoRunSharedText = prefs.getBoolean("autoRunSharedText", d.autoRunSharedText),
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putString("engineMode", s.engineMode.name)
            .putInt("defaultBullets", s.defaultBullets)
            .putBoolean("autoDownloadModel", s.autoDownloadModel)
            .putBoolean("warmUpOnLaunch", s.warmUpOnLaunch)
            .putBoolean("saveHistory", s.saveHistory)
            .putBoolean("cleanChatExports", s.cleanChatExports)
            .putBoolean("replaceLinks", s.replaceLinks)
            .putBoolean("autoRunSharedText", s.autoRunSharedText)
            .apply()
    }
}
