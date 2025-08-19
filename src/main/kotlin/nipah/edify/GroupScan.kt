package nipah.edify

import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.WorldData.chunkData
import nipah.edify.utils.TickScheduler
import nipah.edify.utils.findNeighbor
import nipah.edify.utils.forEachNeighbor
import nipah.edify.utils.worldToLocalPos

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = 100_000,
) {
    private val toVisit = ArrayDeque<BlockPos>()
    private val visited = mutableSetOf<Long>()
    private val group = mutableSetOf<Long>()

    fun clear() {
        toVisit.clear()
        visited.clear()
        group.clear()
    }

    private var currentJob: Job? = null
    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        currentJob?.cancel()
        clear()
        for (pos in seed) {
            toVisit.add(pos)
            currentJob = mapGroup()
            try {
                currentJob?.join()
            }
            catch (_: Throwable) {
                return null
            }
            finally {
                currentJob = null
            }
        }
        return group.map { BlockPos.of(it) }
    }

    private fun mapGroup() = TickScheduler.serverScope.launch {
        fun inFoundation(pos: BlockPos): Boolean {
            val chunk = chunks.at(pos) ?: return false
            val lpos = chunk.worldToLocalPos(pos)
            val cdata = chunkData[chunk.pos] ?: return false
            return cdata.foundationAt(lpos.x, lpos.y, lpos.z)
                    || lpos.findNeighbor { npos ->
                val neighborData = chunkData[chunk.pos] ?: return@findNeighbor false
                neighborData.foundationAt(npos.x, npos.y, npos.z)
            } != null
        }

        var iter = 0

        val metaGroup = mutableSetOf<Long>()

        while (toVisit.isNotEmpty() && isActive) {
            iter++
            if (iter >= limit) {
                return@launch
            }
            val pos = toVisit.removeFirst()
            val longPos = pos.asLong()
            if (longPos in visited) continue
            val chunk = chunks.at(pos) ?: continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) continue
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroup.add(longPos)
            }
            else {
                return@launch
            }

            pos.forEachNeighbor { pos ->
                if (pos.asLong() in visited) return@forEachNeighbor
                toVisit.add(pos)
            }
        }
        group.addAll(metaGroup)
    }
}
