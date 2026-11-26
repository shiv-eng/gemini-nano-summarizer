package com.nanodigest.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "summaries", indices = [Index("createdAt")])
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceText: String,
    val summary: String,
    val inputKind: String,
    val bullets: Int,
    val language: String,
    val engine: String,
    val chunkCount: Int,
    val sourceChars: Int,
    val firstTokenMs: Long?,
    val totalMs: Long,
    val createdAt: Long,
    val pinned: Boolean = false,
)

@Dao
interface SummaryDao {

    @Query(
        """
        SELECT * FROM summaries
        WHERE :query = '' OR title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%'
              OR sourceText LIKE '%' || :query || '%'
        ORDER BY pinned DESC, createdAt DESC
        """,
    )
    fun observe(query: String): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE id = :id")
    fun observeById(id: Long): Flow<SummaryEntity?>

    @Insert
    suspend fun insert(entity: SummaryEntity): Long

    @Query("UPDATE summaries SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM summaries WHERE pinned = 0")
    suspend fun deleteUnpinned()
}

@Database(entities = [SummaryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun summaryDao(): SummaryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "nanodigest.db").build()
    }
}

class SummaryRepository(private val dao: SummaryDao) {
    fun observe(query: String): Flow<List<SummaryEntity>> = dao.observe(query.trim())
    fun observe(id: Long): Flow<SummaryEntity?> = dao.observeById(id)
    suspend fun save(entity: SummaryEntity): Long = dao.insert(entity)
    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clearUnpinned() = dao.deleteUnpinned()
}
