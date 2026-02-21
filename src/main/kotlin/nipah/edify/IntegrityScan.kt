package nipah.edify

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
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
import java.util.*

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
                key.contains("glass") || key.contains("ice") -> 0.5f
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
                key.contains("glass") || key.contains("ice") -> 3f
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
            if (structure.size == 0) return
            val prep = PreparedStructure.from(structure, groundedBlocks)
            prep.simulate()
            prep.syncTo(structure)
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

        fun weightOfId(stateId: Int): Float =
            if (weightOverrides.containsKey(stateId)) weightOverrides.get(stateId)
            else BlockWeight.of(Block.stateById(stateId)).value

        fun resistanceOfId(stateId: Int): Float =
            if (resistanceOverrides.containsKey(stateId)) resistanceOverrides.get(stateId)
            else BlockResistance.of(Block.stateById(stateId)).value.f

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

        fun clone(): Structure = Structure(
            Long2LongOpenHashMap(map),
            Int2FloatOpenHashMap(weightOverrides),
            Int2FloatOpenHashMap(resistanceOverrides),
        )

        fun longIterator() = map.long2LongEntrySet().fastIterator()

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
                    }
                    else if (!structure.weightOverrides.containsKey(sid)) {
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
            var maxRatioLong = Long.MIN_VALUE
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val data = Float2(entry.longValue)
                val stateId = data.xi
                val pressure = data.y
                val weight = weightOfId(stateId)
                if (weight < 1f) continue
                val resistance = resistanceOfId(stateId)
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
                if (maxPressure <= 0f) continue
                val ratio = pressure / maxPressure
                if (ratio > maxRatioSeen) {
                    maxRatioSeen = ratio
                    maxRatioLong = entry.longKey
                }
                if (pressure > maxPressure) {
                    result.add(entry.longKey)
                }
            }
            if (maxRatioLong != Long.MIN_VALUE) {
                Edify.LOGGER.info("[IntegrityScan] Max ratio: ${"%.2f".format(maxRatioSeen)} at ${BlockPos.of(maxRatioLong)} (${result.size} overpressured)")
            }
            return result
        }

        fun collectOverpressuredSorted(): List<Long> {
            val overpressured = mutableListOf<Pair<Long, Float>>()
            val iterator = longIterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val data = Float2(entry.longValue)
                val stateId = data.xi
                val pressure = data.y
                val weight = weightOfId(stateId)
                if (weight < 1f) continue
                val resistance = resistanceOfId(stateId)
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

    class PreparedStructure private constructor(
        private val n: Int,
        private val allPos: LongArray,
        private val allSids: IntArray,
        private val allWeights: FloatArray,
        private val allResist: FloatArray,
        private val grid: IntArray,
        private val gIdx: IntArray,
        private val groundedArr: BooleanArray,
        private val alive: BooleanArray,
        private val minX: Int,
        private val minY: Int,
        private val minZ: Int,
        private val yStride: Int,
        private val zStride: Int,
    ) {
        var aliveCount = n; private set
        private val pressure = FloatArray(n)

        fun simulate() {
            val reachableArr = BooleanArray(n)
            val spanDistArr = IntArray(n) { Int.MAX_VALUE }
            val bfsFront = IntArrayList()
            val bfsBack = IntArrayList()
            var backRead = 0
            for (i in 0 until n) {
                if (alive[i] && groundedArr[i]) {
                    spanDistArr[i] = 0; bfsBack.add(i)
                }
            }
            while (bfsFront.isNotEmpty() || backRead < bfsBack.size) {
                val ci = if (bfsFront.isNotEmpty()) bfsFront.removeInt(bfsFront.size - 1)
                else bfsBack.getInt(backRead++)
                if (reachableArr[ci]) continue
                reachableArr[ci] = true
                val curDist = spanDistArr[ci]
                val gi = gIdx[ci]
                var ni = grid[gi - yStride]
                if (ni >= 0 && !reachableArr[ni] && curDist < spanDistArr[ni]) {
                    spanDistArr[ni] = curDist; bfsFront.add(ni)
                }
                ni = grid[gi + yStride]
                if (ni >= 0 && !reachableArr[ni] && curDist < spanDistArr[ni]) {
                    spanDistArr[ni] = curDist; bfsFront.add(ni)
                }
                val nd = curDist + 1
                ni = grid[gi - zStride]
                if (ni >= 0 && !reachableArr[ni] && nd < spanDistArr[ni]) {
                    spanDistArr[ni] = nd; bfsBack.add(ni)
                }
                ni = grid[gi + zStride]
                if (ni >= 0 && !reachableArr[ni] && nd < spanDistArr[ni]) {
                    spanDistArr[ni] = nd; bfsBack.add(ni)
                }
                ni = grid[gi + 1]
                if (ni >= 0 && !reachableArr[ni] && nd < spanDistArr[ni]) {
                    spanDistArr[ni] = nd; bfsBack.add(ni)
                }
                ni = grid[gi - 1]
                if (ni >= 0 && !reachableArr[ni] && nd < spanDistArr[ni]) {
                    spanDistArr[ni] = nd; bfsBack.add(ni)
                }
            }

            val loadArr = FloatArray(n)
            val rawLoadArr = FloatArray(n)
            var totalWeight = 0f
            for (i in 0 until n) {
                if (!alive[i]) continue
                val weight = allWeights[i]
                totalWeight += weight
                val totalLoad = weight + loadArr[i]
                rawLoadArr[i] = totalLoad
                if (!reachableArr[i] || groundedArr[i]) continue
                val gi = gIdx[i]
                val belowIdx = grid[gi - yStride]
                var belowWeight = 0f
                if (belowIdx >= 0) belowWeight = allWeights[belowIdx] * GRAVITY_BIAS
                val n0 = grid[gi - zStride];
                val n1 = grid[gi + zStride]
                val n2 = grid[gi + 1];
                val n3 = grid[gi - 1]
                var w0 = -1f;
                var w1 = -1f;
                var w2 = -1f;
                var w3 = -1f
                var horizTotal = 0f
                if (n0 >= 0 && !groundedArr[n0]) {
                    w0 = allWeights[n0]; horizTotal += w0
                }
                if (n1 >= 0 && !groundedArr[n1]) {
                    w1 = allWeights[n1]; horizTotal += w1
                }
                if (n2 >= 0 && !groundedArr[n2]) {
                    w2 = allWeights[n2]; horizTotal += w2
                }
                if (n3 >= 0 && !groundedArr[n3]) {
                    w3 = allWeights[n3]; horizTotal += w3
                }
                val sumWeight = belowWeight + horizTotal
                if (sumWeight > 0f) {
                    if (belowWeight > 0f) loadArr[belowIdx] += totalLoad * (belowWeight / sumWeight)
                    if (w0 >= 0f) loadArr[n0] += totalLoad * (w0 / sumWeight)
                    if (w1 >= 0f) loadArr[n1] += totalLoad * (w1 / sumWeight)
                    if (w2 >= 0f) loadArr[n2] += totalLoad * (w2 / sumWeight)
                    if (w3 >= 0f) loadArr[n3] += totalLoad * (w3 / sumWeight)
                }
            }

            var groundedCount = 0
            var totalGroundedCap = 0f
            for (i in 0 until n) {
                if (alive[i] && groundedArr[i]) {
                    groundedCount++
                    totalGroundedCap += allWeights[i] * allResist[i]
                }
            }
            val overloadFactor = if (totalGroundedCap > 0f)
                maxOf(0f, totalWeight / totalGroundedCap - 1f)
            else 0f
            val supportPressure = if (groundedCount > 0 && overloadFactor > 0f)
                totalWeight / groundedCount * overloadFactor
            else 0f

            for (i in 0 until n) {
                if (!alive[i]) continue
                val dist = spanDistArr[i]
                pressure[i] = if (!reachableArr[i]) {
                    rawLoadArr[i] * FLOATING_PENALTY
                }
                else {
                    val weight = allWeights[i]
                    val resistance = allResist[i]
                    val spanStress = if (dist > 0) weight * dist.toFloat() * dist.toFloat() * SPAN_STRESS else 0f
                    val spanExceeded = dist > 0 && dist.toFloat() * dist.toFloat() * SPAN_LIMIT > resistance * GLOBAL_STRENGTH
                    val base = if (spanExceeded) rawLoadArr[i] * FLOATING_PENALTY
                    else rawLoadArr[i] + spanStress
                    if (supportPressure > 0f && !groundedArr[i]) maxOf(base, supportPressure)
                    else base
                }
            }
        }

        fun remove(pos: Long) {
            val x = BlockPos.getX(pos);
            val y = BlockPos.getY(pos);
            val z = BlockPos.getZ(pos)
            val gi = (x - minX + 1) + (z - minZ + 1) * zStride + (y - minY + 1) * yStride
            if (gi < 0 || gi >= grid.size) return
            val idx = grid[gi]
            if (idx < 0) return
            alive[idx] = false
            grid[gi] = -1
            if (groundedArr[idx]) groundedArr[idx] = false
            aliveCount--
        }

        fun collectOverpressured(): LongArrayList {
            val result = LongArrayList()
            var maxRatioSeen = 0f
            var maxRatioLong = Long.MIN_VALUE
            for (i in 0 until n) {
                if (!alive[i]) continue
                val weight = allWeights[i]
                if (weight < 1f) continue
                val resistance = allResist[i]
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
                if (maxPressure <= 0f) continue
                val ratio = pressure[i] / maxPressure
                if (ratio > maxRatioSeen) {
                    maxRatioSeen = ratio; maxRatioLong = allPos[i]
                }
                if (pressure[i] > maxPressure) result.add(allPos[i])
            }
            if (maxRatioLong != Long.MIN_VALUE) {
                Edify.LOGGER.info("[IntegrityScan] Max ratio: ${"%.2f".format(maxRatioSeen)} at ${BlockPos.of(maxRatioLong)} (${result.size} overpressured)")
            }
            return result
        }

        fun collectOverpressuredSorted(): List<Long> {
            val overpressured = mutableListOf<Pair<Long, Float>>()
            for (i in 0 until n) {
                if (!alive[i]) continue
                val weight = allWeights[i]
                if (weight < 1f) continue
                val resistance = allResist[i]
                val maxPressure = weight * resistance * GLOBAL_STRENGTH
                if (maxPressure <= 0f) continue
                val ratio = pressure[i] / maxPressure
                if (ratio > 1f) overpressured.add(allPos[i] to ratio)
            }
            return overpressured.sortedByDescending { it.second }.map { it.first }
        }

        fun maxRatio(): Float {
            var max = 0f
            for (i in 0 until n) {
                if (!alive[i]) continue
                val weight = allWeights[i]
                if (weight < 1f) continue
                val resistance = allResist[i]
                val cap = weight * resistance * GLOBAL_STRENGTH
                if (cap <= 0f) continue
                val ratio = pressure[i] / cap
                if (ratio > max) max = ratio
            }
            return max
        }

        fun syncTo(structure: Structure) {
            for (i in 0 until n) {
                if (alive[i]) structure.put(allPos[i], Float2.of(allSids[i], pressure[i]))
                else structure.remove(allPos[i])
            }
        }

        fun clone(): PreparedStructure {
            val p = PreparedStructure(
                n, allPos, allSids, allWeights, allResist,
                grid.copyOf(), gIdx, groundedArr.copyOf(), alive.copyOf(),
                minX, minY, minZ, yStride, zStride,
            )
            p.aliveCount = aliveCount
            return p
        }

        companion object {
            fun from(structure: Structure, groundedBlocks: LongOpenHashSet): PreparedStructure {
                val size = structure.size
                val unsortedPos = LongArray(size)
                val unsortedIds = IntArray(size)
                var minX = Int.MAX_VALUE;
                var maxX = Int.MIN_VALUE
                var minY = Int.MAX_VALUE;
                var maxY = Int.MIN_VALUE
                var minZ = Int.MAX_VALUE;
                var maxZ = Int.MIN_VALUE
                var extractIdx = 0
                val extractIter = structure.longIterator()
                while (extractIter.hasNext()) {
                    val entry = extractIter.next()
                    val key = entry.longKey
                    unsortedPos[extractIdx] = key
                    unsortedIds[extractIdx] = Float2(entry.longValue).xi
                    val ex = BlockPos.getX(key);
                    val ey = BlockPos.getY(key);
                    val ez = BlockPos.getZ(key)
                    if (ex < minX) minX = ex; if (ex > maxX) maxX = ex
                    if (ey < minY) minY = ey; if (ey > maxY) maxY = ey
                    if (ez < minZ) minZ = ez; if (ez > maxZ) maxZ = ez
                    extractIdx++
                }

                val yRange = maxY - minY + 1
                val counts = IntArray(yRange)
                for (i in 0 until extractIdx) counts[BlockPos.getY(unsortedPos[i]) - minY]++
                val offsets = IntArray(yRange)
                var off = 0
                for (yi in yRange - 1 downTo 0) {
                    offsets[yi] = off; off += counts[yi]
                }
                val allPos = LongArray(extractIdx)
                val allSids = IntArray(extractIdx)
                for (i in 0 until extractIdx) {
                    val yi = BlockPos.getY(unsortedPos[i]) - minY
                    val dest = offsets[yi]++
                    allPos[dest] = unsortedPos[i]
                    allSids[dest] = unsortedIds[i]
                }

                val xDim = maxX - minX + 3
                val zStride = xDim
                val yStride = xDim * (maxZ - minZ + 3)
                val gridSize = yStride * (maxY - minY + 3)
                val grid = IntArray(gridSize)
                grid.fill(-1)
                val gIdx = IntArray(extractIdx)
                for (i in 0 until extractIdx) {
                    val lp = allPos[i]
                    val gi = (BlockPos.getX(lp) - minX + 1) + (BlockPos.getZ(lp) - minZ + 1) * zStride + (BlockPos.getY(lp) - minY + 1) * yStride
                    grid[gi] = i
                    gIdx[i] = gi
                }

                val wBySid = Int2FloatOpenHashMap()
                val rBySid = Int2FloatOpenHashMap()
                val allWeights = FloatArray(extractIdx)
                val allResist = FloatArray(extractIdx)
                for (i in 0 until extractIdx) {
                    val sid = allSids[i]
                    if (!wBySid.containsKey(sid)) {
                        wBySid.put(sid, structure.weightOfId(sid))
                        rBySid.put(sid, structure.resistanceOfId(sid))
                    }
                    allWeights[i] = wBySid.get(sid)
                    allResist[i] = rBySid.get(sid)
                }

                val groundedArr = BooleanArray(extractIdx)
                val gIter = groundedBlocks.longIterator()
                while (gIter.hasNext()) {
                    val gp = gIter.nextLong()
                    val gi = (BlockPos.getX(gp) - minX + 1) + (BlockPos.getZ(gp) - minZ + 1) * zStride + (BlockPos.getY(gp) - minY + 1) * yStride
                    if (gi in 0 until gridSize) {
                        val idx = grid[gi]
                        if (idx >= 0) groundedArr[idx] = true
                    }
                }

                return PreparedStructure(
                    extractIdx, allPos, allSids, allWeights, allResist,
                    grid, gIdx, groundedArr, BooleanArray(extractIdx) { true },
                    minX, minY, minZ, yStride, zStride,
                )
            }
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
        val groundedBlocks = LongOpenHashSet()
        structure.forEach { p, _, _ ->
            val belowPos = p.below()
            if (!structure.exists(belowPos)) {
                val chunk = chunks.at(belowPos)
                val isGround = chunk?.getBlockState(belowPos)?.let { !it.isAir && !it.isEmpty } ?: false
                if (isGround) groundedBlocks.add(p.asLong())
            }
        }
        val prep = PreparedStructure.from(structure, groundedBlocks)
        repeat(2) { prep.simulate() }
        prep.syncTo(structure)

        val maxIterations = 40
        var iteration = 0
        val maxBreaksPerTick = 15
        var stableIterations = 0

        while (iteration < maxIterations && prep.aliveCount > 0) {
            prep.simulate()
            prep.syncTo(structure)
            iteration++

            val broken = prep.collectOverpressuredSorted()
            if (broken.isNotEmpty()) {
                stableIterations = 0
                val toBreak = broken.take(maxBreaksPerTick)
                Edify.LOGGER.info("[IntegrityScan] Breaking ${toBreak.size}/${broken.size} overpressured blocks")

                withContext(TickScheduler.ServerDispatcher) {
                    for (longPos in toBreak) {
                        prep.remove(longPos)
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

                repeat(2) { prep.simulate() }
                prep.syncTo(structure)
            }
            else {
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
        }
        catch (ex: Exception) {
            Edify.LOGGER.error("[IntegrityScan] Failed to save structure", ex)
        }
    }
}
