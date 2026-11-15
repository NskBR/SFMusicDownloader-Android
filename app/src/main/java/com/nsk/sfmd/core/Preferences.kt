package com.nsk.sfmd.core

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

private val Context.settings by preferencesDataStore("sfmd")
data class Preferences(val videoFolder: String = "", val musicFolder: String = "", val metadata: Boolean = true, val permissionSetupDone: Boolean = false) {
    fun folderFor(tab: MediaTab) = if (tab == MediaTab.MUSIC) musicFolder else videoFolder
}
class PreferencesStore(private val context: Context) {
    private val legacyFolderKey = stringPreferencesKey("folder")
    private val videoFolderKey = stringPreferencesKey("video_folder")
    private val musicFolderKey = stringPreferencesKey("music_folder")
    private val metadataKey = booleanPreferencesKey("metadata")
    private val permissionKey = booleanPreferencesKey("permission_setup_done")
    val flow = context.settings.data.map { Preferences(it[videoFolderKey] ?: it[legacyFolderKey].orEmpty(), it[musicFolderKey] ?: it[legacyFolderKey].orEmpty(), it[metadataKey] ?: true, it[permissionKey] ?: false) }
    suspend fun folder(tab: MediaTab, uri: String) { context.settings.edit { it[if (tab == MediaTab.MUSIC) musicFolderKey else videoFolderKey] = uri } }
    suspend fun metadata(value: Boolean) { context.settings.edit { it[metadataKey] = value } }
    suspend fun permissionSetupDone() { context.settings.edit { it[permissionKey] = true } }
}
