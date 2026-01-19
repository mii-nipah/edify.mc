package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.chunks.setDebrisAt
import nipah.edify.types.BlockResistance
import nipah.edify.types.BlockWeight
import nipah.edify.types.Float2
import nipah.edify.utils.*

class IntegrityScan(
    val chunks: ChunkAccess,
    private val limit: Int = 30_000,
) {
    class Structure(
        private val map: Long2LongOpenHashMap = Long2LongOpenHashMap(),
    ) {
        val isEmpty: Boolean
            get() = map.isEmpty()

        val size: Int
            get() = map.size

        fun exists(at: BlockPos): Boolean = map.containsKey(at.asLong())
        fun exists(at: Long): Boolean = map.containsKey(at)

        fun getLong(at: Long): Float2 = Float2(map.get(at))
        fun getLong(at: BlockPos): Float2 = getLong(at.asLong())

        fun put(at: Long, data: Float2) {
            map.put(at, data.bits)
        }

        fun remove(at: Long) {
            map.remove(at)
        }

        fun longIterator() = map.long2LongEntrySet().iterator()

        inline fun forEach(block: (pos: BlockPos, state: BlockState, pressure: Float) -> Unit) {
            val iterator = longIterator()
            val pos = BlockPos.MutableBlockPos()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                pos.set(entry.longKey)
                val data = Float2(entry.longValue)
                val state = Block.stateById(data.xi)
                block(pos, state, data.y)
            }
        }

        fun collectOverpressured(): LongArrayList {
            val result = LongArrayList()
            val iterator = longIterator()
            var maxRatioSeen = 0f
            var maxRatioPos: BlockPos? = null
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val data = Float2(entry.longValue)
                val state = Block.stateById(data.xi)
                val pressure = data.y
                val weight = BlockWeight.of(state).value
                val resistance = BlockResistance.of(state).value.f
                val maxPressure = weight * resistance
                val ratio = pressure / maxPressure
                if (ratio > maxRatioSeen) {
                    maxRatioSeen = ratio
                    maxRatioPos = BlockPos.of(entry.longKey)
                }
                if (pressure > maxPressure) {
                    result.add(entry.longKey)
                }
            }
            if (maxRatioPos != null) {
                Edify.LOGGER.info("[IntegrityScan] Max ratio: ${"%.2f".format(maxRatioSeen)} at $maxRatioPos (${result.size} overpressured)")
            }
            return result
        }

        fun collectOverpressuredSorted(): List<Long> {
            val overpressured = mutableListOf<Pair<Long, Float>>()
            val iterator = longIterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val data = Float2(entry.longValue)
                val state = Block.stateById(data.xi)
                val pressure = data.y
                val weight = BlockWeight.of(state).value
                val resistance = BlockResistance.of(state).value.f
                val maxPressure = weight * resistance
                val ratio = pressure / maxPressure
                if (ratio > 1f) {
                    overpressured.add(entry.longKey to ratio)
                }
            }
            return overpressured.sortedByDescending { it.second }.map { it.first }
        }
    }

    private val structures = mutableListOf<Structure>()
    private val simulatingStructures = mutableSetOf<Structure>()

    suspend fun scan(seed: BlockPos, level: ServerLevel): Structure? {
        return withContext(TickScheduler.roundRobinDispatcher()) {
            var isNew = false
            val structure = run {
                structures.find {
                    if (it.exists(seed)) true
                    else {
                        var found = false
                        seed.forEachNeighborWithCornersNoAlloc { npos ->
                            if (it.exists(npos)) found = true
                        }
                        found
                    }
                } ?: initStructure(seed).also {
                    if (it == null) return@withContext null
                    structures.add(it)
                    isNew = true
                }
            }
            if (structure != null && isNew && structure !in simulatingStructures) {
                simulatingStructures.add(structure)
                TickScheduler.serverScope.launch {
                    try {
                        simulateStructure(structure, level)
                    }
                    finally {
                        simulatingStructures.remove(structure)
                    }
                }
            }
            structure
        }
    }

    private suspend fun simulateStructure(structure: Structure, level: ServerLevel) {
        repeat(5) { updateStructure(structure) }

        val maxIterations = 40
        var iteration = 0
        val maxBreaksPerTick = 3

        while (iteration < maxIterations && structure.size > 0) {
            updateStructure(structure)
            iteration++

            val broken = structure.collectOverpressuredSorted()
            if (broken.isNotEmpty()) {
                val toBreak = broken.take(maxBreaksPerTick)
                Edify.LOGGER.info("[IntegrityScan] Breaking ${toBreak.size}/${broken.size} overpressured blocks")

                withContext(TickScheduler.ServerDispatcher) {
                    for (longPos in toBreak) {
                        structure.remove(longPos)
                        val pos = BlockPos.of(longPos)
                        val state = level.getBlockState(pos)
                        if (!state.isAir) {
                            level.removeBlock(pos, false)
                            level.setDebrisAt(pos, state)
                            level.playSound(
                                null,
                                pos,
                                state.soundType.breakSound,
                                SoundSource.BLOCKS,
                                4.0f,
                                0.8f + level.random.nextFloat() * 0.4f
                            )
                        }
                    }
                }

                repeat(2) { updateStructure(structure) }
            }

            TickScheduler.sleep(3)
        }

        structures.remove(structure)
    }

    fun updateStructure(structure: Structure) {
        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()

        val groundedBlocks = LongOpenHashSet()
        structure.forEach { p, _, _ ->
            val belowPos = p.below()
            if (!structure.exists(belowPos)) {
                val chunk = chunks.at(belowPos)
                val isGround = chunk?.getBlockState(belowPos)?.let { !it.isAir && !it.isEmpty } ?: false
                if (isGround) groundedBlocks.add(p.asLong())
            }
        }

        val canReachGround = LongOpenHashSet()
        canReachGround.addAll(groundedBlocks)
        var changed = true
        while (changed) {
            changed = false
            structure.forEach { p, _, _ ->
                val longPos = p.asLong()
                if (canReachGround.contains(longPos)) return@forEach
                p.forEachNeighborNoAlloc(npos) { n ->
                    if (canReachGround.contains(n.asLong())) {
                        canReachGround.add(longPos)
                        changed = true
                    }
                }
            }
        }

        val blocksByHeight = mutableMapOf<Int, MutableList<Long>>()
        val iter = structure.longIterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val y = BlockPos.getY(entry.longKey)
            blocksByHeight.getOrPut(y) { mutableListOf() }.add(entry.longKey)
        }

        val sortedHeights = blocksByHeight.keys.sortedDescending()
        val loadOnBlock = Long2LongOpenHashMap()

        for (height in sortedHeights) {
            val blocksAtHeight = blocksByHeight[height] ?: continue

            for (longPos in blocksAtHeight) {
                pos.set(longPos)
                val data = structure.getLong(longPos)
                val state = Block.stateById(data.xi)
                val ownWeight = BlockWeight.of(state).value
                val isGrounded = groundedBlocks.contains(longPos)

                val loadFromAbove = if (loadOnBlock.containsKey(longPos))
                    Float.fromBits(loadOnBlock.get(longPos).toInt())
                else 0f
                val totalLoad = ownWeight + loadFromAbove

                val belowPos = pos.below()
                val hasDirectBelow = structure.exists(belowPos)

                val verticalSupporters = mutableListOf<Long>()
                val horizontalSupporters = mutableListOf<Long>()

                if (hasDirectBelow) {
                    verticalSupporters.add(belowPos.asLong())
                }

                pos.forEachNeighborNoAlloc(npos) { n ->
                    if (n.y < height && structure.exists(n) && n.asLong() != belowPos.asLong()) {
                        verticalSupporters.add(n.asLong())
                    }
                    else if (n.y == height && structure.exists(n) && canReachGround.contains(n.asLong())) {
                        horizontalSupporters.add(n.asLong())
                    }
                }

                val allSupporters = verticalSupporters + horizontalSupporters

                if (allSupporters.isEmpty()) {
                    val pressure = if (isGrounded) ownWeight else totalLoad * 3f
                    structure.put(longPos, Float2.of(Block.getId(state), pressure))
                }
                else {
                    val verticalRatio = if (verticalSupporters.isNotEmpty()) 0.8f else 0f
                    val horizontalRatio = 1f - verticalRatio

                    if (verticalSupporters.isNotEmpty()) {
                        val perVertical = (totalLoad * verticalRatio) / verticalSupporters.size
                        for (supporter in verticalSupporters) {
                            val existing = if (loadOnBlock.containsKey(supporter))
                                Float.fromBits(loadOnBlock.get(supporter).toInt())
                            else 0f
                            loadOnBlock.put(supporter, (existing + perVertical).toRawBits().toLong())
                        }
                    }

                    if (horizontalSupporters.isNotEmpty() && horizontalRatio > 0f) {
                        val perHorizontal = (totalLoad * horizontalRatio) / horizontalSupporters.size
                        for (supporter in horizontalSupporters) {
                            val existing = if (loadOnBlock.containsKey(supporter))
                                Float.fromBits(loadOnBlock.get(supporter).toInt())
                            else 0f
                            loadOnBlock.put(supporter, (existing + perHorizontal).toRawBits().toLong())
                        }
                    }

                    val pressure = if (isGrounded) ownWeight else totalLoad
                    structure.put(longPos, Float2.of(Block.getId(state), pressure))
                }
            }
        }
    }

    suspend fun initStructure(seed: BlockPos): Structure? {
        val structure = Structure()
        val dfs = BfsBox(limit, 10_000)

        dfs.scan(seed) { p ->
            val chunk = chunks.at(p) ?: return@scan BfsBox.ScanCommand.Continue
            val block = chunk.getBlockState(p)
            if (block.isAir || block.isEmpty || block.isBuilding().not) {
                return@scan BfsBox.ScanCommand.Continue
            }
            val w = BlockWeight.of(block).value
            structure.put(p.asLong(), Float2.of(Block.getId(block), w))
            BfsBox.ScanCommand.VisitNeighborWithCorners
        }

        return if (structure.isEmpty) null else structure
    }
}
