package nipah.edify.client.render

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import nipah.edify.CollapseMode
import nipah.edify.Configs
import nipah.edify.entities.ModEntities
import nipah.edify.spatial.SparseSpatialGrid
import nipah.edify.types.BlockWeight
import nipah.edify.types.to
import nipah.edify.utils.toVec3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.absoluteValue
import kotlin.random.Random

fun createBatch(
    level: Level,
    blocks: List<BlockPos>,
    origin: BlockPos = blocks.first(),
) {
    if (blocks.isEmpty()) return

    val collapseMode = Configs.common.collapse.mode.get()

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

    val isSelfDestruct = collapseMode == CollapseMode.ACE_OF_SPADES

    val velocity = if (isSelfDestruct) {
        Vector3f(
            (Random.nextFloat() - 0.5f) * 0.01f,
            0f,
            (Random.nextFloat() - 0.5f) * 0.01f
        )
    } else {
        Vector3f(
            centerOfMass.x - origin.x.toFloat(),
            centerOfMass.y - origin.y.toFloat(),
            centerOfMass.z - origin.z.toFloat()
        ).normalize().mul(-0.1f).also {
            it.x *= -1f
            it.z *= -1f
            it.y = 0f
            it.y = (-(it.length() * 1.15f).absoluteValue).coerceAtMost(-0.25f)
        }
    }

    val space = run {
        val space = SparseSpatialGrid(
            cellSize = 16
        )
        computedBlocks.forEach { pair ->
            space.set(pair)
        }
        space
    }

    val batch = FallingBatch(
        origin,
        pos = Vector3f(origin.x.toFloat(), origin.y.toFloat(), origin.z.toFloat()),
        vel = velocity,
        centerOfMass = centerOfMass,
        gravity = if (isSelfDestruct) 0.06f else velocity.y.absoluteValue,
        totalWeight = totalWeight,
        foot = lowestFootPos.toVec3f(),
        rotation = Quaternionf(),
        blocks = computedBlocks.toMutableList(),
        space = space,
        levelKey = level.dimension(),
        selfDestructMode = isSelfDestruct,
        selfDestructDelay = if (isSelfDestruct) Configs.common.collapse.aceOfSpadesDelay.get() else 0,
    )

    val entity = ModEntities.fallingStructure.value().create(level)!!
    entity.moveTo(
        origin.x.toDouble(),
        origin.y.toDouble(),
        origin.z.toDouble(),
        0f, 0f
    )
    entity.setSpawnData(batch)
    level.addFreshEntity(entity)
}
