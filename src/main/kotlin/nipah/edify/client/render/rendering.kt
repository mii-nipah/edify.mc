package nipah.edify.client.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.RenderShape
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

// Client setup
@EventBusSubscriber(value = [Dist.CLIENT])
object ClientHooks {
    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Pre) {
        BatchRenderer.tick()
    }

    val buffer = ByteBufferBuilder(64_000)

    @SubscribeEvent
    fun onRenderLevelStage(e: RenderLevelStageEvent) {
        if (e.stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return

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

        pose.pushPose()
        for (b in BatchRenderer.batches) {
            pose.pushPose()
            // camera-space placement
            pose.translate(b.pos.x - cam.x, b.pos.y - cam.y, b.pos.z - cam.z)

            for ((worldPos, state) in b.blocks) {
                if (state.renderShape != RenderShape.MODEL) continue

                val layer = ItemBlockRenderTypes.getChunkRenderType(state)
                val vc = buf.getBuffer(layer)

                pose.pushPose()
                val localPos = worldPos.subtract(b.origin)
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
                    net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
                    layer
                )
                pose.popPose()
            }
            pose.popPose()
        }
        pose.popPose()

        // flush everything we wrote
        buf.endBatch()
    }
}
