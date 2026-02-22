package nipah.edify.client.render

import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import nipah.edify.Configs
import nipah.edify.block.DebrisBlock
import nipah.edify.chunks.setDebrisAt
import nipah.edify.spatial.SparseSpatialGrid
import nipah.edify.types.BlockStrength
import nipah.edify.types.BlockWeight
import nipah.edify.types.WorldBlock
import nipah.edify.utils.*
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import kotlin.random.Random

class FallingBatch(
    val origin: BlockPos,
    var pos: Vector3f,
    var foot: Vector3f,
    val centerOfMass: Vector3f,
    var rotation: Quaternionf,
    var vel: Vector3f = Vector3f(0f, -0.1f, 0f),
    var totalWeight: Float,
    val gravity: Float = vel.y.absoluteValue,
    val travelled: Float = 0f,
    val blocks: MutableList<WorldBlock>,
    val space: SparseSpatialGrid,
    val levelKey: ResourceKey<Level>,
    val tickCollisions: LongOpenHashSet = LongOpenHashSet(10_000),
    val selfDestructMode: Boolean = false,
    val selfDestructDelay: Int = 15,
) {
    companion object {
        fun computeAabb(
            blocks: List<WorldBlock>,
            origin: BlockPos,
            pos: Vector3f,
            rotation: Quaternionf,
        ): AABB {
            val minX = blocks.minOfOrNull { it.pos.x } ?: origin.x
            val minY = blocks.minOfOrNull { it.pos.y } ?: origin.y
            val minZ = blocks.minOfOrNull { it.pos.z } ?: origin.z
            val maxX = blocks.maxOfOrNull { it.pos.x } ?: origin.x
            val maxY = blocks.maxOfOrNull { it.pos.y } ?: origin.y
            val maxZ = blocks.maxOfOrNull { it.pos.z } ?: origin.z

            val minPos = BlockPos(minX, minY, minZ)
            val maxPos = BlockPos(maxX, maxY, maxZ)

            var aabb = AABB.encapsulatingFullBlocks(minPos, maxPos)
            var delta = pos.toVec3() - aabb.minPosition
//            delta = delta.add(0.0, 0.1, 0.0)
//            aabb = aabb.move(delta)

            val pivotLocal = origin.toVec3()
            val pivotWorld = pos.toVec3()

            aabb = aabb.move(
                pivotWorld.x - pivotLocal.x,
                pivotWorld.y - pivotLocal.y,
                pivotWorld.z - pivotLocal.z
            )

            return aabb.rotate(
                rotation,
                pivotWorld = pos
            )
        }
    }

    fun invalidate() {
        cachedAabb = null
    }

    private var cachedAabb: AABB? = null
    val aabb: AABB
        get() {
            if (cachedAabb != null) return cachedAabb!!
            return computeAabb(
                blocks = blocks,
                origin = origin,
                pos = pos,
                rotation = rotation
            )
        }

    fun tick() {
        pos.add(vel)
        foot.add(vel)
        vel.y = (vel.y - gravity).coerceAtLeast(-(gravity * 1.5f))
        val ogRot = rotation
        rotation = rotation.tiltTowardCoM(
            comWorld = centerOfMass,
            pivotWorld = foot
        )
        if (ogRot != rotation) {
            cachedAabb = null
            val eat = aabb
        }
        travelled + vel.length()
//        if (cachedAabb != null) {
//            val delta = pos.toVec3() - aabb.minPosition
//            cachedAabb = cachedAabb!!.move(delta)
//        }
    }

    private var selfDestructTicks = 0

    fun tickServer(level: ServerLevel): LongArrayList {
        if (selfDestructMode) {
            selfDestructTicks++
            tickCollisions.clear()
            level.getBlockCollisionsOptimized(aabb, tickCollisions)
            if (tickCollisions.any()) {
                vel.y = vel.y.absoluteValue * 0.4f
                vel.x *= -0.6f
                vel.z *= -0.6f
                pos.add(0f, vel.y, 0f)
                foot.add(0f, vel.y, 0f)
                invalidate()
            }
            if (selfDestructTicks < selfDestructDelay) return LongArrayList()
            val allRemoved = LongArrayList()
            val useDebris = Configs.common.collapse.useDebris.get()
            val offset = pos.toVec3i() - origin.toVec3i()
            for ((bpos, state) in blocks.toList()) {
                allRemoved.add(bpos.asLong())
                if (useDebris) {
                    level.setDebrisAt(bpos, state)
                }
                val visualPos = bpos.offset(offset)
                level.sendParticles(
                    ParticleTypes.DUST_PLUME,
                    visualPos.x + 0.5, visualPos.y + 0.5, visualPos.z + 0.5,
                    3, 0.25, 0.25, 0.25, 0.01
                )
            }
            if (blocks.isNotEmpty()) {
                val center = blocks[blocks.size / 2].pos.offset(offset)
                level.playSound(
                    null, center,
                    blocks.first().state.soundType.breakSound,
                    SoundSource.BLOCKS,
                    4.0f, 0.7f + level.random.nextFloat() * 0.3f
                )
            }
            blocks.clear()
            return allRemoved
        }

        tickCollisions.clear()
        level.getBlockCollisionsOptimized(aabb, tickCollisions)
        val entities = level.getEntities(null, aabb)

        val allRemovedBlocks = LongArrayList()

        fun spawnDust(pos: BlockPos) {
            level.sendParticles(
                ParticleTypes.DUST_PLUME,
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5,
                5, // count
                0.25, 0.25, 0.25, // spread
                0.01 // speed
            )
        }

        fun spawnSmoke(pos: BlockPos) {
            level.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5,
                5, // count
                0.25, 0.25, 0.25, // spread
                0.01 // speed
            )
        }

        fun spawnDebris(pos: BlockPos, block: BlockState) {
            spawnSmoke(pos)
            level.setDebrisAt(pos, block)
        }

        var moveUp = 0f
        var moves = 0
        var collisionWeight = 0f
        val toRemove = mutableListOf<WorldBlock>()
        if (tickCollisions.any()) {
            val voxel = BlockPos.MutableBlockPos()
            val cells = tickCollisions
                .map { longVoxel ->
                    voxel.set(longVoxel)
                    val voxelInOrigin = voxel.subtract(
                        pos.toVec3i() - origin.toVec3i()
                    )
                    space.keyOf(voxelInOrigin)
                }
                .distinct()
            for (cellKey in cells) {
                toRemove.clear()
                val cell = space.get(cellKey) ?: continue
                for (blockPair in cell) {
                    val longPos = blockPair.pos.asLong()

                    val (originalBlockPos, block) = blockPair
                    val movedBlockPos = originalBlockPos.offset(
                        pos.toVec3i() - origin.toVec3i()
                    )
                    val travelledFactor = 1 - (1f / originalBlockPos.distManhattan(movedBlockPos))
                    val worldBlock = level.getBlockState(movedBlockPos)
                    if (worldBlock.isAir || worldBlock.isEmpty) continue
                    if (block.block is DebrisBlock) {
                        toRemove.add(blockPair)
                        totalWeight -= BlockWeight.of(block).value
                        continue
                    }

                    val blockStr = BlockStrength.of(block)
                    val blockW = BlockWeight.of(block)
                    val worldBlockStr = BlockStrength.of(worldBlock)
                    val worldBlockW = BlockWeight.of(worldBlock)

                    moveUp += 0.5f
                    moves++
                    collisionWeight += blockW.value

                    if (Random.nextChance(blockStr.willPut)) {
                        level.setBlockInTheFirstFreePos(movedBlockPos, block)
                        toRemove.add(blockPair)
                        totalWeight -= blockW.value
                        level.sendParticlesAt(
                            movedBlockPos,
                            ParticleTypes.DUST_PLUME
                        )
                        invalidate()
                        moveUp += 0.5f
                        moves++
                        continue
                    }

                    var somethingBreaking = false
                    var selfBreaking = false
                    if (Random.nextChance(blockStr.willBreak * (1f - worldBlockStr.willBreak))) {
                        spawnDebris(movedBlockPos, block)
                        toRemove.add(blockPair)
                        totalWeight -= blockW.value
                        invalidate()
                        somethingBreaking = true
                        selfBreaking = true
                        moveUp += 0.2f
                        moves++
                    }
                    if (worldBlockStr !is BlockStrength.Unbreakable) {
                        if (blockStr.willBreak < worldBlockStr.willBreak
                            && worldBlockW.value < (totalWeight * travelledFactor)
                            && Random.nextChance(blockStr.willBreak * (1f + worldBlockStr.willBreak))
                        ) {
                            level.setDebrisAt(movedBlockPos, worldBlock)
                            somethingBreaking = true
                            moveUp += 0.2f
                            moves++
                        }
                        if (Random.nextChance(worldBlockStr.willExplode)) {
                            val intensity = blockStr.intensity(blockW)
                            // spawn explosion
                            level.lightweightExplode(
                                movedBlockPos.x + 0.5,
                                movedBlockPos.y + 0.5,
                                movedBlockPos.z + 0.5,
                                sqrt(intensity / 2f).coerceAtLeast(1f),
                                intensity
                            )
                            moveUp += 0.1f
                            moves++
                        }
                    }
                    if (somethingBreaking) {
                        level.sendParticlesAt(
                            movedBlockPos,
                            ParticleTypes.POOF
                        )
                    }
                    if (selfBreaking) {
                        continue
                    }

                    if (Random.nextChance(blockStr.willExplode * travelledFactor)) {
                        val blockW = BlockWeight.of(block)
                        val intensity = blockStr.intensity(blockW) * travelledFactor
                        // spawn explosion
                        level.lightweightExplode(
                            movedBlockPos.x + 0.5,
                            movedBlockPos.y + 0.5,
                            movedBlockPos.z + 0.5,
                            sqrt(intensity / 2f).coerceAtLeast(1f),
                            intensity
                        )
                        toRemove.add(blockPair)
                        totalWeight -= blockW.value
                        invalidate()
                        moveUp += 0.5f
                        moves++
                        continue
                    }
                }
                toRemove.forEach { blockPair ->
                    blocks.remove(blockPair)
                    space.remove(blockPair)
                    allRemovedBlocks.add(blockPair.pos.asLong())
                }
            }
        }
        moveUp /= moves.coerceAtLeast(1)
        val collisionWeightPerc = collisionWeight / totalWeight
        moveUp *= collisionWeightPerc.coerceAtMost(1f)
        vel.x += -vel.x * (0.1f * moveUp)
        vel.z += -vel.z * (0.1f * moveUp)
        vel.y += moveUp
        for (entity in entities) {
            val epos = entity.blockPosition()
            val eposInOrigin = epos.offset(
                pos.toVec3i() - origin.toVec3i()
            )
            val closestCells = space.getClosestCells(epos, 2)
                .mapNotNull { cellKey -> space.get(cellKey) }
            val closest = run {
                var closest: WorldBlock? = null
                var closestDist = Double.MAX_VALUE
                for (cell in closestCells) {
                    for (blockPair in cell) {
                        val dist = blockPair.pos.distSqr(eposInOrigin)
                        if (dist < closestDist) {
                            closestDist = dist
                            closest = blockPair
                        }
                    }
                }
                closest
            } ?: continue
            if (entity.boundingBox.inflate(2.0).contains(closest.pos.toVec3()).not()) continue
            val (bpos, bstate) = closest
            val movedPos = bpos.offset(pos.toVec3i() - origin.toVec3i())
            val travelledFactor = (1 - (1f / bpos.distManhattan(movedPos))).coerceAtLeast(0.1f)
            val blockStr = BlockStrength.of(bstate)
            val blockW = BlockWeight.of(bstate)
            val damage = blockStr.intensity(blockW) * travelledFactor
            if (entity.isInvulnerable || entity.isSpectator) continue
            entity.hurt(
                level.damageSources().fall(),
                damage
            )
        }
        return allRemovedBlocks
    }

    fun close() {
    }
}

class BatchRenderer {
    val batches = CopyOnWriteArrayList<FallingBatch>()

    fun add(batch: FallingBatch) = batches.add(batch)
    fun close() {
        batches.forEach { it.close() }
        batches.clear()
    }

    fun tick() {
        batches.removeAll {
            var toRemove = it.blocks.isEmpty()
            if (it.pos.y < -100f || it.pos.y.isNaN()) toRemove = true
            if (toRemove) {
                it.close()
            }
            toRemove
        }
        batches.forEach { it.tick() }
    }
}
