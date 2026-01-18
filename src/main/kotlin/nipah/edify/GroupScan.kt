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
import nipah.edify.workers.UrWorker
import java.util.concurrent.CopyOnWriteArrayList

class GroupScan(
    val chunks: ChunkAccess,
    private val limit: Int = Configs.startup.groupScan.limit.get(),
    private val scanPerTick: Int = Configs.startup.groupScan.scanPerTick.get(),
    private val blocksPerFloatingSupports: Int =
        Configs.startup.groupScan.blocksPerFloatingSupports.get(),
    private val floatingSupportsNaturalIslandLimit: Int =
        Configs.startup.groupScan.floatingSupportsNaturalIslandLimit.get(),
): UrWorker {
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

    override var isRunning = false
        private set

    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        try {
            isRunning = true
            currentlyScanning.add(this)
            clear()
            Edify.LOGGER.info("[GroupScan] Starting scan with ${seed.size} seeds")
            val seed = if (seed.size > 1000) seed.takeRandomNPercentile(0.01f) else seed
            withContext(TickScheduler.roundRobinDispatcher()) {
                for (item in seed) {
                    mapGroupBranches(item)
                }
            }
            Edify.LOGGER.info("[GroupScan] Scan complete, group size=${group.size}")
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

        toVisitWeak.clear()
        toVisitWeakSet.clear()

        metaGroup.clear()

        var solidHits = 0
        var floatingHits = 0

        var iterTicks = 0

        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()
        while (toVisit.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (visited.size > limit || toVisitSet.size > limit) {
                Edify.LOGGER.info("[GroupScan] Hit limit at pos=$pos, visited=${visited.size}, toVisit=${toVisitSet.size}")
                return
            }
            iterTicks++
            if (iterTicks > scanPerTick) {
                iterTicks = 0
                yield()
            }

            val longPos = toVisit.dequeueLong()
            pos.set(longPos)

            val chunk = chunks.backgroundAt(pos) ?: run {
                Edify.LOGGER.info("[GroupScan] Chunk null at pos=$pos")
                return
            }
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) {
                continue
            }
            if (isFoundation(pos)) {
                Edify.LOGGER.info("[GroupScan] Hit foundation at pos=$pos, block=$block, metaGroup size=${metaGroup.size}")
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
            if (block.isNonSupporting()) {
                if (toVisitWeakSet.add(longPos)) {
                    toVisitWeak.enqueue(longPos)
                }
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
        solidHits += mapWeakLinks(64)
        if (floatingHits > 0) {
            val blocksPerSupport =
                solidHits / blocksPerFloatingSupports.coerceAtLeast(1)
            if (blocksPerSupport <= floatingHits) {
                Edify.LOGGER.info("[GroupScan] Floating ratio check failed: solidHits=$solidHits, floatingHits=$floatingHits, blocksPerSupport=$blocksPerSupport")
                return
            }
        }
        if (metaGroup.isEmpty()) {
            return
        }
        Edify.LOGGER.info("[GroupScan] Adding ${metaGroup.size} blocks to fall group")
        group.addAll(metaGroup)
    }

    suspend fun mapWeakLinks(sizeLimitWhenHitSolid: Int): Int {
        if (toVisitWeak.isEmpty) {
            return 0
        }

        metaGroupWeak.clear()

        val smallestPos = BlockPos.of(toVisitWeak.firstLong()).mutable()
        val largestPos = BlockPos.of(toVisitWeak.lastLong()).mutable()
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

            smallestPos.minAssign(pos)
            largestPos.maxAssign(pos)

            if (hitSolid.not()) {
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
            }
            metaGroupWeak.add(longPos)
            pos.forEachNeighborNoAlloc(npos) { npos ->
                val longNpos = npos.asLong()
                if (toVisitWeakSet.add(longNpos).not()) {
                    return@forEachNeighborNoAlloc
                }
                toVisitWeak.enqueue(longNpos)
            }
        }
        metaGroup.addAll(metaGroupWeak)
        return metaGroupWeak.size
    }
}
