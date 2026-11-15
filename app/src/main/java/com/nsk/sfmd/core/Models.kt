package com.nsk.sfmd.core

import org.json.JSONObject
import java.net.URI
import java.util.UUID

enum class MediaTab(val label: String) { VIDEO("Vídeo"), MUSIC("Música") }
enum class TaskState(val label: String) {
    QUEUED("Na fila"), PREPARING("Preparando"), DOWNLOADING("Baixando"), PROCESSING("Processando"),
    SAVING("Salvando"), COMPLETED("Concluído"), PAUSED("Pausado"), CANCELED("Cancelado"), INTERRUPTED("Interrompido"), FAILED("Falhou");
    val active get() = this in setOf(PREPARING, DOWNLOADING, PROCESSING, SAVING)
    val terminal get() = this in setOf(COMPLETED, CANCELED, FAILED, INTERRUPTED, PAUSED)
}
data class MediaFormat(val id: String, val extension: String, val height: Int, val fps: Int, val bytes: Long, val video: Boolean, val audio: Boolean)
data class MediaInfo(
    val url: String, val title: String, val author: String = "", val platform: String = "", val duration: Long = 0,
    val thumbnail: String = "", val formats: List<MediaFormat> = emptyList(), val entries: List<MediaInfo> = emptyList()
) {
    val heights get() = formats.filter { it.video && it.height > 0 }.map { it.height }.distinct().sortedDescending()
}
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(), val url: String, val title: String, val thumbnail: String,
    val tab: MediaTab, val format: String, val height: Int = 0, val bitrate: Int = 192, val metadata: Boolean = true,
    val state: TaskState = TaskState.QUEUED, val progress: Float = 0f, val bytes: Long = 0, val total: Long = 0,
    val speed: Long = 0, val eta: Long = -1, val uri: String = "", val filename: String = "", val error: String = "",
    val createdAt: Long = System.currentTimeMillis(), val destination: String = "", val outputName: String = ""
) {
    fun json() = JSONObject().apply {
        put("id", id); put("url", url); put("title", title); put("thumbnail", thumbnail); put("tab", tab.name)
        put("format", format); put("height", height); put("bitrate", bitrate); put("metadata", metadata)
        put("state", state.name); put("progress", progress); put("bytes", bytes); put("total", total); put("speed", speed)
        put("eta", eta); put("uri", uri); put("filename", filename); put("error", error); put("createdAt", createdAt); put("destination", destination); put("outputName", outputName)
    }.toString()
    companion object {
        fun fromJson(s: String): DownloadTask = JSONObject(s).let { j ->
            DownloadTask(j.getString("id"), j.getString("url"), j.getString("title"), j.optString("thumbnail"),
                MediaTab.valueOf(j.getString("tab")), j.getString("format"), j.optInt("height"), j.optInt("bitrate", 192),
                j.optBoolean("metadata", true), TaskState.valueOf(j.getString("state")), j.optDouble("progress", 0.0).toFloat(),
                j.optLong("bytes"), j.optLong("total"), j.optLong("speed"), j.optLong("eta", -1), j.optString("uri"),
                j.optString("filename"), j.optString("error"), j.getLong("createdAt"), j.optString("destination"), j.optString("outputName"))
        }
    }
}
fun validMediaUrl(text: String): Boolean = runCatching {
    val uri = URI(text.trim())
    uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)
fun extractSharedUrl(text: String) = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ']') ?: text.trim()
fun cleanFilename(input: String): String = input
    .replace(Regex("\\.(mp4|mkv|webm|mp3|m4a|opus|ogg|flac|wav)$", RegexOption.IGNORE_CASE), "")
    .replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]+"), "_")
    .take(120).trim(' ', '.').ifBlank { "SFMD" }
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes > 0 -> "%.0f KB".format(bytes / 1024.0)
    else -> "Tamanho desconhecido"
}
fun formatDuration(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
fun readableError(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("DRM", true) -> "Este conteúdo tem proteção DRM e não pode ser baixado."
        raw.contains("Unsupported URL", true) -> "Este link não é compatível com o motor instalado."
        raw.contains("Requested format is not available", true) -> "A qualidade ou o formato selecionado não está disponível. Consulte o link novamente ou use Melhor disponível."
        raw.contains("Sign in", true) || raw.contains("login", true) || raw.contains("private", true) -> "A fonte exige autenticação ou restringiu este conteúdo."
        raw.contains("not available", true) || raw.contains("unavailable", true) -> "Conteúdo indisponível, removido ou bloqueado nesta região."
        raw.contains("No space", true) || raw.contains("espaço", true) -> "Não há espaço suficiente. Libere armazenamento e tente novamente."
        raw.contains("resolve", true) || raw.contains("timed out", true) || raw.contains("Network", true) -> "Não foi possível acessar a fonte. Confira sua conexão e tente novamente."
        else -> raw.lines().lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()?.take(500) ?: raw.takeLast(500).ifBlank { "A operação falhou. Tente novamente." }
    }
}
