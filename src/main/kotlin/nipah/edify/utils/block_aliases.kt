package nipah.edify.utils

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

fun BlockState.isOf(block: Block): Boolean {
    return this.`is`(block)
}
