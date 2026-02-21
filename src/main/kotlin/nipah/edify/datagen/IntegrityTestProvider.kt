package nipah.edify.datagen

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import nipah.edify.IntegrityScan
import nipah.edify.types.Float2
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.concurrent.CompletableFuture

class IntegrityTestProvider(private val output: PackOutput) : DataProvider {

    override fun getName() = "Edify Integrity Tests"

    override fun run(cache: CachedOutput): CompletableFuture<*> = CompletableFuture.runAsync {
        runSyntheticTests()
        runCapturedTests()
    }

    companion object {
        private val LOGGER = LogManager.getLogger("EdifyIntegrityTest")

        private const val W_STONE = 50f
        private const val R_STONE = 45f
        private const val W_GLASS = 10f
        private const val R_GLASS = 15f
        private const val W_PLANK = 10f
        private const val R_PLANK = 6f
        private const val W_LOG = 15f
        private const val R_LOG = 8f

        private fun runSyntheticTests() {
            LOGGER.info("[Synthetic] ========================================")
            LOGGER.info("[Synthetic] Running synthetic structure tests")

            val tests = listOf(
                "plank_tower_3x3x11" to ::plankTower,
                "glass_tower_3x3x27" to ::glassTower,
                "stone_tower_3x3x85" to ::stoneTower,
                "glass_base_stone_top_3x3x21" to ::glassBaseStoneTower,
                "plank_house_5x5x10" to ::plankHouse,
                "stone_cube_10x10x10" to ::solidStoneCube,
                "stone_bridge_3x1x15" to ::stoneBridge,
            )

            val totalStartNs = System.nanoTime()
            for ((name, builder) in tests) {
                val (structure, grounded) = builder()
                val startNs = System.nanoTime()
                progressiveDestructionTestSynthetic(name, structure, grounded)
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                LOGGER.info("[Synthetic] $name completed in ${"%.2f".format(elapsedMs)}ms")
            }
            val totalMs = (System.nanoTime() - totalStartNs) / 1_000_000.0
            LOGGER.info("[Synthetic] All synthetic tests completed in ${"%.2f".format(totalMs)}ms")
            LOGGER.info("[Synthetic] ========================================")
        }

        private fun plankTower(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val plank = Blocks.OAK_PLANKS.defaultBlockState()
            for (x in 0 until 3) for (z in 0 until 3) for (y in 0 until 11) {
                val pos = BlockPos.asLong(x, y, z)
                structure.putBlock(pos, plank, W_PLANK, R_PLANK)
                if (y == 0) grounded.add(pos)
            }
            return structure to grounded
        }

        private fun glassTower(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val glass = Blocks.GLASS.defaultBlockState()
            for (x in 0 until 3) for (z in 0 until 3) for (y in 0 until 27) {
                val pos = BlockPos.asLong(x, y, z)
                structure.putBlock(pos, glass, W_GLASS, R_GLASS)
                if (y == 0) grounded.add(pos)
            }
            return structure to grounded
        }

        private fun stoneTower(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val stone = Blocks.STONE.defaultBlockState()
            for (x in 0 until 3) for (z in 0 until 3) for (y in 0 until 85) {
                val pos = BlockPos.asLong(x, y, z)
                structure.putBlock(pos, stone, W_STONE, R_STONE)
                if (y == 0) grounded.add(pos)
            }
            return structure to grounded
        }

        private fun glassBaseStoneTower(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val glass = Blocks.GLASS.defaultBlockState()
            val stone = Blocks.STONE.defaultBlockState()
            for (x in 0 until 3) for (z in 0 until 3) {
                for (y in 0 until 19) {
                    val pos = BlockPos.asLong(x, y, z)
                    structure.putBlock(pos, glass, W_GLASS, R_GLASS)
                    if (y == 0) grounded.add(pos)
                }
                for (y in 19 until 21) {
                    val pos = BlockPos.asLong(x, y, z)
                    structure.putBlock(pos, stone, W_STONE, R_STONE)
                }
            }
            return structure to grounded
        }

        private fun plankHouse(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val plank = Blocks.OAK_PLANKS.defaultBlockState()
            for (x in 0 until 5) for (z in 0 until 5) for (y in 0 until 10) {
                val isWall = x == 0 || x == 4 || z == 0 || z == 4
                val isRoof = y == 9
                if (!isWall && !isRoof) continue
                val pos = BlockPos.asLong(x, y, z)
                structure.putBlock(pos, plank, W_PLANK, R_PLANK)
                if (y == 0) grounded.add(pos)
            }
            return structure to grounded
        }

        private fun solidStoneCube(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val stone = Blocks.STONE.defaultBlockState()
            for (x in 0 until 10) for (z in 0 until 10) for (y in 0 until 10) {
                val pos = BlockPos.asLong(x, y, z)
                structure.putBlock(pos, stone, W_STONE, R_STONE)
                if (y == 0) grounded.add(pos)
            }
            return structure to grounded
        }

        private fun stoneBridge(): Pair<IntegrityScan.Structure, LongOpenHashSet> {
            val structure = IntegrityScan.Structure()
            val grounded = LongOpenHashSet()
            val stone = Blocks.STONE.defaultBlockState()
            for (x in 0 until 3) for (z in 0 until 15) {
                val pos = BlockPos.asLong(x, 0, z)
                structure.putBlock(pos, stone, W_STONE, R_STONE)
                if (z == 0 || z == 14) grounded.add(pos)
            }
            return structure to grounded
        }

        private fun progressiveDestructionTestSynthetic(
            name: String,
            refStructure: IntegrityScan.Structure,
            refGrounded: LongOpenHashSet,
        ) {
            val baseSupports = LongArrayList()
            val gIter = refGrounded.longIterator()
            while (gIter.hasNext()) {
                val gp = gIter.nextLong()
                if (refStructure.exists(gp)) baseSupports.add(gp)
            }
            if (baseSupports.isEmpty) return

            val rng = java.util.Random(42)
            for (i in baseSupports.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = baseSupports.getLong(i)
                baseSupports.set(i, baseSupports.getLong(j))
                baseSupports.set(j, tmp)
            }

            LOGGER.info("[Synthetic] === $name: ${refStructure.size} blocks, ${baseSupports.size} base supports ===")

            val refPrep = IntegrityScan.PreparedStructure.from(refStructure, refGrounded)

            for (pct in 0..100 step 10) {
                val prep = refPrep.clone()
                val toRemove = (baseSupports.size.toLong() * pct / 100).toInt().coerceAtMost(baseSupports.size)
                for (i in 0 until toRemove) prep.remove(baseSupports.getLong(i))

                val initialSize = prep.aliveCount
                var rounds = 0
                var totalBroken = 0
                while (true) {
                    prep.simulate()
                    val ovp = prep.collectOverpressured()
                    if (ovp.isEmpty) break
                    totalBroken += ovp.size
                    rounds++
                    val oi = ovp.iterator()
                    while (oi.hasNext()) { prep.remove(oi.nextLong()) }
                    if (rounds > 200) break
                }

                val maxRatio = prep.maxRatio()
                val survived = prep.aliveCount
                val survivedPct = if (initialSize > 0) survived * 100f / initialSize else 0f
                LOGGER.info("[Synthetic]   ${"%3d".format(pct)}% base removed ($toRemove/${baseSupports.size}): broken=$totalBroken rounds=$rounds survived=$survived/$initialSize (${"%.1f".format(survivedPct)}%) maxRatio=${"%.3f".format(maxRatio)}")
            }
        }

        private fun runCapturedTests() {
            val dir = File("/tmp/edify")
            if (!dir.exists() || !dir.isDirectory) {
                LOGGER.warn("[IntegrityTest] No structures in /tmp/edify")
                return
            }
            val files = dir.listFiles { f -> f.extension == "nbt" }?.sortedBy { it.name } ?: return
            if (files.isEmpty()) return

            LOGGER.info("[IntegrityTest] Found ${files.size} structure(s)")

            val infos = files.map { file ->
                val (structure, _) = IntegrityScan.loadTestData(file)
                var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
                var minZ = Int.MAX_VALUE; var maxZ = Int.MIN_VALUE
                structure.forEach { pos, _, _ ->
                    if (pos.x < minX) minX = pos.x; if (pos.x > maxX) maxX = pos.x
                    if (pos.z < minZ) minZ = pos.z; if (pos.z > maxZ) maxZ = pos.z
                }
                Triple(file, structure.size, BlockPos.asLong((minX + maxX) / 2 shr 4, 0, (minZ + maxZ) / 2 shr 4))
            }

            val buildings = infos.groupBy { it.third }
            LOGGER.info("[IntegrityTest] ${buildings.size} building(s)")

            val totalCapturedStartNs = System.nanoTime()
            for ((_, group) in buildings.entries.sortedByDescending { it.value.maxOf { g -> g.second } }) {
                val ref = group.maxByOrNull { it.second } ?: continue
                val startNs = System.nanoTime()
                progressiveDestructionTest(ref.first)
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
                LOGGER.info("[Progressive] ${ref.first.name} completed in ${"%.2f".format(elapsedMs)}ms")
            }
            val totalCapturedMs = (System.nanoTime() - totalCapturedStartNs) / 1_000_000.0
            LOGGER.info("[IntegrityTest] All captured tests completed in ${"%.2f".format(totalCapturedMs)}ms")

            LOGGER.info("[IntegrityTest] ========================================")
            var anyRefFailed = false
            for ((_, group) in buildings.entries.sortedByDescending { it.value.maxOf { g -> g.second } }) {
                val ref = group.maxByOrNull { it.second } ?: continue
                val (structure, grounded) = IntegrityScan.loadTestData(ref.first)
                val refPrep = IntegrityScan.PreparedStructure.from(structure, grounded)
                refPrep.simulate()
                val ovp = refPrep.collectOverpressured()
                val passed = ovp.isEmpty
                LOGGER.info("[IntegrityTest] Reference ${ref.first.name} (${ref.second} blocks): ${if (passed) "PASS" else "FAIL (${ovp.size} overpressured)"}")
                if (!passed) anyRefFailed = true
            }
            LOGGER.info("[IntegrityTest] ========================================")

            if (anyRefFailed) {
                throw RuntimeException("IntegrityTest: reference structure(s) failed!")
            }
        }

        private fun progressiveDestructionTest(file: File) {
            val (refStructure, refGrounded) = IntegrityScan.loadTestData(file)

            val baseSupports = LongArrayList()
            val gIter = refGrounded.longIterator()
            while (gIter.hasNext()) {
                val gp = gIter.nextLong()
                if (refStructure.exists(gp)) baseSupports.add(gp)
            }
            if (baseSupports.isEmpty) {
                LOGGER.warn("[Progressive] ${file.name}: no base supports, skipping")
                return
            }

            val rng = java.util.Random(42)
            for (i in baseSupports.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = baseSupports.getLong(i)
                baseSupports.set(i, baseSupports.getLong(j))
                baseSupports.set(j, tmp)
            }

            LOGGER.info("[Progressive] === ${file.name}: ${refStructure.size} blocks, ${baseSupports.size} base supports ===")

            val refPrep = IntegrityScan.PreparedStructure.from(refStructure, refGrounded)

            for (pct in 0..100 step 5) {
                val prep = refPrep.clone()
                val toRemove = (baseSupports.size.toLong() * pct / 100).toInt().coerceAtMost(baseSupports.size)
                for (i in 0 until toRemove) prep.remove(baseSupports.getLong(i))

                val initialSize = prep.aliveCount
                var rounds = 0
                var totalBroken = 0
                while (true) {
                    prep.simulate()
                    val ovp = prep.collectOverpressured()
                    if (ovp.isEmpty) break
                    totalBroken += ovp.size
                    rounds++
                    val oi = ovp.iterator()
                    while (oi.hasNext()) { prep.remove(oi.nextLong()) }
                    if (rounds > 200) break
                }

                val maxRatio = prep.maxRatio()
                val survived = prep.aliveCount
                val survivedPct = if (initialSize > 0) survived * 100f / initialSize else 0f
                LOGGER.info("[Progressive]   ${"%3d".format(pct)}% removed ($toRemove/${baseSupports.size}): broken=$totalBroken rounds=$rounds survived=$survived/$initialSize (${"%.1f".format(survivedPct)}%) maxRatio=${"%.3f".format(maxRatio)}")
            }
        }
    }
}
