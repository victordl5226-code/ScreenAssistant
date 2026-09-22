package com.screenassistant.core.ai.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO para hechos y preferencias del usuario.
 */
@Dao
interface KnowledgeFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: KnowledgeFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facts: List<KnowledgeFactEntity>)

    @Query("SELECT * FROM knowledge_facts WHERE category = :category ORDER BY confidence DESC")
    suspend fun getByCategory(category: String): List<KnowledgeFactEntity>

    @Query("SELECT * FROM knowledge_facts WHERE subject LIKE '%' || :subject || '%' ORDER BY confidence DESC")
    suspend fun getBySubject(subject: String): List<KnowledgeFactEntity>

    @Query("SELECT * FROM knowledge_facts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<KnowledgeFactEntity>

    @Query("SELECT * FROM knowledge_facts WHERE subject LIKE '%' || :query || '%' OR predicate LIKE '%' || :query || '%' OR `object` LIKE '%' || :query || '%' ORDER BY confidence DESC LIMIT :limit")
    suspend fun searchByQuery(query: String, limit: Int = 50): List<KnowledgeFactEntity>

    @Query("SELECT COUNT(*) FROM knowledge_facts")
    suspend fun count(): Int

    @Query("DELETE FROM knowledge_facts WHERE id = :factId")
    suspend fun delete(factId: String)

    @Query("DELETE FROM knowledge_facts")
    suspend fun clear()
}
