package nipah.edify.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexBuffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.client.model.data.ModelData
import nipah.edify.entities.FallingStructureEntity
import nipah.edify.utils.withPush
import org.joml.Matrix4f

// Client setup
@EventBusSubscriber(value = [Dist.CLIENT])
object ClientHooks {
    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Pre) {

    }

    val buffer = ByteBufferBuilder(64_000)

    @SubscribeEvent
    fun onRenderLevelStage(e: RenderLevelStageEvent) {
        if (e.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return
        return

        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val pose = e.poseStack ?: return
        val cam = e.camera.position
        val rnd = RandomSource.create(42)
        val disp = mc.blockRenderer

        // turn lightmap on for block shaders (some stages don't have it)
        mc.gameRenderer.lightTexture().turnOnLightLayer()

        // local buffer; we’ll flush after drawing
        val buf = MultiBufferSource.immediate(ByteBufferBuilder(1_048_576))

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

        pose.pushPose()
        for (b in FallingStructureEntity.toRender) {
//            Gizmos.box(
//                aabb = b.aabb,
//                color = 0xff00ff00.toInt(),
//            )

            pose.withPush {
                // camera-space placement
                pose.translate(b.pos.x - cam.x, b.pos.y - cam.y, b.pos.z - cam.z)

                if (b.blocks.size < 500) {
                    pose.withPush {
                        pose.mulPose(b.rotation)
                        for ((worldPos, state) in b.blocks) {
                            drawBlock(b.origin, worldPos, state)
                        }
                    }
                }
                else {
                    val vbo = b.vbo ?: return@withPush

                    RenderSystem.enableDepthTest()
                    RenderSystem.disableBlend()

                    val view = Matrix4f(e.modelViewMatrix)
                        .translate(
                            (b.pos.x - cam.x).toFloat(),
                            (b.pos.y - cam.y).toFloat(),
                            (b.pos.z - cam.z).toFloat()
                        )
                        .rotate(b.rotation)

                    mc.gameRenderer.lightTexture().turnOnLightLayer()
                    RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS)
                    val shader = GameRenderer.getRendertypeCutoutShader() ?: run { pose.popPose(); return }

                    vbo.bind()
                    vbo.drawWithShader(view, e.projectionMatrix, shader)
                    VertexBuffer.unbind()

                    for ((worldPos, state) in b.nonRenderable) {
                        drawBlock(b.origin, worldPos, state)
                    }
                }
            }
        }
        pose.popPose()

        // flush everything we wrote
        buf.endBatch()
    }
}
