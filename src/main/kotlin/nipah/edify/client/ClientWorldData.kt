package nipah.edify.client

import net.minecraft.core.BlockPos
import net.neoforged.neoforge.network.PacketDistributor
import nipah.edify.BlockGroup
import nipah.edify.network.BlockGroupPacket

object ClientWorldData {
    private val blocks = mutableMapOf<BlockPos, BlockGroup>()

    internal fun setBlockGroup(pos: BlockPos, group: BlockGroup) {
        blocks[pos] = group
    }

    fun groupAt(pos: BlockPos): BlockGroup? {
        return blocks[pos]
    }

    private var clocks = 0
    fun groupAtOrRequest(pos: BlockPos): BlockGroup? {
        clocks++
        if (clocks > 20) {
            clocks = 0
            PacketDistributor.sendToServer(
                BlockGroupPacket.Request(pos)
            )
        }
        return blocks.getOrElse(pos) {
            PacketDistributor.sendToServer(
                BlockGroupPacket.Request(pos)
            )
            null
        }
    }
}
