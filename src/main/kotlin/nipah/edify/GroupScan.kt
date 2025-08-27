package nipah.edify

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.utils.*
import java.util.concurrent.CopyOnWriteArrayList

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = Configs.startup.groupScan.limit.get(),
    private val scanPerTick: Int = Configs.startup.groupScan.scanPerTick.get(),
    private val blocksPerFloatingSupports: Int =
        Configs.startup.groupScan.blocksPerFloatingSupports.get(),
    private val floatingSupportsNaturalIslandLimit: Int =
        Configs.startup.groupScan.floatingSupportsNaturalIslandLimit.get(),
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

    private fun isFoundation(pos: BlockPos): Boolean {
        val chunkPos = ChunkPos.asLong(pos)
        val chunk = WorldData.getChunkData(chunks.level, chunkPos) ?: return true
        val lposX = pos.toLocalX()
        val lposZ = pos.toLocalZ()
        return chunk.foundationAt(lposX, pos.y, lposZ)
    }

    suspend fun mapGroup(seed: BlockPos) {
        toVisit.enqueue(seed.asLong())
        toVisitSet.add(seed.asLong())

        metaGroup.clear()

        var solidHits = 0
        var floatingHits = 0

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
            if (isFoundation(pos)) {
                return
            }
            if (block.isFloating()) {
                floatingHits++
                if (floatingHits > floatingSupportsNaturalIslandLimit) {
                    return
                }
            }
            else {
                solidHits++
            }
//            if (block.isNonSupporting()) {
//                solidHits += mapWeakLinks(pos, sizeLimitWhenHitSolid = 7)
//                continue
//            }
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
        val blocksPerSupport =
            solidHits / blocksPerFloatingSupports.coerceAtLeast(1)
        if (blocksPerSupport <= floatingHits) {
            return
        }
        group.addAll(metaGroup)
    }

    suspend fun mapWeakLinks(seed: BlockPos, sizeLimitWhenHitSolid: Int): Int {
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
                return 0
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

            val chunk = chunks.backgroundAt(pos) ?: return 0
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) {
                continue
            }
            if (isFoundation(pos)) {
                return 0
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
        return metaGroupWeak.size
    }
}
