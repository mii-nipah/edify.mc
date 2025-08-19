package nipah.edify

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
        chunk.forEachBlock { pos ->
            val wpos = chunk.localToWorldPos(pos)
            val block = chunk.getBlockState(wpos)
            if (block.isAir || block.block is LiquidBlock) {
                return@forEachBlock
            }
            if (block.isOf(Blocks.BEDROCK)) {
                foundation[pos.x, safeY(pos.y), pos.z] = 1
            }
            else {
                val anyIsFoundation = pos.findNeighbor { npos ->
                    if (npos.x in 0 until 16 && npos.y in 0 until (chunk.maxBuildHeight - chunk.minBuildHeight) && npos.z in 0 until 16) {
                        if (foundation[npos.x, safeY(npos.y), npos.z] == 1) {
                            return@findNeighbor true
                        }
                    }
                    return@findNeighbor false
                } != null
                if (anyIsFoundation) {
                    foundation[pos.x, safeY(pos.y), pos.z] = 1
                }
            }
        }
    }
}
