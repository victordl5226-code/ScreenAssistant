package com.screenassistant.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): com.screenassistant.core.domain.model.Memory = com.screenassistant.core.domain.model.Memory(
        id = id, content = content, timestamp = timestamp
    )

    companion object {
        fun fromDomain(memory: com.screenassistant.core.domain.model.Memory): MemoryEntity = MemoryEntity(
            id = memory.id, content = memory.content, timestamp = memory.timestamp
        )
    }
}
