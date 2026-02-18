package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import nipah.edify.events.UniversalBlockEvent
import nipah.edify.gizmos.Depth
import nipah.edify.gizmos.Gizmos
import nipah.edify.types.BlockResistance
import nipah.edify.types.BlockWeight
import nipah.edify.utils.TickScheduler
import java.util.*

object ChunkEvents {

    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Post) {
        val map = session?.worldData?.structure ?: return
        try {
            map.forEach { pos, state, pressure ->
                val w = BlockWeight.of(state)
                val r = BlockResistance.of(state)

                val maxPressure = w.value * r.value.f
                val ratio = (pressure / maxPressure).coerceIn(0f, 1f)

                val color = Gizmos.Color.lerp(Gizmos.Color.green, Gizmos.Color.red, ratio)
                Gizmos.block(pos, color, Depth.DEPTH_TEST)
            }
        }
        catch (_: ConcurrentModificationException) {
        }
        catch (_: NullPointerException) {
        }
    }

    @SubscribeEvent
    fun onChunkLoad(e: ChunkEvent.Load) {
        if (e.level.isClientSide) return
        val pos = e.chunk.pos
        TickScheduler.scheduleServer(10) {
            val chunk = e.level.chunkSource.getChunkNow(pos.x, pos.z) ?: return@scheduleServer
            for (listener in weakChunkLoadListeners.values) listener(chunk)
        }
    }

    private val weakUnloadListeners = WeakHashMap<Any, (LevelAccessor, ChunkPos) -> Unit>()
    private val weakBlockRemovedListeners = WeakHashMap<Any, (UniversalBlockEvent.BlockRemovedBatch) -> Unit>()
    private val weakServerTickListeners = WeakHashMap<Any, (ServerTickEvent.Post) -> Unit>()
    private val weakBatchedListeners = WeakHashMap<Any, (List<Triple<LevelChunk, BlockPos, BlockChangeKind>>) -> Unit>()
    private val weakChunkLoadListeners = WeakHashMap<Any, (LevelChunk) -> Unit>()

    fun listenToChunkUnloadWeak(owner: Any, listener: (LevelAccessor, ChunkPos) -> Unit) {
        weakUnloadListeners[owner] = listener
    }

    fun listenToBlockRemovedWeak(owner: Any, listener: (UniversalBlockEvent.BlockRemovedBatch) -> Unit) {
        weakBlockRemovedListeners[owner] = listener
    }

    fun listenToServerTickWeak(owner: Any, listener: (ServerTickEvent.Post) -> Unit) {
        weakServerTickListeners[owner] = listener
    }

    fun listenToBatchedBlockChangesWeak(owner: Any, listener: (List<Triple<LevelChunk, BlockPos, BlockChangeKind>>) -> Unit) {
        weakBatchedListeners[owner] = listener
    }

    fun listenToChunkLoadWeak(owner: Any, listener: (LevelChunk) -> Unit) {
        weakChunkLoadListeners[owner] = listener
    }

    @SubscribeEvent
    fun onChunkUnload(e: ChunkEvent.Unload) {
        if (e.level.isClientSide) return
        val pos = e.chunk.pos
        for (listener in weakUnloadListeners.values) listener(e.level, pos)
    }

    private val queued = mutableSetOf<Triple<LevelChunk, BlockPos, BlockChangeKind>>()
    private var serverTicks = 0

    @SubscribeEvent
    fun onServerTick(ev: ServerTickEvent.Post) {
        for (listener in weakServerTickListeners.values) listener(ev)
        val ticksBetweenBatches = Configs.common.chunkEvents.ticksToBatchRemovalOperations.get()
        if (serverTicks % ticksBetweenBatches == 0) {
            if (queued.isNotEmpty()) {
                val batch = queued.toList()
                for (listener in weakBatchedListeners.values) listener(batch)
                queued.clear()
            }
        }
        serverTicks++
        val session = session ?: return
        session.batchRenderer.tick()
        for (batch in session.batchRenderer.batches) {
            batch.tickServer(
                ev.server.getLevel(batch.levelKey) ?: continue
            )
        }
    }

    @SubscribeEvent
    fun onBlockPlaced(e: BlockEvent.EntityPlaceEvent) {
        val chunk = e.level.getChunk(e.pos).let {
            e.level.chunkSource.getChunkNow(it.pos.x, it.pos.z)
        } ?: return
        queued.add(Triple(chunk, e.pos, BlockChangeKind.Placed))
    }

    @SubscribeEvent
    fun onFallingBlockLands(e: BlockEvent.EntityPlaceEvent) {
        if (e.entity is FallingBlockEntity) {
            val ent = e.entity as FallingBlockEntity
            val state = ent.blockState
            val pos = e.pos
            val chunk = e.level.getChunk(pos).let {
                e.level.chunkSource.getChunkNow(it.pos.x, it.pos.z)
            } ?: return
            queued.add(Triple(chunk, pos, BlockChangeKind.Landed))
        }
    }

    @SubscribeEvent
    fun onRemoveBatch(e: UniversalBlockEvent.BlockRemovedBatch) {
        for (listener in weakBlockRemovedListeners.values) listener(e)
        var blocks = e.blocks
        if (blocks.isEmpty()) return
        if (blocks.size > 60) {
            val percentile = when (blocks.size) {
                in 51..100 -> 0.3
                in 101..150 -> 0.15
                in 151..300 -> 0.1
                in 301..500 -> 0.05
                else -> 0.01
            }
            val percentileCount = (blocks.size * percentile).toInt().let {
                if (it < 1) 1 else it
            }
            val randomBlocks = blocks.asList().shuffled().take(percentileCount)
            blocks = randomBlocks.toLongArray()
        }

        for (block in blocks) {
            val pos = BlockPos.of(block)
            val chunk = e.level.getChunk(pos).let {
                e.level.chunkSource.getChunkNow(it.pos.x, it.pos.z)
            } ?: continue
            queued.add(Triple(chunk, pos, BlockChangeKind.Broken))
        }
    }

    internal fun onSessionStart() {
        queued.clear()
        serverTicks = 0
        weakUnloadListeners.clear()
        weakBlockRemovedListeners.clear()
        weakServerTickListeners.clear()
        weakBatchedListeners.clear()
        weakChunkLoadListeners.clear()
    }
}

sealed class BlockChangeKind {
    object Placed: BlockChangeKind()
    object Broken: BlockChangeKind()
    object Landed: BlockChangeKind()
}
