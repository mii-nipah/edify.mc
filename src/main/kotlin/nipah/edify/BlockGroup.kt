package nipah.edify

import net.minecraft.core.BlockPos

sealed class BlockGroup(open val blocks: MutableSet<BlockPos>) {
    data class Bedrock(override val blocks: MutableSet<BlockPos> = mutableSetOf()): BlockGroup(blocks) {
        override fun toString(): String {
            return "Bedrock(blocks=${blocks.size})"
        }
    }

    data class Natural(override val blocks: MutableSet<BlockPos> = mutableSetOf()): BlockGroup(blocks) {
        override fun toString(): String {
            return "Natural(blocks=${blocks.size})"
        }
    }

    data class Group(val id: Int, override val blocks: MutableSet<BlockPos> = mutableSetOf(), var pressure: Int = 0): BlockGroup(blocks) {
        override fun toString(): String {
            return "Group(id=$id, pressure=$pressure, blocks=${blocks.size})"
        }
    }
}
