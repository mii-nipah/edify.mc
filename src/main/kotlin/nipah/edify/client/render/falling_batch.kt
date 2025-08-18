package nipah.edify.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.concurrent.CopyOnWriteArrayList

class FallingBatch(
    val origin: BlockPos,
    val vbo: VertexBuffer?,
    var pos: Vector3f,
    var vel: Vector3f = Vector3f(0f, -0.1f, 0f),
    val blocks: List<Pair<BlockPos, BlockState>>,
) {
    fun tick() {
        pos.add(vel) // super simple; add gravity, damping, etc.
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
