package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import nipah.edify.types.*
import nipah.edify.utils.*
import kotlin.math.absoluteValue

class IntegrityScan(
    val chunks: ChunkAccess,
    private val limit: Int = 1_000,
) {
    @JvmInline
    value class BlockWRD(val value: Half4) {
        val weight get() = value.xy
        val resistance get() = value.z
        val distribution get() = value.w

        companion object {
            fun of(weight: Float, resistance: Float, distribution: Float) = BlockWRD(Half4.of(weight, resistance.half, distribution.half))
            fun of(bits: Long) = BlockWRD(Half4(bits))
        }
    }

    class Map(limit: Int) {
        val map = Long2LongOpenHashMap(limit).apply {
            defaultReturnValue(-1L)
        }
        val originalWeights = Long2FloatOpenHashMap(limit).apply {
            defaultReturnValue(0f)
        }
        var lowExtremity: BlockPos.MutableBlockPos? = null
            private set
        var highExtremity: BlockPos.MutableBlockPos? = null
            private set

        val isEmpty get() = map.isEmpty()

        fun getCenterOfMass(): BlockPos? {
            if (map.isEmpty()) return null
            var sumX = 0f
            var sumY = 0f
            var sumZ = 0f
            val pos = BlockPos.MutableBlockPos()
            var totalW = 0f
            map.forEach { longPos, _ ->
                pos.set(longPos)
                val w = get(longPos).weight
                sumX += pos.x * w
                sumY += pos.y * w
                sumZ += pos.z * w
                totalW += w
            }
            if (totalW == 0f) return null
            return BlockPos(
                (sumX / totalW).toInt(),
                (sumY / totalW).toInt(),
                (sumZ / totalW).toInt(),
            )
        }

        fun getClosest(to: BlockPos): BlockPos? {
            var closest = -1L
            var closestDist = Double.MAX_VALUE
            val mpos = BlockPos.MutableBlockPos()
            map.forEach { longPos, _ ->
                mpos.set(longPos)
                val dist = mpos.distSqr(to)
                if (dist < closestDist) {
                    closestDist = dist
                    closest = longPos
                }
            }
            return run {
                if (closest == -1L)
                    null
                else
                    BlockPos.of(closest)
            }
        }

        fun getClosestToLowExtremity(): BlockPos? {
            val lowExtremity = lowExtremity ?: return null
            return getClosest(lowExtremity)
        }

        fun getClosestToHighExtremity(): BlockPos? {
            val highExtremity = highExtremity ?: return null
            return getClosest(highExtremity)
        }

        val size get() = map.size

        fun clear() {
            map.clear()
            lowExtremity = null
            highExtremity = null
        }

        fun forEach(action: (pos: BlockPos, originalWeight: Float, weight: Float, resistance: Float, distribution: Float) -> Unit) {
            val mpos = BlockPos.MutableBlockPos()
            map.forEach { longPos, wr ->
                val wr = Half4(wr)
                val ogW = originalWeights.getOrDefault(longPos, wr.xy)
                mpos.set(longPos)
                action(mpos, ogW, wr.xy, wr.z, wr.w)
            }
        }

        fun updateWeight(pos: BlockPos, weight: Float) {
            map[pos.asLong()].let {
                val wr = Half4(it)
                map[pos.asLong()] = Half4.of(weight, wr.z.half, wr.w.half).bits
            }
        }

        inline fun updateWeight(pos: BlockPos, weight: (Float) -> Float) {
            map[pos.asLong()].let {
                val wr = Half4(it)
                val newW = weight(wr.xy)
                map[pos.asLong()] = Half4.of(newW, wr.z.half, wr.w.half).bits
            }
        }

        fun updateResistance(pos: BlockPos, resistance: Float) {
            map[pos.asLong()].let {
                val wr = Half4(it)
                map[pos.asLong()] = Half4.of(wr.xy, resistance.half, wr.w.half).bits
            }
        }

        fun updateDistribution(pos: BlockPos, distribution: Float) {
            map[pos.asLong()].let {
                val wrd = Half4(it)
                map[pos.asLong()] = Half4.of(wrd.xy, wrd.z.half, distribution.half).bits
            }
        }

        fun put(pos: BlockPos, weight: Float, resistance: Float, distribution: Float) {
            put(pos.asLong(), weight, resistance, distribution)
        }

        private val mpos = BlockPos.MutableBlockPos()
        fun put(longPos: Long, weight: Float, resistance: Float, distribution: Float) {
            map[longPos] = Half4.of(weight, resistance.half, distribution.half).bits
            if (longPos !in originalWeights) {
                originalWeights[longPos] = weight
            }
            mpos.set(longPos)
            if (lowExtremity == null) {
                lowExtremity = mpos.immutable().mutable()
                if (highExtremity == null) {
                    highExtremity = mpos.immutable().mutable()
                }
                return
            }
            if (highExtremity == null) {
                highExtremity = mpos.immutable().mutable()
                return
            }

            lowExtremity!!.minAssign(mpos)
            highExtremity!!.maxAssign(mpos)
        }

        fun maybeGet(pos: BlockPos): BlockWRD? {
            return map.getOrDefault(pos.asLong(), -1L)
                .takeIf { it != -1L }?.let { BlockWRD(Half4(it)) }
        }

        fun maybeGet(longPos: Long): BlockWRD? {
            return map.getOrDefault(longPos, -1L)
                .takeIf { it != -1L }?.let { BlockWRD(Half4(it)) }
        }

        fun get(pos: BlockPos): BlockWRD {
            return map[pos.asLong()].let { BlockWRD.of(it) }
        }

        fun get(longPos: Long): BlockWRD {
            return map[longPos].let { BlockWRD.of(it) }
        }

        operator fun contains(pos: BlockPos): Boolean {
            return pos.asLong() in map
        }

        operator fun contains(longPos: Long): Boolean {
            return longPos in map
        }
    }

    suspend fun scan(seed: BlockPos): Pair<List<BlockPos>, Map> {
        return withContext(TickScheduler.roundRobinDispatcher()) {
            val map = step1(seed)
            for (i in 0 until 1) {
                step2(map)
            }
            step3(map) to map
        }
    }

    suspend fun step1(seed: BlockPos): Map {
        val map = Map(limit)
        val bfs = BfsBox(limit, scanPerTick = limit)

        val result = bfs.scan(seed) { pos ->
            val chunk = chunks.at(pos) ?: return@scan BfsBox.ScanCommand.Continue
            val state = chunk.getBlockState(pos)
            if (state.isAir
                || state.isEmpty
                || state.block is LiquidBlock
                || state.block == Blocks.BEDROCK
            ) return@scan BfsBox.ScanCommand.Continue

            val blockW = BlockWeight.of(state)
            val blockR = BlockResistance.of(state)
            val blockD = BlockDistribution.of(state)
            map.put(pos.asLong(), blockW.value, blockR.value.f, blockD.value.f)
            if (map.size >= limit) return@scan BfsBox.ScanCommand.Stop(BfsBox.ScanResult.Ok)

            BfsBox.ScanCommand.VisitNeighbors
        }
        when (result) {
            is BfsBox.ScanResult.Cancelled -> {
                map.clear()
            }

            is BfsBox.ScanResult.Ok, is BfsBox.ScanResult.Limit -> {}
        }

        return map
    }

    suspend fun step2(map: Map) {
        if (map.isEmpty) return
        val npos = BlockPos.MutableBlockPos()
        fun upPass() {
            map.forEach { pos, _, weight, resistance, distribution ->
                npos.set(pos)
                npos.move(Direction.DOWN)

                if (npos !in map) {
                    val chunk = chunks.at(npos) ?: run {
                        map.updateWeight(pos, weight * 0.01f)
                        return@forEach
                    }
                    val below = chunk.getBlockState(npos)
                    if (below.isAir || below.isEmpty || below.block is LiquidBlock) {
                        map.updateWeight(pos, weight * 1.5f)
                    }
                    else {
                        map.updateWeight(pos, weight * 0.01f)
                    }
                    return@forEach
                }
                val below = map.get(npos)
                map.updateWeight(npos, below.weight + weight)
                map.updateWeight(pos, weight * 0.5f)
            }
        }
        upPass()

        fun horPass() {
            map.forEach { pos, _, weight, resistance, distribution ->
                val part = weight / pos.neighborHorizontalSize

                var usedPart = 0f
                pos.forEachHorizontalNeighborNoAlloc(npos) { npos ->
                    if (npos !in map) {
                        val chunk = chunks.at(npos) ?: run {
                            map.updateWeight(pos, weight * 0.001f)
                            return@forEachHorizontalNeighborNoAlloc
                        }
                        val neighbor = chunk.getBlockState(npos)
                        if (neighbor.isAir || neighbor.isEmpty || neighbor.block is LiquidBlock) {
//                        map.updateWeight(pos, weight * 3f)
                        }
                        else {
                            map.updateWeight(pos, weight * 0.001f)
                        }
                        return@forEachHorizontalNeighborNoAlloc
                    }
                    val neighbor = map.get(npos)
                    val spreadFactor = distribution / neighbor.distribution
                    map.updateWeight(npos, neighbor.weight + (part * spreadFactor))
                    usedPart += part * spreadFactor
                }
                map.updateWeight(pos, weight - usedPart)
            }
        }

        fun selfDistributivePass() {
            map.forEach { pos, ogW, weight, resistance, distribution ->
                val part = weight / pos.neighborSize
                var usedPart = 0f
                pos.forEachNeighborNoAlloc(npos) { npos ->
                    if (npos !in map) {
                        return@forEachNeighborNoAlloc
                    }
                    val neighbor = map.get(npos)

                    fun Float.percentEqual(b: Float): Boolean {
                        return (this - b).absoluteValue < 0.03f
                    }

                    if (neighbor.resistance.percentEqual(resistance).not()) {
                        map.updateWeight(npos, neighbor.weight + part * 0.1f)
                        usedPart += part
                        return@forEachNeighborNoAlloc
                    }

                    map.updateWeight(npos, neighbor.weight + (part * 0.7f))
                    usedPart += part * 1f
                }
                map.updateWeight(pos, weight - usedPart)
            }
        }

        fun finalPass() {
            map.forEach { pos, originalWeight, weight, resistance, distribution ->
                pos.forEachNeighborNoAlloc(npos) { npos ->
                    if (npos in map) return@forEachNeighborNoAlloc
                    val chunk = chunks.at(npos) ?: run {
                        map.updateWeight(pos, weight * 0.001f)
                        return@forEachNeighborNoAlloc
                    }
                    val neighbor = chunk.getBlockState(npos)
                    if (neighbor.isAir || neighbor.isEmpty || neighbor.block is LiquidBlock) {
                        return@forEachNeighborNoAlloc
                    }
                    map.updateWeight(pos, weight * 0.0001f)
                }
            }
        }

        repeat(7) {
            horPass()
            selfDistributivePass()
            upPass()
        }
        upPass()
        selfDistributivePass()
        finalPass()
        selfDistributivePass()
        selfDistributivePass()
        selfDistributivePass()
    }

    suspend fun step3(map: Map): List<BlockPos> {
        val toRemove = mutableListOf<BlockPos>()
        map.forEach { pos, ogW, weight, resistance, _ ->
            val wRes = (ogW * 3) + (ogW * resistance)
            if (weight > wRes) {
                toRemove.add(pos.immutable())
            }
        }
        return toRemove
    }
}