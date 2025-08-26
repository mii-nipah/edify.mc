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
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import nipah.edify.block.DebrisBlock
import nipah.edify.chunks.removeDebrisData
import nipah.edify.chunks.setDebrisAt
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

                        if (block is DebrisBlock) {
                            if (Random.nextChance(0.3f)) {
                                removeDebrisData(pos)
                                setBlockNeverNotify(pos, Blocks.AIR.defaultBlockState())
                                continue
                            }
                            setDebrisAt(pos, state)
                            continue
                        }

                        setBlockNeverNotify(pos, Blocks.AIR.defaultBlockState())
                        if (Random.nextChance(powerToResistanceRatio)) {
                            setDebrisAt(pos, state)
                        }
                    }
                }
            }
        }
    }
    sendParticlesAt(
        center,
        ParticleTypes.EXPLOSION,
        (radius * radius * 0.5f).toInt().coerceAtLeast(1).coerceAtMost(10),
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

inline fun Level.blockcastRay(
    start: BlockPos,
    direction: BlockPos,
    length: Int,
    step: Int = 10,
    stopCondition: (BlockPos) -> Boolean = { pos -> getBlockState(pos).isAir.not() },
): BlockPos? {
    val end = start.offset(direction.x * length, direction.y * length, direction.z * length)
    return blockcastLine(start, end, step, stopCondition)
}

inline fun Level.blockcastLine(
    start: BlockPos,
    end: BlockPos,
    step: Int = 10,
    stopCondition: (BlockPos) -> Boolean = { pos -> getBlockState(pos).isAir.not() },
): BlockPos? {
    val deltaX = (end.x - start.x).toDouble()
    val deltaY = (end.y - start.y).toDouble()
    val deltaZ = (end.z - start.z).toDouble()
    val length = sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
    if (length == 0.0) return null
    val stepX = deltaX / length * step
    val stepY = deltaY / length * step
    val stepZ = deltaZ / length * step

    val pos = BlockPos.MutableBlockPos()
    var currentX = start.x.toDouble()
    var currentY = start.y.toDouble()
    var currentZ = start.z.toDouble()
    var traveled = 0.0
    while (traveled <= length) {
        pos.set(currentX.toInt(), currentY.toInt(), currentZ.toInt())
        if (stopCondition(pos)) {
            return pos
        }
        currentX += stepX
        currentY += stepY
        currentZ += stepZ
        traveled += step
    }
    return null
}

fun Level.setBlockInTheFirstFreePos(
    pos: BlockPos,
    state: BlockState,
    limit: Int = 100,
) {
    val mutPos = pos.mutable()
    var iter = 0
    while (true) {
        iter++
        if (iter > limit) {
            return
        }
        val curState = getBlockState(mutPos)

        fun checkAndPlace(at: BlockPos): Boolean {
            val to = getBlockState(at)
            if (to.isAir || to.isEmpty || to.block is LiquidBlock) {
                setBlockAndUpdate(at, state)
                return true
            }
            return false
        }

        if (checkAndPlace(mutPos)) {
            return
        }
        val ogX = mutPos.x
        val ogY = mutPos.y
        val ogZ = mutPos.z
        mutPos.forEachNeighborNoAlloc(mutPos) { npos ->
            if (checkAndPlace(npos)) {
                return
            }
        }
        mutPos.set(ogX, ogY, ogZ)
        mutPos.move(0, 1, 0)
        if (mutPos.y > this.maxBuildHeight) {
            mutPos.set(mutPos.x, pos.y, mutPos.z)
            if (Random.nextChance(0.5f)) {
                mutPos.move(1, 0, 0)
            }
            else {
                mutPos.move(-1, 0, 0)
            }
            if (Random.nextChance(0.5f)) {
                mutPos.move(0, 0, 1)
            }
            else {
                mutPos.move(0, 0, -1)
            }
        }
    }
}
