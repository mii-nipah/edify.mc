package nipah.edify.client.render

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import org.joml.Vector3f

fun createBatch(
    level: BlockAndTintGetter,
    blocks: List<BlockPos>,
    origin: BlockPos = blocks.first(),
) {
    val batch = FallingBatch(
        origin,
        null,
        pos = Vector3f(origin.x.toFloat(), origin.y.toFloat(), origin.z.toFloat()),
        vel = Vector3f(0f, -0.1f, 0f),
        blocks = blocks.map { pos ->
            pos to level.getBlockState(pos)
        }
    )
    BatchRenderer.add(batch)
}
