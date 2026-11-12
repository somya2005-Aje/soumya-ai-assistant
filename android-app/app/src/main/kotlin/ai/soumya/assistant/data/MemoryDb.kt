package ai.soumya.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "general",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun search(query: String): List<MemoryEntity>

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Database(entities = [MemoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "soumya_ai.db"
                ).build().also { instance = it }
            }
    }
}

class MemoryRepository(context: Context) {
    private val dao = AppDatabase.get(context).memoryDao()

    suspend fun all(): List<MemoryEntity> = dao.getAll()
    suspend fun search(query: String): List<MemoryEntity> =
        if (query.isBlank()) dao.getAll() else dao.search(query)

    /** Returns null when the content is refused (secret) so callers can explain why. */
    suspend fun save(title: String, content: String, category: String = "general"): MemoryEntity? {
        if (SecretFilter.looksLikeSecret(content) || SecretFilter.looksLikeSecret(title)) return null
        val entity = MemoryEntity(
            title = title.ifBlank { content.take(40) },
            content = content,
            category = category
        )
        val id = dao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun update(memory: MemoryEntity) {
        if (SecretFilter.looksLikeSecret(memory.content)) return
        dao.update(memory)
    }

    suspend fun delete(memory: MemoryEntity) = dao.delete(memory)
    suspend fun deleteAll() = dao.deleteAll()

    fun exportText(memories: List<MemoryEntity>): String =
        memories.joinToString("\n\n") { m ->
            "${m.title} [${m.category}]\n${SecretFilter.redact(m.content)}"
        }
}
