package nipah.edify

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.client.render.createBatch
import nipah.edify.utils.TickScheduler
import nipah.edify.utils.collectNeighborsWithCornersUpFirst

object WorldData {
    val chunkData = mutableMapOf<ChunkPos, ChunkData>()
    private val scans = mutableMapOf<Level, GroupScan>()
    private fun getScan(level: Level): GroupScan {
        return scans.getOrPut(level) {
            GroupScan(
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
                getScan(removed.first().first.level!!)
            )
        }
    }

    fun mapChunk(chunk: LevelChunk) {
        val chunkPos = chunk.pos
        val mapped = ChunkData(chunkPos, chunk)
        chunkData[chunkPos] = mapped
    }

    private fun onBlocksRemoved(removed: List<BlockPos>, scan: GroupScan) = TickScheduler.serverScope.launch {
        val removedArray = removed.toTypedArray()
        val seed = listOf(
            *removedArray,
            *removed.flatMap { it.collectNeighborsWithCornersUpFirst() }.toTypedArray()
        )

        val toRemove = scan.scan(seed) ?: return@launch

        fallingFun(scan.chunks, toRemove)
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
            level.destroyBlock(bpos, false)
        }
    }

    fun unloadChunkData(chunkPos: ChunkPos) {
        chunkData.remove(chunkPos)
    }
}
