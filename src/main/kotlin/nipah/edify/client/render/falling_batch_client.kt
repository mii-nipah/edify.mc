package nipah.edify.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexBuffer
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import nipah.edify.client.render.FallingBatch.Companion.computeAabb
import nipah.edify.types.WorldBlock
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.CopyOnWriteArrayList

data class FallingBatchClient(
    val origin: BlockPos,
    var pos: Vector3f,
    var rotation: Quaternionf,
    var vel: Vector3f = Vector3f(0f, -0.1f, 0f),
    val blocks: CopyOnWriteArrayList<WorldBlock>,
) {
    fun invalidate() {
        cachedAabb = null
        val existingVbo = cachedVbo
        RenderSystem.recordRenderCall {
            existingVbo?.close()
        }
        cachedVbo = null
    }

    fun close() {
        val cvbo = cachedVbo
        if (cvbo != null) {
            if (RenderSystem.isOnRenderThread()) {
                cvbo.close()
            }
            else {
                RenderSystem.recordRenderCall {
                    cvbo.close()
                }
            }
            cachedVbo = null
        }
    }

    private var cachedVbo: VertexBuffer? = null
    private var cachedNonRenderable: List<WorldBlock>? = null
    val vbo: VertexBuffer?
        get() {
            if (blocks.isEmpty()) return null
            cachedVbo?.let { return it }
            val mc = Minecraft.getInstance()
            val level = mc.level ?: error("No level")
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
            return computeAabb(
                blocks = blocks,
                origin = origin,
                pos = pos,
                rotation = rotation
            )
        }
}

