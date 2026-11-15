@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nsk.sfmd

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nsk.sfmd.core.*
import com.nsk.sfmd.updates.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private val Red = Color(0xFFFF2346)
private val Background = Color(0xFF100F12)
private val Panel = Color(0xFF201E22)
private val Muted = Color(0xFFAAA5AF)
private val Stroke = Color(0xFF39353D)
private val Green = Color(0xFF27D86B)
private fun folderLabel(value: String): String = runCatching {
    val id = android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(value))
    val volume = id.substringBefore(':')
    val relative = id.substringAfter(':', "")
    (if (volume == "primary") "Armazenamento interno" else volume) + if (relative.isNotBlank()) " / $relative" else ""
}.getOrDefault("Pasta escolhida")

class MainActivity : ComponentActivity() {
    private var vm: MainViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val model: MainViewModel = viewModel()
            vm = model
            LaunchedEffect(Unit) { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(model::shared); intent.removeExtra(Intent.EXTRA_TEXT); model.app.updates.startup() }
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, onPrimary = Color.White, background = Background, onBackground = Color.White, surface = Panel, onSurface = Color.White, secondary = Red, outline = Stroke)) { SfmdScreen(model) }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { vm?.shared(it) }; intent.removeExtra(Intent.EXTRA_TEXT) }
    override fun onResume() { super.onResume(); (application as SfmdApp).updates.returnedFromInstaller() }
}

