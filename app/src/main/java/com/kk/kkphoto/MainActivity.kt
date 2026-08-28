package com.kk.kkphoto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kk.kkphoto.ui.theme.KkPhotoTheme
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val readImagesPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KkPhotoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isImagesGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readImagesPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isMediaLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isImagesGranted = results[readImagesPermission] == true
        isMediaLocationGranted = results[Manifest.permission.ACCESS_MEDIA_LOCATION] == true
    }

    if (isImagesGranted) {
        Column(modifier = modifier.fillMaxSize()) {
            if (!isMediaLocationGranted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "位置情報(GPS)を保持するには追加の許可が必要です",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        launcher.launch(arrayOf(readImagesPermission, Manifest.permission.ACCESS_MEDIA_LOCATION))
                    }) {
                        Text("許可する")
                    }
                }
            }
            DateRangeQueryScreen(modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "写真へのアクセス: 未許可")
            Button(onClick = {
                launcher.launch(arrayOf(readImagesPermission, Manifest.permission.ACCESS_MEDIA_LOCATION))
            }) {
                Text("許可をリクエスト")
            }
        }
    }
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun LocalDate.endOfDayMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

private fun LocalDate.toUtcMidnightMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DateRangeQueryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).processedPhotoDao() }

    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var matchedPhotos by remember { mutableStateOf<List<PhotoEntry>>(emptyList()) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isQuerying by remember { mutableStateOf(false) }

    var selectedPreset by remember { mutableStateOf<ResizePreset?>(ResizePreset.SMALL) }
    var customMegapixelsText by remember { mutableStateOf("0.3") }
    var isResizing by remember { mutableStateOf(false) }
    var resizeStatusText by remember { mutableStateOf<String?>(null) }
    var lastResizedFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    val effectiveMegapixels = selectedPreset?.megapixels
        ?: customMegapixelsText.toDoubleOrNull()

    // 選択中のリサイズ設定に対して、何件が未処理で何件がスキップ予定かを事前に見せる。
    // queryTokenは「件数を確認」を押すたびに増分し、matchedPhotosの中身が前回と同じでも
    // (=処理済みDBの状態だけが変わった場合でも)必ず再計算されるようにする
    var queryToken by remember { mutableStateOf(0) }
    var skipPreviewText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(queryToken, effectiveMegapixels) {
        val megapixels = effectiveMegapixels
        if (matchedPhotos.isEmpty() || megapixels == null) {
            skipPreviewText = null
            return@LaunchedEffect
        }
        val (toProcess, alreadyProcessed) = partitionByProcessedState(
            dao, matchedPhotos, areaResizeKey(megapixels)
        )
        skipPreviewText = "未処理: ${toProcess.size}件 / スキップ予定: ${alreadyProcessed.size}件"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = { showStartPicker = true }) {
            Text("開始日: ${startDate.format(dateFormatter)}")
        }
        TextButton(onClick = { showEndPicker = true }) {
            Text("終了日: ${endDate.format(dateFormatter)}")
        }
        Button(
            enabled = !isQuerying,
            onClick = {
                isQuerying = true
                resultText = null
                resizeStatusText = null
                scope.launch {
                    val photos = queryPhotosInRange(
                        context = context,
                        startMillis = startDate.startOfDayMillis(),
                        endMillis = endDate.endOfDayMillis()
                    )
                    matchedPhotos = photos
                    resultText = "該当件数: ${photos.size}件"
                    queryToken++
                    isQuerying = false
                }
            }
        ) {
            Text(if (isQuerying) "確認中..." else "件数を確認")
        }
        resultText?.let { Text(text = it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResizePreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { selectedPreset = preset },
                    label = { Text(preset.label) }
                )
            }
            FilterChip(
                selected = selectedPreset == null,
                onClick = { selectedPreset = null },
                label = { Text("カスタム") }
            )
        }
        if (selectedPreset == null) {
            OutlinedTextField(
                value = customMegapixelsText,
                onValueChange = { customMegapixelsText = it },
                label = { Text("目標メガピクセル数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        skipPreviewText?.let { Text(text = it) }

        Button(
            enabled = !isResizing && matchedPhotos.isNotEmpty() && effectiveMegapixels != null,
            onClick = {
                val megapixels = effectiveMegapixels ?: return@Button
                isResizing = true
                resizeStatusText = null
                scope.launch {
                    val resizeKey = areaResizeKey(megapixels)
                    val (toProcess, alreadyProcessed) = partitionByProcessedState(
                        dao, matchedPhotos, resizeKey
                    )
                    var successCount = 0
                    var failCount = 0
                    val outputFiles = mutableListOf<File>()
                    toProcess.forEachIndexed { index, photo ->
                        resizeStatusText = "処理中: ${index + 1} / ${toProcess.size}"
                        try {
                            val outputFile = resizeAndSave(context, photo, megapixels)
                            dao.upsert(
                                ProcessedPhotoEntity(
                                    mediaStoreId = photo.id,
                                    resizeKey = resizeKey,
                                    fileSize = photo.size,
                                    dateModified = photo.dateModified,
                                    outputPath = outputFile.absolutePath,
                                    processedAt = System.currentTimeMillis()
                                )
                            )
                            outputFiles.add(outputFile)
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    resizeStatusText = "保存完了: 成功 ${successCount}件 / 失敗 ${failCount}件 / " +
                        "スキップ ${alreadyProcessed.size}件"
                    lastResizedFiles = outputFiles
                    queryToken++
                    isResizing = false
                }
            }
        ) {
            Text(if (isResizing) "リサイズ中..." else "リサイズして保存")
        }
        resizeStatusText?.let { Text(text = it) }

        SharePhotosSection(files = lastResizedFiles, modifier = Modifier.fillMaxWidth())
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate.toUtcMidnightMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startDate = it.toUtcLocalDate() }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate.toUtcMidnightMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDate = it.toUtcLocalDate() }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
