package nipah.edify.utils

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.phys.AABB
import nipah.edify.levels.setBlockRegion
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime
import kotlin.math.sqrt
import kotlin.random.Random

fun Level.preventNextUniversalEventFromRemovingBlock() {
    Level_AnyBlockRemovedMixinRuntime.preventPostingNext()
}

fun ServerLevel.lightweightExplode(
    x: Double,
    y: Double,
    z: Double,
    radius: Float,
    power: Float = 1.0f,
) {
    val power = sqrt(power)
    val center = BlockPos(x.toInt(), y.toInt(), z.toInt())
    val pos = BlockPos.MutableBlockPos()
    val intRadius = radius.toInt() + 1
    val level = this
    setBlockRegion {
        for (dx in -intRadius..intRadius) {
            for (dy in -intRadius..intRadius) {
                for (dz in -intRadius..intRadius) {
                    pos.set(center).move(dx, dy, dz)
                    val dist = center.distSqr(pos)
                    val proximity = 1.0f - (sqrt(dist) / radius)
                    if (dist <= radius * radius) {
                        val state = getBlockState(pos)
                        val block = state.block
                        if (block is LiquidBlock) continue
                        if (block == Blocks.BEDROCK) continue
                        if (block == Blocks.AIR) continue
                        if (block == Blocks.BARRIER) continue
                        if (block == Blocks.STRUCTURE_BLOCK) continue
                        if (block == Blocks.STRUCTURE_VOID) continue
                        if (block == Blocks.JIGSAW) continue
                        if (block == Blocks.COMMAND_BLOCK) continue
                        if (block == Blocks.REINFORCED_DEEPSLATE) continue
                        if (block == Blocks.NETHER_PORTAL) continue
                        if (block == Blocks.END_PORTAL) continue
                        if (block == Blocks.END_PORTAL_FRAME) continue

                        val relativePower = power * proximity

                        val explosionResistance = state.block.explosionResistance
                        val powerToResistanceRatio = relativePower / explosionResistance
                        if (Random.nextChance(powerToResistanceRatio).not()) {
                            continue
                        }

                        setBlockNeverNotify(pos, Blocks.AIR.defaultBlockState())
                    }
                }
            }
        }
    }
    sendParticlesAt(
        center,
        ParticleTypes.EXPLOSION_EMITTER,
        1
    )
    playSound(
        null,
        center,
        SoundEvents.GENERIC_EXPLODE.value(),
        SoundSource.BLOCKS,
        3.0f,
        (1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f) * 0.7f
    )
}

fun Level.getBlockCollisionsOptimized(aabb: AABB, collisions: LongOpenHashSet) {
    val voxelCollisions = getBlockCollisions(null, aabb)
    for (voxel in voxelCollisions) {
        val bounds = voxel.bounds()
        bounds.betweenClosedBlocksNoAlloc { pos ->
            collisions.add(pos.asLong())
        }
    }
}
