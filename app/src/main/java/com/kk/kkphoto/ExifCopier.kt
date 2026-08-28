package com.kk.kkphoto

import androidx.exifinterface.media.ExifInterface

/**
 * ExifInterfaceが公開するTAG_*定数を反射で列挙し、既知の全タグを対象にする。
 * ライブラリのバージョンアップでタグが増えても追従できるようにするため。
 */
private val allExifTagNames: List<String> by lazy {
    ExifInterface::class.java.fields
        .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
        .mapNotNull { it.get(null) as? String }
        .distinct()
}

/**
 * サムネイルの格納位置を示すオフセット/長さタグ。値は元ファイル内の相対バイト位置であり、
 * 別ファイルにそのままコピーすると壊れたサムネイル参照になるため対象から除外する。
 */
private val offsetTagNames = setOf(
    ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
    ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
)

/**
 * [source]の全EXIF/XMPタグを[dest]にコピーする(保存は呼び出し側の[ExifInterface.saveAttributes]で行う)。
 * TAG_XMPはUTF-8バイト列として扱う([ExifInterface.getAttributeBytes]/[ExifInterface.setAttribute]の組み合わせが必要なため)。
 */
fun copyExifAttributes(source: ExifInterface, dest: ExifInterface) {
    for (tag in allExifTagNames) {
        if (tag in offsetTagNames) continue
        if (tag == ExifInterface.TAG_XMP) {
            val xmpBytes = source.getAttributeBytes(tag)
            if (xmpBytes != null) {
                dest.setAttribute(tag, String(xmpBytes, Charsets.UTF_8))
            }
            continue
        }
        val value = source.getAttribute(tag) ?: continue
        dest.setAttribute(tag, value)
    }
}
