package nipah.edify

import net.minecraft.core.BlockPos
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.random.Random

sealed class BlockGroup(open val blocks: MutableSet<BlockPos>) {
    data class Foundation(override val blocks: MutableSet<BlockPos> = mutableSetOf()): BlockGroup(blocks) {
        override fun toString(): String {
            return "Bedrock(blocks=${blocks.size})"
        }
    }

    data class Group(val id: Int = Random.nextInt(), override val blocks: ConcurrentSkipListSet<BlockPos> = ConcurrentSkipListSet()): BlockGroup(blocks) {
        override fun toString(): String {
            return "Group(id=$id, blocks=${blocks.size})"
        }
    }
}
