package nipah.edify.network

import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import nipah.edify.BlockGroup
import nipah.edify.modId

sealed class BlockGroupPacket: CustomPacketPayload {

    data class Request(val pos: BlockPos): BlockGroupPacket() {
        companion object {
            val type = CustomPacketPayload.Type<Request>(
                ResourceLocation.fromNamespaceAndPath(modId, "block_group_req")
            )
            val codec = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                Request::pos,
                ::Request
            )
        }

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
            return type
        }
    }

    data class Response(val pos: BlockPos, val group: BlockGroup?): BlockGroupPacket() {
        companion object {
            val type = CustomPacketPayload.Type<Response>(
                ResourceLocation.fromNamespaceAndPath(modId, "block_group_res")
            )
            val codec = StreamCodec.composite(
                BlockPos.STREAM_CODEC,
                Response::pos,
                StreamCodec.of<ByteBuf, BlockGroup?>(
                    { buf, group ->
                        when (group) {
                            null -> buf.writeByte(-1)
                            is BlockGroup.Bedrock -> buf.writeByte(0)
                            is BlockGroup.Natural -> buf.writeByte(1)
                            is BlockGroup.Group -> {
                                buf.writeByte(2)
                                buf.writeInt(group.id)
                            }
                        }
                    },
                    { buf ->
                        val type = buf.readByte().toInt()
                        when (type) {
                            -1 -> null
                            0 -> BlockGroup.Bedrock()
                            1 -> BlockGroup.Natural()
                            2 -> {
                                val id = buf.readInt()
                                BlockGroup.Group(id)
                            }

                            else -> throw IllegalArgumentException("Unknown block group type: $type")
                        }
                    }
                ),
                Response::group,
                ::Response
            )
        }

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
            return type
        }
    }
}
