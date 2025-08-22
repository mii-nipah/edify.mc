package nipah.edify

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.WorldData.chunkData
import nipah.edify.utils.*
import kotlin.coroutines.coroutineContext

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = 100_000,
    private val scanPerTick: Int = 10_000,
    private val blocksPerFloatingSupports: Int = 3,
    private val floatingSupportsNaturalIslandLimit: Int = 5_000,
) {
    private val toVisit = LongArrayFIFOQueue(50_000)
    private val toVisitWeak = LongArrayFIFOQueue(50_000)
    private val visited = LongOpenHashSet(1_000_000)
    private val group = LongOpenHashSet(300_000)
    private val metaGroup = LongOpenHashSet(300_000)
    private val metaGroupWeak = LongOpenHashSet(300_000)

    fun clear() {
        toVisit.clear()
        visited.clear()
        group.clear()
    }

    private var currentJob: Job? = null
    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        val seed = if (seed.size > 1000) seed.takeRandomNPercentile(0.01f) else seed
        currentJob?.cancelAndJoin()
        clear()
        var matched = 0
        for (pos in seed) {
            toVisitWeak.clear()
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

    private fun inFoundation(pos: BlockPos): Boolean {
        val chunk = chunks.at(pos) ?: return false
        val lpos = chunk.worldToLocalPos(pos)
        val cdata = chunkData[chunk.pos] ?: return false
        return cdata.foundationAt(lpos.x, lpos.y, lpos.z)
    }

    private fun mapGroup() = TickScheduler.serverScope.launch {
        var iter = 0
        var tickIter = toVisit.size()

        metaGroup.clear()

        var floatingSupports = 0

        val pos = BlockPos.MutableBlockPos()
        while (toVisit.isNotEmpty() && isActive) {
            iter++
            if (iter >= limit) {
                return@launch
            }

            val longPos = toVisit.dequeueLong()
            if (longPos in visited) continue

            tickIter++
            if (tickIter > scanPerTick) {
                tickIter = 0
                nextServerTick()
            }

            pos.set(longPos)
            val chunk = chunks.at(pos) ?: continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) continue
            if (block.isNonSupporting()) {
                toVisitWeak.enqueue(longPos)
                continue
            }
            if (block.isFloating()) {
                floatingSupports++
                if (floatingSupports > floatingSupportsNaturalIslandLimit) {
                    return@launch
                }
            }
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroup.add(longPos)
            }
            else {
                return@launch
            }

            pos.forEachNeighborNoAlloc { pos ->
                val longPos = pos.asLong()
                if (longPos in visited) return@forEachNeighborNoAlloc
                toVisit.enqueue(longPos)
            }
        }
        val blocksPerSupport =
            (metaGroup.size / blocksPerFloatingSupports.coerceAtLeast(1))
                .coerceAtLeast(1)
        if (floatingSupports >= blocksPerSupport) {
            return@launch
        }
        mapGroupWeak(iter, tickIter)
        group.addAll(metaGroup)
    }

    private suspend fun mapGroupWeak(startWithIters: Int, startWithTicks: Int) {
        coroutineContext.ensureActive()

        var iter = startWithIters
        var tickIter = startWithTicks

        metaGroupWeak.clear()

        val pos = BlockPos.MutableBlockPos()
        while (toVisitWeak.isNotEmpty() && coroutineContext.isActive) {
            iter++
            if (iter >= limit) {
                return
            }

            val longPos = toVisitWeak.dequeueLong()
            if (longPos in visited) continue

            tickIter++
            if (tickIter > scanPerTick) {
                tickIter = 0
                nextServerTick()
            }

            pos.set(longPos)
            val chunk = chunks.at(pos) ?: continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) continue
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroupWeak.add(longPos)
            }
            else {
                return
            }

            pos.forEachNeighborNoAlloc { pos ->
                val longPos = pos.asLong()
                if (longPos in visited) return@forEachNeighborNoAlloc
                toVisitWeak.enqueue(longPos)
            }
        }
        group.addAll(metaGroupWeak)
    }
}
