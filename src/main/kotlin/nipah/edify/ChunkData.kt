package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.utils.*

class ChunkData(val chunkPos: ChunkPos, chunk: LevelChunk) {
    private val minBuildHeight = chunk.minBuildHeight
    private val maxBuildHeight = chunk.maxBuildHeight
    private val foundation = IntArray3d(
        16,
        chunk.height + 1,
        16
    )

    fun foundationAt(x: Int, y: Int, z: Int): Boolean {
        return foundation.boundedGet(x, safeY(y), z) == 1
    }

    fun setFoundationAt(x: Int, y: Int, z: Int, value: Boolean) {
        foundation[x, safeY(y), z] = if (value) 1 else 0
    }

    fun safeY(worldY: Int): Int {
        return worldY - minBuildHeight
    }

    init {
        val wpos = BlockPos.MutableBlockPos()
        chunk.forEachBlock { pos ->
            chunk.localToWorldPosNoAlloc(pos, wpos)
            val block = chunk.getBlockState(wpos)
            if (block.isAir || block.block is LiquidBlock) {
                return@forEachBlock
            }
            if (block.isOf(Blocks.BEDROCK)) {
                foundation[pos.x, safeY(pos.y), pos.z] = 1
            }
            else {
                val anyIsFoundation = pos.findNeighborNoAlloc { npos ->
                    return@findNeighborNoAlloc foundation.boundedGet(npos.x, safeY(npos.y), npos.z) == 1
                } != null
                if (anyIsFoundation) {
                    foundation[pos.x, safeY(pos.y), pos.z] = 1
                }
            }
        }
    }
}
