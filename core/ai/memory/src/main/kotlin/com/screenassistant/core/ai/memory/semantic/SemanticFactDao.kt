package com.screenassistant.core.ai.memory.semantic

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO para embeddings vectoriales de hechos.
 *
 * Esta tabla se carga completa en memoria al iniciar la app.
 * Las búsquedas se hacen en memoria (BruteForceVectorStore),
 * NO en SQL — los vectores no son consultables vía SQL.
 */
@Dao
interface SemanticFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SemanticFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SemanticFactEntity>)

    @Query("SELECT * FROM semantic_facts")
    suspend fun getAll(): List<SemanticFactEntity>

    @Query("SELECT * FROM semantic_facts WHERE factId = :factId")
    suspend fun getByFactId(factId: String): SemanticFactEntity?

    @Query("DELETE FROM semantic_facts WHERE factId = :factId")
    suspend fun deleteByFactId(factId: String)

    @Query("DELETE FROM semantic_facts")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM semantic_facts")
    suspend fun count(): Int
}
