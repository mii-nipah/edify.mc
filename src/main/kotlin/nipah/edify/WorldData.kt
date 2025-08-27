package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import nipah.edify.block.DebrisBlock
import nipah.edify.chunks.ChunkDebris
import nipah.edify.chunks.getDebrisStateAt
import nipah.edify.chunks.removeDebrisData
import nipah.edify.chunks.setDebrisAt
import nipah.edify.client.render.createBatch
import nipah.edify.events.UniversalBlockEvent
import nipah.edify.utils.TickScheduler
import nipah.edify.utils.preventNextUniversalEventFromRemovingBlock
import nipah.edify.utils.toLocalX
import nipah.edify.utils.toLocalZ

@EventBusSubscriber
object WorldData {
    private val chunkData = mutableMapOf<LevelAccessor, Long2ObjectOpenHashMap<ChunkData>>()

    private fun setChunkData(level: LevelAccessor, chunkPosLong: Long, data: ChunkData) {
        val map = chunkData.getOrPut(level) { Long2ObjectOpenHashMap() }
        map[chunkPosLong] = data
    }

    fun getChunkData(level: LevelAccessor, chunkPos: ChunkPos) =
        getChunkData(level, chunkPos.toLong())

    fun getChunkData(level: LevelAccessor, chunkPosLong: Long): ChunkData? {
        return chunkData[level]?.get(chunkPosLong)
    }

    private val scans = mutableMapOf<Level, GroupScanWorker>()
    private fun getScanWorker(level: Level): GroupScanWorker {
        return scans.getOrPut(level) {
            GroupScanWorker(
                ChunkAccess(level),
                Configs.startup.worldData.maxConcurrentScans.get()
            )
        }
    }

    @SubscribeEvent
    fun universalBlockRemoval(e: UniversalBlockEvent.BlockRemovedBatch) {
        if (e.level.isClientSide) return
        val pos = BlockPos.MutableBlockPos()
        e.blocks.forEach { lpos ->
            pos.set(lpos)
            val chunk = getChunkData(e.level, ChunkPos.asLong(pos)) ?: return@forEach
            val lposX = pos.toLocalX()
            val lposZ = pos.toLocalZ()
            chunk.setFoundationAt(lposX, pos.y, lposZ, false)
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
        val level = chunk.level ?: return
        val chunkPos = chunk.pos
        val mapped = ChunkData(chunkPos, chunk)
        setChunkData(level, chunkPos.toLong(), mapped)
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

    fun unloadChunkData(level: LevelAccessor, chunkPos: ChunkPos) {
        val chunkData = chunkData[level] ?: return
        chunkData.remove(chunkPos.toLong())
    }
}
