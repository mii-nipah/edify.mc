package nipah.edify

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
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
    companion object {
        private const val GLOBAL_STRENGTH = 4.0f
        private const val LEVERAGE_MULTIPLIER = 1.6f
    }

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
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
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
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
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

        // BFS for connectivity - O(N) using frontier lists
        val canReachGround = LongOpenHashSet()
        val distances = Long2IntOpenHashMap()
        distances.defaultReturnValue(Int.MAX_VALUE)

        var frontier = LongArrayList()
        val iter = groundedBlocks.iterator()
        while (iter.hasNext()) {
            val p = iter.nextLong()
            canReachGround.add(p)
            distances.put(p, 0)
            frontier.add(p)
        }

        var currentDist = 0
        while (!frontier.isEmpty) {
            val nextFrontier = LongArrayList()
            val frontierIter = frontier.iterator()
            currentDist++

            while (frontierIter.hasNext()) {
                val longPos = frontierIter.nextLong()
                pos.set(longPos)

                pos.forEachNeighborNoAlloc(npos) { n ->
                    val nLong = n.asLong()
                    if (structure.exists(nLong) && !canReachGround.contains(nLong)) {
                        canReachGround.add(nLong)
                        distances.put(nLong, currentDist)
                        nextFrontier.add(nLong)
                    }
                }
            }
            frontier = nextFrontier
        }

        // Processing load top-down and distance-aware (High -> Low distance)
        // Primary Sort: Height Descending (Standard gravity)
        // Secondary Sort: Distance Descending (Furthest blocks push to closer blocks)
        val allBlocks = LongArrayList(structure.size)
        val structIter = structure.longIterator()
        while (structIter.hasNext()) {
            allBlocks.add(structIter.next().longKey)
        }

        // Sorting using custom logic
        allBlocks.sortWith { a, b ->
            val yA = BlockPos.getY(a)
            val yB = BlockPos.getY(b)
            if (yA != yB) {
                // Higher blocks first
                return@sortWith yB.compareTo(yA)
            }
            val distA = distances.get(a)
            val distB = distances.get(b)
            // Further blocks first
            return@sortWith distB.compareTo(distA)
        }

        val loadOnBlock = Long2LongOpenHashMap()

        val blockIter = allBlocks.iterator()
        while (blockIter.hasNext()) {
            val longPos = blockIter.nextLong()

            pos.set(longPos)
            val data = structure.getLong(longPos)
            val state = Block.stateById(data.xi)
            val ownWeight = BlockWeight.of(state).value
            val isGrounded = groundedBlocks.contains(longPos)

            val currentLoadBits = loadOnBlock.get(longPos) // 0L if missing
            val loadFromAbove = if (currentLoadBits != 0L) Float.fromBits(currentLoadBits.toInt()) else 0f
            val totalLoad = ownWeight + loadFromAbove

            // Identify supporters
            // A supporter must be closure to ground (distance < current_distance) OR (distance == 0 && grounded)
            // Or strictly below?
            val myDist = distances.get(longPos)

            val belowPos = pos.below()
            val hasDirectBelow = structure.exists(belowPos) && canReachGround.contains(belowPos.asLong())

            val verticalSupporters = LongArrayList()
            val otherSupporters = LongArrayList()

            if (hasDirectBelow) {
                verticalSupporters.add(belowPos.asLong())
            }

            // A block supports us if:
            // 1. It exists
            // 2. It is connected to ground
            // 3. It is lower (y < myY) OR
            // 4. Same height (y == myY) AND closer to ground (dist < myDist)

            val height = pos.y
            pos.forEachNeighborNoAlloc(npos) { n ->
                val nLong = n.asLong()

                // Vertical check handled above via 'belowPos' optimization, but let's check other diagnostics?
                // Actually foreachNeighbor includes below. We must avoid double counting logic if we use generalized loop.
                // But for horizontal, we must check 'dist < myDist'.

                if (nLong == belowPos.asLong()) return@forEachNeighborNoAlloc // Already handled

                if (structure.exists(nLong) && canReachGround.contains(nLong)) {
                    val nDist = distances.get(nLong)

                    if (n.y < height) {
                        // Diagonal support? Usually acceptable if robust.
                        // Minecraft doesn't really have diagonal gravity usually, but for structural integrity it helps.
                        // But let's stick to Below (Vertical) and Same-level (Horizontal/Arch) for now.
                        // Actually, if n.y < height, it's valid vertical support (offset).
                        verticalSupporters.add(nLong)
                    }
                    else if (n.y == height) {
                        // Horizontal support
                        // Only valid if it's strictly closer to ground in BFS tree
                        if (nDist < myDist) {
                            otherSupporters.add(nLong)
                        }
                    }
                }
            }

            val pressure: Float
            if (isGrounded) {
                // Grounded blocks dissipate load into the world
                pressure = ownWeight
            }
            else if (!canReachGround.contains(longPos)) {
                // Floating block
                pressure = totalLoad * 50f // Massive penalty for being floating
            }
            else {
                // Distribute load
                if (!verticalSupporters.isEmpty) {
                    // Vertical support is efficient (1.0x)
                    val loadPerSupport = totalLoad / verticalSupporters.size
                    val iterator = verticalSupporters.iterator()
                    while (iterator.hasNext()) {
                        val supp = iterator.nextLong()
                        val current = Float.fromBits(loadOnBlock.get(supp).toInt())
                        loadOnBlock.put(supp, (current + loadPerSupport).toRawBits().toLong())
                    }
                    pressure = totalLoad
                }
                else if (!otherSupporters.isEmpty) {
                    // Horizontal support -> We are a cantilever/beam

                    val loadPerSupport = totalLoad / otherSupporters.size
                    val iterator = otherSupporters.iterator()
                    while (iterator.hasNext()) {
                        val supp = iterator.nextLong()

                        // Check if supporter is firmly grounded (has block below, or is ground)
                        // This prevents leverage from propagating DOWN into the foundation
                        val suppBelow = BlockPos.of(supp).below().asLong()
                        val isPillar = structure.exists(suppBelow) || groundedBlocks.contains(supp)

                        val loadToPass = if (isPillar) loadPerSupport else (loadPerSupport * LEVERAGE_MULTIPLIER)

                        val current = Float.fromBits(loadOnBlock.get(supp).toInt())
                        loadOnBlock.put(supp, (current + loadToPass).toRawBits().toLong())
                    }
                    // Our pressure is high because we are being leveraged
                    pressure = totalLoad * LEVERAGE_MULTIPLIER
                }
                else {
                    // Should technically be unreachable if 'canReachGround' is true
                    pressure = totalLoad * 2f
                }
            }
            structure.put(longPos, Float2.of(Block.getId(state), pressure))
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
