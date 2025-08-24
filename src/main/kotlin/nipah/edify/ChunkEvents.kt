package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import nipah.edify.client.render.BatchRenderer
import nipah.edify.events.UniversalBlockEvent
import nipah.edify.utils.TickScheduler

object ChunkEvents {
    @SubscribeEvent
    fun onChunkLoad(e: ChunkEvent.Load) {
        if (e.level.isClientSide) return
        TickScheduler.scheduleServer(10) {
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

    private var serverTicks = 0

    @SubscribeEvent
    fun onHud(e: RenderGuiLayerEvent.Post) {
        // Draw after a vanilla layer; HOTBAR is a good anchor
        val gg = e.guiGraphics
        val mc = net.minecraft.client.Minecraft.getInstance()
        val font = mc.font
        // text
        var i = 0
        for (scan in GroupScan.currentlyScanning.distinct()) {
            val id = scan.hashCode()

            gg.drawString(font, "Scan#$id [ACTIVE]", 10, 30 + i * 10, 0xFFFFFFFF.toInt())

            i++
        }
    }

    @SubscribeEvent
    fun onServerTick(ev: ServerTickEvent.Post) {
        if (serverTicks % 5 == 0) {
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
        if (blocks.size > 30) {
            val percentile = when (blocks.size) {
                in 31..50 -> 0.5
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
