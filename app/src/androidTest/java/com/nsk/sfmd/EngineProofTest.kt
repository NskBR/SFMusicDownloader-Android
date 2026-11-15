package com.nsk.sfmd

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nsk.sfmd.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EngineProofTest {
    @Test fun embeddedEngineQueriesDownloadsAndExtractsAudio() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SfmdApp
        val source = InstrumentationRegistry.getArguments().getString("sourceUrl") ?: "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val engine = app.engine
        val diagnostic = engine.diagnostics()
        Log.i("SFMD_PROOF", diagnostic)
        assertTrue(diagnostic.contains("2026.10.26"))
        val media = withTimeout(120_000) { engine.query(source, "proof-query") }
        assertTrue(media.title.isNotBlank())
        Log.i("SFMD_PROOF", "QUERY: ${media.title}; formats=${media.formats.size}; platform=${media.platform}")
        val videoTask = DownloadTask(url = source, title = media.title, thumbnail = media.thumbnail, tab = MediaTab.VIDEO, format = "mp4", height = 360)
        val videoDir = File(context.cacheDir, "proof-video")
        val video = withTimeout(240_000) { engine.download(videoTask, videoDir) {} }
        assertTrue(video.length() > 1000)
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(video.path)
        assertNotNull(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
        assertNotNull(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
        retriever.release()
        val videoUri = FileStore(context).publish(video, MediaTab.VIDEO, "")
        Log.i("SFMD_PROOF", "VIDEO: ${video.name}; bytes=${video.length()}; URI=$videoUri")
        val audioTask = videoTask.copy(id = "proof-audio", tab = MediaTab.MUSIC, format = "mp3", metadata = true)
        val audioDir = File(context.cacheDir, "proof-audio")
        val audio = withTimeout(240_000) { engine.download(audioTask, audioDir) {} }
        assertEquals("mp3", audio.extension)
        assertTrue(audio.length() > 1000)
        val audioRetriever = android.media.MediaMetadataRetriever()
        audioRetriever.setDataSource(audio.path)
        assertEquals("yes", audioRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
        audioRetriever.release()
        val audioUri = FileStore(context).publish(audio, MediaTab.MUSIC, "")
        Log.i("SFMD_PROOF", "AUDIO: ${audio.name}; bytes=${audio.length()}; URI=$audioUri")
        // Keep the two published samples available for manual playback; remove private intermediates.
        videoDir.deleteRecursively(); audioDir.deleteRecursively()
    }
}
