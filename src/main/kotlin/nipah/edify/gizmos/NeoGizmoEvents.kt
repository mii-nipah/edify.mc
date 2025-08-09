package nipah.edify.gizmos

import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientTickEvent

@EventBusSubscriber(value = [Dist.CLIENT])
object NeoGizmoEvents {
    @SubscribeEvent
    fun onRender(e: RenderLevelStageEvent) {
        if (e.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return
        val cam = e.camera
        Gizmos.render(e.poseStack, cam.position)
    }
    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Pre) {
        Gizmos.tick()
    }
}
