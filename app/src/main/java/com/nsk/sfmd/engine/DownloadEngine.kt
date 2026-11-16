package com.nsk.sfmd.engine

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.nsk.sfmd.core.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_common.utils.ZipUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface DownloadEngine {
    suspend fun initialize(): String
    suspend fun query(url: String, id: String): MediaInfo
    suspend fun download(task: DownloadTask, directory: File, progress: (DownloadTask) -> Unit): File
    fun cancel(id: String)
}

/** Uses the wrapper only to unpack its Android binaries. Owns each process group for cancellation. */
class YtDlpEngine(private val context: Context) : DownloadEngine {
    private val mutex = Mutex()
    private var ready = false
    private val nativeDir get() = File(context.applicationInfo.nativeLibraryDir)
    private val base get() = File(context.noBackupFilesDir, "youtubedl-android/packages")
    private val ffmpegRoot get() = File(context.noBackupFilesDir, "sfmd-ffmpeg-9.0.2")
    private val modules get() = File(context.noBackupFilesDir, "sfmd-engine-2026.10.26-ejs0.8.0")
    private data class Running(val process: Process, @Volatile var group: Int = 0)
    private val running = ConcurrentHashMap<String, Running>()
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "sfmd-engine").apply { isDaemon = true } }
    private data class CachedMedia(val info: MediaInfo, val time: Long, val sourceJson: String?)
    private val infoCache = object : LinkedHashMap<String, CachedMedia>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMedia>?) = size > 16
    }

    override suspend fun initialize(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!ready) {
                YoutubeDL.init(context)
                if (!File(ffmpegRoot, ".complete").exists()) {
                    ffmpegRoot.deleteRecursively(); ffmpegRoot.mkdirs()
                    ZipUtils.unzip(File(nativeDir, "libffmpeg.zip.so"), ffmpegRoot)
                    File(ffmpegRoot, ".complete").writeText("9.0.2")
                }
                if (!File(modules, ".complete").exists()) {
                    modules.deleteRecursively(); modules.mkdirs()
                    for (asset in listOf("yt_dlp-2026.10.26-py3-none-any.whl", "yt_dlp_ejs-0.8.0-py3-none-any.whl")) {
                        ZipInputStream(context.assets.open("engine/$asset")).use { zip ->
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                val target = File(modules, entry.name)
                                check(target.canonicalPath.startsWith(modules.canonicalPath + File.separator))
                                if (entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); target.outputStream().use { zip.copyTo(it) } }
                            }
                        }
                    }
                    File(modules, ".complete").writeText("2026.10.26 / ejs 0.8.0")
                }
                check(listOf("libpython.so", "libqjs.so", "libffmpeg.so", "libffprobe.so").all { File(nativeDir, it).canExecute() }) { "Binários incompletos para esta arquitetura." }
                ready = true
            }
            "yt-dlp 2026.10.26 · Python 3.12 · QuickJS · EJS 0.8.0"
        }
    }

    private suspend fun execute(id: String, args: List<String>, onLine: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val bootstrap = "import os,runpy;os.setsid();print('SFMD_PID:'+str(os.getpid()),flush=True);runpy.run_module('yt_dlp',run_name='__main__')"
            val command = listOf(File(nativeDir, "libpython.so").path, "-u", "-c", bootstrap) + listOf(
                "--ignore-config", "--cache-dir", File(context.cacheDir, "ytdlp").path, "--no-colors", "--socket-timeout", "25", "--retries", "3",
                "--js-runtimes", "quickjs:${File(nativeDir, "libqjs.so")}", "--ffmpeg-location", File(nativeDir, "libffmpeg.so").path
            ) + args
            val builder = ProcessBuilder(command).redirectErrorStream(true)
            builder.environment().apply {
                put("LD_LIBRARY_PATH", "${File(base, "python/usr/lib")}:${File(ffmpegRoot, "usr/lib")}")
                put("PYTHONHOME", File(base, "python/usr").path); put("PYTHONPATH", modules.path)
                put("SSL_CERT_FILE", File(base, "python/usr/etc/tls/cert.pem").path)
                put("HOME", context.filesDir.path); put("TMPDIR", context.cacheDir.path)
                put("PATH", "${System.getenv("PATH")}:${nativeDir.path}")
            }
            val process = try { builder.start() } catch (e: Exception) { continuation.resumeWithException(e); return@suspendCancellableCoroutine }
            val entry = Running(process)
            check(running.putIfAbsent(id, entry) == null) { "Já existe uma execução com este ID." }
            continuation.invokeOnCancellation { cancel(id) }
            workers.execute {
                try {
                    val output = StringBuilder()
                    process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        if (line.startsWith("SFMD_PID:")) {
                            entry.group = line.removePrefix("SFMD_PID:").toInt()
                            if (!continuation.isActive) terminate(entry)
                        } else {
                            if (output.length < 16 * 1024 * 1024) output.appendLine(line)
                            onLine(line)
                        }
                    } }
                    val code = process.waitFor()
                    if (continuation.isActive) {
                        if (code == 0) continuation.resume(output.toString())
                        else continuation.resumeWithException(IllegalStateException(output.toString().takeLast(6000)))
                    }
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                finally { running.remove(id, entry); terminate(entry) }
            }
        }
    }
    private fun terminate(entry: Running) {
        if (entry.group > 0) runCatching { Os.kill(-entry.group, OsConstants.SIGKILL) }
        entry.process.destroy()
    }
    override fun cancel(id: String) { running[id]?.let(::terminate) }

    override suspend fun query(url: String, id: String): MediaInfo {
        require(validMediaUrl(url)) { "Cole uma URL HTTP ou HTTPS válida." }
        val key = mediaIdentity(url)
        synchronized(infoCache) {
            infoCache[key]?.takeIf { android.os.SystemClock.elapsedRealtime() - it.time < 5 * 60_000 }?.let { return it.info }
        }
        initialize()
        val host = android.net.Uri.parse(url).host.orEmpty().lowercase()
        if (host == "open.spotify.com" || host.endsWith(".spotify.com") || host == "music.apple.com" || host.endsWith("deezer.com")) {
            error("Este serviço fornece catálogo ou conteúdo protegido. A busca em outra fonte ainda não está implementada. Cole o link da fonte de áudio ou de um vídeo.")
        }
        val options = mutableListOf("--dump-single-json", "--skip-download", "--flat-playlist", "--playlist-end", "100", "--no-check-formats",
            "--socket-timeout", "15", "--retries", "1", "--extractor-retries", "1")
        if (key.startsWith("youtube:") && !key.startsWith("youtube:playlist:") &&
            (host == "youtu.be" || android.net.Uri.parse(url).path.orEmpty().let { it == "/watch" || it.startsWith("/shorts/") || it.startsWith("/embed/") })) options += "--no-playlist"
        val out = execute(id, options + listOf("--", url))
        val json = out.lineSequence().firstOrNull { it.startsWith("{") } ?: error("A fonte não retornou informações de mídia.")
        val extracted = JSONObject(json)
        if (!validMediaUrl(extracted.optString("webpage_url"))) extracted.put("webpage_url", url)
        return parseMedia(extracted, url).also { info ->
            // Flat playlist entries lack the complete download information.
            val sourceJson = extracted.toString().takeIf {
                info.entries.isEmpty() && info.formats.isNotEmpty() && it.length <= 2 * 1024 * 1024
            }
            val cached = CachedMedia(info, android.os.SystemClock.elapsedRealtime(), sourceJson)
            synchronized(infoCache) {
                infoCache[key] = cached
                infoCache[mediaIdentity(info.url)] = cached
            }
        }
    }
    private fun parseMedia(j: JSONObject, fallback: String): MediaInfo {
        val entries = j.optJSONArray("entries")
        val formats = j.optJSONArray("formats")
        return MediaInfo(j.optString("webpage_url").ifBlank { j.optString("url").takeIf(::validMediaUrl) ?: fallback },
            j.optString("title", "Mídia sem título"), j.optString("uploader").ifBlank { j.optString("artist") },
            j.optString("extractor_key").ifBlank { j.optString("extractor") }, j.optDouble("duration", 0.0).toLong(),
            j.optString("thumbnail").takeUnless { it == "null" }.orEmpty(),
            (0 until (formats?.length() ?: 0)).map { i -> formats!!.getJSONObject(i).let { f ->
                MediaFormat(f.optString("format_id"), f.optString("ext"), f.optInt("height"), f.optDouble("fps").toInt(),
                    f.optLong("filesize", f.optLong("filesize_approx")), f.optString("vcodec", "none") != "none", f.optString("acodec", "none") != "none")
            } }, (0 until (entries?.length() ?: 0)).mapNotNull { entries!!.optJSONObject(it)?.let { e -> parseMedia(e, fallback) } })
    }
    override suspend fun download(task: DownloadTask, directory: File, progress: (DownloadTask) -> Unit): File {
        initialize(); directory.mkdirs()
        check(directory.usableSpace > 100L * 1024 * 1024) { "Libere espaço no armazenamento antes de baixar." }
        // --print enables quiet mode. Request progress explicitly so the app receives transfer metrics.
        val args = mutableListOf("--no-playlist", "--newline", "--continue", "--progress", "--progress-delta", "0.25",
            "--concurrent-fragments", "4", "--windows-filenames", "--trim-filenames", "160",
            "--progress-template", "download:SFMD_PROGRESS:%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s",
            "--print", "post_process:SFMD_STAGE:PROCESSING", "--print", "after_move:SFMD_FILE:%(filepath)s", "-o", File(directory, cleanFilename(task.outputName.ifBlank { task.title }).replace("%", "%%") + ".%(ext)s").path)
        // Replace metadata before postprocessing; argument lists preserve spaces and accents.
        args += listOf("--replace-in-metadata", "title", "(?s)^.*$", task.outputName.ifBlank { task.title }.replace("\\", "\\\\"), "--parse-metadata", "title:%(meta_title)s", "--embed-metadata")
        if (task.tab == MediaTab.MUSIC) {
            args += listOf("-f", "bestaudio/best")
            if (task.format != "original") {
                args += listOf("-x", "--audio-format", task.format)
                if (task.format == "mp3" || task.format == "m4a" || task.format == "opus") args += listOf("--audio-quality", "${task.bitrate}K")
            }
            if (task.metadata) {
                if (task.format != "original") args += "--embed-thumbnail"
            }
        } else {
            val height = if (task.height > 0) "[height<=${task.height}]" else ""
            val selector = if (task.format == "mp4") "bv*$height[ext=mp4]+ba[ext=m4a]/b$height[ext=mp4]/b$height" else "bv*$height+ba/b$height"
            args += listOf("-f", selector, "--merge-output-format", task.format, "--remux-video", task.format)
        }
        // Direct URLs can expire. Reuse only a recent, complete consultation.
        val cached = synchronized(infoCache) {
            infoCache[mediaIdentity(task.url)]?.takeIf {
                android.os.SystemClock.elapsedRealtime() - it.time in 0 until 2 * 60_000L
            }
        }
        val sourceJson = cached?.sourceJson
        val infoFile = File(directory, "source.info.json")
        var result: File? = null
        var latest = task.copy(state = TaskState.PREPARING, bytes = 0, total = 0, speed = 0, eta = -1, progress = 0f)
        try {
            if (sourceJson != null) {
                withContext(Dispatchers.IO) { infoFile.writeText(sourceJson, Charsets.UTF_8) }
                args += listOf("--abort-on-error", "--load-info-json", infoFile.path)
            } else {
                args += listOf("--", task.url)
            }
            execute(task.id, args) { line ->
                when {
                    line.startsWith("SFMD_PROGRESS:") -> {
                        val numbers = line.removePrefix("SFMD_PROGRESS:").split('|').map { it.toDoubleOrNull()?.toLong() ?: 0L }
                        if (numbers.size == 5) {
                            val total = numbers[1].takeIf { it > 0 } ?: numbers[2]
                            latest = latest.copy(state = TaskState.DOWNLOADING, bytes = numbers[0], total = total, speed = numbers[3], eta = numbers[4], progress = if (total > 0) (numbers[0].toFloat() / total).coerceIn(0f, 1f) else 0f)
                            progress(latest)
                        }
                    }
                    line == "SFMD_STAGE:PROCESSING" || line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") || line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]") -> {
                        latest = latest.copy(state = TaskState.PROCESSING, speed = 0); progress(latest)
                    }
                    // yt-dlp retries the webpage itself if saved information has expired.
                    sourceJson != null && line.startsWith("WARNING: The info failed to download:") -> {
                        synchronized(infoCache) { infoCache.entries.removeAll { it.value === cached } }
                        latest = task.copy(state = TaskState.PREPARING, bytes = 0, total = 0, speed = 0, eta = -1, progress = 0f)
                        progress(latest)
                    }
                    line.startsWith("SFMD_FILE:") -> result = File(line.removePrefix("SFMD_FILE:").trim())
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { infoFile.delete() }
        }
        return result?.takeIf { it.isFile && it.length() > 0 && it.canonicalPath.startsWith(directory.canonicalPath + File.separator) }
            ?: error("O motor terminou sem produzir um arquivo válido.")
    }
    suspend fun diagnostics(): String {
        initialize()
        val py = execute("diagnostics", listOf("--version")).trim()
        return withContext(Dispatchers.IO) {
            fun nativeVersion(binary: String, args: List<String>): String {
                val builder = ProcessBuilder(listOf(File(nativeDir, binary).path) + args).redirectErrorStream(true)
                builder.environment().apply {
                    put("LD_LIBRARY_PATH", "${File(base, "python/usr/lib")}:${File(ffmpegRoot, "usr/lib")}")
                    put("PYTHONHOME", File(base, "python/usr").path)
                }
                val process = builder.start()
                return try {
                    val output = process.inputStream.bufferedReader().readText()
                    val status = process.waitFor()
                    check(status == 0 || (binary == "libqjs.so" && status == 1)) { output.takeLast(2000) }
                    output.lineSequence().firstOrNull { line -> line.startsWith("ffmpeg version") || line.startsWith("ffprobe version") || line.startsWith("QuickJS version") || line.startsWith("Python ") } ?: output.lineSequence().firstOrNull().orEmpty()
                }
                finally { process.destroy() }
            }
            val python = nativeVersion("libpython.so", listOf("--version"))
            val qjs = nativeVersion("libqjs.so", listOf("--help"))
            val ffmpeg = nativeVersion("libffmpeg.so", listOf("-version"))
            val ffprobe = nativeVersion("libffprobe.so", listOf("-version"))
            "$py\n$python\n$qjs\nEJS 0.8.0 incluído\n$ffmpeg\n$ffprobe\nABI: ${android.os.Build.SUPPORTED_ABIS.first()}"
        }
    }
}
