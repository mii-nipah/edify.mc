package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk

data class ChunkAccess(val level: Level) {
    private val chunks = mutableMapOf<ChunkPos, LevelChunk>()

    fun at(chunkPos: ChunkPos): LevelChunk? {
        return chunks[chunkPos] ?: level.getChunk(chunkPos.x, chunkPos.z)
            .also { chunks[chunkPos] = it }
    }

    fun at(blockPos: BlockPos): LevelChunk? {
        val chunkPos = ChunkPos(blockPos)
        return at(chunkPos)
    }
}
