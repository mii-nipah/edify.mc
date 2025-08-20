package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.LevelChunk

inline fun LevelChunk.forEachBlock(action: (pos: BlockPos) -> Unit) {
    val bpos = BlockPos.MutableBlockPos(0, 0, 0)
    for (x in 0 until 16) {
        for (y in minBuildHeight until maxBuildHeight) {
            for (z in 0 until 16) {
                bpos.set(x, y, z)
                action(bpos)
            }
        }
    }
}

inline fun LevelChunk.forEachBlock(yRange: IntRange, action: (pos: BlockPos) -> Unit) {
    val bpos = BlockPos.MutableBlockPos(0, 0, 0)
    for (x in 0 until 16) {
        for (y in yRange) {
            for (z in 0 until 16) {
                bpos.set(x, y, z)
                action(bpos)
            }
        }
    }
}

fun LevelChunk.isInBounds(pos: BlockPos): Boolean {
    if ((pos.x shr 4) != this.pos.x) return false
    if ((pos.z shr 4) != this.pos.z) return false
    return pos.y in this.minBuildHeight until this.maxBuildHeight
}

fun LevelChunk.isInBoundsLocal(pos: BlockPos): Boolean {
    return pos.x in 0..15
            && pos.z in 0..15
            && pos.y in this.minBuildHeight until this.maxBuildHeight
}

fun LevelChunk.localToWorldPos(local: BlockPos): BlockPos {
    val baseX = this.pos.x shl 4 // chunk.x * 16
    val baseZ = this.pos.z shl 4 // chunk.z * 16
    return BlockPos(baseX + local.x, local.y, baseZ + local.z)
}

fun LevelChunk.localToWorldPosNoAlloc(local: BlockPos, into: BlockPos.MutableBlockPos) {
    val baseX = this.pos.x shl 4 // chunk.x * 16
    val baseZ = this.pos.z shl 4 // chunk.z * 16
    into.set(baseX + local.x, local.y, baseZ + local.z)
}

fun LevelChunk.localToWorldPos(localX: Int, y: Int, localZ: Int): BlockPos {
    val baseX = this.pos.x shl 4 // chunk.x * 16
    val baseZ = this.pos.z shl 4 // chunk.z * 16
    return BlockPos(baseX + localX, y, baseZ + localZ)
}

fun LevelChunk.worldToLocalPos(world: BlockPos): BlockPos {
    val baseX = this.pos.x shl 4 // chunk.x * 16
    val baseZ = this.pos.z shl 4 // chunk.z * 16
    return BlockPos(world.x - baseX, world.y, world.z - baseZ)
}

fun LevelChunk.worldToLocalPos(worldX: Int, y: Int, worldZ: Int): BlockPos {
    val baseX = this.pos.x shl 4 // chunk.x * 16
    val baseZ = this.pos.z shl 4 // chunk.z * 16
    return BlockPos(worldX - baseX, y, worldZ - baseZ)
}
