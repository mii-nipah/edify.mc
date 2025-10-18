package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.utils.TickScheduler

data class ChunkAccess(val level: Level) {
    private val chunks = Long2ObjectOpenHashMap<LevelChunk>()

    init {
        ChunkEvents.listenToChunkUnloadWeak(this) { cpos ->
            chunks.remove(cpos.toLong())
        }
    }

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

    fun getBlockStateAt(blockPos: BlockPos) =
        at(blockPos)?.getBlockState(blockPos)

    suspend fun backgroundAt(blockPos: BlockPos): LevelChunk? {
        val longChunkPos = ChunkPos.asLong(blockPos)
        val chunk = chunks[longChunkPos]
        if (chunk == null) {
            return withContext(TickScheduler.ServerDispatcher) {
                val loadedChunk = level.chunkSource.getChunkNow(
                    ChunkPos.getX(longChunkPos), ChunkPos.getZ(longChunkPos)
                )
                chunks[longChunkPos] = loadedChunk
                loadedChunk
            }
        }
        return chunk
    }
}

