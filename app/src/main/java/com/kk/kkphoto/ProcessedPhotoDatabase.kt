package com.kk.kkphoto

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

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
