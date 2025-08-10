package nipah.edify

import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk

object WorldData {
    private val chunks = mutableMapOf<ChunkPos, ChunkData>()

    fun getChunkData(chunk: LevelChunk): ChunkData {
        val chunkPos = chunk.pos
        val data = chunks.computeIfAbsent(chunkPos) { ChunkData(chunk) }
        return data
    }

    fun unloadChunkData(chunkPos: ChunkPos) {
        chunks.remove(chunkPos)
    }
}
