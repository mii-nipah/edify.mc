package nipah.edify.types

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.utils.*

@JvmInline
value class BlockWeight(val value: Float) {
    companion object {
        fun of(state: BlockState): BlockWeight {
            if (state.isOf(Blocks.BEDROCK)) {
                return BlockWeight(0f)
            }
            if (state.isHeavy()) {
                return BlockWeight(5f)
            }
            if (state.isDirtLike()) {
                return BlockWeight(2.5f)
            }
            if (state.isNonSupporting()) {
                return BlockWeight(0.5f)
            }
            if (state.isLogLike()) {
                return BlockWeight(2f)
            }
            if (state.isPlankLike()) {
                return BlockWeight(1f)
            }
            if (state.isStoneLike()) {
                return BlockWeight(3f)
            }
            if (state.isExplosive()) {
                return BlockWeight(1.5f)
            }
            if (state.isAir) {
                return BlockWeight(0f)
            }
            return BlockWeight(1f)
        }
    }
}
