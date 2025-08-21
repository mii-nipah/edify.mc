package nipah.edify.client.render

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import nipah.edify.entities.ModEntities
import nipah.edify.modId

@EventBusSubscriber(modid = modId, value = [Dist.CLIENT])
object ClientInit {
    @SubscribeEvent
    fun registerRenderers(event: net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(ModEntities.fallingStructure.value()) { ctx ->
            FallingStructureRenderer(ctx)
        }
    }
}
