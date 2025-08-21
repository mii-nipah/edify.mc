package nipah.edify.network

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@EventBusSubscriber
object NetworkHandler {
    const val PROTOCOL = "1"

    @SubscribeEvent
    fun register(event: RegisterPayloadHandlersEvent) {
        val reg = event.registrar(PROTOCOL)
            .versioned("0.0.1")
            .optional()

        reg.playToClient(
            FallingStructureBlockRemovedPacket.TYPE,
            FallingStructureBlockRemovedPacket.STREAM_CODEC,
            FallingStructureBlockRemovedPacket::handle
        )
    }
}
