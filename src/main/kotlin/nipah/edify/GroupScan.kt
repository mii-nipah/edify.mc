package nipah.edify

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.utils.*
import java.util.concurrent.CopyOnWriteArrayList

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = 100_000,
    private val scanPerTick: Int = limit / 20,
    private val blocksPerFloatingSupports: Int = 3,
    private val floatingSupportsNaturalIslandLimit: Int = 5_000,
) {
    companion object {
        val currentlyScanning = CopyOnWriteArrayList<GroupScan>()
    }

    private val toVisit = LongArrayFIFOQueue(limit * 2)
    private val toVisitSet = LongOpenHashSet(limit * 4, 0.4f)
    private val toVisitWeak = LongArrayFIFOQueue(limit)
    private val toVisitWeakSet = LongOpenHashSet(limit * 2, 0.4f)
    private val visited = LongOpenHashSet(limit * 10, 0.4f)
    private val group = LongOpenHashSet(limit * 2)
    private val metaGroup = LongOpenHashSet(limit * 2)
    private val metaGroupWeak = LongOpenHashSet(limit * 2)

    fun clear() {
        toVisit.clear()
        toVisitSet.clear()
        visited.clear()
        group.clear()
    }

    var isRunning = false
        private set

    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        try {
            isRunning = true
            currentlyScanning.add(this)
            clear()
            val seed = if (seed.size > 1000) seed.takeRandomNPercentile(0.01f) else seed
            withContext(TickScheduler.roundRobinDispatcher()) {
                for (item in seed) {
                    mapGroupBranches(item)
                }
            }
            return group.map { BlockPos.of(it) }
        }
        catch (e: Throwable) {
            Edify.LOGGER.error("Error during group scan", e)
            return null
        }
        finally {
            isRunning = false
            currentlyScanning.remove(this)
        }
    }

    suspend fun mapGroupBranches(seed: BlockPos) {
        seed.forEachNeighborWithCornersUpFirstNoAlloc { npos ->
            toVisit.clear()
            toVisitSet.clear()
            visited.clear()
            mapGroup(npos)
        }
    }

    suspend fun mapGroup(seed: BlockPos) {
        toVisit.enqueue(seed.asLong())
        toVisitSet.add(seed.asLong())

        metaGroup.clear()

        var iterTicks = 0

        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()
        while (toVisit.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (visited.size > limit || toVisitSet.size > limit) {
                return
            }
            iterTicks++
            if (iterTicks > scanPerTick) {
                iterTicks = 0
                yield()
            }

            val longPos = toVisit.dequeueLong()
            pos.set(longPos)

            val chunk = chunks.backgroundAt(pos) ?: return
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) {
                continue
            }
            if (block.isNonSupporting()) {
                mapWeakLinks(pos, sizeLimitWhenHitSolid = 7)
                continue
            }
            if (visited.add(longPos).not()) {
                continue
            }
            metaGroup.add(longPos)

            pos.forEachNeighborFaceOrEdgeNoAlloc(npos) { npos ->
                val longNpos = npos.asLong()
                if (toVisitSet.add(longNpos).not()) {
                    return@forEachNeighborFaceOrEdgeNoAlloc
                }
                toVisit.enqueue(longNpos)
            }
        }
        group.addAll(metaGroup)
    }

    suspend fun mapWeakLinks(seed: BlockPos, sizeLimitWhenHitSolid: Int) {
        toVisitWeak.clear()
        toVisitWeakSet.clear()
        toVisitWeak.enqueue(seed.asLong())
        metaGroupWeak.clear()

        var smallestPos = seed
        var largestPos = seed
        var hitSolid = false

        var iterTicks = 0
        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()
        while (toVisitWeak.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            if (visited.size > limit || toVisitWeakSet.size > limit) {
                return
            }

            iterTicks++
            if (iterTicks > scanPerTick) {
                iterTicks = 0
                yield()
            }

            val longPos = toVisitWeak.dequeueLong()
            pos.set(longPos)

            if (pos < smallestPos) {
                smallestPos = pos.immutable()
            }
            else if (pos > largestPos) {
                largestPos = pos.immutable()
            }

            if (hitSolid) {
                val size = largestPos.distManhattan(smallestPos)
                if (size > sizeLimitWhenHitSolid) {
                    break
                }
            }

            val chunk = chunks.backgroundAt(pos) ?: return
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) {
                continue
            }
            if (visited.add(longPos).not()) {
                continue
            }
            if (block.isNonSupporting().not()) {
                hitSolid = true
                continue
            }
            metaGroupWeak.add(longPos)
            pos.forEachNeighborFaceOrEdgeNoAlloc(npos) { npos ->
                val longNpos = npos.asLong()
                if (toVisitWeakSet.add(longNpos).not()) {
                    return@forEachNeighborFaceOrEdgeNoAlloc
                }
                toVisitWeak.enqueue(longNpos)
            }
        }
        metaGroup.addAll(metaGroupWeak)
    }
}
