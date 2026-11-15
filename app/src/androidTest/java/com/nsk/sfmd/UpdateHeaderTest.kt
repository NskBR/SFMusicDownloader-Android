package com.nsk.sfmd

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nsk.sfmd.updates.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.io.File

@androidx.test.filters.SdkSuppress(minSdkVersion = 33)
class UpdateHeaderTest {
    @get:Rule(order = 0) val permission = androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()

    @Test fun availableUpdateAppearsBesideAppNameAndOpensSettings() {
        val app = ui.activity.application as SfmdApp
        ui.waitUntil(45_000) { app.updates.state.value.phase !in setOf(UpdatePhase.IDLE, UpdatePhase.CHECKING, UpdatePhase.VERIFYING) }
        org.junit.Assert.assertEquals("Consulta real do manifesto público", UpdatePhase.CURRENT, app.updates.state.value.phase)
        @Suppress("UNCHECKED_CAST")
        val state = UpdateRepository::class.java.getDeclaredField("mutable").apply { isAccessible = true }.get(app.updates) as MutableStateFlow<UpdateState>
        val original = state.value
        try {
            ui.onNodeWithText("Atualização disponível").assertDoesNotExist()
            val offer = UpdateOffer("0.1.3", "v0.1.3", "Oferta apenas para teste visual", "SFMD_0.1.3_x86_64.apk",
                "https://github.com/NskBR/SFMusicDownloader-Android/releases/download/v0.1.3/SFMD_0.1.3_x86_64.apk", 100, "a".repeat(64), "x86_64", 4)
            ui.runOnUiThread { state.value = UpdateState(UpdatePhase.AVAILABLE, offer) }
            ui.onNodeWithText("SFMD").assertIsDisplayed()
            ui.onNodeWithText("Atualização disponível").assertIsDisplayed()
            val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
            File(app.externalCacheDir, "update-header-qa.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            ui.onNodeWithText("Atualização disponível").performClick()
            ui.onNodeWithText("Configurações").assertIsDisplayed()
        } finally { ui.runOnUiThread { state.value = original } }
    }
}
