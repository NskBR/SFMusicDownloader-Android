package com.nsk.sfmd

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nsk.sfmd.core.*
import com.nsk.sfmd.data.TaskDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledUpgradeTest {
    @Test fun installedUpgradeKeepsCompletedHistory() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("Teste opcional após instalar N+1.", args.getString("verifyInstalledUpgrade") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("0.1.1", installed.versionName)
        val db = Room.databaseBuilder(context, TaskDatabase::class.java, "sfmd.db").build()
        try {
            val tasks = withContext(Dispatchers.IO) { db.tasks().all().map { DownloadTask.fromJson(it.payload) } }
            val complete = tasks.filter { it.state == TaskState.COMPLETED }
            assertTrue(complete.size >= 2)
            assertTrue(complete.all { it.uri.startsWith("content://") && it.bytes > 0 })
            Log.i("SFMD_UPGRADE", "version=${installed.versionName}; completedTasks=${complete.size}; URIs preserved")
        } finally { db.close() }
    }
}
