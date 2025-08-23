package nipah.edify

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.client.render.createBatch
import nipah.edify.utils.TickScheduler
import nipah.edify.utils.collectNeighborsWithCornersUpFirst
import nipah.edify.utils.preventNextUniversalEventFromRemovingBlock

object WorldData {
    val chunkData = mutableMapOf<ChunkPos, ChunkData>()
    private val scans = mutableMapOf<Level, GroupScanWorker>()
    private fun getScanWorker(level: Level): GroupScanWorker {
        return scans.getOrPut(level) {
            GroupScanWorker(
                ChunkAccess(level)
            )
        }
    }

    init {
        ChunkEvents.listenToBatchedBlockChanges { changes ->
            val added = changes
                .mapNotNull { it.takeIf { it.third is BlockChangeKind.Placed }?.second }
            val removed = changes
                .filter { it.third is BlockChangeKind.Broken }
            val landed = changes
                .filter { it.third is BlockChangeKind.Landed }

            if (removed.isEmpty()) return@listenToBatchedBlockChanges
            onBlocksRemoved(
                removed.map { it.second },
                getScanWorker(removed.first().first.level!!)
            )
        }
    }

    fun mapChunk(chunk: LevelChunk) {
        val chunkPos = chunk.pos
        val mapped = ChunkData(chunkPos, chunk)
        chunkData[chunkPos] = mapped
    }

    private fun onBlocksRemoved(removed: List<BlockPos>, scanWorker: GroupScanWorker) = TickScheduler.serverScope.launch {
        val removedArray = removed.toTypedArray()
        val seed = listOf(
            *removedArray,
            *removed.flatMap { it.collectNeighborsWithCornersUpFirst() }.toTypedArray()
        )

        val toRemove = scanWorker.scan(seed) ?: return@launch

        fallingFun(scanWorker.chunks, toRemove)
    }

    private fun fallingFun(chunks: ChunkAccess, fall: List<BlockPos>) {
        if (fall.isEmpty()) return

        val level = chunks.level

        createBatch(
            level,
            fall
        )

        for (bpos in fall) {
            val chunk = chunks.at(bpos) ?: continue
            val block = chunk.getBlockState(bpos)
            if (block.isAir || block.isEmpty) {
                continue
            }
            level.preventNextUniversalEventFromRemovingBlock()
            level.destroyBlock(bpos, false)
        }
    }

    fun unloadChunkData(chunkPos: ChunkPos) {
        chunkData.remove(chunkPos)
    }
}
