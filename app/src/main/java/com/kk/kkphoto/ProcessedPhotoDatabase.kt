package com.kk.kkphoto

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import java.io.File

/**
 * 重複スキップの記録単位。「同じ写真(mediaStoreId) × 同じリサイズ設定(resizeKey)」がキー。
 * リサイズ設定を変えれば別物として扱われ、再処理される。
 * fileSize/dateModifiedは元ファイルが変化していないかの確認に使う(変化していれば再処理する)。
 */
@Entity(tableName = "processed_photos", primaryKeys = ["mediaStoreId", "resizeKey"])
data class ProcessedPhotoEntity(
    val mediaStoreId: Long,
    val resizeKey: String,
    val fileSize: Long,
    val dateModified: Long,
    val outputPath: String,
    val processedAt: Long
)

@Dao
interface ProcessedPhotoDao {
    @Query("SELECT * FROM processed_photos WHERE mediaStoreId IN (:mediaStoreIds) AND resizeKey = :resizeKey")
    suspend fun findAll(mediaStoreIds: List<Long>, resizeKey: String): List<ProcessedPhotoEntity>

    @Upsert
    suspend fun upsert(entity: ProcessedPhotoEntity)
}

/** [photos]を(未処理, 処理済みでスキップ対象)に振り分ける。サイズ/更新日時が記録と異なる場合は再処理対象とする。 */
suspend fun partitionByProcessedState(
    dao: ProcessedPhotoDao,
    photos: List<PhotoEntry>,
    resizeKey: String
): Pair<List<PhotoEntry>, List<PhotoEntry>> {
    if (photos.isEmpty()) return emptyList<PhotoEntry>() to emptyList()
    val existingById = dao.findAll(photos.map { it.id }, resizeKey).associateBy { it.mediaStoreId }
    val toProcess = mutableListOf<PhotoEntry>()
    val alreadyProcessed = mutableListOf<PhotoEntry>()
    for (photo in photos) {
        val record = existingById[photo.id]
        val isUpToDate = record != null &&
            record.fileSize == photo.size &&
            record.dateModified == photo.dateModified
        if (isUpToDate) alreadyProcessed.add(photo) else toProcess.add(photo)
    }
    return toProcess to alreadyProcessed
}

/**
 * [photo]の[resizeKey]に対する出力ファイルを返す。既に処理済み(記録のサイズ/更新日時が現在の元ファイルと一致)なら
 * その出力ファイルを再利用し、そうでなければ今リサイズして記録を作成/更新する。
 */
suspend fun resolveOutputFile(
    context: Context,
    dao: ProcessedPhotoDao,
    photo: PhotoEntry,
    resizeKey: String,
    targetMegapixels: Double
): File {
    val existing = dao.findAll(listOf(photo.id), resizeKey).firstOrNull()
    if (existing != null && existing.fileSize == photo.size && existing.dateModified == photo.dateModified) {
        val file = File(existing.outputPath)
        if (file.exists()) return file
    }
    val outputFile = resizeAndSave(context, photo, targetMegapixels)
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
    return outputFile
}

@Database(entities = [ProcessedPhotoEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun processedPhotoDao(): ProcessedPhotoDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kkphoto.db"
                ).build().also { instance = it }
            }
    }
}
