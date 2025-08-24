package nipah.edify

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.WorldData.chunkData
import nipah.edify.utils.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.coroutineContext

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = 50_000,
    private val scanPerTick: Int = 1_000,
    private val blocksPerFloatingSupports: Int = 3,
    private val floatingSupportsNaturalIslandLimit: Int = 5_000,
) {
    companion object {
        val currentlyScanning = CopyOnWriteArrayList<GroupScan>()
    }

    private val toVisit = LongArrayFIFOQueue(limit * 2)
    private val toVisitWeak = LongArrayFIFOQueue(limit)
    private val visited = LongOpenHashSet(limit * 10, 0.4f)
    private val group = LongOpenHashSet(limit * 2)
    private val metaGroup = LongOpenHashSet(limit * 2)
    private val metaGroupWeak = LongOpenHashSet(limit * 2)

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
            val seedX = seed
            toVisitWeak.clear()
            toVisit.enqueue(pos.asLong())
            val ogGroupSize = group.size
            currentJob = mapGroup()
            try {
                currentlyScanning.add(this)
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
                currentlyScanning.remove(this)
            }
        }
        return group.map { BlockPos.of(it) }
    }

    private fun inFoundation(pos: BlockPos): Boolean {
        val chunkPos = ChunkPos.asLong(pos)
        val lpos = pos.toLocalPos()
        val cdata = chunkData[chunkPos] ?: return false
        return cdata.foundationAt(lpos.x, lpos.y, lpos.z)
    }

    private fun mapGroup() = TickScheduler.serverScope.launch {
        val level = chunks.level
        var iter = 0
        var tickIter = toVisit.size()

        metaGroup.clear()

        var floatingSupports = 0
        var capturedBlocks = 0

        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()
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
            if (level.isLoaded(pos).not()) continue
            val block = level.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) continue
            if (block.isNonSupporting()) {
                toVisitWeak.enqueue(longPos)
                continue
            }
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroup.add(longPos)
                if (block.isFloating()) {
                    floatingSupports++
                    if (floatingSupports > floatingSupportsNaturalIslandLimit) {
                        return@launch
                    }
                }
                else {
                    capturedBlocks++
                }
            }
            else {
                return@launch
            }

            pos.forEachNeighborNoAlloc(npos) { npos ->
                val longPos = npos.asLong()
                if (longPos in visited) return@forEachNeighborNoAlloc
                toVisit.enqueue(longPos)
            }
        }
        if (floatingSupports * blocksPerFloatingSupports >= capturedBlocks.coerceAtLeast(1)) {
            return@launch
        }
        mapGroupWeak(iter, tickIter)
        group.addAll(metaGroup)
    }

    private suspend fun mapGroupWeak(startWithIters: Int, startWithTicks: Int) {
        coroutineContext.ensureActive()

        val level = chunks.level

        var iter = startWithIters
        var tickIter = startWithTicks

        metaGroupWeak.clear()

        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()
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
            val block = level.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) continue
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroupWeak.add(longPos)
            }
            else {
                return
            }

            pos.forEachNeighborNoAlloc(npos) { npos ->
                val longPos = npos.asLong()
                if (longPos in visited) return@forEachNeighborNoAlloc
                toVisitWeak.enqueue(longPos)
            }
        }
        group.addAll(metaGroupWeak)
    }
}
