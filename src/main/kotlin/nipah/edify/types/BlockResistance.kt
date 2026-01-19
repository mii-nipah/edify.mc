package nipah.edify.types

import net.minecraft.tags.BlockTags
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
                return BlockResistance(20f.half)
            }
            if (state.isDirtLike()) {
                return BlockResistance(4f.half)
            }
            if (state.isNonSupporting()) {
                return BlockResistance(0.1f.half)
            }
            if (state.isLogLike()) {
                return BlockResistance(8f.half)
            }
            if (state.isPlankLike()) {
                return BlockResistance(6f.half)
            }
            if (state.isStoneLike()) {
                return BlockResistance(10f.half)
            }
            if (state.isExplosive()) {
                return BlockResistance(1f.half)
            }
            if (state.isAir) {
                return BlockResistance(0f.half)
            }
            if (state.has(BlockTags.IMPERMEABLE)) {
                return BlockResistance(5f.half)
            }
            return BlockResistance(3f.half)
        }
    }
}
