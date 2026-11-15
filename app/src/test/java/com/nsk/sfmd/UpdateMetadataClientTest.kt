package com.nsk.sfmd

import com.nsk.sfmd.updates.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateMetadataClientTest {
    private fun manifest() = """{
        "schema_version":1,"package_name":"com.nsk.sfmd","version_code":3,
        "tag_name":"v0.1.2","draft":false,"prerelease":false,"body":"Correções",
        "assets":[{"name":"SFMD_0.1.2_arm64-v8a.apk","size":1234,
        "digest":"sha256:${"a".repeat(64)}",
        "browser_download_url":"https://github.com/NskBR/SFMusicDownloader-Android/releases/download/v0.1.2/SFMD_0.1.2_arm64-v8a.apk"}]
    }"""
    private fun http(handler: (Request) -> Pair<Int, String>): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            val (status, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()

    @Test fun manifestWorksWithoutAnyApiRequestAndHasExpiringCache() {
        val requests = mutableListOf<String>(); var time = 0L
        val metadata = UpdateMetadataClient(http { requests += it.url.toString(); 200 to manifest() }) { time }
        val release = metadata.latest()!!
        val offer = release.offerFor("0.1.1", "arm64-v8a")!!
        assertEquals(3L, offer.versionCode)
        assertTrue(UpdateState(UpdatePhase.AVAILABLE, offer).hasUpdate)
        assertNull(release.offerFor("0.1.2", "arm64-v8a"))
        metadata.latest(); assertEquals(listOf(UpdateMetadataClient.MANIFEST_URL), requests)
        metadata.latest(force = true); assertEquals(2, requests.size)
        time += 5 * 60_000; metadata.latest(); assertEquals(3, requests.size)
    }
    @Test fun missingManifestFallsBackToApi() {
        val requests = mutableListOf<String>()
        val metadata = UpdateMetadataClient(http {
            requests += it.url.toString()
            if (it.url.host == "api.github.com") 200 to manifest() else 404 to ""
        })
        assertEquals("0.1.2", metadata.latest()!!.version)
        assertEquals(listOf(UpdateMetadataClient.MANIFEST_URL, UpdateMetadataClient.API_URL), requests)
    }
    @Test fun invalidManifestCannotBeHiddenByApiFallback() {
        var requests = 0
        val metadata = UpdateMetadataClient(http { requests++; 200 to manifest().replace("com.nsk.sfmd", "other.package") })
        assertTrue(runCatching { metadata.latest() }.isFailure)
        assertEquals(1, requests)
    }
    @Test fun rejectsUntrustedApkUrlBadDigestUnstableReleaseAndDuplicateAbi() {
        val original = JSONObject(manifest())
        fun rejects(change: (JSONObject) -> Unit) {
            val json = JSONObject(original.toString()); change(json)
            assertTrue(runCatching { UpdateMetadataClient.parse(json.toString(), true) }.isFailure)
        }
        rejects { it.put("prerelease", true) }
        rejects { it.put("version_code", 0) }
        rejects { it.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:bad") }
        rejects { it.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://attacker.test/app.apk") }
        rejects { it.getJSONArray("assets").put(it.getJSONArray("assets").getJSONObject(0)) }
    }
    @Test fun apiLimitIsNeverReportedAsUpToDate() {
        val metadata = UpdateMetadataClient(http { if (it.url.host == "api.github.com") 403 to "limited" else 404 to "" })
        assertTrue(runCatching { metadata.latest() }.exceptionOrNull()?.message.orEmpty().contains("API"))
    }
    @Test fun boundedResponseAndUntrustedRedirectAreRejected() {
        val large = UpdateMetadataClient(http { 200 to " ".repeat(256 * 1024 + 1) })
        assertTrue(runCatching { large.latest() }.isFailure)
        val redirect = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("redirect")
                .header("Location", "https://attacker.test/update.json").body("".toResponseBody()).build()
        }.build()
        assertTrue(runCatching { UpdateMetadataClient(redirect).latest() }.isFailure)
    }
    @Test fun followsOnlyPublicGithubAssetRedirectsWithoutUsingApi() {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString(); requests += url
            val next = when (requests.size) {
                1 -> "https://github.com/NskBR/SFMusicDownloader-Android/releases/download/v0.1.2/update.json"
                2 -> "https://release-assets.githubusercontent.com/test/update.json?signature=test"
                else -> null
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (next == null) 200 else 302).message("test")
                .apply { if (next != null) header("Location", next) }
                .body((if (next == null) manifest() else "").toResponseBody()).build()
        }.build()
        assertEquals("0.1.2", UpdateMetadataClient(client).latest()!!.version)
        assertEquals(3, requests.size)
        assertFalse(requests.any { it.contains("api.github.com") })
    }
}