@Composable private fun SfmdScreen(vm: MainViewModel) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val states by vm.tabs.collectAsStateWithLifecycle()
    val tasks by vm.app.downloads.tasks.collectAsStateWithLifecycle()
    val preferences by vm.app.preferences.collectAsStateWithLifecycle()
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()
    val pendingRedownload by vm.pendingRedownload.collectAsStateWithLifecycle()
    val update by vm.app.updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { vm.app.preferenceStore.permissionSetupDone() }
    }
    LaunchedEffect(Unit) {
        val stored = vm.app.preferenceStore.flow.first()
        if (!stored.permissionSetupDone) {
            val required = buildList {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (required.isNotEmpty()) firstPermissions.launch(required.toTypedArray())
            else vm.app.preferenceStore.permissionSetupDone()
        }
    }
    var settings by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var playlist by remember { mutableStateOf<MediaInfo?>(null) }
    var pendingLegacy by remember { mutableStateOf<List<Pair<MediaTab, MediaInfo?>>>(emptyList()) }
    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingLegacy
        pendingLegacy = emptyList()
        if (granted) pending.forEach { vm.download(it.first, it.second) }
        else if (!granted) Toast.makeText(context, "Escolha uma pasta nas configurações ou autorize o acesso à pasta Download.", Toast.LENGTH_LONG).show()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun beginDownload(info: MediaInfo? = null) {
        if (Build.VERSION.SDK_INT <= 28 && preferences.folderFor(selected).isBlank() && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingLegacy = pendingLegacy + (selected to info)
            if (pendingLegacy.size == 1) legacyStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.download(selected, info)
    }
    BackHandler(settings || history) { settings = false; history = false }
    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().widthIn(max = 680.dp).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settings || history) IconButton(onClick = { settings = false; history = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
                else Image(painterResource(R.drawable.sf_logo), "Logo SFMD", Modifier.size(49.dp))
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(if (settings) "Configurações" else if (history) "Downloads" else "SFMD", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    if (!settings && !history) Text("SFMusicDownloader", color = Muted, fontSize = 11.sp)
                }
                if (!settings) IconButton(onClick = { settings = true }) { BadgedBox(badge = { if (update.offer != null) Badge(containerColor = Red) }) { Icon(Icons.Outlined.Settings, "Configurações", Modifier.size(26.dp)) } }
            }
            if (settings) { SettingsScreen(vm); return@Column }
            SecondaryTabRow(selectedTabIndex = selected.ordinal, containerColor = Background, contentColor = Red, divider = { HorizontalDivider(color = Stroke) }) {
                MediaTab.entries.forEach { tab ->
                    Tab(selected = selected == tab, onClick = { vm.selected.value = tab }, selectedContentColor = Red, unselectedContentColor = Muted) {
                        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(if (tab == MediaTab.VIDEO) Icons.Outlined.PlayArrow else Icons.Outlined.MusicNote, null, Modifier.size(25.dp))
                            Text(tab.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            val state = states.getValue(selected)
            val filtered = tasks.filter { it.tab == selected }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!history) {
                    item {
                        UrlForm(state, selected, vm::url, { vm.query(selected) }, { vm.cancelQuery(selected) })
                    }
                    if (update.phase == UpdatePhase.AVAILABLE || update.phase == UpdatePhase.READY) item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Red.copy(alpha = .09f)).clickable { settings = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SystemUpdate, null, tint = Red, modifier = Modifier.size(20.dp)); Text("SFMD ${update.offer?.version} disponível", Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp); Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                    state.info?.let { info -> item {
                        ResultCard(info, selected, state, preferences.metadata,
                            onFormat = { value -> vm.change(selected) { it.copy(format = value) } },
                            onHeight = { value -> vm.change(selected) { it.copy(height = value) } },
                            onBitrate = { value -> vm.change(selected) { it.copy(bitrate = value) } },
                            onName = { value -> vm.change(selected) { it.copy(filename = value) } },
                            onMetadata = { scope.launch { vm.app.preferenceStore.metadata(it) } },
                            onDownload = { if (info.entries.isNotEmpty()) playlist = info else beginDownload() })
                    } }
                    if (state.info == null) item { EmptyConsult(selected, state.loading) }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(24.dp))
                        Text("Downloads", Modifier.weight(1f).padding(start = 9.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        if (!history && filtered.isNotEmpty()) TextButton(onClick = { history = true }) { Text("Ver todos", color = Red) }
                        if (history) Text("${filtered.size}", color = Muted)
                    }
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().border(1.dp, Stroke, RoundedCornerShape(16.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.DownloadDone, null, tint = Muted, modifier = Modifier.size(27.dp))
                        Text("Seus downloads aparecem aqui", Modifier.padding(top = 10.dp), color = Muted, fontSize = 13.sp)
                    }
                }
                items(if (history) filtered else filtered.take(4), key = { it.id }) { task ->
                    DownloadCard(task, onPause = { scope.launch { vm.app.downloads.pause(task.id) } }, onCancel = { scope.launch { vm.app.downloads.cancel(task.id) } },
                        onRetry = { scope.launch { runCatching { vm.app.downloads.retry(task) }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() } } },
                        onDelete = { scope.launch { vm.app.downloads.delete(task) } }, onOpen = { openMedia(context, task, false) }, onShare = { openMedia(context, task, true) })
                }
                item { Text("Arquivos salvos no seu celular", Modifier.fillMaxWidth().padding(top = 8.dp), color = Muted.copy(alpha = .65f), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
    playlist?.let { info -> PlaylistDialog(info, onDismiss = { playlist = null }, onConfirm = { chosen -> chosen.forEach { beginDownload(it) }; playlist = null }) }
    pendingRedownload?.let { request -> AlertDialog(
        onDismissRequest = vm::dismissRedownload,
        title = { Text(if (request.tab == MediaTab.MUSIC) "Música já baixada" else "Vídeo já baixado") },
        text = { Text(if (request.tab == MediaTab.MUSIC) "Você já baixou esta música. Deseja baixar novamente?" else "Você já baixou este vídeo. Deseja baixar novamente?") },
        confirmButton = { TextButton(onClick = vm::confirmRedownload) { Text("Baixar novamente") } },
        dismissButton = { TextButton(onClick = vm::dismissRedownload) { Text("Cancelar") } }) }
    if (pendingShare != null && preferences.permissionSetupDone) AlertDialog(
        onDismissRequest = { vm.pendingShare.value = null },
        icon = { Icon(Icons.Outlined.FileDownload, null, tint = Red) },
        title = { Text("Como você quer baixar?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Escolha o tipo. Depois, ajuste qualidade, formato e nome antes de iniciar o download.", color = Muted, fontSize = 13.sp)
            AccentButton("Baixar vídeo", Icons.Outlined.PlayArrow, onClick = { settings = false; history = false; vm.chooseShared(MediaTab.VIDEO) })
            AccentButton("Baixar música", Icons.Outlined.MusicNote, onClick = { settings = false; history = false; vm.chooseShared(MediaTab.MUSIC) })
        } },
        confirmButton = {}, dismissButton = { TextButton(onClick = { vm.pendingShare.value = null }) { Text("Cancelar") } })
}

@Composable private fun UrlForm(state: TabState, tab: MediaTab, onUrl: (MediaTab, String) -> Unit, onQuery: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = state.url, onValueChange = { onUrl(tab, it) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            placeholder = { Text(if (tab == MediaTab.VIDEO) "Cole o link do vídeo" else "Cole o link da música ou playlist", fontSize = 14.sp, color = Muted) },
            leadingIcon = { Icon(Icons.Outlined.Link, null, tint = Muted) },
            trailingIcon = { IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                if (!text.isNullOrBlank()) onUrl(tab, extractSharedUrl(text)) else Toast.makeText(context, "A área de transferência está vazia.", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Outlined.ContentPaste, "Colar link") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = state.error.isNotBlank(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel, unfocusedBorderColor = Stroke))
        AccentButton(if (state.loading) "Consultando link…" else "Consultar", if (state.loading) null else Icons.Outlined.Search, loading = state.loading, onClick = { if (!state.loading) { keyboard?.hide(); onQuery() } })
        Box(Modifier.fillMaxWidth().heightIn(min = 28.dp)) {
            if (state.loading) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterEnd).height(28.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Cancelar consulta", color = Muted, fontSize = 12.sp) }
            if (state.error.isNotBlank()) Text(state.error, color = Color(0xFFFF8799), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 3.dp))
        }
    }
}

@Composable private fun EmptyConsult(tab: MediaTab, loading: Boolean = false) {
    Column(Modifier.fillMaxWidth().heightIn(min = 206.dp).padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Red.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(if (tab == MediaTab.VIDEO) Icons.Outlined.OndemandVideo else Icons.Outlined.Headphones, null, Modifier.size(29.dp), tint = Red)
        }
        Text(if (loading) "Buscando sua mídia" else if (tab == MediaTab.VIDEO) "Seu próximo vídeo, aqui" else "Leve sua música com você", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        Text(if (loading) "Consultando os formatos e as qualidades\ndisponíveis na fonte." else "Cole um link e consulte as opções\ndisponíveis para baixar.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable private fun ResultCard(info: MediaInfo, tab: MediaTab, state: TabState, metadata: Boolean, onFormat: (String) -> Unit, onHeight: (Int) -> Unit, onBitrate: (Int) -> Unit, onName: (String) -> Unit, onMetadata: (Boolean) -> Unit, onDownload: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF29262B), Color(0xFF19171B)))).border(1.dp, Stroke, shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(if (tab == MediaTab.MUSIC) 94.dp else 124.dp).height(94.dp).clip(RoundedCornerShape(11.dp)).background(Stroke), contentAlignment = Alignment.Center) {
                if (info.thumbnail.isNotBlank()) AsyncImage(info.thumbnail, "Capa de ${info.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Icon(if (tab == MediaTab.MUSIC) Icons.Outlined.MusicNote else Icons.Outlined.PlayArrow, null, Modifier.size(36.dp), tint = Muted)
                if (info.duration > 0) Text(formatDuration(info.duration), Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .8f)).padding(horizontal = 4.dp), fontSize = 10.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(info.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                if (info.author.isNotBlank()) Text(info.author, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(info.platform.ifBlank { Uri.parse(info.url).host.orEmpty() }, color = Red, fontSize = 12.sp, maxLines = 1)
                if (info.entries.isNotEmpty()) Text("${info.entries.size} itens · até 100 por consulta", color = Muted, fontSize = 11.sp)
            }
        }
        if (info.entries.isEmpty()) OutlinedTextField(state.filename, onName, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Nome do arquivo", fontSize = 12.sp) }, shape = RoundedCornerShape(10.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Stroke))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == MediaTab.VIDEO) {
                val heights = listOf(0) + info.heights
                Selector("Qualidade", if (state.height == 0) "Melhor disponível" else "${state.height}p", heights.map { if (it == 0) "Melhor disponível" else "${it}p" }, Modifier.weight(1f)) { onHeight(heights[it]) }
                val formats = if (info.formats.any { it.extension == "mp4" }) listOf("mp4", "mkv") else listOf("mkv")
                Selector("Formato", state.format.uppercase(), formats.map(String::uppercase), Modifier.weight(1f)) { onFormat(formats[it]) }
            } else {
                val formats = listOf("original", "mp3", "m4a", "opus")
                Selector("Formato", if (state.format == "original") "Original" else state.format.uppercase(), listOf("Original", "MP3", "M4A", "Opus"), Modifier.weight(1f)) { onFormat(formats[it]) }
                val bitrates = listOf(128, 192, 256, 320)
                Selector("Qualidade", if (state.format == "original") "Da fonte" else "${state.bitrate} kbps", bitrates.map { "$it kbps" }, Modifier.weight(1f), enabled = state.format != "original") { onBitrate(bitrates[it]) }
            }
        }
        if (tab == MediaTab.MUSIC) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .035f)).padding(start = 10.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sell, null, Modifier.size(18.dp), tint = Muted)
                Text("Incluir capa e informações", Modifier.weight(1f).padding(start = 9.dp), fontSize = 12.sp)
                Switch(metadata, onMetadata, modifier = Modifier.height(42.dp), colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White))
            }
            Text(if (state.format == "original") "Áudio sem conversão. Capa não é incorporada neste modo." else "A conversão mantém os limites de qualidade da fonte.", color = Muted, fontSize = 10.sp)
        }
        AccentButton(if (state.enqueuing) "Adicionando à fila…" else if (info.entries.isNotEmpty()) "Selecionar itens" else if (tab == MediaTab.VIDEO) "Baixar vídeo" else "Baixar música", Icons.Outlined.FileDownload, enabled = !state.enqueuing, onClick = onDownload)
    }
}

