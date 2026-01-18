package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.types.BlockDistribution
import nipah.edify.types.BlockSupport
import nipah.edify.types.BlockWeight
import nipah.edify.types.Float2
import nipah.edify.utils.*

class IntegrityScan(
    val chunks: ChunkAccess,
    private val limit: Int = 30_000,
) {
    class Structure(
        // blockStateId, pressure
        private val map: Long2LongOpenHashMap = Long2LongOpenHashMap(),
    ) {
        val isEmpty: Boolean
            get() = map.isEmpty()

        fun exists(at: BlockPos): Boolean {
            return exists(at.asLong())
        }

        fun exists(at: Long): Boolean {
            return map.containsKey(at)
        }

        fun getLong(at: BlockPos): Float2 {
            return getLong(at.asLong())
        }

        fun getLong(at: Long): Float2 {
            return Float2(map.get(at))
        }

        fun put(at: Long, data: Float2) {
            map.put(at, data.bits)
        }

        inline fun use(at: BlockPos, block: (state: BlockState, pressure: Float) -> Unit) {
            val data = getLong(at)
            val state = Block.stateById(data.xi)
            val pressure = data.y
            block(state, pressure)
        }

        inline fun useMut(at: BlockPos, block: (state: BlockState, pressure: Float) -> Float) {
            val data = getLong(at)
            val state = Block.stateById(data.xi)
            val pressure = data.y
            val newPressure = block(state, pressure)
            put(at.asLong(), Float2.of(data.xi, newPressure))
        }

        fun longIterator() = map.long2LongEntrySet().iterator()

        inline fun forEach(block: (posLong: BlockPos, state: BlockState, pressure: Float) -> Unit) {
            val iterator = longIterator()
            val pos = BlockPos.MutableBlockPos()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val posLong = entry.longKey
                pos.set(posLong)
                val data = Float2(entry.longValue)
                val state = Block.stateById(data.xi)
                val pressure = data.y
                block(pos, state, pressure)
            }
        }
    }

    private val structures = mutableListOf<Structure>()

    suspend fun scan(seed: BlockPos): Structure? {
        return withContext(TickScheduler.roundRobinDispatcher()) {
            val structure = run {
                structures.find {
                    if (it.exists(seed))
                        true
                    else
                        seed.forEachNeighborWithCornersNoAlloc { npos ->
                            if (it.exists(npos)) {
                                return@find true
                            }
                        }
                    false
                } ?: initStructure(seed).also {
                    if (it == null) {
                        return@withContext null
                    }
                    structures.add(it)
                }
            }
            if (structure != null) {
                TickScheduler.serverScope.launch {
                    repeat(10) {
                        updateStructure(structure)
                        TickScheduler.sleep(20)
                    }
                }
            }
            structure
        }
    }

    suspend fun updateStructure(structure: Structure) {
        val mutablePos = BlockPos.MutableBlockPos()
        structure.forEach { pos, state, pressure ->
            val w = BlockWeight.of(state)
            if (pressure < w.value * 0.25f) {
                return@forEach
            }
            val d = BlockDistribution.of(state).value.f
            val down = pressure * 0.25f * d
            val side = (pressure / pos.neighborHorizontalWithCornersSize) * d
            var nextPressure = pressure

            pos.forEachHorizontalNeighborWithCornersNoAlloc(mutablePos) { npos ->
                if (structure.exists(npos).not) {
                    return@forEachHorizontalNeighborWithCornersNoAlloc
                }
                structure.useMut(npos) { nstate, npressure ->
                    nextPressure -= side
                    npressure + side
                }
            }
            val belowPos = pos.below()
            if (structure.exists(belowPos)) {
                structure.useMut(belowPos) { nstate, npressure ->
                    nextPressure -= down
                    npressure + down
                }
            }
            structure.put(pos.asLong(), Float2.of(Block.getId(state), nextPressure))
        }
    }

    suspend fun initStructure(seed: BlockPos): Structure? {
        val structure = Structure()
        val dfs = BfsBox(limit, 10_000)

        dfs.scan(seed) { pos ->
            val chunk = chunks.at(pos) ?: return@scan BfsBox.ScanCommand.Continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty || block.isBuilding().not) {
                return@scan BfsBox.ScanCommand.Continue
            }
            var w = BlockWeight.of(block).value
            val s = BlockSupport.of(block)
            if (chunk.getBlockState(pos.below()).let { it.isAir || it.isEmpty }) {
                w *= s.value.f
            }
            structure.put(pos.asLong(), Float2.of(Block.getId(block), w))
            BfsBox.ScanCommand.VisitNeighborWithCorners
        }
        if (structure.isEmpty) {
            return null
        }

        return structure
    }
}
