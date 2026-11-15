package com.nsk.sfmd.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

class FileStore(private val context: Context) {
    /** Incomplete files stay private; only a successful copy is published. */
    fun publish(file: File, tab: MediaTab, tree: String): Uri {
        val mime = when (file.extension.lowercase()) { "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "opus", "ogg" -> "audio/ogg"; "mkv" -> "video/x-matroska"; "webm" -> if (tab == MediaTab.MUSIC) "audio/webm" else "video/webm"; else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream" }
        val resolver = context.contentResolver
        if (tree.isNotBlank()) {
            val root = Uri.parse(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
            val uri = DocumentsContract.createDocument(resolver, parent, mime, file.name) ?: error("A pasta escolhida não permite criar arquivos.")
            try { resolver.openOutputStream(uri, "w")!!.use { out -> file.inputStream().use { it.copyTo(out) } }; return uri }
            catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, uri) }; throw e }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("Não foi possível criar o arquivo de destino.")
            try {
                resolver.openOutputStream(uri, "w")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
        }
        check(context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) { "Autorize o acesso à pasta Download ou escolha outra pasta nas configurações." }
        @Suppress("DEPRECATION") val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
        val temporary = File(downloads, ".sfmd-${java.util.UUID.randomUUID()}.part")
        var target: File? = null
        try {
            file.inputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            for (index in 0..10000) {
                val name = if (index == 0) file.name else "${file.nameWithoutExtension} ($index).${file.extension}"
                val candidate = File(downloads, name)
                if (candidate.createNewFile()) { target = candidate; break }
            }
            val result = target ?: error("Não foi possível escolher um nome de arquivo disponível.")
            check(temporary.renameTo(result)) { "Não foi possível finalizar o arquivo na pasta Download." }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(result.path), arrayOf(mime), null)
            return FileProvider.getUriForFile(context, "${context.packageName}.files", result)
        } catch (e: Exception) { target?.delete(); throw e }
        finally { temporary.delete() }
    }
}
