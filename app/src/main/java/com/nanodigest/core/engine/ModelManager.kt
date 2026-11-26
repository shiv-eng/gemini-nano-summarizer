package com.nanodigest.core.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EngineStatus(
    val config: SummaryConfig? = null,
    /** null while checking. */
    val availability: Availability? = null,
    val download: DownloadState = DownloadState.Idle,
    val warming: Boolean = false,
    val warmUpMs: Long? = null,
    val modelName: String? = null,
)

/**
 * App-scoped owner of Gemini Nano's lifecycle: status checks, the model download and warm-up.
 * Lives outside any screen so a download keeps going (and keeps reporting) while the user
 * moves around the app.
 */
class ModelManager(
    private val nano: GeminiNanoEngine,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var refreshJob: Job? = null
    private var downloadJob: Job? = null
    private var warmJob: Job? = null

    /** Checks availability for [config] and, depending on the flags, downloads and warms up. */
    fun refresh(config: SummaryConfig, autoDownload: Boolean, warmUp: Boolean) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            _status.update {
                it.copy(
                    config = config,
                    availability = null,
                    warmUpMs = if (it.config == config) it.warmUpMs else null,
                )
            }
            val availability = nano.availability(config)
            _status.update { it.copy(availability = availability) }
            when (availability) {
                Availability.NeedsDownload, Availability.Downloading -> if (autoDownload) download(config)
                Availability.Ready -> {
                    fetchModelName(config)
                    if (warmUp) warmUp(config)
                }
                is Availability.Unavailable -> Unit
            }
        }
    }

    fun download(config: SummaryConfig) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            nano.download(config).collect { state -> _status.update { it.copy(download = state) } }
            val availability = nano.availability(config)
            _status.update { it.copy(availability = availability) }
            if (availability == Availability.Ready) fetchModelName(config)
        }
    }

    fun warmUp(config: SummaryConfig) {
        if (warmJob?.isActive == true) return
        warmJob = scope.launch {
            _status.update { it.copy(warming = true) }
            val ms = nano.warmUp(config)
            _status.update { it.copy(warming = false, warmUpMs = ms ?: it.warmUpMs) }
        }
    }

    private suspend fun fetchModelName(config: SummaryConfig) {
        if (_status.value.modelName != null) return
        val name = nano.baseModelName(config)
        _status.update { it.copy(modelName = name) }
    }
}
