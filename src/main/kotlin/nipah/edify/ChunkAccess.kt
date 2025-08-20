package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk

data class ChunkAccess(val level: Level) {
    private val chunks = Long2ObjectOpenHashMap<LevelChunk>()

    private inline fun BlockPos.toChunkPosNoAlloc(): Long {
        return ChunkPos.asLong(this)
    }

    fun at(chunkPos: ChunkPos): LevelChunk? {
        return at(chunkPos.toLong())
    }

    fun at(chunkPos: Long): LevelChunk? {
        return chunks[chunkPos]
            ?: level.getChunk(ChunkPos.getX(chunkPos), ChunkPos.getZ(chunkPos))
                .also { chunks[chunkPos] = it }
    }

    fun at(blockPos: BlockPos): LevelChunk? {
        return at(ChunkPos.asLong(blockPos))
    }
}
