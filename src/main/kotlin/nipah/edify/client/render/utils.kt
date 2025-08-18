package nipah.edify.client.render

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import org.joml.Vector3f

fun createBatch(
    level: BlockAndTintGetter,
    blocks: List<BlockPos>,
    origin: BlockPos = blocks.first(),
) {
    if (blocks.isEmpty()) return
    val centerOfMass = blocks.fold(Vector3f(0f, 0f, 0f)) { acc, pos ->
        acc.add(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
    }.div(blocks.size.toFloat())
    val velocity = Vector3f(
        centerOfMass.x - origin.x.toFloat(),
        centerOfMass.y - origin.y.toFloat(),
        centerOfMass.z - origin.z.toFloat()
    ).normalize().mul(-0.1f)
    velocity.x *= -1f
    velocity.z *= -1f
    velocity.y = -0.3f
    val batch = FallingBatch(
        origin,
        null,
        pos = Vector3f(origin.x.toFloat(), origin.y.toFloat(), origin.z.toFloat()),
        vel = velocity,
        blocks = blocks.map { pos ->
            pos to level.getBlockState(pos)
        }
    )
    BatchRenderer.add(batch)
}
