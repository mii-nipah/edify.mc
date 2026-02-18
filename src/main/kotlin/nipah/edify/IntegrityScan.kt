package nipah.edify

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.chunks.setDebrisAt
import nipah.edify.types.BlockResistance
import nipah.edify.types.BlockWeight
import nipah.edify.types.Float2
import nipah.edify.utils.*
import java.io.File
import java.util.UUID

class IntegrityScan(
    val chunks: ChunkAccess,
    private val scope: CoroutineScope,
    private val limit: Int = 30_000,
) {
    companion object {
        const val GLOBAL_STRENGTH = 2f
        const val FLOATING_PENALTY = 500f
        const val GRAVITY_BIAS = 4f
        const val SPAN_STRESS = 1f
        const val SPAN_LIMIT = 1.19f

        private fun estimateWeight(state: BlockState): Float {
            if (state.isAir) return 0f
            val key = BuiltInRegistries.BLOCK.getKey(state.block)?.path ?: return 10f
            return when {
                key == "bedrock" || key == "barrier" -> 0f
                key.contains("obsidian") || key.contains("netherite") || key.contains("ancient_debris")
                    || key.contains("iron_block") || key.contains("gold_block") || key.contains("diamond_block")
                    || key.contains("emerald_block") || key == "dragon_egg" || key == "end_portal_frame" -> 100f
                key.contains("dirt") || key.contains("sand") || key.contains("gravel") || key.contains("clay")
                    || key.contains("mud") || key.contains("soul_soil") || key.contains("farmland")
                    || key.contains("podzol") || key.contains("mycelium") || key.contains("grass_block") -> 25f
                key.contains("log") || key.contains("stem") || key.contains("wood")
                    || key.contains("hyphae") -> 15f
                key.contains("plank") -> 10f
                key.contains("stone") || key.contains("brick") || key.contains("cobble")
                    || key.contains("granite") || key.contains("diorite") || key.contains("andesite")
                    || key.contains("deepslate") || key.contains("tuff") || key.contains("basalt")
                    || key.contains("blackstone") || key.contains("ore") || key.contains("concrete")
                    || key.contains("terracotta") || key.contains("prismarine") || key.contains("purpur")
                    || key.contains("end_stone") || key.contains("sandstone") || key.contains("dripstone")
                    || key.contains("calcite") || key.contains("amethyst") -> 50f
                key.contains("tnt") || key.contains("fire") || key.contains("campfire") -> 7f
                key.contains("torch") || key.contains("button") || key.contains("pressure_plate")
                    || key.contains("lever") || key.contains("sign") || key.contains("banner")
                    || key.contains("flower") || key.contains("sapling") || key.contains("carpet")
                    || key.contains("rail") || key.contains("door") || key.contains("trapdoor")
                    || key.contains("fence") || key.contains("wall") || key.contains("slab")
                    || key.contains("stair") || key.contains("leaves") || key.contains("vine")
                    || key.contains("ladder") || key.contains("web") || key.contains("bed")
                    || key.contains("snow") || key.contains("crop") || key.contains("wheat")
                    || key.contains("carrot") || key.contains("potato") || key.contains("beetroot")
                    || key.contains("pane") || key.contains("_bars") -> 0.001f
                else -> 10f
            }
        }

        private fun estimateResistance(state: BlockState): Float {
            if (state.isAir) return 0f
            val key = BuiltInRegistries.BLOCK.getKey(state.block)?.path ?: return 3f
            return when {
                key == "bedrock" || key == "barrier" -> 65504f
                key.contains("obsidian") || key.contains("netherite") || key.contains("ancient_debris")
                    || key.contains("iron_block") || key.contains("gold_block") || key.contains("diamond_block")
                    || key.contains("emerald_block") || key == "dragon_egg" || key == "end_portal_frame" -> 80f
                key.contains("dirt") || key.contains("sand") || key.contains("gravel") || key.contains("clay")
                    || key.contains("mud") || key.contains("soul_soil") || key.contains("farmland")
                    || key.contains("podzol") || key.contains("mycelium") || key.contains("grass_block") -> 4f
                key.contains("log") || key.contains("stem") || key.contains("wood")
                    || key.contains("hyphae") -> 8f
                key.contains("plank") -> 6f
                key.contains("stone") || key.contains("brick") || key.contains("cobble")
                    || key.contains("granite") || key.contains("diorite") || key.contains("andesite")
                    || key.contains("deepslate") || key.contains("tuff") || key.contains("basalt")
                    || key.contains("blackstone") || key.contains("ore") || key.contains("concrete")
                    || key.contains("terracotta") || key.contains("prismarine") || key.contains("purpur")
                    || key.contains("end_stone") || key.contains("sandstone") || key.contains("dripstone")
                    || key.contains("calcite") || key.contains("amethyst") -> 45f
                key.contains("tnt") || key.contains("fire") || key.contains("campfire") -> 1f
                key.contains("glass") || key.contains("ice") -> 15f
                key.contains("torch") || key.contains("button") || key.contains("pressure_plate")
                    || key.contains("lever") || key.contains("sign") || key.contains("banner")
                    || key.contains("flower") || key.contains("sapling") || key.contains("carpet")
                    || key.contains("rail") || key.contains("door") || key.contains("trapdoor")
                    || key.contains("fence") || key.contains("wall") || key.contains("slab")
                    || key.contains("stair") || key.contains("leaves") || key.contains("vine")
                    || key.contains("ladder") || key.contains("web") || key.contains("bed")
                    || key.contains("snow") || key.contains("crop") || key.contains("pane")
                    || key.contains("_bars") -> 0.1f
                else -> 3f
            }
        }

        fun updateStructureWithGround(structure: Structure, groundedBlocks: LongOpenHashSet) {
            val pos = BlockPos.MutableBlockPos()
            val npos = BlockPos.MutableBlockPos()

            val reachable = LongOpenHashSet()
            val spanDist = Long2IntOpenHashMap()
            spanDist.defaultReturnValue(Int.MAX_VALUE)
            val deque = java.util.ArrayDeque<Long>()
            val giter = groundedBlocks.iterator()
            while (giter.hasNext()) {
                val gpos = giter.nextLong()
                if (structure.exists(gpos)) {
                    spanDist.put(gpos, 0)
                    deque.addLast(gpos)
                }
            }
            while (deque.isNotEmpty()) {
                val longPos = deque.pollFirst()
                if (!reachable.add(longPos)) continue
                val curDist = spanDist.get(longPos)
                pos.set(longPos)
                for (dy in intArrayOf(-1, 1)) {
                    val nLong = BlockPos.asLong(pos.x, pos.y + dy, pos.z)
                    if (structure.exists(nLong) && !reachable.contains(nLong) && curDist < spanDist.get(nLong)) {
                        spanDist.put(nLong, curDist)
                        deque.addFirst(nLong)
                    }
                }
                pos.forEachHorizontalNeighborNoAlloc(npos) { n ->
                    val nLong = n.asLong()
                    val nd = curDist + 1
                    if (structure.exists(nLong) && !reachable.contains(nLong) && nd < spanDist.get(nLong)) {
                        spanDist.put(nLong, nd)
                        deque.addLast(nLong)
                    }
                }
            }

            val allBlocks = LongArrayList(structure.size)
            val biter = structure.longIterator()
            while (biter.hasNext()) {
                allBlocks.add(biter.next().longKey)
            }
            allBlocks.sortWith { a, b -> BlockPos.getY(b).compareTo(BlockPos.getY(a)) }

            val loadOnBlock = Long2FloatOpenHashMap()
            loadOnBlock.defaultReturnValue(0f)
            val rawLoad = Long2FloatOpenHashMap()
            rawLoad.defaultReturnValue(0f)

            var totalWeight = 0f
            val blockIter = allBlocks.iterator()
            while (blockIter.hasNext()) {
                val longPos = blockIter.nextLong()
                pos.set(longPos)
                val data = structure.getLong(longPos)
                val state = Block.stateById(data.xi)
                val weight = structure.weightOf(state)
                totalWeight += weight
                val totalLoad = weight + loadOnBlock.get(longPos)
                rawLoad.put(longPos, totalLoad)

                if (!reachable.contains(longPos)) continue
                if (groundedBlocks.contains(longPos)) continue

                val belowLong = BlockPos.asLong(pos.x, pos.y - 1, pos.z)
                var belowWeight = 0f
                if (structure.exists(belowLong)) {
                    belowWeight = structure.weightOf(Block.stateById(structure.getLong(belowLong).xi)) * GRAVITY_BIAS
                }
                var horizTotal = 0f
                pos.forEachHorizontalNeighborNoAlloc(npos) { n ->
                    val nLong = n.asLong()
                    if (structure.exists(nLong) && !groundedBlocks.contains(nLong)) {
                        horizTotal += structure.weightOf(Block.stateById(structure.getLong(nLong).xi))
                    }
                }
                val sumWeight = belowWeight + horizTotal
                if (sumWeight > 0f) {
                    if (belowWeight > 0f) {
                        val share = belowWeight / sumWeight
                        loadOnBlock.put(belowLong, loadOnBlock.get(belowLong) + totalLoad * share)
                    }
                    pos.forEachHorizontalNeighborNoAlloc(npos) { n ->
                        val nLong = n.asLong()
                        if (structure.exists(nLong) && !groundedBlocks.contains(nLong)) {
                            val nw = structure.weightOf(Block.stateById(structure.getLong(nLong).xi))
                            val share = nw / sumWeight
                            loadOnBlock.put(nLong, loadOnBlock.get(nLong) + totalLoad * share)
                        }
                    }
                }
            }

            var groundedCount = 0
            var totalGroundedCap = 0f
            val gcIter = groundedBlocks.longIterator()
            while (gcIter.hasNext()) {
                val gp = gcIter.nextLong()
                if (structure.exists(gp)) {
                    groundedCount++
                    val gs = Block.stateById(structure.getLong(gp).xi)
                    totalGroundedCap += structure.weightOf(gs) * structure.resistanceOf(gs)
                }
            }
            val overloadFactor = if (totalGroundedCap > 0f)
                maxOf(0f, totalWeight / totalGroundedCap - 1f) else 0f
            val supportPressure = if (groundedCount > 0 && overloadFactor > 0f)
                totalWeight / groundedCount * overloadFactor else 0f

            val finalIter = allBlocks.iterator()
            while (finalIter.hasNext()) {
                val longPos = finalIter.nextLong()
                val data = structure.getLong(longPos)
                val stateId = data.xi
                val dist = spanDist.get(longPos)
                val pressure = if (!reachable.contains(longPos)) {
                    rawLoad.get(longPos) * FLOATING_PENALTY
                } else {
                    val state = Block.stateById(stateId)
                    val weight = structure.weightOf(state)
                    val resistance = structure.resistanceOf(state)
                    val spanStress = if (dist > 0) weight * dist.toFloat() * dist.toFloat() * SPAN_STRESS else 0f
                    val spanExceeded = dist > 0 && dist.toFloat() * dist.toFloat() * SPAN_LIMIT > resistance * GLOBAL_STRENGTH
                    val base = if (spanExceeded) rawLoad.get(longPos) * FLOATING_PENALTY
                    else rawLoad.get(longPos) + spanStress
                    if (supportPressure > 0f && !groundedBlocks.contains(longPos)) maxOf(base, supportPressure)
                    else base
                }
                structure.put(longPos, Float2.of(stateId, pressure))
            }
        }

        fun loadTestData(file: File): Pair<Structure, LongOpenHashSet> {
            val tag = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap())
            val structure = Structure.fromNbt(tag)
            val grounded = LongOpenHashSet(tag.getLongArray("grounded").toSet())
            return structure to grounded
        }
    }

    class Structure(
        private val map: Long2LongOpenHashMap = Long2LongOpenHashMap(),
        private val weightOverrides: Int2FloatOpenHashMap = Int2FloatOpenHashMap(),
        private val resistanceOverrides: Int2FloatOpenHashMap = Int2FloatOpenHashMap(),
    ) {
        val isEmpty: Boolean
            get() = map.isEmpty()

        val size: Int
            get() = map.size

        fun weightOf(state: BlockState): Float {
            val id = Block.getId(state)
            return if (weightOverrides.containsKey(id)) weightOverrides.get(id)
            else BlockWeight.of(state).value
        }

        fun resistanceOf(state: BlockState): Float {
            val id = Block.getId(state)
            return if (resistanceOverrides.containsKey(id)) resistanceOverrides.get(id)
            else BlockResistance.of(state).value.f
        }

        fun exists(at: BlockPos): Boolean = map.containsKey(at.asLong())
        fun exists(at: Long): Boolean = map.containsKey(at)

        fun getLong(at: Long): Float2 = Float2(map.get(at))
        fun getLong(at: BlockPos): Float2 = getLong(at.asLong())

        fun put(at: Long, data: Float2) {
            map.put(at, data.bits)
        }

        fun putBlock(at: Long, state: BlockState, weight: Float, resistance: Float) {
            val sid = Block.getId(state)
            weightOverrides.put(sid, weight)
            resistanceOverrides.put(sid, resistance)
            map.put(at, Float2.of(sid, weight).bits)
        }

        fun remove(at: Long) {
            map.remove(at)
        }

        fun longIterator() = map.long2LongEntrySet().iterator()

        fun toNbt(): CompoundTag {
            val tag = CompoundTag()
            val entries = ListTag()
            val seenIds = it.unimi.dsi.fastutil.ints.IntOpenHashSet()
            val iter = map.long2LongEntrySet().iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val data = Float2(entry.longValue)
                val state = Block.stateById(data.xi)
                val e = CompoundTag()
                e.putLong("p", entry.longKey)
                e.putString("s", BlockStateParser.serialize(state))
                val sid = data.xi
                if (seenIds.add(sid)) {
                    e.putFloat("w", BlockWeight.of(state).value)
                    e.putFloat("r", BlockResistance.of(state).value.f)
                }
                entries.add(e)
            }
            tag.put("entries", entries)
            return tag
        }

        companion object {
            fun fromNbt(tag: CompoundTag): Structure {
                val structure = Structure()
                val entries = tag.getList("entries", 10)
                for (i in 0 until entries.size) {
                    val e = entries.getCompound(i)
                    val pos = e.getLong("p")
                    val stateStr = e.getString("s")
                    val state = BlockStateParser.parseForBlock(
                        BuiltInRegistries.BLOCK.asLookup(), stateStr, false
                    ).blockState()
                    val sid = Block.getId(state)
                    if (e.contains("w")) {
                        structure.weightOverrides.put(sid, e.getFloat("w"))
                        structure.resistanceOverrides.put(sid, e.getFloat("r"))
                    } else if (!structure.weightOverrides.containsKey(sid)) {
                        structure.weightOverrides.put(sid, estimateWeight(state))
                        structure.resistanceOverrides.put(sid, estimateResistance(state))
                    }
                    if (state.isNonSupporting()) {
                        structure.weightOverrides.put(sid, 0.001f)
                        structure.resistanceOverrides.put(sid, 0.1f)
                    }
                    val w = structure.weightOf(state)
                    structure.put(pos, Float2.of(sid, w))
                }
                return structure
            }

            fun load(file: File): Structure {
                val tag = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                return fromNbt(tag)
            }
        }

        fun save(file: File) {
            file.parentFile?.mkdirs()
            NbtIo.writeCompressed(toNbt(), file.toPath())
        }

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
                val weight = weightOf(state)
                if (weight < 1f) continue
                val resistance = resistanceOf(state)
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
                if (maxPressure <= 0f) continue
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
                val weight = weightOf(state)
                if (weight < 1f) continue
                val resistance = resistanceOf(state)
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
                if (maxPressure <= 0f) continue
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
                    saveStructure(it)
                    isNew = true
                }
            }
            if (structure != null && isNew && structure !in simulatingStructures) {
                simulatingStructures.add(structure)
                scope.launch {
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
        repeat(2) { updateStructure(structure) }

        val maxIterations = 40
        var iteration = 0
        val maxBreaksPerTick = 3
        var stableIterations = 0

        while (iteration < maxIterations && structure.size > 0) {
            updateStructure(structure)
            iteration++

            val broken = structure.collectOverpressuredSorted()
            if (broken.isNotEmpty()) {
                stableIterations = 0
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
            } else {
                stableIterations++
                if (stableIterations >= 2) break
            }

            TickScheduler.sleep(3)
        }

        structures.remove(structure)
    }

    fun updateStructure(structure: Structure) {
        val groundedBlocks = LongOpenHashSet()
        structure.forEach { p, _, _ ->
            val belowPos = p.below()
            if (!structure.exists(belowPos)) {
                val chunk = chunks.at(belowPos)
                val isGround = chunk?.getBlockState(belowPos)?.let { !it.isAir && !it.isEmpty } ?: false
                if (isGround) groundedBlocks.add(p.asLong())
            }
        }
        updateStructureWithGround(structure, groundedBlocks)
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
            BfsBox.ScanCommand.VisitNeighbors
        }

        return if (structure.isEmpty) null else structure
    }

    private fun saveStructure(structure: Structure) {
        try {
            val dir = File("/tmp/edify")
            dir.mkdirs()
            val name = UUID.randomUUID().toString().take(8)
            val file = File(dir, "structure_${name}.nbt")

            val tag = structure.toNbt()

            val grounded = LongArrayList()
            structure.forEach { p, _, _ ->
                val belowPos = p.below()
                if (!structure.exists(belowPos)) {
                    val chunk = chunks.at(belowPos)
                    val isGround = chunk?.getBlockState(belowPos)?.let { !it.isAir && !it.isEmpty } ?: false
                    if (isGround) grounded.add(p.asLong())
                }
            }
            tag.putLongArray("grounded", grounded.toLongArray())

            file.parentFile?.mkdirs()
            NbtIo.writeCompressed(tag, file.toPath())
            Edify.LOGGER.info("[IntegrityScan] Saved structure (${structure.size} blocks, ${grounded.size} grounded) to ${file.absolutePath}")
        } catch (ex: Exception) {
            Edify.LOGGER.error("[IntegrityScan] Failed to save structure", ex)
        }
    }
}
