package com.nsk.sfmd

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nsk.sfmd.core.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@androidx.test.filters.SdkSuppress(minSdkVersion = 33)
class DownloadFlowTest {
    @get:Rule(order = 0) val permissions = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Test fun completedMusicRequiresConfirmationAndCancelKeepsPreview() {
        val app = ui.activity.application as SfmdApp
        val model = androidx.lifecycle.ViewModelProvider(ui.activity)[MainViewModel::class.java]
        val url = "https://example.com/${UUID.randomUUID()}.mp3"
        val task = DownloadTask(url = url, title = "Teste de duplicidade", thumbnail = "", tab = MediaTab.MUSIC, format = "mp3", state = TaskState.COMPLETED)
        runBlocking { app.preferenceStore.permissionSetupDone(); app.downloads.update(task) }
        try {
            ui.runOnUiThread {
                model.selected.value = MediaTab.MUSIC
                model.change(MediaTab.MUSIC) { it.copy(url = url, info = MediaInfo(url, task.title), filename = task.title, format = "mp3") }
            }
            ui.onNodeWithText("Baixar música").performScrollTo().performClick()
            ui.waitUntil(10_000) { model.pendingRedownload.value != null }
            ui.onNodeWithText("Você já baixou esta música. Deseja baixar novamente?").assertIsDisplayed()
            ui.onNodeWithText("Cancelar", useUnmergedTree = true).performClick()
            ui.onNodeWithText("Baixar música").assertExists()
            ui.onNodeWithText("Baixar música").performScrollTo().performClick()
            ui.waitUntil(10_000) { model.pendingRedownload.value != null }
            ui.onNodeWithText("Baixar novamente").performClick()
            ui.waitUntil(10_000) { model.tabs.value.getValue(MediaTab.MUSIC).info == null }
            org.junit.Assert.assertEquals(2, app.downloads.tasks.value.count { it.url == url })
        } finally { runBlocking {
            app.downloads.tasks.value.filter { it.url == url && it.id != task.id }.forEach { app.downloads.cancel(it.id); app.downloads.delete(it.copy(state = TaskState.CANCELED)) }
            app.downloads.delete(task)
        } }
    }
    @Test fun enqueuingClosesPreviewAndRepeatedClicksCreateOneTask() {
        val app = ui.activity.application as SfmdApp
        val model = androidx.lifecycle.ViewModelProvider(ui.activity)[MainViewModel::class.java]
        val url = "https://192.0.2.1/${UUID.randomUUID()}.mp4"
        ui.runOnUiThread {
            model.change(MediaTab.VIDEO) { it.copy(url = url, info = MediaInfo(url, "Teste de fila"), filename = "Teste de fila", format = "mp4") }
            model.download(MediaTab.VIDEO); model.download(MediaTab.VIDEO)
        }
        try {
            ui.waitUntil(10_000) { model.tabs.value.getValue(MediaTab.VIDEO).info == null }
            org.junit.Assert.assertEquals(1, app.downloads.tasks.value.count { it.url == url })
            ui.onNodeWithText("Baixar vídeo").assertDoesNotExist()
        } finally { runBlocking { app.downloads.tasks.value.filter { it.url == url }.forEach { app.downloads.cancel(it.id); app.downloads.delete(it.copy(state = TaskState.CANCELED)) } } }
    }
}
