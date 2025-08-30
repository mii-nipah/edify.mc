package nipah.edify.types

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.utils.*

@JvmInline
value class BlockSupport(val value: Half) {
    companion object {
        fun of(state: BlockState): BlockSupport {
            if (state.isOf(Blocks.BEDROCK)) {
                return BlockSupport(0f.half)
            }
            if (state.isHeavy()) {
                return BlockSupport(0.3f.half)
            }
            if (state.isDirtLike()) {
                return BlockSupport(0.5f.half)
            }
            if (state.isNonSupporting()) {
                return BlockSupport(0.2f.half)
            }
            if (state.isLogLike()) {
                return BlockSupport(0.8f.half)
            }
            if (state.isPlankLike()) {
                return BlockSupport(0.9f.half)
            }
            if (state.isStoneLike()) {
                return BlockSupport(0.5f.half)
            }
            if (state.isExplosive()) {
                return BlockSupport(0.07f.half)
            }
            if (state.isAir) {
                return BlockSupport(0f.half)
            }
            return BlockSupport(0.5f.half)
        }
    }
}
