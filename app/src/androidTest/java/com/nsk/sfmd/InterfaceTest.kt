package com.nsk.sfmd

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nsk.sfmd.core.MediaTab
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InterfaceTest {
    @get:Rule(order = 0) val permission = androidx.test.rule.GrantPermissionRule.grant(
        *(if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
          else if (android.os.Build.VERSION.SDK_INT <= 28) arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyArray())
    )
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    @Test fun draftsSurviveTabSwitchAndInvalidUrlIsExplained() {
        ui.onNode(hasSetTextAction()).performTextReplacement("link inválido")
        ui.onNodeWithText("Consultar").performClick()
        ui.onNodeWithText("Cole um link completo começando com https://.").assertIsDisplayed()
        ui.onNodeWithText("Música", useUnmergedTree = true).performClick()
        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        ui.onNode(hasSetTextAction()).performTextReplacement("https://example.com/song")
        ui.onNodeWithText("Vídeo", useUnmergedTree = true).performClick()
        ui.onNode(hasSetTextAction()).assertTextEquals("link inválido")
        ui.onNodeWithText("Música", useUnmergedTree = true).performClick()
        ui.onNode(hasSetTextAction()).assertTextEquals("https://example.com/song")
    }
    @Test fun queryCancellationReleasesTheActiveOperation() {
        ui.onNode(hasSetTextAction()).performTextReplacement("https://192.0.2.1/video")
        ui.onNodeWithText("Consultar").performClick()
        ui.onNodeWithText("Cancelar consulta").performClick()
        ui.waitUntil(10_000) { (ui.activity.application as SfmdApp).downloads.querying.value == 0 }
        ui.onNodeWithText("Consultar").assertIsDisplayed()
        assertEquals(0, (ui.activity.application as SfmdApp).downloads.querying.value)
    }
    @Test fun sharedUrlAsksMusicOrVideoBeforeConsulting() {
        val app = ui.activity.application as SfmdApp
        kotlinx.coroutines.runBlocking { app.preferenceStore.permissionSetupDone() }
        val model = androidx.lifecycle.ViewModelProvider(ui.activity)[MainViewModel::class.java]
        ui.runOnUiThread { model.shared("Confira https://192.0.2.1/video") }
        ui.onNodeWithText("Como você quer baixar?").assertIsDisplayed()
        ui.onNodeWithText("Baixar música").performClick()
        ui.waitUntil { model.selected.value == MediaTab.MUSIC }
        ui.onNodeWithText("Consultando link…").assertIsDisplayed()
        ui.onNodeWithText("Cancelar consulta").performClick()
        ui.waitUntil(10_000) { app.downloads.querying.value == 0 }
        ui.onNode(hasSetTextAction()).assertTextEquals("https://192.0.2.1/video")
    }
}
