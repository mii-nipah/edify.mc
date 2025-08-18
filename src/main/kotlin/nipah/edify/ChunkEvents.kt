package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import nipah.edify.utils.TickScheduler

object ChunkEvents {
    @SubscribeEvent
    fun onChunkLoad(e: ChunkEvent.Load) {
        if (e.level.isClientSide) return
        TickScheduler.schedule(10) {
            val pos = e.chunk.pos
            val chunk = e.level.chunkSource.getChunkNow(pos.x, pos.z)
            if (chunk != null) {
                WorldData.mapChunk(chunk)
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(e: ChunkEvent.Unload) {
        val pos = e.chunk.pos
        WorldData.unloadChunkData(pos)
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

    @SubscribeEvent
    fun onServerTick(ev: ServerTickEvent.Post) {
        if (queued.isEmpty()) return
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

    @SubscribeEvent
    fun onBlockPlaced(e: BlockEvent.EntityPlaceEvent) {
        val chunk = e.level.getChunk(e.pos).let {
            e.level.chunkSource.getChunkNow(it.pos.x, it.pos.z)
        } ?: return
        queued.add(Triple(chunk, e.pos, BlockChangeKind.Placed))
    }

    @SubscribeEvent
    fun onBlockBroken(e: BlockEvent.BreakEvent) {
        val chunk = e.level.getChunk(e.pos).let {
            e.level.chunkSource.getChunkNow(it.pos.x, it.pos.z)
        } ?: return
        queued.add(Triple(chunk, e.pos, BlockChangeKind.Broken))
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
}

sealed class BlockChangeKind {
    object Placed: BlockChangeKind()
    object Broken: BlockChangeKind()
    object Landed: BlockChangeKind()
}