@Composable private fun Selector(label: String, value: String, options: List<String>, modifier: Modifier, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Stroke, RoundedCornerShape(10.dp)).clickable(enabled) { expanded = true }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, color = Muted, fontSize = 11.sp); Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp), overflow = TextOverflow.Ellipsis) }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp), tint = if (enabled) Color.White else Muted)
        }
        DropdownMenu(expanded, { expanded = false }) { options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(index) }) } }
    }
}

@Composable private fun AccentButton(text: String, icon: ImageVector? = null, loading: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled && !loading, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().height(49.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, disabledContentColor = Color.White)) {
        Row(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFF2346), Color(0xFFDE0029)))).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            else if (icon != null) Icon(icon, null, Modifier.size(23.dp))
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = if (icon != null || loading) 9.dp else 0.dp))
        }
    }
}

@Composable private fun DownloadCard(task: DownloadTask, onPause: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Panel, Color(0xFF19171A)))).border(1.dp, Stroke.copy(alpha = .7f), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(57.dp).clip(RoundedCornerShape(9.dp)).background(Stroke), contentAlignment = Alignment.Center) {
                if (task.thumbnail.isNotBlank()) AsyncImage(task.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(if (task.tab == MediaTab.MUSIC) Icons.Outlined.MusicNote else Icons.Outlined.PlayArrow, null, tint = Muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(task.filename.ifBlank { task.title }, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (task.state.active) {
                    if (task.state == TaskState.DOWNLOADING && task.total > 0) LinearProgressIndicator(progress = { task.progress }, Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), color = Red, trackColor = Stroke)
                    else LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp), color = Red, trackColor = Stroke)
                    Text(if (task.state == TaskState.DOWNLOADING) "${if (task.total > 0) "${(task.progress * 100).toInt()}% · " else ""}${if (task.speed > 0) "${formatBytes(task.speed)}/s" else "Baixando"}" else task.state.label, color = Muted, fontSize = 10.sp)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.state == TaskState.COMPLETED) Icon(Icons.Outlined.CheckCircle, null, tint = Green, modifier = Modifier.size(15.dp))
                    Text("${task.state.label}${if (task.state == TaskState.COMPLETED) " · ${formatBytes(task.bytes)}" else ""}", color = if (task.state == TaskState.FAILED) Color(0xFFFF8799) else Muted, fontSize = 11.sp, modifier = Modifier.padding(start = if (task.state == TaskState.COMPLETED) 5.dp else 0.dp))
                }
            }
            when {
                task.state == TaskState.COMPLETED -> { SmallAction(Icons.Outlined.OpenInNew, "Abrir arquivo", onOpen); SmallAction(Icons.Outlined.Share, "Compartilhar arquivo", onShare) }
                task.state.active || task.state == TaskState.QUEUED -> {
                    if (task.state in setOf(TaskState.DOWNLOADING, TaskState.QUEUED)) SmallAction(Icons.Outlined.Pause, "Pausar download", onPause)
                    if (task.state != TaskState.SAVING) SmallAction(Icons.Outlined.Close, "Cancelar download", onCancel)
                }
                else -> { SmallAction(Icons.Outlined.Refresh, "Tentar novamente", onRetry); SmallAction(Icons.Outlined.DeleteOutline, "Remover do histórico", onDelete) }
            }
        }
        if (task.state == TaskState.DOWNLOADING && task.bytes > 0) Text("${formatBytes(task.bytes)}${if (task.total > 0) " de ~${formatBytes(task.total)}" else ""}${if (task.eta > 0) " · ~${formatDuration(task.eta)} restantes" else ""}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
        if (task.error.isNotBlank()) Text(task.error, color = if (task.state == TaskState.FAILED) Color(0xFFFF8799) else Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable private fun SmallAction(icon: ImageVector, description: String, onClick: () -> Unit) { IconButton(onClick, Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .035f))) { Icon(icon, description, Modifier.size(20.dp)) } }

