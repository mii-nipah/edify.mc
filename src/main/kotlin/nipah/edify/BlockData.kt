package nipah.edify

import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level

sealed class BlockData() {
    object Air: BlockData()

    object Bedrock: BlockData()

    object Foundation: BlockData()

    data class Group(var id: Int, var limit: Int, var pressure: Int = 0): BlockData() {
        override fun toString(): String {
            return "Group(id=$id, pressure=$pressure) | limit=$limit"
        }
    }

    data class Deferred(
        private var chunkPos: ChunkPos,
        private var x: Int,
        private var y: Int,
        private var z: Int,
        private var block: BlockData? = null,
    ): BlockData() {
        fun getBlock(level: Level): BlockData {
            if (block != null) {
                return block!!
            }
            val lchunk = level.chunkSource.getChunkNow(chunkPos.x, chunkPos.z)
                ?: return Foundation
            val chunk = WorldData.getChunkData(lchunk)
            val blockData = chunk.getBlock(x, y, z)
            block = blockData
            return blockData
        }
    }
}
