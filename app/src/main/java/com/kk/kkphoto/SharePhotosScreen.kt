package com.kk.kkphoto

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** [files]を共有する。1件ならACTION_SEND、複数ならACTION_SEND_MULTIPLEでchooserを起動する。 */
fun shareFiles(context: Context, files: List<File>) {
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