private fun openMedia(context: Context, task: DownloadTask, share: Boolean) {
    try {
        val uri = Uri.parse(task.uri)
        context.contentResolver.openFileDescriptor(uri, "r")?.close() ?: error("Arquivo não encontrado.")
        val mime = context.contentResolver.getType(uri) ?: if (task.tab == MediaTab.MUSIC) "audio/*" else "video/*"
        val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(if (share) Intent.createChooser(intent, "Compartilhar arquivo") else intent)
    } catch (_: Exception) { Toast.makeText(context, "Não foi possível abrir o arquivo. Confira se ele ainda existe e se há um app compatível.", Toast.LENGTH_LONG).show() }
}

@Composable private fun PlaylistDialog(info: MediaInfo, onDismiss: () -> Unit, onConfirm: (List<MediaInfo>) -> Unit) {
    var chosen by remember { mutableStateOf(info.entries.map { it.url }.toSet()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Selecionar itens") }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            item { Text("Até 100 itens por consulta. A melhor qualidade disponível será resolvida para cada item.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)) }
            items(info.entries) { entry -> Row(Modifier.fillMaxWidth().clickable { chosen = if (entry.url in chosen) chosen - entry.url else chosen + entry.url }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(entry.url in chosen, { checked -> chosen = if (checked) chosen + entry.url else chosen - entry.url }); Text(entry.title, maxLines = 2, fontSize = 13.sp)
            } }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(info.entries.filter { it.url in chosen }) }, enabled = chosen.isNotEmpty()) { Text("Enfileirar (${chosen.size})") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun SettingsScreen(vm: MainViewModel) {
    val prefs by vm.app.preferences.collectAsStateWithLifecycle()
    val status by vm.app.engineStatus.collectAsStateWithLifecycle()
    val update by vm.app.updates.state.collectAsStateWithLifecycle()
    val tasks by vm.app.downloads.tasks.collectAsStateWithLifecycle()
    val queries by vm.app.downloads.querying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf("") }
    var diagnosticRunning by remember { mutableStateOf(false) }
    fun saveFolder(tab: MediaTab, uri: Uri?) { if (uri != null) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .onSuccess { scope.launch { vm.app.preferenceStore.folder(tab, uri.toString()) } }
            .onFailure { Toast.makeText(context, "Não foi possível manter o acesso à pasta.", Toast.LENGTH_LONG).show() }
    } }
    val videoFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { saveFolder(MediaTab.VIDEO, it) }
    val musicFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { saveFolder(MediaTab.MUSIC, it) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.app.updates.returnedFromInstaller() }
    val busy = queries > 0 || tasks.any { it.state.active || it.state == TaskState.QUEUED }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsPanel("Pasta de vídeos", Icons.Outlined.FolderOpen) {
            Text(if (prefs.videoFolder.isBlank()) "Padrão: Download do dispositivo" else folderLabel(prefs.videoFolder), color = Muted, fontSize = 13.sp)
            OutlinedButton(onClick = { videoFolderPicker.launch(prefs.videoFolder.takeIf { it.isNotBlank() }?.let(Uri::parse)) }, modifier = Modifier.fillMaxWidth()) { Text("Alterar pasta de vídeos") }
            if (prefs.videoFolder.isNotBlank()) TextButton(onClick = { scope.launch { vm.app.preferenceStore.folder(MediaTab.VIDEO, "") } }) { Text("Restaurar Download para vídeos") }
        }
        SettingsPanel("Pasta de músicas", Icons.Outlined.MusicNote) {
            Text(if (prefs.musicFolder.isBlank()) "Padrão: Download do dispositivo" else folderLabel(prefs.musicFolder), color = Muted, fontSize = 13.sp)
            OutlinedButton(onClick = { musicFolderPicker.launch(prefs.musicFolder.takeIf { it.isNotBlank() }?.let(Uri::parse)) }, modifier = Modifier.fillMaxWidth()) { Text("Alterar pasta de músicas") }
            if (prefs.musicFolder.isNotBlank()) TextButton(onClick = { scope.launch { vm.app.preferenceStore.folder(MediaTab.MUSIC, "") } }) { Text("Restaurar Download para músicas") }
        }
        SettingsPanel("Atualizações", Icons.Outlined.SystemUpdate) {
            Text("Versão instalada: ${BuildConfig.VERSION_NAME}", fontSize = 14.sp)
            Text(update.message.ifBlank { "Atualizações pelo GitHub Releases." }, fontSize = 12.sp, color = Muted)
            update.offer?.let { offer ->
                Text("SFMD ${offer.version} · ${offer.abi} · ${formatBytes(offer.size)}", color = Red, fontSize = 13.sp)
                if (offer.notes.isNotBlank()) Text(offer.notes.take(4000), color = Muted, fontSize = 12.sp)
            }
            when (update.phase) {
                UpdatePhase.DOWNLOADING -> {
                    LinearProgressIndicator(progress = { update.progress }, Modifier.fillMaxWidth(), color = Red)
                    Text("${(update.progress * 100).toInt()}%", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { vm.app.updates.cancel() }) { Text("Cancelar download") }
                }
                UpdatePhase.VERIFYING, UpdatePhase.CHECKING -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Red)
                UpdatePhase.AVAILABLE -> AccentButton("Baixar atualização", Icons.Outlined.FileDownload, onClick = { vm.app.updates.download() })
                UpdatePhase.READY, UpdatePhase.AUTHORIZATION -> {
                    if (busy) Text("Aguarde as tarefas ativas terminarem para instalar.", fontSize = 12.sp, color = Muted)
                    AccentButton("Instalar atualização", Icons.Outlined.SystemUpdate, enabled = !busy, onClick = { scope.launch {
                        runCatching { vm.app.updates.installationIntent()?.let(installer::launch) }.onFailure { vm.app.updates.returnedFromInstaller(); Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    } })
                }
                else -> OutlinedButton(onClick = { scope.launch { vm.app.updates.check() } }, modifier = Modifier.fillMaxWidth()) { Text("Verificar atualizações") }
            }
            if (update.lastChecked > 0) Text("Última consulta: ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, java.util.Locale.forLanguageTag("pt-BR")).format(java.util.Date(update.lastChecked))}", fontSize = 10.sp, color = Muted)
            Text("O Android pede sua autorização para instalar. Seus dados são preservados quando a assinatura é a mesma.", color = Muted, fontSize = 11.sp)
        }
        SettingsPanel("Motor de download", Icons.Outlined.Memory) {
            Text(status, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            Text("Componentes incluídos no APK. Nenhuma instalação auxiliar.", fontSize = 12.sp)
            OutlinedButton(enabled = !diagnosticRunning && !busy, onClick = { scope.launch { diagnosticRunning = true; diagnostics = runCatching { vm.app.engine.diagnostics() }.getOrElse(::readableError); diagnosticRunning = false } }) { Text(if (diagnosticRunning) "Verificando…" else "Verificar motor") }
            if (diagnostics.isNotBlank()) Text(diagnostics, color = Muted, fontSize = 12.sp)
        }
        SettingsPanel("Fila e compatibilidade", Icons.Outlined.Info) {
            Text("Um download por vez nesta versão inicial. Pausar preserva arquivos parciais; a retomada depende da fonte. Tarefas interrompidas podem ser reiniciadas pelo histórico.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            Text("Links de catálogos de música e conteúdo com DRM não oferecem download direto. O suporte aos sites depende dos extratores e das restrições da fonte.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            Text("SFMD · SFMusicDownloader\nDesenvolvido em Kotlin para Android", color = Muted, fontSize = 11.sp)
        }
    }
}
@Composable private fun SettingsPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { Icon(icon, null, tint = Red, modifier = Modifier.size(21.dp)); Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        content()
    }
}
