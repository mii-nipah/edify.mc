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

class IntegrityScan(
    val chunks: ChunkAccess,
    private val limit: Int = 3_000,
) {
    @JvmInline
    value class BlockWDS(val value: Half4) {
        val weight get() = value.xy
        val distribution get() = value.z
        val support get() = value.w

        fun withWeight(newWeight: Float) =
            BlockWDS(Half4.of(newWeight, distribution.half, support.half))

        fun withDistribution(newDistribution: Float) =
            BlockWDS(Half4.of(weight, newDistribution.half, support.half))

        fun withSupport(newSupport: Float) =
            BlockWDS(Half4.of(weight, distribution.half, newSupport.half))

        companion object {
            fun of(weight: Float, distribution: Float, support: Float) =
                BlockWDS(Half4.of(weight, distribution.half, support.half))

            fun of(bits: Long) = BlockWDS(Half4(bits))
        }
    }

    @JvmInline
    value class BlockWeightResistance(val value: Float2) {
        val weight get() = value.x
        val resistance get() = value.y

        fun withWeight(newWeight: Float) =
            BlockWeightResistance(Float2.of(newWeight, resistance))

        fun withResistance(newResistance: Float) =
            BlockWeightResistance(Float2.of(weight, newResistance))

        companion object {
            fun of(weight: Float, resistance: Float) =
                BlockWeightResistance(Float2.of(weight, resistance))

            fun of(bits: Long) = BlockWeightResistance(Float2(bits))
        }
    }

    class Map(limit: Int) {
        val map = Long2LongOpenHashMap(limit).apply {
            defaultReturnValue(-1L)
        }
        val originals = Long2LongOpenHashMap(limit).apply {
            defaultReturnValue(-1L)
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

        fun forEach(action: (pos: BlockPos, originalWeight: Float, weight: Float, resistance: Float, distribution: Float, support: Float) -> Unit) {
            val mpos = BlockPos.MutableBlockPos()
            for (entry in map.long2LongEntrySet()) {
                val longPos = entry.longKey
                val wdsBits = entry.longValue
                val ogVal = originals.getOrDefault(longPos, -1L)
                if (ogVal == -1L) continue
                val wds = BlockWDS.of(wdsBits)
                val ogWr = BlockWeightResistance.of(ogVal)
                mpos.set(longPos)
                action(
                    mpos,
                    ogWr.weight,
                    wds.weight,
                    ogWr.resistance,
                    wds.distribution,
                    wds.support
                )
            }
        }

        fun updateWeight(pos: BlockPos, weight: Float) {
            map[pos.asLong()].let {
                val wds = BlockWDS.of(it)
                map[pos.asLong()] = wds.withWeight(weight).value.bits
            }
        }

        inline fun updateWeight(pos: BlockPos, weight: (Float) -> Float) {
            map[pos.asLong()].let {
                val wds = BlockWDS.of(it)
                val newW = weight(wds.weight)
                map[pos.asLong()] = wds.withWeight(newW).value.bits
            }
        }

        fun updateResistance(pos: BlockPos, resistance: Float) {
            originals[pos.asLong()].let {
                val wr = BlockWeightResistance.of(it)
                originals[pos.asLong()] = wr.withResistance(resistance).value.bits
            }
        }

        fun updateDistribution(pos: BlockPos, distribution: Float) {
            map[pos.asLong()].let {
                val wds = BlockWDS.of(it)
                map[pos.asLong()] = wds.withDistribution(distribution).value.bits
            }
        }

        fun updateSupport(pos: BlockPos, support: Float) {
            map[pos.asLong()].let {
                val wds = BlockWDS.of(it)
                map[pos.asLong()] = wds.withSupport(support).value.bits
            }
        }

        fun put(pos: BlockPos, weight: Float, resistance: Float, distribution: Float, support: Float) {
            put(pos.asLong(), weight, resistance, distribution, support)
        }

        private val mpos = BlockPos.MutableBlockPos()
        fun put(longPos: Long, weight: Float, resistance: Float, distribution: Float, support: Float) {
            map[longPos] = BlockWDS.of(weight, distribution, support).value.bits
            if (longPos !in originals) {
                originals[longPos] = BlockWeightResistance.of(weight, resistance).value.bits
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

        fun maybeGet(pos: BlockPos): BlockWDS? {
            return map.getOrDefault(pos.asLong(), -1L)
                .takeIf { it != -1L }?.let { BlockWDS(Half4(it)) }
        }

        fun maybeGet(longPos: Long): BlockWDS? {
            return map.getOrDefault(longPos, -1L)
                .takeIf { it != -1L }?.let { BlockWDS(Half4(it)) }
        }

        fun get(pos: BlockPos): BlockWDS {
            return map[pos.asLong()].let { BlockWDS.of(it) }
        }

        fun get(longPos: Long): BlockWDS {
            return map[longPos].let { BlockWDS.of(it) }
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
            val blockS = BlockSupport.of(state)
            map.put(
                pos.asLong(),
                blockW.value, blockR.value.f,
                blockD.value.f, blockS.value.f
            )
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

    @JvmInline
    value class MapApplyChangesScope(private val buffer: Long2FloatOpenHashMap) {
        fun setWeight(pos: BlockPos, w: Float) {
            buffer[pos.asLong()] = w
        }

        fun updateWeight(pos: BlockPos, w: Float) {
            buffer[pos.asLong()] += w
        }
    }

    private inline fun Map.applyChanges(action: MapApplyChangesScope.() -> Boolean) {
        val buffer = Long2FloatOpenHashMap(size).apply {
            defaultReturnValue(0f)
        }
        val scope = MapApplyChangesScope(buffer)
        val result = scope.action()
        if (result) {
            for (entry in buffer.long2FloatEntrySet()) {
                val longPos = entry.longKey
                val w = entry.floatValue
//                updateWeight(BlockPos.of(longPos), w)
                updateWeight(BlockPos.of(longPos)) { it + w }
            }
        }
    }

    suspend fun step2(map: Map) {
        if (map.isEmpty) return

        val npos = BlockPos.MutableBlockPos()

        fun applyGravity() = map.applyChanges {
            map.forEach { pos, originalWeight, weight, resistance, distribution, support ->
                npos.set(pos)
                npos.move(Direction.DOWN)
                if (npos !in map) {
                    val chunk = chunks.at(npos) ?: return@forEach
                    val state = chunk.getBlockState(npos)
                    if (state.isAir || state.isEmpty) {
                        val newW = (weight * (1f - support)) * 1.01f
                        updateWeight(pos, newW)
                    }
                    else if (state.block is LiquidBlock) {
//                        val newW = weight * (1f - support) * 0.5f
//                        setWeight(pos, newW)
                        updateWeight(pos, -weight * 0.5f * (1f - support))
                    }
                    else {
//                        val newW = weight * 0.01f
                        updateWeight(pos, -weight * 0.5f)
                    }
                    return@forEach
                }
                val below = map.get(npos)
                val spread = (weight)
                updateWeight(npos, spread * below.distribution)
                updateWeight(pos, -spread * distribution)
            }
            true
        }

        fun applyEquilibrium() = map.applyChanges {
            map.forEach { pos, originalWeight, weight, resistance, distribution, support ->
                val spread = weight / pos.neighborHorizontalSize
                var spreadAmount = 0f
                pos.forEachHorizontalNeighborNoAlloc(npos) { npos ->
                    if (npos !in map) {
                        val chunk = chunks.at(npos) ?: return@forEachHorizontalNeighborNoAlloc
                        val state = chunk.getBlockState(npos)
                        if (state.isAir || state.isEmpty || state.block is LiquidBlock) {
                            return@forEachHorizontalNeighborNoAlloc
                        }
                        spreadAmount += spread
                        return@forEachHorizontalNeighborNoAlloc
                    }
                    val neighbor = map.get(npos)
                    val effectiveSpread = spread * (neighbor.support)
                    updateWeight(npos, effectiveSpread)
                    spreadAmount += effectiveSpread
                }
                updateWeight(pos, -spreadAmount)
//                setWeight(pos, (weight - spreadAmount).coerceAtLeast(0.1f))
            }
            true
        }
        repeat(5) {
            applyGravity()
            applyEquilibrium()
        }
        repeat(7) {
            applyEquilibrium()
        }
    }

    suspend fun step3(map: Map): List<BlockPos> {
        val toRemove = mutableListOf<BlockPos>()
        map.forEach { pos, ogW, weight, resistance, _, _ ->
            val wRes = (ogW) + (ogW * resistance)
            if (weight > wRes) {
                toRemove.add(pos.immutable())
            }
        }
        return toRemove
    }
}
