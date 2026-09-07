package com.example.felezjoo.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}

@Dao
interface DecayBlockDao {
    @Query("SELECT * FROM decay_blocks WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    suspend fun getBlocksForSession(sessionId: String): List<DecayBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: DecayBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<DecayBlockEntity>)

    @Query("DELETE FROM decay_blocks WHERE sessionId = :sessionId")
    suspend fun deleteBlocksForSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM decay_blocks")
    suspend fun getTotalBlockCount(): Int
}

@Dao
interface TargetEventDao {
    @Query("SELECT * FROM target_events WHERE sessionId = :sessionId ORDER BY startTimeMs DESC")
    fun getEventsForSession(sessionId: String): Flow<List<TargetEventEntity>>

    @Query("SELECT * FROM target_events ORDER BY startTimeMs DESC")
    fun getAllEvents(): Flow<List<TargetEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TargetEventEntity)

    @Query("DELETE FROM target_events WHERE id = :id")
    suspend fun deleteEvent(id: String)
}

@Dao
interface ExperimentDao {
    @Query("SELECT * FROM experiments ORDER BY timestamp DESC")
    fun getAllExperiments(): Flow<List<ExperimentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiment(exp: ExperimentEntity)

    @Delete
    suspend fun deleteExperiment(exp: ExperimentEntity)
}

@Database(
    entities = [
        SessionEntity::class,
        DecayBlockEntity::class,
        TargetEventEntity::class,
        ExperimentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun decayBlockDao(): DecayBlockDao
    abstract fun targetEventDao(): TargetEventDao
    abstract fun experimentDao(): ExperimentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "felezjoo_research_v2.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
