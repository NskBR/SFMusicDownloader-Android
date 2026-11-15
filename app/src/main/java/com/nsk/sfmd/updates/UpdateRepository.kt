package com.nsk.sfmd.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.nsk.sfmd.DownloadService
import com.nsk.sfmd.BuildConfig
import com.nsk.sfmd.core.DownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class UpdateOffer(val version: String, val tag: String, val notes: String, val name: String, val url: String, val size: Long, val sha256: String, val abi: String, val versionCode: Long = 0)
enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, VERIFYING, READY, AUTHORIZATION, ERROR }
data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val offer: UpdateOffer? = null, val progress: Float = 0f, val message: String = "", val lastChecked: Long = 0)
val UpdateState.hasUpdate: Boolean get() = offer != null

object UpdateRules {
    fun version(value: String): List<Int>? = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$").matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
    fun newer(candidate: String, installed: String): Boolean {
        val c = version(candidate) ?: return false; val i = version(installed) ?: return false
        for (index in 0..2) if (c[index] != i[index]) return c[index] > i[index]
        return false
    }
    fun validAssetUrl(url: String, tag: String, name: String): Boolean {
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return parsed.scheme == "https" && parsed.host == "github.com" && parsed.userInfo == null && parsed.port == -1 && parsed.rawQuery == null && parsed.fragment == null &&
            parsed.path == "/NskBR/SFMusicDownloader-Android/releases/download/$tag/$name"
    }
    fun downloadHost(host: String) = host == "github.com" || host == "release-assets.githubusercontent.com" || host == "objects.githubusercontent.com" || host == "github-releases.githubusercontent.com"
}

