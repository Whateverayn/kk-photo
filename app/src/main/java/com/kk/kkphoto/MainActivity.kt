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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readImagesPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> isGranted = granted }

    if (isGranted) {
        DateRangeQueryScreen(modifier = modifier)
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "写真へのアクセス: 未許可")
            Button(onClick = { launcher.launch(readImagesPermission) }) {
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

    val effectiveMegapixels = selectedPreset?.megapixels
        ?: customMegapixelsText.toDoubleOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
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

        Button(
            enabled = !isResizing && matchedPhotos.isNotEmpty() && effectiveMegapixels != null,
            onClick = {
                val megapixels = effectiveMegapixels ?: return@Button
                isResizing = true
                resizeStatusText = null
                scope.launch {
                    var successCount = 0
                    var failCount = 0
                    matchedPhotos.forEachIndexed { index, photo ->
                        resizeStatusText = "処理中: ${index + 1} / ${matchedPhotos.size}"
                        try {
                            resizeAndSave(context, photo, megapixels)
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    resizeStatusText = "保存完了: 成功 ${successCount}件 / 失敗 ${failCount}件"
                    isResizing = false
                }
            }
        ) {
            Text(if (isResizing) "リサイズ中..." else "リサイズして保存")
        }
        resizeStatusText?.let { Text(text = it) }
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
