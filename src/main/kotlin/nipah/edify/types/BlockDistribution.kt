package nipah.edify.types

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.utils.*

@JvmInline
value class BlockDistribution(val value: Half) {
    companion object {
        fun of(state: BlockState): BlockDistribution {
            if (state.isOf(Blocks.BEDROCK)) {
                return BlockDistribution(0f.half)
            }
            if (state.isHeavy()) {
                return BlockDistribution(0.3f.half)
            }
            if (state.isDirtLike()) {
                return BlockDistribution(0.7f.half)
            }
            if (state.isNonSupporting()) {
                return BlockDistribution(0.9f.half)
            }
            if (state.isLogLike()) {
                return BlockDistribution(0.8f.half)
            }
            if (state.isPlankLike()) {
                return BlockDistribution(0.9f.half)
            }
            if (state.isStoneLike()) {
                return BlockDistribution(0.5f.half)
            }
            if (state.isExplosive()) {
                return BlockDistribution(0.07f.half)
            }
            if (state.isAir) {
                return BlockDistribution(0f.half)
            }
            return BlockDistribution(0.5f.half)
        }
    }
}
