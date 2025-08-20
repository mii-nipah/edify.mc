package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f

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

fun AABB.rotate(
    rotation: Quaternionf,
    pivotWorld: Vector3f? = null,
): net.minecraft.world.phys.AABB {
    val aabb = this

    val min = Vector3f(aabb.minX.toFloat(), aabb.minY.toFloat(), aabb.minZ.toFloat())
    val max = Vector3f(aabb.maxX.toFloat(), aabb.maxY.toFloat(), aabb.maxZ.toFloat())

    // center & half-extents in world
    val center = Vector3f(min).add(max).mul(0.5f)
    val half = Vector3f(max).sub(min).mul(0.5f)

    // rotation matrix R and |R| element-wise
    val R = Matrix3f().rotation(rotation)
    val absR = Matrix3f(R).absolute()

    // new half-extents = |R| * old half-extents
    val halfRot = absR.transform(Vector3f(half))

    // rotate center about pivot (if provided)
    val pivot = pivotWorld ?: center
    val newCenter = Vector3f(center).sub(pivot)
    R.transform(newCenter)
    newCenter.add(pivot)

    val newMin = Vector3f(newCenter).sub(halfRot)
    val newMax = Vector3f(newCenter).add(halfRot)

    return AABB(
        newMin.x.toDouble(), newMin.y.toDouble(), newMin.z.toDouble(),
        newMax.x.toDouble(), newMax.y.toDouble(), newMax.z.toDouble()
    )
}
