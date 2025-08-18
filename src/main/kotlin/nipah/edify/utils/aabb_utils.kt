package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

fun AABB.forEachBlock(
    action: (pos: BlockPos) -> Unit,
) {
    val minX = minX.toInt()
    val minY = minY.toInt()
    val minZ = minZ.toInt()
    val maxX = maxX.toInt()
    val maxY = maxY.toInt()
    val maxZ = maxZ.toInt()

    for (x in minX..maxX) {
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                action(BlockPos(x, y, z))
            }
        }
    }
}

fun AABB.betweenClosedBlocks(): Iterable<BlockPos> {
    val minX = minX.toInt()
    val minY = minY.toInt()
    val minZ = minZ.toInt()
    val maxX = maxX.toInt()
    val maxY = maxY.toInt()
    val maxZ = maxZ.toInt()
    return BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)
}
