package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.block.DebrisBlock
import nipah.edify.chunks.ChunkDebris
import nipah.edify.chunks.getDebrisStateAt
import nipah.edify.chunks.removeDebrisData
import nipah.edify.chunks.setDebrisAt
import nipah.edify.client.render.createBatch
import nipah.edify.utils.TickScheduler
import nipah.edify.utils.preventNextUniversalEventFromRemovingBlock

object WorldData {
    val chunkData = Long2ObjectOpenHashMap<ChunkData>()
    private val scans = mutableMapOf<Level, GroupScanWorker>()
    private fun getScanWorker(level: Level): GroupScanWorker {
        return scans.getOrPut(level) {
            GroupScanWorker(
                ChunkAccess(level),
                Configs.startup.worldData.maxConcurrentScans.get()
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
        chunkData[chunkPos.toLong()] = mapped
    }

    private fun onBlocksRemoved(removed: List<BlockPos>, scanWorker: GroupScanWorker) = TickScheduler.serverScope.launch {
        val removedArray = removed.toTypedArray()
        val seed = listOf(
            *removedArray,
//            *removed.flatMap { it.collectNeighborsWithCornersUpFirst() }.toTypedArray()
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

        data class DebrisToMove(
            val pos: BlockPos,
            val entry: ChunkDebris.Entry,
        )

        val debrisToMove = mutableListOf<DebrisToMove>()

        for (bpos in fall) {
            val chunk = chunks.at(bpos) ?: continue
            val block = chunk.getBlockState(bpos)
            if (block.isAir || block.isEmpty) {
                continue
            }

            if (block.block is DebrisBlock) {
                val entry = level.getDebrisStateAt(bpos)
                if (entry != null) {
                    level.removeDebrisData(bpos)
                    debrisToMove.add(DebrisToMove(bpos, entry))
                }
            }

            level.preventNextUniversalEventFromRemovingBlock()
            level.destroyBlock(bpos, false)
        }
        for ((pos, debris) in debrisToMove) {
            for (state in debris.toBlockStateList()) {
                level.setDebrisAt(pos, state)
            }
        }
    }

    fun unloadChunkData(chunkPos: ChunkPos) {
        chunkData.remove(chunkPos.toLong())
    }
}
