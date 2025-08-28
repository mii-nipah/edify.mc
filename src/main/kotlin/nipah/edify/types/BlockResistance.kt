package nipah.edify.types

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.utils.*

@JvmInline
value class BlockResistance(val value: Half) {
    companion object {
        fun of(state: BlockState): BlockResistance {
            if (state.isOf(Blocks.BEDROCK)) {
                return BlockResistance(Half.MAX_VALUE)
            }
            if (state.isHeavy()) {
                return BlockResistance(5f.half)
            }
            if (state.isDirtLike()) {
                return BlockResistance(7f.half)
            }
            if (state.isNonSupporting()) {
                return BlockResistance(0.1f.half)
            }
            if (state.isLogLike()) {
                return BlockResistance(3f.half)
            }
            if (state.isPlankLike()) {
                return BlockResistance(3.5f.half)
            }
            if (state.isStoneLike()) {
                return BlockResistance(1.5f.half)
            }
            if (state.isExplosive()) {
                return BlockResistance(0.07f.half)
            }
            if (state.isAir) {
                return BlockResistance(0f.half)
            }
            return BlockResistance(1f.half)
        }
    }
}
