package com.nsk.sfmd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nsk.sfmd.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TabState(val url: String = "", val loading: Boolean = false, val info: MediaInfo? = null, val error: String = "", val height: Int = 0, val format: String = "", val bitrate: Int = 192, val filename: String = "")
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as SfmdApp
    private val states = MutableStateFlow(MediaTab.entries.associateWith { TabState() })
    val tabs = states.asStateFlow()
    val selected = MutableStateFlow(MediaTab.VIDEO)
    val pendingShare = MutableStateFlow<String?>(null)
    private val queries = mutableMapOf<MediaTab, Job>()
    fun change(tab: MediaTab, transform: (TabState) -> TabState) { states.value = states.value + (tab to transform(states.value.getValue(tab))) }
    fun url(tab: MediaTab, text: String) { if (queries[tab]?.isActive == true) cancelQuery(tab); change(tab) { it.copy(url = text, info = null, error = "") } }
    fun shared(text: String) { pendingShare.value = extractSharedUrl(text) }
    fun chooseShared(tab: MediaTab) {
        val shared = pendingShare.value ?: return
        selected.value = tab
        url(tab, shared)
        pendingShare.value = null
        query(tab)
    }
    fun query(tab: MediaTab) {
        if (app.downloads.installing.value || queries[tab]?.isActive == true) return
        val url = states.value.getValue(tab).url.trim()
        if (!validMediaUrl(url)) { change(tab) { it.copy(error = "Cole um link completo começando com https://.") }; return }
        change(tab) { it.copy(loading = true, info = null, error = "") }
        app.downloads.querying.value++
        queries[tab] = viewModelScope.launch {
            try {
                val info = app.engine.query(url, "query-${tab.name}-${java.util.UUID.randomUUID()}")
                change(tab) { it.copy(loading = false, info = info, filename = cleanFilename(info.title), height = 0, format = if (tab == MediaTab.MUSIC) "mp3" else if (info.formats.any { f -> f.extension == "mp4" }) "mp4" else "mkv") }
            } catch (e: CancellationException) { change(tab) { it.copy(loading = false) }; throw e }
            catch (e: Exception) { change(tab) { it.copy(loading = false, error = readableError(e)) } }
            finally { app.downloads.querying.value-- }
        }
    }
    fun cancelQuery(tab: MediaTab) { queries.remove(tab)?.cancel(); change(tab) { it.copy(loading = false) } }
    fun download(tab: MediaTab, info: MediaInfo? = null) {
        val state = states.value.getValue(tab)
        val media = info ?: state.info ?: return
        viewModelScope.launch { try { app.downloads.enqueue(media, tab, state.format.ifBlank { if (tab == MediaTab.MUSIC) "mp3" else "mp4" }, state.height, state.bitrate, app.preferences.value.metadata, if (info != null) media.title else state.filename) }
            catch (e: Exception) { change(tab) { it.copy(error = readableError(e)) } } }
    }
}
