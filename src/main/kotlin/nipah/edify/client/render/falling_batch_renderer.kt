package nipah.edify.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.client.model.data.ModelData
import nipah.edify.entities.FallingStructureEntity
import nipah.edify.utils.withPush
import org.joml.Matrix4f

class FallingStructureRenderer(val ctx: EntityRendererProvider.Context): EntityRenderer<FallingStructureEntity>(ctx) {
    override fun getTextureLocation(p0: FallingStructureEntity) = TextureAtlas.LOCATION_BLOCKS

    private val disp = Minecraft.getInstance().blockRenderer

    override fun render(e: FallingStructureEntity, entityYaw: Float, partialTick: Float, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {
        super.render(e, entityYaw, partialTick, poseStack, bufferSource, packedLight)
        val level = e.level()
        val cam = ctx

        val rnd = RandomSource.create(42)

        val buf = bufferSource

        val pose = poseStack
        pose.pushPose()

        val data = e.dataClient

        fun drawBlock(origin: BlockPos, worldPos: BlockPos, state: BlockState) {
            if (state.renderShape != RenderShape.MODEL) return

            val layer = ItemBlockRenderTypes.getChunkRenderType(state)
            val vc = buf.getBuffer(layer)

            pose.pushPose()
            val localPos = worldPos.subtract(origin)
            pose.translate(localPos.x.toDouble(), localPos.y.toDouble(), localPos.z.toDouble())

            disp.modelRenderer.tesselateBlock(
                level,
                disp.getBlockModel(state),
                state,
                worldPos,
                pose,
                vc,
                true,
                rnd,
                state.getSeed(worldPos),
                0,  // overlay
                ModelData.EMPTY,
                layer
            )
            pose.popPose()
        }

        if (data.blocks.size < 500) {
            pose.withPush {
                pose.mulPose(data.rotation)
                for ((worldPos, state) in data.blocks) {
                    drawBlock(data.origin, worldPos, state)
                }
            }
        }
        else {
            val vbo = data.vbo ?: return

            RenderSystem.enableDepthTest()
            RenderSystem.disableBlend()

            val cam = Minecraft.getInstance().gameRenderer.mainCamera.position
            val modelViewMatrix = RenderSystem.getModelViewMatrix()

            val view = Matrix4f(modelViewMatrix)
                .translate(
                    (data.pos.x - cam.x).toFloat(),
                    (data.pos.y - cam.y).toFloat(),
                    (data.pos.z - cam.z).toFloat()
                )
                .rotate(data.rotation)

            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS)
            val shader = GameRenderer.getRendertypeCutoutShader() ?: run { pose.popPose(); return }

            Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer()
            vbo.bind()
            vbo.drawWithShader(view, RenderSystem.getProjectionMatrix(), shader)
            VertexBuffer.unbind()

            for ((worldPos, state) in data.nonRenderable) {
                drawBlock(data.origin, worldPos, state)
            }
        }

        pose.popPose()
    }

    override fun shouldRender(e: FallingStructureEntity, camera: Frustum, camX: Double, camY: Double, camZ: Double): Boolean {
        if (e.dataClient.blocks.isEmpty()) {
            return false
        }
        return super.shouldRender(e, camera, camX, camY, camZ)
    }
}
