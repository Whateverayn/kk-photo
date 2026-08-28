package com.kk.kkphoto

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val galleryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

private fun LocalDate.galleryStartOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun LocalDate.galleryEndOfDayMillis(): Long =
    plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

private fun LocalDate.toUtcMidnightMillisG(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDateG(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private const val GALLERY_THUMBNAIL_PX = 200

private suspend fun loadGalleryThumbnail(context: Context, photo: PhotoEntry): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo.id)
            context.contentResolver.loadThumbnail(uri, Size(GALLERY_THUMBNAIL_PX, GALLERY_THUMBNAIL_PX), null)
        } catch (e: Exception) {
            null
        }
    }

@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getInstance(context).processedPhotoDao() }

    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<PhotoEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(startDate, endDate) {
        isLoading = true
        photos = queryPhotosInRange(
            context = context,
            startMillis = startDate.galleryStartOfDayMillis(),
            endMillis = endDate.galleryEndOfDayMillis()
        )
        isLoading = false
    }

    var selected by remember { mutableStateOf<Set<PhotoEntry>>(emptySet()) }
    LaunchedEffect(photos) { selected = emptySet() }

    var selectedPreset by remember { mutableStateOf<ResizePreset?>(ResizePreset.SMALL) }
    var customMegapixelsText by remember { mutableStateOf("0.3") }
    val effectiveMegapixels = selectedPreset?.megapixels ?: customMegapixelsText.toDoubleOrNull()

    var isSharing by remember { mutableStateOf(false) }
    var shareStatusText by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showStartPicker = true }) {
                Text(startDate.format(galleryDateFormatter))
            }
            Text("〜")
            TextButton(onClick = { showEndPicker = true }) {
                Text(endDate.format(galleryDateFormatter))
            }
            Text(
                text = if (isLoading) "読み込み中..." else "${photos.size}件",
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(photos, key = { it.id }) { photo ->
                GalleryThumbnailItem(
                    photo = photo,
                    isSelected = photo in selected,
                    onToggle = {
                        selected = if (photo in selected) selected - photo else selected + photo
                    }
                )
            }
        }

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
            enabled = !isSharing && selected.isNotEmpty() && effectiveMegapixels != null,
            onClick = {
                val megapixels = effectiveMegapixels ?: return@Button
                val targets = selected.toList()
                isSharing = true
                shareStatusText = null
                scope.launch {
                    val resizeKey = areaResizeKey(megapixels)
                    val outputFiles = mutableListOf<File>()
                    targets.forEachIndexed { index, photo ->
                        shareStatusText = "変換中: ${index + 1} / ${targets.size}"
                        try {
                            outputFiles.add(resolveOutputFile(context, dao, photo, resizeKey, megapixels))
                        } catch (e: Exception) {
                            // 失敗した写真はスキップして続行する
                        }
                    }
                    shareStatusText = null
                    isSharing = false
                    shareFiles(context, outputFiles)
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (isSharing) "変換中..." else "選択した${selected.size}件を共有")
        }
        shareStatusText?.let { Text(text = it) }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate.toUtcMidnightMillisG())
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { startDate = it.toUtcLocalDateG() }
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
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate.toUtcMidnightMillisG())
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endDate = it.toUtcLocalDateG() }
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

@Composable
private fun GalleryThumbnailItem(photo: PhotoEntry, isSelected: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, photo) {
        value = loadGalleryThumbnail(context, photo)
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onToggle)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
        )
    }
}
