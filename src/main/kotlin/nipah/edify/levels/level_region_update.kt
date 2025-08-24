package nipah.edify.levels

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.utils.preventNextUniversalEventFromRemovingBlock

data class BlockRegionResult(val changedCount: Int)

class BlockRegionScope internal constructor(
    private val level: ServerLevel,
    private val options: Options,
) {
    data class Options(
        val syncClients: Boolean = true,
        val notifyNeighbors: Boolean = true,
        val updateLight: Boolean = true,
        val knownShape: Boolean = false,
    )

    private data class Change(var prev: BlockState, var next: BlockState)

    private val changes = Long2ObjectOpenHashMap<Change>(256, 0.75f)

    /**
     * Queues a block change using UPDATE_NONE immediately (fast, silent).
     * Repeated calls to the same pos within the region collapse to the final state.
     */
    fun setBlock(pos: BlockPos, state: BlockState): Boolean {
        val server = level
        val prev = server.getBlockState(pos)
        if (prev === state) return false
        // Build phase: do the actual write but suppress neighbor + client updates.
        val changed = server.setBlock(pos, state, Block.UPDATE_NONE)
        if (!changed) return false

        val key = pos.asLong()
        val existing = changes.get(key)
        if (existing == null) {
            // Keep the first 'prev' we ever saw in this region; update 'next' as we go.
            changes.put(key, Change(prev, state))
        }
        else {
            existing.next = state
        }
        return true
    }

    fun setBlockNeverNotify(pos: BlockPos, state: BlockState): Boolean {
        level.preventNextUniversalEventFromRemovingBlock()
        return setBlock(pos, state)
    }

    /** Flush once: send client updates, neighbor notifications, and light fixes. */
    internal fun flush(): BlockRegionResult {
        if (changes.isEmpty()) return BlockRegionResult(0)
        val server = level
        val flagClients = if (options.knownShape) (Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE) else Block.UPDATE_CLIENTS

        changes.long2ObjectEntrySet().forEach { entry ->
            val posLong = entry.longKey
            val pos = BlockPos.of(posLong)
            val (prev, next) = entry.value

            if (options.syncClients) {
                // Push a single render/state update to clients
                server.sendBlockUpdated(pos, prev, next, flagClients)
            }
            if (options.notifyNeighbors) {
                // Notify neighbors once, after the batch
                server.updateNeighborsAt(pos, next.block)
            }
            if (options.updateLight) {
                // Ensure lighting catches up (cheap no-op if unnecessary)
                server.chunkSource.lightEngine.checkBlock(pos)
            }
        }
        val count = changes.size
        changes.clear()
        return BlockRegionResult(count)
    }
}

fun Level.setBlockRegion(
    syncClients: Boolean = true,
    notifyNeighbors: Boolean = true,
    updateLight: Boolean = true,
    knownShape: Boolean = false,
    block: BlockRegionScope.() -> Unit,
): BlockRegionResult {
    require(this is ServerLevel) { "setBlockRegion must be called on ServerLevel" }
    val scope = BlockRegionScope(this, BlockRegionScope.Options(syncClients, notifyNeighbors, updateLight, knownShape))
    scope.block()
    return scope.flush()
}
