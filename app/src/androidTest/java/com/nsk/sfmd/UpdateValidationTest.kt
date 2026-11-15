package com.nsk.sfmd

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nsk.sfmd.updates.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest

/** Optional local APK fixtures. Build 0.1.1/code 2 in debug and release; copy before running. */
@RunWith(AndroidJUnit4::class)
class UpdateValidationTest {
    @Test fun acceptsMatchingSignerAndRejectsDigestIdentityAndVersionErrors() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue("Este fixture valida atualização a partir de 0.1.0.", context.packageManager.getPackageInfo(context.packageName, 0).versionName == "0.1.0")
        val app = context.applicationContext as SfmdApp
        val fixtures = File(context.filesDir, "fixtures")
        val candidate = File(fixtures, "next-debug.apk")
        val otherSigner = File(fixtures, "next-release.apk")
        val oldVersion = File(fixtures, "current-debug.apk")
        org.junit.Assume.assumeTrue("Prepare os fixtures descritos em docs/VALIDACAO.md.", listOf(candidate, otherSigner, oldVersion).all(File::isFile))
        fun offer(file: File, version: String = "0.1.1"): UpdateOffer {
            val hash = MessageDigest.getInstance("SHA-256"); file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
            return UpdateOffer(version, "v$version", "fixture local", "SFMD_${version}_x86_64.apk", "https://github.com/NskBR/SFMusicDownloader-Android/releases/download/v$version/SFMD_${version}_x86_64.apk", file.length(), hash.digest().joinToString("") { "%02x".format(it) }, "x86_64")
        }
        val method = UpdateRepository::class.java.getDeclaredMethod("verify", File::class.java, UpdateOffer::class.java).apply { isAccessible = true }
        fun verify(file: File, proposed: UpdateOffer) { try { method.invoke(app.updates, file, proposed) } catch (e: InvocationTargetException) { throw e.targetException } }
        fun rejects(file: File, proposed: UpdateOffer, expected: String) {
            val result = runCatching { verify(file, proposed) }
            assertTrue("Deveria rejeitar: $expected", result.isFailure)
            assertTrue("Mensagem recebida: ${result.exceptionOrNull()?.message}", result.exceptionOrNull()?.message.orEmpty().contains(expected, ignoreCase = true))
        }
        val valid = offer(candidate)
        verify(candidate, valid)
        rejects(candidate, valid.copy(sha256 = "0".repeat(64)), "SHA-256")
        rejects(candidate, valid.copy(size = valid.size + 1), "Tamanho")
        rejects(candidate, valid.copy(version = "9.9.9"), "Versão")
        rejects(candidate, valid.copy(abi = "arm64-v8a"), "ABI")
        rejects(otherSigner, offer(otherSigner), "Assinatura")
        rejects(oldVersion, offer(oldVersion, "0.1.0"), "Versão")
        app.downloads.querying.value++
        try { assertTrue(runCatching { app.updates.installationIntent() }.exceptionOrNull()?.message.orEmpty().contains("Aguarde")) }
        finally { app.downloads.querying.value-- }
    }
}
