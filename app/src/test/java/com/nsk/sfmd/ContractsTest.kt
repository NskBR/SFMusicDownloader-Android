package com.nsk.sfmd

import com.nsk.sfmd.core.*
import com.nsk.sfmd.updates.UpdateRules
import org.junit.Assert.*
import org.junit.Test

class ContractsTest {
    @Test fun versionsCompareNumericallyAndRejectPrereleases() {
        assertTrue(UpdateRules.newer("v1.10.0", "1.9.9"))
        assertFalse(UpdateRules.newer("v1.0.0", "1.0.0"))
        assertFalse(UpdateRules.newer("v1.2.0-beta", "1.0.0"))
        assertFalse(UpdateRules.newer("v01.2.0", "1.0.0"))
        assertFalse(UpdateRules.newer("v99999999999999999999.0.0", "1.0.0"))
    }
    @Test fun updaterRejectsDifferentRepositoryAndUntrustedHosts() {
        val path = "https://github.com/NskBR/SFMusicDownloader-Android/releases/download/v1.0.0/SFMD_1.0.0_arm64-v8a.apk"
        assertTrue(UpdateRules.validAssetUrl(path, "v1.0.0", "SFMD_1.0.0_arm64-v8a.apk"))
        assertFalse(UpdateRules.validAssetUrl(path.replace("NskBR", "attacker"), "v1.0.0", "SFMD_1.0.0_arm64-v8a.apk"))
        assertFalse(UpdateRules.validAssetUrl(path.replace("https:", "http:"), "v1.0.0", "SFMD_1.0.0_arm64-v8a.apk"))
        assertFalse(UpdateRules.downloadHost("github.com.attacker.test"))
    }
    @Test fun mediaInputAcceptsOnlyHttpUrls() {
        assertTrue(validMediaUrl("https://youtu.be/jNQXAC9IVRw"))
        assertFalse(validMediaUrl("file:///data/user/0/secret"))
        assertFalse(validMediaUrl("https://"))
        assertFalse(validMediaUrl("https://user:pass@example.com"))
        assertEquals("https://example.com/a", extractSharedUrl("Confira https://example.com/a"))
    }
    @Test fun outputNamesCannotEscapeTheTaskDirectory() {
        assertEquals("_test_song", cleanFilename("..\\test/song.mp3"))
        assertEquals("SFMD", cleanFilename("..."))
        assertEquals("Minha música", cleanFilename("Minha música.mp3"))
        assertFalse(cleanFilename("a/b:c").contains('/'))
    }
}
