package nipah.edify

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import nipah.edify.client.ClientWorldData
import nipah.edify.network.BlockGroupPacket

@EventBusSubscriber(modid = modId)
object Network {
    @SubscribeEvent
    fun register(e: RegisterPayloadHandlersEvent) {
        val reg = e.registrar("1")
        reg.playToServer(
            BlockGroupPacket.Request.type,
            BlockGroupPacket.Request.codec,
            { data, x ->
//                val pos = data.pos
//                val group = WorldData.groupAt(pos)
//                if (group != null) {
//                    x.reply(BlockGroupPacket.Response(pos, group))
//                }
//                else {
//                    x.reply(BlockGroupPacket.Response(pos, null))
//                }
            }
        )
        reg.playToClient(
            BlockGroupPacket.Response.type,
            BlockGroupPacket.Response.codec,
            { data, x ->
                x.enqueueWork {
                    val pos = data.pos
                    val group = data.group ?: return@enqueueWork
                    ClientWorldData.setBlockGroup(pos, group)
                }
            }
        )
    }
}
