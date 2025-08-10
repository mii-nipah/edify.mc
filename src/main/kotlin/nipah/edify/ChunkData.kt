package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.types.NeighborCheck
import nipah.edify.utils.Array3d
import nipah.edify.utils.isOf
import kotlin.random.Random

class ChunkData(val lchunk: LevelChunk) {
    private val data = Array3d<BlockData>(
        16 + 2,
        lchunk.height,
        16 + 2
    ) { BlockData.Air }

    val safeRangeX = 1 until 17
    val safeRangeY = 0 until lchunk.height
    val safeRangeZ = 1 until 17

    fun getBlock(x: Int, y: Int, z: Int): BlockData {
        return data[x, y, z]
    }

    fun getBlockSafe(x: Int, y: Int, z: Int): BlockData {
        var x = x
        var y = y
        var z = z
        if (x == 0) {
            x = 1
        }
        if (x == 17) {
            x = 16
        }
        if (z == 0) {
            z = 1
        }
        if (z == 17) {
            z = 16
        }
        return data[x, y, z]
    }

    inline fun forEachSafe(block: (x: Int, y: Int, z: Int, data: BlockData) -> Unit) {
        for (x in safeRangeX) {
            for (y in safeRangeY) {
                for (z in safeRangeZ) {
                    block(x, y, z, getBlock(x, y, z))
                }
            }
        }
    }

    fun forEachNeighbor(
        x: Int, y: Int, z: Int,
        block: (x: Int, y: Int, z: Int, data: BlockData) -> Unit,
    ) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    val nz = z + dz
                    block(nx, ny, nz, getBlock(nx, ny, nz))
                }
            }
        }
    }

    inline fun forEachNeighborNoCorners(
        x: Int, y: Int, z: Int,
        block: (x: Int, y: Int, z: Int, data: BlockData) -> Unit,
    ) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if ((dx == 0 && dy == 0 && dz == 0) || (dx != 0 && dy != 0 && dz != 0)) continue
                    val nx = x + dx
                    val ny = y + dy
                    val nz = z + dz
                    block(nx, ny, nz, getBlock(nx, ny, nz))
                }
            }
        }
    }

    inline fun forEachOnlyUnsafe(block: (x: Int, y: Int, z: Int, data: BlockData) -> Unit) {
        for (x in 0 until 18) {
            for (y in 0 until lchunk.height) {
                for (z in 0 until 18) {
                    if (x in safeRangeX && y in safeRangeY && z in safeRangeZ) continue
                    block(x, y, z, getBlock(x, y, z))
                }
            }
        }
    }

    fun isNeighbor(x: Int, y: Int): NeighborCheck {
        if (x == 0 && y == 0) return NeighborCheck.LeftBack
        if (x == 17 && y == 0) return NeighborCheck.RightBack
        if (x == 0 && y == 17) return NeighborCheck.LeftFront
        if (x == 17 && y == 17) return NeighborCheck.RightFront
        if (x == 0) return NeighborCheck.Left
        if (x == 17) return NeighborCheck.Right
        if (y == 0) return NeighborCheck.Back
        if (y == 17) return NeighborCheck.Front
        return NeighborCheck.None
    }

    fun neighborPos(check: NeighborCheck): ChunkPos? {
        return when (check) {
            NeighborCheck.Left -> ChunkPos(lchunk.pos.x - 1, lchunk.pos.z)
            NeighborCheck.Right -> ChunkPos(lchunk.pos.x + 1, lchunk.pos.z)
            NeighborCheck.Back -> ChunkPos(lchunk.pos.x, lchunk.pos.z - 1)
            NeighborCheck.Front -> ChunkPos(lchunk.pos.x, lchunk.pos.z + 1)
            NeighborCheck.LeftBack -> ChunkPos(lchunk.pos.x - 1, lchunk.pos.z - 1)
            NeighborCheck.RightBack -> ChunkPos(lchunk.pos.x + 1, lchunk.pos.z - 1)
            NeighborCheck.LeftFront -> ChunkPos(lchunk.pos.x - 1, lchunk.pos.z + 1)
            NeighborCheck.RightFront -> ChunkPos(lchunk.pos.x + 1, lchunk.pos.z + 1)
            else -> null
        }
    }

    init {
        forEachSafe { x, y, z, _ ->
            val block = lchunk.getBlockState(BlockPos(x - 1, y, z - 1))
            data[x, y, z] = when {
                block.isAir -> BlockData.Air
                block.isOf(Blocks.BEDROCK) -> BlockData.Bedrock
                block.isOf(Blocks.OBSIDIAN) -> BlockData.Foundation
                else -> {
                    var groupId = -1
                    forEachNeighborNoCorners(x, y, z) { _, _, _, neighborData ->
                        if (neighborData is BlockData.Group) {
                            groupId = neighborData.id
                        }
                    }
                    if (groupId == -1) {
                        groupId = Random.nextInt()
                    }

                    val limit = block.getDestroySpeed(
                        EmptyBlockGetter.INSTANCE,
                        BlockPos(x - 1, y, z - 1)
                    ) * 10
                    BlockData.Group(groupId, limit.toInt()).apply {
                        forEachNeighborNoCorners(x, y, z) { _, _, _, neighborData ->
                            if (neighborData is BlockData.Group) {
                                pressure += neighborData.pressure
                            }
                        }
                    }
                }
            }
        }
        forEachOnlyUnsafe { x, y, z, _ ->
            val check = isNeighbor(x, z)
            if (check != NeighborCheck.None) {
                val neighborPos = neighborPos(check) ?: return@forEachOnlyUnsafe

                val (placeX, placeY, placeZ) = when (check) {
                    NeighborCheck.Left -> Triple(0, y, z - 1)
                    NeighborCheck.Right -> Triple(17, y, z - 1)
                    NeighborCheck.Back -> Triple(x - 1, y, 0)
                    NeighborCheck.Front -> Triple(x - 1, y, 17)
                    NeighborCheck.LeftBack -> Triple(0, y, 0)
                    NeighborCheck.RightBack -> Triple(17, y, 0)
                    NeighborCheck.LeftFront -> Triple(0, y, 17)
                    NeighborCheck.RightFront -> Triple(17, y, 17)
                    else -> return@forEachOnlyUnsafe
                }

                data[x, y, z] = BlockData.Deferred(
                    neighborPos,
                    placeX, placeY, placeZ
                )
            }
        }
    }
}
