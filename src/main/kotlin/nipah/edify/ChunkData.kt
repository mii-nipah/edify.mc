package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.utils.*
import kotlin.random.Random

class ChunkData(val chunkPos: ChunkPos, chunk: LevelChunk) {
    private val minBuildHeight = chunk.minBuildHeight
    private val maxBuildHeight = chunk.maxBuildHeight
    private val foundation = IntArray3d(
        16,
        chunk.height + 1,
        16
    )

    fun foundationAt(x: Int, y: Int, z: Int): Boolean {
        return foundation.boundedContainsValue(x, safeY(y), z, 1)
    }

    fun findClosestFoundations(wx: Int, wy: Int, wz: Int, maxDistance: Int): List<BlockPos> {
        val checkPos = BlockPos(wx, safeY(wy), wz).toLocalPos()

        val results = mutableListOf<BlockPos>()

        val pos = BlockPos.MutableBlockPos()
        foundation.forEach { x, y, z, v ->
            if (v == 1) {
                pos.set(x, y + minBuildHeight, z)
                val dist = checkPos.distManhattan(pos)
                if (dist > maxDistance) {
                    return@forEach LoopControl.Break
                }
                val wpos = pos.toWorldPos(chunkPos)
                results.add(wpos)
            }
            LoopControl.Continue
        }

        return results
    }

    fun setFoundationAt(x: Int, y: Int, z: Int, value: Boolean) {
        foundation[x, safeY(y), z] = if (value) 1 else 0
    }

    fun safeY(worldY: Int): Int {
        return worldY - minBuildHeight
    }

    init {
        val chanceToFoundation =
            Configs.common.chunkData.nonBedrockFoundationChance.get().toFloat()
        val wpos = BlockPos.MutableBlockPos()
        val upperBound = (maxBuildHeight - minBuildHeight) / 2
        chunk.forEachBlock { pos ->
            chunk.localToWorldPosNoAlloc(pos, wpos)
            val block = chunk.getBlockState(wpos)
            if (block.isAir || block.isEmpty || block.block is LiquidBlock) {
                return@forEachBlock
            }
            if (block.isOf(Blocks.BEDROCK)) {
                foundation[pos.x, safeY(pos.y), pos.z] = 1
            }
            else {
                val anyIsFoundation = pos.findNeighborNoAlloc { npos ->
                    return@findNeighborNoAlloc foundation.boundedContainsEitherValue(npos.x, safeY(npos.y), npos.z, 1, 2)
                } != null
                if (anyIsFoundation) {
                    if (block.isNaturalTerrain() && block.has(BlockTags.TERRACOTTA).not) {
                        val distanceToUpper = (pos.y - minBuildHeight).toFloat() / upperBound.toFloat()
                        foundation[pos.x, safeY(pos.y), pos.z] =
                            if (Random.nextChance(chanceToFoundation * (1f - distanceToUpper)))
                                1
                            else 2
                    }
                    else {
                        foundation[pos.x, safeY(pos.y), pos.z] = 2
                    }
                }
            }
        }
    }
}
