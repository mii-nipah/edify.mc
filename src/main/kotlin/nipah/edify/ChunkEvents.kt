package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import nipah.edify.client.render.BatchRenderer
import nipah.edify.events.UniversalBlockEvent
import nipah.edify.gizmos.Depth
import nipah.edify.gizmos.Gizmos
import nipah.edify.types.BlockResistance
import nipah.edify.types.BlockWeight
import nipah.edify.utils.TickScheduler
import java.util.*

object ChunkEvents {
//    @SubscribeEvent
//    fun onClientTick(e: ClientTickEvent.Post) {
//        var serverLevel = serverLevel
//        if (serverLevel == null) return
//        val mc = Minecraft.getInstance()
//        val player = mc.player ?: return
//        val level = player.level() ?: return
//        if (level.isClientSide.not()) return
//
//        val lookingAt = player.pick(20.0, 0.0f, false) as? BlockHitResult ?: return
//        val pos = lookingAt.blockPos
//        val chunkPos = ChunkPos(pos)
//        val cdata = WorldData.getChunkData(serverLevel, chunkPos.toLong()) ?: return
//        val lpos = pos.toLocalPos()
//        val isFoundation = cdata.foundationAt(lpos.x, lpos.y, lpos.z)
//        if (isFoundation) {
//            Gizmos.block(pos, Gizmos.Color.green)
//        }
//        val closestFoundations = cdata.findClosestFoundations(lpos.x, lpos.y, lpos.z, 300)
//        for (wpos in closestFoundations) {
//            Gizmos.block(wpos, Gizmos.Color.yellow, Depth.XRAY)
//        }
//    }

    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Post) {
        val map = WorldData.structure ?: return
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

    private var serverLevel: ServerLevel? = null

    @SubscribeEvent
    fun onChunkLoad(e: ChunkEvent.Load) {
        if (e.level.isClientSide) return
        serverLevel = e.level as? ServerLevel
        val pos = e.chunk.pos
        TickScheduler.scheduleServer(10) {
            val chunk = e.level.chunkSource.getChunkNow(pos.x, pos.z)
            if (chunk != null) {
                WorldData.mapChunk(chunk)
            }
        }
    }

    private val unloadListeners = mutableListOf<(ChunkPos) -> Unit>()
    private val weakUnloadListeners = WeakHashMap<Any, (ChunkPos) -> Unit>()
    fun listenToChunkUnload(listener: (ChunkPos) -> Unit): () -> Unit {
        unloadListeners.add(listener)
        return {
            unloadListeners.remove(listener)
        }
    }

    fun listenToChunkUnloadWeak(owner: Any, listener: (ChunkPos) -> Unit) {
        weakUnloadListeners[owner] = listener
    }

    @SubscribeEvent
    fun onChunkUnload(e: ChunkEvent.Unload) {
        if (e.level.isClientSide) return
        val pos = e.chunk.pos
        WorldData.unloadChunkData(e.level, pos)

        for (listener in unloadListeners) {
            listener(pos)
        }
        for (listener in weakUnloadListeners.values) {
            listener(pos)
        }
    }

    private val queued = mutableSetOf<Triple<LevelChunk, BlockPos, BlockChangeKind>>()
    private val listeners = mutableListOf<(LevelChunk, BlockPos, BlockChangeKind) -> Unit>()
    private val batchedListeners = mutableListOf<(List<Triple<LevelChunk, BlockPos, BlockChangeKind>>) -> Unit>()
    fun listenToBlockChanges(listener: (LevelChunk, BlockPos, BlockChangeKind) -> Unit) {
        listeners.add(listener)
    }

    fun listenToBatchedBlockChanges(listener: (List<Triple<LevelChunk, BlockPos, BlockChangeKind>>) -> Unit) {
        batchedListeners.add(listener)
    }

    private val ticksBetweenBatches =
        Configs.startup.chunkEvents.ticksToBatchRemovalOperations.get()

    private var serverTicks = 0

    @SubscribeEvent
    fun onServerTick(ev: ServerTickEvent.Post) {
        if (serverTicks % ticksBetweenBatches == 0) {
            if (queued.isEmpty().not()) {
                for (cp in queued) {
                    // Notify batched listeners
                    val batch = queued.toList()
                    for (batchedListener in batchedListeners) {
                        batchedListener(batch)
                    }
                    // Notify listeners
                    for (listener in listeners) {
                        listener(cp.first, cp.second, cp.third)
                    }
                }
                queued.clear()
            }
        }

        serverTicks++
//        if (serverTicks % 5 != 0) return
        BatchRenderer.tick()
        for (batch in BatchRenderer.batches) {
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
}

sealed class BlockChangeKind {
    object Placed: BlockChangeKind()
    object Broken: BlockChangeKind()
    object Landed: BlockChangeKind()
}
