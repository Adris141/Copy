package com.hola.backup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hola.backup.data.BackupPrefs
import com.hola.backup.data.BackupSettings
import com.hola.backup.data.CallLogReader
import com.hola.backup.data.MediaReader
import com.hola.backup.data.SmsReader
import com.hola.backup.data.TelegramUploader
import com.hola.backup.data.TreeUriReader
import com.hola.backup.data.ZipBuilder
import com.hola.backup.domain.BackupFileFilters
import com.hola.backup.domain.BackupOptions
import com.hola.backup.domain.BackupOrchestrator
import com.hola.backup.work.BackupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedTreeUriState = mutableStateOf<String?>(null)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        val treePickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                selectedTreeUriState.value = uri.toString()
            }
        }

        setContent {
            MaterialTheme {
                BackupScreen(
                    requestPermissions = {
                        permissionLauncher.launch(requiredPermissions().toTypedArray())
                    },
                    hasPermissions = {
                        requiredPermissions().all {
                            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                        }
                    },
                    openTreePicker = { treePickerLauncher.launch(null) },
                    latestTreeUri = selectedTreeUriState.value
                )
            }
        }
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return permissions
    }
}

@Composable
private fun BackupScreen(
    requestPermissions: () -> Unit,
    hasPermissions: () -> Boolean,
    openTreePicker: () -> Unit,
    latestTreeUri: String?
) {
    val context = LocalContext.current
    val prefs = remember { BackupPrefs(context) }
    val scheduler = remember { BackupScheduler(context) }
    val scope = rememberCoroutineScope()
    val saved = remember { prefs.load() }

    var botToken by remember { mutableStateOf(saved.botToken) }
    var chatId by remember { mutableStateOf(saved.chatId) }
    var includeSms by remember { mutableStateOf(saved.options.includeSms) }
    var includeCalls by remember { mutableStateOf(saved.options.includeCallLogs) }
    var includeMedia by remember { mutableStateOf(saved.options.includeMediaFiles) }
    var includeTreeFiles by remember { mutableStateOf(saved.options.includeTreeFiles) }
    var treeUri by remember { mutableStateOf(saved.options.treeUri.orEmpty()) }
    var includeExtCsv by remember { mutableStateOf(saved.options.fileFilters.includeExtensionsCsv) }
    var excludeExtCsv by remember { mutableStateOf(saved.options.fileFilters.excludeExtensionsCsv) }
    var includeMimeCsv by remember { mutableStateOf(saved.options.fileFilters.includeMimePrefixesCsv) }
    var excludeMimeCsv by remember { mutableStateOf(saved.options.fileFilters.excludeMimePrefixesCsv) }
    var excludeFoldersCsv by remember { mutableStateOf(saved.options.fileFilters.excludeFolderNamesCsv) }
    var minSizeMbText by remember { mutableStateOf((saved.options.fileFilters.minSizeBytes / (1024 * 1024)).toString()) }
    var maxSizeMbText by remember { mutableStateOf((saved.options.fileFilters.maxSizeBytes / (1024 * 1024)).toString()) }
    var modifiedDaysText by remember { mutableStateOf(saved.options.fileFilters.modifiedWithinDays.toString()) }
    var includeHidden by remember { mutableStateOf(saved.options.fileFilters.includeHiddenFiles) }
    var maxMediaText by remember { mutableStateOf(saved.options.maxMediaFiles.toString()) }
    var maxTreeText by remember { mutableStateOf(saved.options.maxTreeFiles.toString()) }
    var scheduleDaily by remember { mutableStateOf(saved.scheduleDaily) }
    var status by remember { mutableStateOf("Listo") }

    LaunchedEffect(latestTreeUri) {
        if (!latestTreeUri.isNullOrBlank()) {
            treeUri = latestTreeUri
        }
    }

    fun persist() {
        val minMb = minSizeMbText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val maxMb = maxSizeMbText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val modifiedDays = modifiedDaysText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val maxMedia = maxMediaText.toIntOrNull()?.coerceAtLeast(1) ?: 500
        val maxTree = maxTreeText.toIntOrNull()?.coerceAtLeast(1) ?: 1000
        val settings = BackupSettings(
            botToken = botToken.trim(),
            chatId = chatId.trim(),
            options = BackupOptions(
                includeSms = includeSms,
                includeCallLogs = includeCalls,
                includeMediaFiles = includeMedia,
                includeTreeFiles = includeTreeFiles,
                treeUri = treeUri.ifBlank { null },
                fileFilters = BackupFileFilters(
                    includeExtensionsCsv = includeExtCsv,
                    excludeExtensionsCsv = excludeExtCsv,
                    includeMimePrefixesCsv = includeMimeCsv,
                    excludeMimePrefixesCsv = excludeMimeCsv,
                    excludeFolderNamesCsv = excludeFoldersCsv,
                    minSizeBytes = minMb * 1024L * 1024L,
                    maxSizeBytes = maxMb * 1024L * 1024L,
                    modifiedWithinDays = modifiedDays,
                    includeHiddenFiles = includeHidden
                ),
                maxMediaFiles = maxMedia,
                maxTreeFiles = maxTree
            ),
            scheduleDaily = scheduleDaily
        )
        prefs.save(settings)
        if (settings.scheduleDaily) scheduler.scheduleDaily() else scheduler.cancel()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hola Backup", style = MaterialTheme.typography.headlineSmall)
            Text("Respaldo de SMS, llamadas y archivos multimedia a Telegram")

            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                label = { Text("Bot token") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it },
                label = { Text("Chat ID") },
                modifier = Modifier.fillMaxWidth()
            )

            ToggleRow("Incluir SMS", includeSms) { includeSms = it }
            ToggleRow("Incluir llamadas", includeCalls) { includeCalls = it }
            ToggleRow("Incluir fotos/videos/audio", includeMedia) { includeMedia = it }
            ToggleRow("Incluir archivos de carpeta elegida", includeTreeFiles) { includeTreeFiles = it }
            ToggleRow("Incluir archivos ocultos", includeHidden) { includeHidden = it }
            ToggleRow("Copia diaria automática", scheduleDaily) { scheduleDaily = it }

            Button(
                onClick = { openTreePicker() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Elegir carpeta para detección avanzada")
            }
            Text(
                text = if (treeUri.isBlank()) "Carpeta: no seleccionada" else "Carpeta: $treeUri",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = includeExtCsv,
                onValueChange = { includeExtCsv = it },
                label = { Text("Incluir extensiones CSV (ej: pdf,docx,zip,apk)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = excludeExtCsv,
                onValueChange = { excludeExtCsv = it },
                label = { Text("Excluir extensiones CSV") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = includeMimeCsv,
                onValueChange = { includeMimeCsv = it },
                label = { Text("Incluir MIME prefijos CSV (ej: image/,application/pdf)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = excludeMimeCsv,
                onValueChange = { excludeMimeCsv = it },
                label = { Text("Excluir MIME prefijos CSV") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = excludeFoldersCsv,
                onValueChange = { excludeFoldersCsv = it },
                label = { Text("Excluir carpetas CSV (nombre exacto)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = minSizeMbText,
                onValueChange = { minSizeMbText = it.filter { c -> c.isDigit() } },
                label = { Text("Tamaño mínimo MB (0 = sin filtro)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = maxSizeMbText,
                onValueChange = { maxSizeMbText = it.filter { c -> c.isDigit() } },
                label = { Text("Tamaño máximo MB (0 = sin filtro)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = modifiedDaysText,
                onValueChange = { modifiedDaysText = it.filter { c -> c.isDigit() } },
                label = { Text("Modificados en últimos N días (0 = sin filtro)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = maxMediaText,
                onValueChange = { maxMediaText = it.filter { c -> c.isDigit() } },
                label = { Text("Máximo archivos de media") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = maxTreeText,
                onValueChange = { maxTreeText = it.filter { c -> c.isDigit() } },
                label = { Text("Máximo archivos de carpeta elegida") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    persist()
                    if (!hasPermissions()) {
                        status = "Solicitando permisos..."
                        requestPermissions()
                    } else {
                        status = "Configuración guardada"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar configuración")
            }

            Button(
                onClick = {
                    persist()
                    if (!hasPermissions()) {
                        status = "Solicitando permisos..."
                        requestPermissions()
                        return@Button
                    }
                    if (botToken.isBlank() || chatId.isBlank()) {
                        status = "Falta token o chat_id"
                        return@Button
                    }

                    scope.launch {
                        status = "Generando backup..."
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val orchestrator = BackupOrchestrator(
                                    smsReader = SmsReader(context),
                                    callLogReader = CallLogReader(context),
                                    mediaReader = MediaReader(context),
                                    treeUriReader = TreeUriReader(context),
                                    zipBuilder = ZipBuilder(context.contentResolver),
                                    telegramUploader = TelegramUploader()
                                )

                                orchestrator.runBackup(
                                    options = BackupOptions(
                                        includeSms = includeSms,
                                        includeCallLogs = includeCalls,
                                        includeMediaFiles = includeMedia,
                                        includeTreeFiles = includeTreeFiles,
                                        treeUri = treeUri.ifBlank { null },
                                        fileFilters = BackupFileFilters(
                                            includeExtensionsCsv = includeExtCsv,
                                            excludeExtensionsCsv = excludeExtCsv,
                                            includeMimePrefixesCsv = includeMimeCsv,
                                            excludeMimePrefixesCsv = excludeMimeCsv,
                                            excludeFolderNamesCsv = excludeFoldersCsv,
                                            minSizeBytes = (minSizeMbText.toLongOrNull() ?: 0L) * 1024L * 1024L,
                                            maxSizeBytes = (maxSizeMbText.toLongOrNull() ?: 0L) * 1024L * 1024L,
                                            modifiedWithinDays = modifiedDaysText.toIntOrNull() ?: 0,
                                            includeHiddenFiles = includeHidden
                                        ),
                                        maxMediaFiles = maxMediaText.toIntOrNull() ?: 500,
                                        maxTreeFiles = maxTreeText.toIntOrNull() ?: 1000
                                    ),
                                    outputDir = File(context.filesDir, "backups"),
                                    botToken = botToken.trim(),
                                    chatId = chatId.trim()
                                )
                            }
                        }.fold(
                            onSuccess = {
                                status = "Backup enviado. SMS=${it.smsCount}, llamadas=${it.callCount}, media=${it.mediaCount}, archivos=${it.treeCount}"
                            },
                            onFailure = {
                                status = "Error: ${it.message}"
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ejecutar backup ahora")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Estado: $status")
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
