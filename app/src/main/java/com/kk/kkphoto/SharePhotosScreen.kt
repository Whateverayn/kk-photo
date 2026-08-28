package com.kk.kkphoto

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMBNAIL_TARGET_PX = 200

private suspend fun decodeThumbnail(file: File): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    val sampleSize = computeInSampleSize(
        bounds.outWidth, bounds.outHeight, THUMBNAIL_TARGET_PX, THUMBNAIL_TARGET_PX
    )
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun shareFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val authority = "${context.packageName}.fileprovider"
    val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.type = "image/jpeg"
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

/** 直前のリサイズ結果から共有する写真を選ぶ画面。 */
@Composable
fun SharePhotosSection(files: List<File>, modifier: Modifier = Modifier) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    var selected by remember(files) { mutableStateOf<Set<File>>(emptySet()) }

    Column(modifier = modifier) {
        Text(
            text = "作成した写真から共有するものを選択(${selected.size}/${files.size}件選択中)",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { selected = files.toSet() }) { Text("全選択") }
            TextButton(onClick = { selected = emptySet() }) { Text("全解除") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(files) { file ->
                ThumbnailItem(
                    file = file,
                    isSelected = file in selected,
                    onToggle = {
                        selected = if (file in selected) selected - file else selected + file
                    }
                )
            }
        }
        Button(
            enabled = selected.isNotEmpty(),
            onClick = { shareFiles(context, selected.toList()) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("選択した${selected.size}件を共有")
        }
    }
}

@Composable
private fun ThumbnailItem(file: File, isSelected: Boolean, onToggle: () -> Unit) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = decodeThumbnail(file)
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
