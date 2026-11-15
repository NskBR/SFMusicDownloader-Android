package com.nsk.sfmd

import android.app.Application
import com.nsk.sfmd.core.*
import com.nsk.sfmd.engine.YtDlpEngine
import com.nsk.sfmd.updates.UpdateRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SfmdApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var preferenceStore: PreferencesStore
    lateinit var preferences: StateFlow<Preferences>
    lateinit var downloads: DownloadRepository
    lateinit var engine: YtDlpEngine
    lateinit var updates: UpdateRepository
    val engineStatus = MutableStateFlow("Preparando motor incluído no app…")
    override fun onCreate() {
        super.onCreate()
        preferenceStore = PreferencesStore(this)
        preferences = preferenceStore.flow.stateIn(scope, SharingStarted.Eagerly, Preferences())
        engine = YtDlpEngine(this)
        downloads = DownloadRepository(this, engine, scope, preferences)
        updates = UpdateRepository(this, downloads, scope)
        scope.launch { engineStatus.value = runCatching { engine.initialize() }.getOrElse { "Motor indisponível: ${readableError(it)}" } }
    }
}
