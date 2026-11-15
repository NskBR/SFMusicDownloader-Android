package com.nsk.sfmd

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nsk.sfmd.core.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Records external failures without hiding the results for the remaining sources. */
@RunWith(AndroidJUnit4::class)
class PlatformCompatibilityTest {
    @Test fun inspectProvidedPublicSources() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as SfmdApp
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Forneça URLs por argumentos para rodar a matriz externa.", listOf("youtubeUrl", "instagramUrl", "tiktokUrl").any { args.getString(it) != null })
        val report = JSONArray()
        for (platform in listOf("youtube", "instagram", "tiktok")) {
            val url = args.getString("${platform}Url") ?: continue
            val row = JSONObject().put("platform", platform).put("url", url)
            val directory = File(context.cacheDir, "compat-$platform")
            try {
                val info = withTimeout(120_000) { app.engine.query(url, "compat-query-$platform") }
                row.put("query", true).put("title", info.title).put("formats", info.formats.size).put("duration", info.duration)
                val height = info.heights.filter { it <= 360 }.maxOrNull() ?: info.heights.minOrNull() ?: 0
                row.put("selectedHeight", height)
                val task = DownloadTask(id = "compat-download-$platform", url = url, title = info.title, thumbnail = info.thumbnail, tab = MediaTab.VIDEO, format = "mp4", height = height, metadata = false)
                val file = withTimeout(240_000) { app.engine.download(task, directory) {} }
                val metadata = android.media.MediaMetadataRetriever()
                metadata.setDataSource(file.path)
                row.put("hasVideo", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                row.put("hasAudio", metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                metadata.release()
                row.put("download", true).put("bytes", file.length()).put("file", file.name)
            } catch (e: Exception) { row.put("error", readableError(e)); row.put("details", e.message.orEmpty().takeLast(6000)); if (!row.has("query")) row.put("query", false); row.put("download", false) }
            finally { directory.deleteRecursively() }
            report.put(row)
            Log.i("SFMD_COMPAT", row.toString())
            File(context.filesDir, "compatibility-report.json").writeText(report.toString(2))
        }
        check(report.length() > 0) { "Forneça pelo menos um argumento youtubeUrl, instagramUrl ou tiktokUrl." }
    }
}
