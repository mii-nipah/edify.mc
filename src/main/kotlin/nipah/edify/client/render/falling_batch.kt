package nipah.edify.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.phys.AABB
import nipah.edify.types.BlockStrength
import nipah.edify.types.BlockWeight
import nipah.edify.types.WorldBlock
import nipah.edify.utils.*
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.absoluteValue
import kotlin.random.Random

class FallingBatch(
    val origin: BlockPos,
    var pos: Vector3f,
    var foot: Vector3f,
    val centerOfMass: Vector3f,
    var rotation: Quaternionf,
    var vel: Vector3f = Vector3f(0f, -0.1f, 0f),
    val gravity: Float = vel.y.absoluteValue,
    val travelled: Float = 0f,
    val blocks: CopyOnWriteArrayList<WorldBlock>,
    val levelKey: ResourceKey<Level>,
) {
    fun invalidate() {
        cachedAabb = null
        val existingVbo = cachedVbo
        RenderSystem.recordRenderCall {
            existingVbo?.close()
        }
        cachedVbo = null
    }

    private var cachedVbo: VertexBuffer? = null
    private var cachedNonRenderable: List<WorldBlock>? = null
    val vbo: VertexBuffer?
        get() {
            if (blocks.isEmpty()) return null
            cachedVbo?.let { return it }
            val mc = Minecraft.getInstance()
            val level = mc.level ?: error("No level")
            if (level.dimension() != levelKey) error("Wrong dimension")
            return (buildSolidMesh(
                level,
                blocks,
                origin
            ) ?: return null).also {
                cachedVbo = it.first
                cachedNonRenderable = it.second
            }.first
        }
    val nonRenderable: List<WorldBlock>
        get() {
            if (cachedNonRenderable != null) return cachedNonRenderable!!
            val nonRenderable = blocks.filter { isNonRenderableMesh(it.state) }
            return nonRenderable.also {
                cachedNonRenderable = it
            }
        }

    private var cachedAabb: AABB? = null
    val aabb: AABB
        get() {
            if (cachedAabb != null) return cachedAabb!!
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

    fun tickServer(level: ServerLevel) {
        val voxels = level.getBlockCollisions(null, aabb).flatMap { it.bounds().betweenClosedBlocks() }
        val entities = level.getEntities(null, aabb)

        val lootParams = LootParams.Builder(level).withLuck(0.5f)

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

        fun spawnBlockItem(pos: BlockPos, block: BlockState) {
            spawnSmoke(pos)
            Block.dropResources(
                block,
                level,
                pos
            )
        }

        var moveUp = 0f
        var moves = 0
        if (voxels.any()) {
            for (blockPair in blocks) {
                val (originalBlockPos, block) = blockPair
                val movedBlockPos = originalBlockPos.offset(
                    pos.toVec3i() - origin.toVec3i()
                )
                val travelledFactor = 1 - (1f / originalBlockPos.distManhattan(movedBlockPos))
                val worldBlock = level.getBlockState(movedBlockPos)
                if (worldBlock.isAir || worldBlock.isEmpty) continue

                val blockStr = BlockStrength.of(block)
                val worldBlockStr = BlockStrength.of(worldBlock)

                if (Random.nextChance(blockStr.willPut)) {
                    level.setBlockAndUpdate(movedBlockPos.above(), block)
                    blocks.remove(blockPair)
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
                    spawnBlockItem(movedBlockPos, block)
                    blocks.remove(blockPair)
                    invalidate()
                    somethingBreaking = true
                    selfBreaking = true
                    moveUp += 0.2f
                    moves++
                }
                if (worldBlockStr !is BlockStrength.Unbreakable) {
                    if (blockStr.willBreak < worldBlockStr.willBreak
                        && Random.nextChance(blockStr.willBreak * (1f + worldBlockStr.willBreak))
                    ) {
                        level.destroyBlock(movedBlockPos, true)
                        somethingBreaking = true
                        moveUp += 0.2f
                        moves++
                    }
                    if (Random.nextChance(worldBlockStr.willExplode)) {
                        val blockW = BlockWeight.of(block)
                        val intensity = blockStr.intensity(blockW)
                        // spawn explosion
                        level.explode(null, movedBlockPos.x + 0.5, movedBlockPos.y + 0.5, movedBlockPos.z + 0.5, intensity, Level.ExplosionInteraction.BLOCK)
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
                    level.explode(null, movedBlockPos.x + 0.5, movedBlockPos.y + 0.5, movedBlockPos.z + 0.5, intensity, Level.ExplosionInteraction.BLOCK)
                    blocks.remove(blockPair)
                    invalidate()
                    moveUp += 0.5f
                    moves++
                    continue
                }
            }
        }
        moveUp /= moves.coerceAtLeast(1)
        vel.x += -vel.x * (0.1f * moveUp)
        vel.z += -vel.z * (0.1f * moveUp)
        vel.y += moveUp
        for (entity in entities) {
            val epos = entity.blockPosition()
            val closest = blocks.minByOrNull {
                val movedPos = it.pos.offset(pos.toVec3i() - origin.toVec3i())
                movedPos.distManhattan(epos)
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
    }

    fun close() = vbo?.close()
}

object BatchRenderer {
    val batches = CopyOnWriteArrayList<FallingBatch>()

    fun add(batch: FallingBatch) = batches.add(batch)
    fun clear() {
        batches.forEach { it.close() }; batches.clear()
    }

    fun tick() {
        batches.forEach { it.tick() }
    }

    fun render(viewMV: Matrix4f, proj: Matrix4f) {
        if (batches.isEmpty()) return

        val shader = GameRenderer.getRendertypeEntitySolidShader() // match what you baked
        RenderSystem.setShader { shader }
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS)

        val lt = Minecraft.getInstance().gameRenderer.lightTexture()
        lt.turnOnLightLayer()

        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        val mv = Matrix4f()
        for (b in batches) {
            mv.set(viewMV).translate(b.pos.x, b.pos.y, b.pos.z)
            b.vbo?.bind()
            b.vbo?.drawWithShader(mv, proj, shader)
            VertexBuffer.unbind()
        }
    }
}
