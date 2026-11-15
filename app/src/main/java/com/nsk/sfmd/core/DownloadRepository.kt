package com.nsk.sfmd.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.nsk.sfmd.DownloadService
import com.nsk.sfmd.data.*
import com.nsk.sfmd.engine.YtDlpEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class DownloadRepository(private val context: Context, val engine: YtDlpEngine, val scope: CoroutineScope, val preferences: StateFlow<Preferences>) {
    private val db = Room.databaseBuilder(context, TaskDatabase::class.java, "sfmd.db").build()
    private val lock = Mutex()
    private val pumpLock = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val tasksState = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = tasksState.asStateFlow()
    val installing = MutableStateFlow(false)
    val querying = MutableStateFlow(0)
    val busy get() = querying.value > 0 || tasks.value.any { it.state.active || it.state == TaskState.QUEUED }
    private val jobs = mutableMapOf<String, Job>()
    private val progressSaved = mutableMapOf<String, Long>()
    init {
        scope.launch {
            try {
                val recovered = withContext(Dispatchers.IO) { db.tasks().all().map { DownloadTask.fromJson(it.payload) } }.map {
                    if (it.state.active || it.state == TaskState.QUEUED) it.copy(state = TaskState.INTERRUPTED, error = "O aplicativo foi interrompido. Toque em tentar novamente para continuar quando a fonte permitir.", speed = 0) else it
                }
                lock.withLock { tasksState.value = recovered; withContext(Dispatchers.IO) { recovered.forEach(::persist) } }
                loaded.complete(Unit)
            } catch (e: Exception) { loaded.completeExceptionally(e) }
        }
    }
    private fun persist(task: DownloadTask) { db.tasks().save(TaskRecord().apply { id = task.id; payload = task.json(); createdAt = task.createdAt }) }
    suspend fun update(task: DownloadTask, save: Boolean = true) = lock.withLock {
        tasksState.value = (tasksState.value.filterNot { it.id == task.id } + task).sortedByDescending { it.createdAt }
        if (save) withContext(Dispatchers.IO) { persist(task) }
    }
    suspend fun enqueue(info: MediaInfo, tab: MediaTab, format: String, height: Int, bitrate: Int, metadata: Boolean, outputName: String = info.title, allowRedownload: Boolean = false) {
        loaded.await()
        lock.withLock {
            check(!installing.value) { "Aguarde o retorno da instalação da atualização." }
            val sameMedia = tasks.value.filter { mediaIdentity(it.url) == mediaIdentity(info.url) && it.tab == tab }
            if (sameMedia.any { it.state.active || it.state == TaskState.QUEUED }) return
            if (!allowRedownload && sameMedia.any { it.state == TaskState.COMPLETED }) throw AlreadyDownloadedException()
            val task = DownloadTask(url = info.url, title = info.title, thumbnail = info.thumbnail, tab = tab, format = format, height = height, bitrate = bitrate, metadata = metadata, destination = preferences.value.folderFor(tab), outputName = cleanFilename(outputName))
            withContext(Dispatchers.IO) { persist(task) }
            tasksState.value = listOf(task) + tasksState.value
        }
        startService()
    }
    private suspend fun startService() {
        try { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        catch (e: Exception) { tasks.value.filter { it.state == TaskState.QUEUED }.forEach { update(it.copy(state = TaskState.INTERRUPTED, error = "Abra o app e tente novamente para iniciar o download.")) }; throw e }
    }
    suspend fun retry(task: DownloadTask) {
        lock.withLock {
            check(!installing.value)
            val current = tasks.value.find { it.id == task.id } ?: return
            if (current.state.active || current.state == TaskState.QUEUED || current.state == TaskState.COMPLETED) return
            val queued = current.copy(state = TaskState.QUEUED, error = "", speed = 0)
            withContext(Dispatchers.IO) { persist(queued) }
            tasksState.value = tasks.value.map { if (it.id == queued.id) queued else it }
        }
        startService()
    }
    suspend fun cancel(id: String) {
        val task = tasks.value.find { it.id == id } ?: return
        if (task.state == TaskState.SAVING || task.state == TaskState.COMPLETED) return
        update(task.copy(state = TaskState.CANCELED, speed = 0))
        engine.cancel(id); jobs[id]?.cancelAndJoin()
        withContext(Dispatchers.IO) { File(context.filesDir, "tasks/$id").deleteRecursively() }
    }
    suspend fun pause(id: String) {
        val task = tasks.value.find { it.id == id } ?: return
        if (task.state !in setOf(TaskState.DOWNLOADING, TaskState.QUEUED)) return
        update(task.copy(state = TaskState.PAUSED, speed = 0, error = "Parciais preservados. A retomada depende da fonte."))
        engine.cancel(id); jobs[id]?.cancel()
    }
    suspend fun delete(task: DownloadTask) {
        if (task.state.active || task.state == TaskState.QUEUED) return
        lock.withLock { withContext(Dispatchers.IO) { db.tasks().delete(task.id) }; tasksState.value = tasks.value.filterNot { it.id == task.id } }
        withContext(Dispatchers.IO) { File(context.filesDir, "tasks/${task.id}").deleteRecursively() }
    }
    /** One pipeline until independent download/conversion scheduling is validated. */
    suspend fun runQueue() = pumpLock.withLock {
        loaded.await()
        while (true) {
            val task = tasks.value.lastOrNull { it.state == TaskState.QUEUED } ?: break
            if (installing.value) break
            val job = scope.launch {
                val directory = File(context.filesDir, "tasks/${task.id}")
                try {
                    update(task.copy(state = TaskState.PREPARING))
                    val file = engine.download(task, directory) { progress -> scope.launch {
                        val current = tasks.value.find { it.id == task.id }
                        if (current?.state?.active == true) {
                            val now = System.currentTimeMillis()
                            val save = now - (progressSaved[task.id] ?: 0) > 1000 || progress.state != current.state
                            if (save) progressSaved[task.id] = now
                            update(progress, save)
                        }
                    } }
                    ensureActive()
                    val current = tasks.value.first { it.id == task.id }
                    if (!current.state.active) return@launch
                    update(current.copy(state = TaskState.SAVING, speed = 0))
                    val size = file.length()
                    val uri = withContext(Dispatchers.IO) { FileStore(context).publish(file, task.tab, task.destination) }
                    update(current.copy(state = TaskState.COMPLETED, uri = uri.toString(), filename = file.name, bytes = size, total = size, progress = 1f, speed = 0, error = ""))
                    withContext(Dispatchers.IO) { directory.deleteRecursively() }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val current = tasks.value.find { it.id == task.id }
                    if (current?.state?.active == true) update(current.copy(state = TaskState.FAILED, speed = 0, error = readableError(e)))
                } finally { jobs.remove(task.id); progressSaved.remove(task.id) }
            }
            jobs[task.id] = job
            job.join()
        }
    }
    suspend fun interruptAll(reason: String) {
        tasks.value.filter { it.state.active || it.state == TaskState.QUEUED }.forEach { task ->
            update(task.copy(state = TaskState.INTERRUPTED, error = reason, speed = 0)); engine.cancel(task.id); jobs[task.id]?.cancel()
        }
    }
}
