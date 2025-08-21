package nipah.edify.network

import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import nipah.edify.entities.FallingStructureEntity
import nipah.edify.modId

data class FallingStructureBlockRemovedPacket(
    val entityId: Int,
    val removedBlockPos: LongArrayList,
): CustomPacketPayload {
    companion object {
        val ID = ResourceLocation.fromNamespaceAndPath(modId, "falling_structure_block_removed")
        val TYPE = CustomPacketPayload.Type<FallingStructureBlockRemovedPacket>(ID)

        val LONG_ARRAY_LIST_CODEC: StreamCodec<FriendlyByteBuf, LongArrayList> =
            StreamCodec.of(
                { buf, list ->
                    buf.writeVarInt(list.size)
                    for (i in 0 until list.size) {
                        buf.writeVarLong(list.getLong(i))
                    }
                },
                { buf ->
                    val size = buf.readVarInt()
                    val arr = LongArrayList(size)
                    repeat(size) { arr.add(buf.readVarLong()) }
                    arr
                }
            )

        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FallingStructureBlockRemovedPacket::entityId,
            LONG_ARRAY_LIST_CODEC,
            FallingStructureBlockRemovedPacket::removedBlockPos,
            ::FallingStructureBlockRemovedPacket
        )

        fun handle(packet: FallingStructureBlockRemovedPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val level = Minecraft.getInstance().level ?: return@enqueueWork
                val e = level.getEntity(packet.entityId) as? FallingStructureEntity ?: return@enqueueWork
                e.removeBlockClient(packet.removedBlockPos)
            }
        }
    }

    override fun type() = TYPE
}
