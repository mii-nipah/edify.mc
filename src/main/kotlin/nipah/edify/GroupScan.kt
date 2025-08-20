package nipah.edify

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.WorldData.chunkData
import nipah.edify.utils.*

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = 100_000,
    private val scanPerTick: Int = 10_000,
) {
    private val toVisit = LongArrayFIFOQueue(50_000)
    private val visited = LongOpenHashSet(1_000_000)
    private val group = LongOpenHashSet(300_000)
    private val metaGroup = LongOpenHashSet(300_000)

    fun clear() {
        toVisit.clear()
        visited.clear()
        group.clear()
    }

    private var currentJob: Job? = null
    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        val seed = if (seed.size > 1000) seed.takeRandomNPercentile(0.01f) else seed
        currentJob?.cancel()
        clear()
        var matched = 0
        for (pos in seed) {
            toVisit.enqueue(pos.asLong())
            val ogGroupSize = group.size
            currentJob = mapGroup()
            try {
                currentJob?.join()
                if (ogGroupSize != group.size) {
                    matched++
                    if (matched >= 2) {
                        break
                    }
                }
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
        }

        var iter = 0
        var tickIter = toVisit.size()

        metaGroup.clear()

        val pos = BlockPos.MutableBlockPos()
        while (toVisit.isNotEmpty() && isActive) {
            iter++
            if (iter >= limit) {
                return@launch
            }
            if (tickIter > scanPerTick) {
                tickIter = 0
                nextServerTick()
            }

            val longPos = toVisit.dequeueLong()
            if (longPos in visited) continue
            pos.set(longPos)
            val chunk = chunks.at(pos) ?: continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) continue
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroup.add(longPos)
            }
            else {
                val xpos = pos
                return@launch
            }

            pos.forEachNeighborNoAlloc { pos ->
                val longPos = pos.asLong()
                if (longPos in visited) return@forEachNeighborNoAlloc
                toVisit.enqueue(longPos)
            }
        }
        group.addAll(metaGroup)
    }
}
