package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

fun BlockState.isOf(block: Block): Boolean {
    return this.`is`(block)
}

fun BlockState.has(tag: TagKey<Block>): Boolean {
    return this.`is`(tag)
}

fun BlockState.hasAny(vararg tags: TagKey<Block>): Boolean {
    for (tag in tags) {
        if (this.`is`(tag)) return true
    }
    return false
}

inline fun BlockPos.forEachNeighbor(func: (BlockPos) -> Unit) {
    func(this.north())
    func(this.south())
    func(this.east())
    func(this.west())
    func(this.above())
    func(this.below())
}
