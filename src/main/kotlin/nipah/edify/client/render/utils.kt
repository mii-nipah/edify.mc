package nipah.edify.client.render

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import nipah.edify.types.BlockWeight
import nipah.edify.types.to
import nipah.edify.utils.toCopyOnWriteArrayList
import nipah.edify.utils.toVec3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.absoluteValue

fun createBatch(
    level: Level,
    blocks: List<BlockPos>,
    origin: BlockPos = blocks.first(),
) {
    if (blocks.isEmpty()) return
    val computedBlocks = blocks.map { pos ->
        pos to level.getBlockState(pos)
    }
    var totalWeight = 0f
    val centerOfMass = computedBlocks.fold(Vector3f(0f, 0f, 0f)) { acc, (pos, block) ->
        val weight = BlockWeight.of(block).value
        totalWeight += weight
        acc.add(pos.x.toFloat() * weight, pos.y.toFloat() * weight, pos.z.toFloat() * weight)
    }.div(totalWeight)

    val lowestFootPos = blocks.minByOrNull { it.y } ?: origin

    val velocity = Vector3f(
        centerOfMass.x - origin.x.toFloat(),
        centerOfMass.y - origin.y.toFloat(),
        centerOfMass.z - origin.z.toFloat()
    ).normalize().mul(-0.1f)
    velocity.x *= -1f
    velocity.z *= -1f
    velocity.y = 0f
    velocity.y = (-(velocity.length() * 1.15f).absoluteValue).coerceAtMost(-0.25f)
    val batch = FallingBatch(
        origin,
        pos = Vector3f(origin.x.toFloat(), origin.y.toFloat(), origin.z.toFloat()),
        vel = velocity,
        centerOfMass = centerOfMass,
        totalWeight = totalWeight,
        foot = lowestFootPos.toVec3f(),
        rotation = Quaternionf(),
        blocks = computedBlocks.toCopyOnWriteArrayList(),
        levelKey = level.dimension()
    )
    BatchRenderer.add(batch)
}
