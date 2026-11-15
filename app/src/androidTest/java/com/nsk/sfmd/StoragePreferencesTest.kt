package com.nsk.sfmd

import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nsk.sfmd.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StoragePreferencesTest {
    @Test fun musicAndVideoFoldersAreIndependentAndPersisted() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SfmdApp
        val original = app.preferenceStore.flow.first()
        try {
            app.preferenceStore.folder(MediaTab.VIDEO, "")
            app.preferenceStore.folder(MediaTab.MUSIC, "content://test/music-folder")
            val music = app.preferenceStore.flow.first()
            assertEquals("", music.videoFolder)
            assertEquals("content://test/music-folder", music.musicFolder)
            app.preferenceStore.folder(MediaTab.VIDEO, "content://test/video-folder")
            val both = app.preferenceStore.flow.first()
            assertEquals("content://test/music-folder", both.musicFolder)
            assertEquals("content://test/video-folder", both.videoFolder)
        } finally {
            app.preferenceStore.folder(MediaTab.VIDEO, original.videoFolder)
            app.preferenceStore.folder(MediaTab.MUSIC, original.musicFolder)
        }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun defaultPublicationUsesDeviceDownloadDirectoryForBothTabs() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (tab in MediaTab.entries) {
            val file = File(context.cacheDir, "sfmd-storage-proof-${tab.name}.txt").apply { writeText("Storage path validation") }
            val uri = FileStore(context).publish(file, tab, "")
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(Environment.DIRECTORY_DOWNLOADS + "/", cursor.getString(0))
                }
                assertEquals(file.readText(), context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            } finally { context.contentResolver.delete(uri, null, null); file.delete() }
        }
    }
}
