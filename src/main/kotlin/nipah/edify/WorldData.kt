package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import nipah.edify.block.DebrisBlock
import nipah.edify.chunks.ChunkDebris
import nipah.edify.chunks.getDebrisStateAt
import nipah.edify.chunks.removeDebrisData
import nipah.edify.chunks.setDebrisAt
import nipah.edify.client.render.createBatch
import nipah.edify.collections.ConcurrentUniqueQueue
import nipah.edify.events.UniversalBlockEvent
import nipah.edify.utils.TickScheduler
import nipah.edify.utils.forEachNeighborNoAlloc
import nipah.edify.utils.nearbyPos
import nipah.edify.utils.not
import nipah.edify.utils.pickItem
import nipah.edify.utils.preventNextUniversalEventFromRemovingBlock
import nipah.edify.utils.toLocalX
import nipah.edify.utils.toLocalZ
import kotlin.collections.set
import kotlin.random.Random

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

    data class QueuedRemoval(val seed: List<BlockPos>, val worker: GroupScanWorker)

    private var tickCounter = 0
    private var removalQueue = ConcurrentUniqueQueue<QueuedRemoval>()

    @SubscribeEvent
    fun serverTick(e: ServerTickEvent.Post) {
        tickCounter++
        if (tickCounter % 20 == 0) {
            val server = e.server
            val player = Random.pickItem(server.playerList.players) ?: return
            val level = player.level()
            val pos = player.blockPosition()
            val rpos = Random.nearbyPos(pos, 50, -30..30)
            val block = level.getBlockState(rpos)
            if (block.isAir || block.isEmpty) return
//            TickScheduler.serverScope.launch {
//                applyIntegrityScan(rpos, level)
//            }
        }
        if (tickCounter % 10 == 0) {
            if (removalQueue.size > 1000) {
                removalQueue =
                    ConcurrentUniqueQueue(removalQueue.toList().distinct().shuffled().take(100))
            }

            removalQueue.peekToConsume { (seed, worker) ->
                if (worker.isAvailable().not) {
                    false
                }
                else {
                    onBlocksRemoved(seed, worker)
                    true
                }
            }
        }
    }

    init {
        ChunkEvents.listenToBatchedBlockChanges { changes ->
            val added = changes
                .mapNotNull { it.takeIf { it.third is BlockChangeKind.Placed }?.second }
            if (added.isNotEmpty()) {
                onBlocksAdded(added, changes.first().first.level!!)
            }

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

    private val integrityScan by lazy {
        IntegrityScan(
            ChunkAccess(ServerLifecycleHooks.getCurrentServer()!!.getLevel(Level.OVERWORLD)!!)
        )
    }
    var map: IntegrityScan.Map? = null
    var toRemoveMap: Set<BlockPos>? = null

    fun onBlocksAdded(added: List<BlockPos>, level: Level) = TickScheduler.serverScope.launch {
        applyIntegrityScan(added.first(), level)
    }

    suspend fun applyIntegrityScan(at: BlockPos, level: Level) {
        val (removed, map) = integrityScan.scan(at)
        this.map = map
        this.toRemoveMap = removed.toSet()

//        removed.forEach { pos ->
//            level.destroyBlock(pos, false)
//        }
    }

    fun mapChunk(chunk: LevelChunk) {
        val level = chunk.level ?: return
        val chunkPos = chunk.pos
        val mapped = ChunkData(chunkPos, chunk)
        setChunkData(level, chunkPos.toLong(), mapped)
    }

    private fun onBlocksRemoved(removed: List<BlockPos>, scanWorker: GroupScanWorker) = TickScheduler.serverScope.launch {
        val seed = removed.distinct()
        if (scanWorker.isAvailable().not) {
            removalQueue.add(QueuedRemoval(seed, scanWorker))
            return@launch
        }

        run {
            val frem = removed.first()
            var useF: BlockPos? = null
            frem.forEachNeighborNoAlloc { npos ->
                val chunk = scanWorker.chunks.at(npos) ?: return@forEachNeighborNoAlloc
                val block = chunk.getBlockState(npos)
                if (block.isAir || block.isEmpty || block.block is LiquidBlock) {
                    return@forEachNeighborNoAlloc
                }
                if (useF == null) {
                    useF = npos.immutable()
                }
            }
            if (useF != null) {
                applyIntegrityScan(useF, scanWorker.chunks.level)
            }
        }

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
