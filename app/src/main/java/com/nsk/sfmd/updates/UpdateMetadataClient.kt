package com.nsk.sfmd.updates

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Public release asset first; the anonymous REST API is a compatibility fallback only. */
class UpdateMetadataClient(
    client: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private var cached: Pair<Long, ReleaseMetadata>? = null

    @Synchronized
    fun latest(force: Boolean = false): ReleaseMetadata? {
        cached?.takeIf { !force && now() - it.first in 0 until 5 * 60_000 }?.let { return it.second }
        val manifest = try { read(MANIFEST_URL) } catch (_: IOException) { null }
        // An invalid manifest is an error, never silently accepted via another source.
        val release = if (manifest != null) parse(manifest, manifest = true) else
            read(API_URL)?.let { parse(it, manifest = false) }
        if (release != null) cached = now() to release
        return release
    }

    private fun read(initial: String): String? {
        var url = initial
        repeat(6) {
            val request = Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", "SFMD-updater").build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val next = response.header("Location")?.let { response.request.url.resolve(it) }
                        ?: error("Redirecionamento da atualização inválido.")
                    check(next.isHttps && next.port == 443 && next.username.isBlank() && next.password.isBlank() && UpdateRules.downloadHost(next.host)) {
                        "Destino da consulta de atualização não autorizado."
                    }
                    url = next.toString()
                } else {
                    if (response.code == 404) return null
                    if (response.code == 403 || response.code == 429 || response.code >= 500) {
                        if (initial == MANIFEST_URL) return null
                        error("Não foi possível consultar a atualização pelo arquivo público nem pela API do GitHub. Tente novamente mais tarde.")
                    }
                    check(response.isSuccessful) { "Falha na consulta de atualização (${response.code})." }
                    val body = response.body ?: error("Resposta de atualização vazia.")
                    check(body.contentLength() <= MAX_METADATA_BYTES) { "Arquivo de atualização muito grande." }
                    val bytes = body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= MAX_METADATA_BYTES) { "Arquivo de atualização muito grande." }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    return bytes.toString(Charsets.UTF_8)
                }
            }
        }
        error("Excesso de redirecionamentos na consulta de atualização.")
    }

    companion object {
        const val MANIFEST_URL = "https://github.com/NskBR/SFMusicDownloader-Android/releases/latest/download/update.json"
        const val API_URL = "https://api.github.com/repos/NskBR/SFMusicDownloader-Android/releases/latest"
        private const val MAX_METADATA_BYTES = 256 * 1024L

        fun parse(text: String, manifest: Boolean): ReleaseMetadata {
            val json = JSONObject(text)
            if (manifest) {
                check(json.getInt("schema_version") == 1) { "Formato de update.json incompatível." }
                check(json.getString("package_name") == "com.nsk.sfmd") { "O update.json pertence a outro aplicativo." }
                check(json.getLong("version_code") > 0) { "versionCode inválido no update.json." }
                check(json.has("draft") && json.has("prerelease")) { "Estado da release ausente no update.json." }
            }
            check(json.get("draft") == false && json.get("prerelease") == false) { "A release não é estável." }
            val tag = json.getString("tag_name")
            check(tag.startsWith("v") && UpdateRules.version(tag) != null) { "Versão da release inválida." }
            val version = tag.removePrefix("v")
            val array = json.getJSONArray("assets")
            val offers = (0 until array.length()).mapNotNull { index ->
                val asset = array.getJSONObject(index)
                val name = asset.getString("name")
                val abi = listOf("arm64-v8a", "x86_64").firstOrNull { name == "SFMD_${version}_$it.apk" } ?: return@mapNotNull null
                val digest = asset.getString("digest")
                check(Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) { "SHA-256 ausente ou inválido no APK de atualização." }
                val size = asset.getLong("size")
                check(size in 1..512L * 1024 * 1024) { "Tamanho do APK inválido." }
                val url = asset.getString("browser_download_url")
                check(UpdateRules.validAssetUrl(url, tag, name)) { "A URL do APK não pertence à release esperada." }
                UpdateOffer(version, tag, json.optString("body"), name, url, size, digest.substringAfter(':').lowercase(), abi,
                    if (manifest) json.getLong("version_code") else 0)
            }
            check(offers.isNotEmpty() && offers.map { it.abi }.distinct().size == offers.size) { "Lista de APKs ausente ou duplicada." }
            return ReleaseMetadata(version, offers)
        }
    }
}

data class ReleaseMetadata(val version: String, val offers: List<UpdateOffer>) {
    fun offerFor(installed: String, abi: String): UpdateOffer? {
        if (!UpdateRules.newer(version, installed)) return null
        return offers.firstOrNull { it.abi == abi } ?: error("A versão $version ainda não tem APK para $abi.")
    }
}
