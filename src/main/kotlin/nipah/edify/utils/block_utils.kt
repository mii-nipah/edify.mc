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

inline fun BlockPos.findNeighbor(func: (BlockPos) -> Boolean): BlockPos? {
    var res = func(this.north())
    if (res) return this.north()
    res = func(this.south())
    if (res) return this.south()
    res = func(this.east())
    if (res) return this.east()
    res = func(this.west())
    if (res) return this.west()
    res = func(this.above())
    if (res) return this.above()
    res = func(this.below())
    if (res) return this.below()
    return null
}