class UpdateRepository(private val context: Context, private val downloads: DownloadRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val checkLock = Mutex()
    private var downloadJob: Job? = null
    @Volatile private var activeCall: okhttp3.Call? = null
    private var startupChecked = false
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(30, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()
    private val metadata = UpdateMetadataClient(client.newBuilder().callTimeout(30, TimeUnit.SECONDS).build())
    private val folder get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private val apk get() = File(folder, "sfmd-update.apk")
    private val partial get() = File(folder, "sfmd-update.apk.part")
    private val offerFile get() = File(folder, "offer.json")
    private val maxBytes = 512L * 1024 * 1024

    fun startup() {
        if (startupChecked) return
        startupChecked = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { readOffer() }.getOrNull() }
            if (saved != null && apk.isFile) {
                mutable.value = UpdateState(UpdatePhase.VERIFYING, saved)
                runCatching { withContext(Dispatchers.IO) { verify(apk, saved) } }.onSuccess {
                    mutable.value = UpdateState(UpdatePhase.READY, saved, message = "Atualização validada. Pronta para instalar.")
                }.onFailure { withContext(Dispatchers.IO) { apk.delete(); offerFile.delete() }; check() }
            } else check()
        }
    }
    suspend fun check() = performCheck(force = false)
    suspend fun checkNow() = performCheck(force = true)
    private suspend fun performCheck(force: Boolean) {
        if (!checkLock.tryLock()) return
        try {
            if (state.value.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING, UpdatePhase.READY, UpdatePhase.AUTHORIZATION)) return
            mutable.value = state.value.copy(phase = UpdatePhase.CHECKING, message = "Verificando atualizações…")
            val offer = withContext(Dispatchers.IO) {
                val installedAbi = context.applicationInfo.nativeLibraryDir.let { if (it.contains("x86_64")) "x86_64" else "arm64-v8a" }
                check(installedAbi in Build.SUPPORTED_ABIS) { "A arquitetura instalada não é compatível com este dispositivo." }
                metadata.latest(force)?.offerFor(BuildConfig.VERSION_NAME, installedAbi)
            }
            mutable.value = UpdateState(if (offer != null) UpdatePhase.AVAILABLE else UpdatePhase.CURRENT, offer, message = if (offer == null) "Nenhuma versão mais recente disponível para a versão ${BuildConfig.VERSION_NAME}." else "Uma nova versão está disponível.", lastChecked = System.currentTimeMillis())
        } catch (e: Exception) { if (e is CancellationException) throw e; mutable.value = state.value.copy(phase = UpdatePhase.ERROR, message = e.message ?: "Falha ao consultar atualizações.") }
        finally { checkLock.unlock() }
    }
    fun download() {
        if (downloadJob?.isActive == true || state.value.phase != UpdatePhase.AVAILABLE) return
        val offer = state.value.offer ?: return
        downloadJob = scope.launch {
            mutable.value = state.value.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, message = "Baixando atualização…")
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction("SFMD_UPDATE"))
                withContext(Dispatchers.IO) {
                    check(UpdateRules.validAssetUrl(offer.url, offer.tag, offer.name))
                    check(folder.usableSpace > offer.size * 2) { "Libere espaço antes de baixar a atualização." }
                    var url = offer.url
                    var downloaded = false
                    for (redirect in 0..5) {
                        ensureActive()
                        val request = Request.Builder().url(url).header("User-Agent", "SFMD/${BuildConfig.VERSION_NAME}").build()
                        val call = client.newCall(request)
                        activeCall = call
                        val cancelHook = currentCoroutineContext()[Job]!!.invokeOnCompletion { if (it != null) call.cancel() }
                        try {
                            call.execute().use { response ->
                                if (response.isRedirect) {
                                    val next = response.header("Location")?.let { response.request.url.resolve(it) } ?: error("Redirecionamento inválido.")
                                    check(next.isHttps && UpdateRules.downloadHost(next.host) && next.username.isBlank() && next.password.isBlank()) { "Destino de download não autorizado." }
                                    url = next.toString()
                                } else {
                                    check(response.isSuccessful) { "Falha no download (${response.code})." }
                                    val body = response.body ?: error("APK vazio.")
                                    val length = body.contentLength()
                                    check(length == -1L || length == offer.size) { "O tamanho anunciado do APK mudou." }
                                    val digest = MessageDigest.getInstance("SHA-256")
                                    var bytes = 0L; var last = 0L
                                    body.byteStream().use { input -> partial.outputStream().use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        while (true) {
                                            ensureActive(); val read = input.read(buffer); if (read < 0) break
                                            bytes += read; check(bytes <= offer.size && bytes <= maxBytes) { "APK maior que o tamanho publicado." }
                                            digest.update(buffer, 0, read); output.write(buffer, 0, read)
                                            if (System.currentTimeMillis() - last > 250) { last = System.currentTimeMillis(); mutable.value = state.value.copy(progress = bytes.toFloat() / offer.size) }
                                        }
                                    } }
                                    check(bytes == offer.size && hex(digest.digest()) == offer.sha256) { "O APK não corresponde ao SHA-256 publicado." }
                                    downloaded = true
                                }
                            }
                        } finally { cancelHook.dispose(); activeCall = null }
                        if (downloaded) break
                    }
                    check(downloaded) { "Excesso de redirecionamentos." }
                    mutable.value = state.value.copy(phase = UpdatePhase.VERIFYING, message = "Verificando pacote e assinatura…")
                    verify(partial, offer)
                    if (apk.exists()) check(apk.delete())
                    check(partial.renameTo(apk)) { "Não foi possível finalizar o APK." }
                    offerFile.writeText(JSONObject().apply { put("version", offer.version); put("tag", offer.tag); put("notes", offer.notes); put("name", offer.name); put("url", offer.url); put("size", offer.size); put("sha256", offer.sha256); put("abi", offer.abi); put("versionCode", offer.versionCode) }.toString())
                }
                mutable.value = state.value.copy(phase = UpdatePhase.READY, progress = 1f, message = "Integridade e assinatura verificadas.")
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) { partial.delete() }
                mutable.value = state.value.copy(phase = if (e is CancellationException) UpdatePhase.AVAILABLE else UpdatePhase.ERROR, message = if (e is CancellationException) "Download cancelado." else e.message.orEmpty())
            }
        }
    }
    fun cancel() { downloadJob?.cancel(); activeCall?.cancel() }
    suspend fun awaitDownload() { downloadJob?.join() }
    @Suppress("DEPRECATION")
    private fun verify(file: File, offer: UpdateOffer) {
        check(file.length() == offer.size) { "Tamanho inválido." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) } }
        check(hex(digest.digest()) == offer.sha256) { "SHA-256 inválido." }
        val flag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flag)
        val candidate = context.packageManager.getPackageArchiveInfo(file.path, flag) ?: error("O arquivo não é um APK válido.")
        check(candidate.packageName == context.packageName) { "O APK pertence a outro aplicativo." }
        check(candidate.versionName == offer.version && UpdateRules.newer(offer.version, BuildConfig.VERSION_NAME)) { "Versão do APK incorreta." }
        val code = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val current = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        check(code > current) { "O versionCode não é superior ao instalado." }
        check(offer.versionCode == 0L || code == offer.versionCode) { "O versionCode do APK difere do update.json." }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet().orEmpty()
        val original = signatures(installed)
        check(original.isNotEmpty() && original == signatures(candidate)) { "Assinatura diferente. A instalação atual foi preservada." }
        ZipFile(file).use { zip -> check(zip.getEntry("lib/${offer.abi}/libpython.so") != null && offer.abi in Build.SUPPORTED_ABIS) { "ABI incompatível ou motor ausente." } }
    }
    private fun readOffer(): UpdateOffer? {
        if (!offerFile.exists()) return null
        val j = JSONObject(offerFile.readText())
        return UpdateOffer(j.getString("version"), j.getString("tag"), j.optString("notes"), j.getString("name"), j.getString("url"), j.getLong("size"), j.getString("sha256"), j.getString("abi"), j.optLong("versionCode")).also { check(UpdateRules.validAssetUrl(it.url, it.tag, it.name)) }
    }
    suspend fun installationIntent(): Intent? {
        check(!downloads.busy) { "Aguarde as consultas, downloads e conversões terminarem antes de instalar." }
        val offer = state.value.offer ?: error("Não há atualização validada.")
        check(state.value.phase in setOf(UpdatePhase.READY, UpdatePhase.AUTHORIZATION))
        downloads.installing.value = true
        try {
            withContext(Dispatchers.IO) { verify(apk, offer) }
            check(!downloads.busy) { "Uma tarefa começou. Aguarde a conclusão." }
            if (!context.packageManager.canRequestPackageInstalls()) {
                mutable.value = state.value.copy(phase = UpdatePhase.AUTHORIZATION, message = "Autorize o SFMD como fonte de instalação e depois toque novamente em Instalar atualização.")
                return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { downloads.installing.value = false; throw e }
    }
    fun returnedFromInstaller() { downloads.installing.value = false }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
