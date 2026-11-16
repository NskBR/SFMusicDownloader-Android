package com.nsk.sfmd

import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nsk.sfmd.core.*
import com.nsk.sfmd.engine.YtDlpEngine
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class PreparationReuseTest {
    @Test fun reuseStartsTransferWithoutAnotherExtractionAndExpiredLinksRecover() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as SfmdApp).engine.initialize()
        val source = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val root = File(context.cacheDir, "preparation-proof-${UUID.randomUUID()}")
        val task = DownloadTask(url = source, title = "Preparation proof", thumbnail = "", tab = MediaTab.VIDEO, format = "mp4", height = 360)

        suspend fun measured(engine: YtDlpEngine, request: DownloadTask, label: String, expectedReuse: Boolean): File {
            val directory = File(root, label)
            val start = SystemClock.elapsedRealtime()
            val firstByte = AtomicLong(-1)
            val refreshes = AtomicInteger(0)
            val file = withTimeout(240_000) {
                engine.download(request, directory) { progress ->
                    if (progress.state == TaskState.PREPARING) refreshes.incrementAndGet()
                    if (progress.bytes > 0 && firstByte.compareAndSet(-1, SystemClock.elapsedRealtime() - start)) {
                        assertEquals("Reutilização da consulta", expectedReuse, File(directory, "source.info.json").exists())
                    }
                }
            }
            assertTrue(file.length() > 1000)
            assertTrue(firstByte.get() >= 0)
            assertFalse("Limpeza dos links temporários", File(directory, "source.info.json").exists())
            if (label == "reused" || label == "audio") assertEquals(0, refreshes.get())
            if (label == "expired") assertTrue("Renovação automática", refreshes.get() > 0)
            Log.i("SFMD_PREPARATION", "$label: first_bytes_ms=${firstByte.get()}, refreshes=${refreshes.get()}")
            return file
        }
        fun videoHeight(file: File): Int = MediaMetadataRetriever().let { reader ->
            try {
                reader.setDataSource(file.path)
                assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            } finally { reader.release() }
        }

        try {
            val baseline = measured(YtDlpEngine(context), task, "baseline", false)
            val engine = YtDlpEngine(context)
            engine.query(source, "preparation-query")
            val reused = measured(engine, task.copy(id = "reuse-video"), "reused", true)
            assertEquals(videoHeight(baseline), videoHeight(reused))
            assertTrue(videoHeight(reused) <= 360)

            val title = "Minha música mais rápida"
            val audio = measured(engine, task.copy(id = "reuse-audio", tab = MediaTab.MUSIC, format = "mp3", metadata = false, outputName = title), "audio", true)
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(audio.path)
                assertEquals(title, reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            } finally { reader.release() }

            // Expire the cached media URLs while retaining the real source page.
            @Suppress("UNCHECKED_CAST")
            val cache = YtDlpEngine::class.java.getDeclaredField("infoCache").apply { isAccessible = true }.get(engine) as MutableMap<String, Any>
            val cached = cache.getValue(mediaIdentity(source))
            val jsonField = cached.javaClass.getDeclaredField("sourceJson").apply { isAccessible = true }
            val json = JSONObject(jsonField.get(cached) as String)
            val server = ServerSocket(0)
            val requests = AtomicInteger(0)
            val worker = thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    socket.use {
                        requests.incrementAndGet()
                        it.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                }
            }
            try {
                val expired = "http://127.0.0.1:${server.localPort}/expired.mp4"
                json.put("url", expired)
                val formats = json.getJSONArray("formats")
                for (index in 0 until formats.length()) formats.getJSONObject(index).put("url", expired)
                jsonField.set(cached, json.toString())
                val recovered = measured(engine, task.copy(id = "reuse-expired"), "expired", true)
                assertTrue(videoHeight(recovered) <= 360)
                assertTrue(requests.get() > 0)
                assertFalse("Cache expirado invalidado", cache.containsKey(mediaIdentity(source)))
            } finally { server.close(); worker.join(2000) }
        } finally { root.deleteRecursively() }
    }
}
