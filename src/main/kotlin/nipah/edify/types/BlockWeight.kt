package nipah.edify.types

import net.minecraft.tags.BlockTags
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
                return BlockWeight(100f)
            }
            if (state.isDirtLike()) {
                return BlockWeight(25f)
            }
            if (state.isNonSupporting()) {
                return BlockWeight(0.001f)
            }
            if (state.isLogLike()) {
                return BlockWeight(15f)
            }
            if (state.isPlankLike()) {
                return BlockWeight(10f)
            }
            if (state.isStoneLike()) {
                return BlockWeight(50f)
            }
            if (state.isExplosive()) {
                return BlockWeight(7f)
            }
            if (state.isAir) {
                return BlockWeight(0f)
            }
            if (state.has(BlockTags.IMPERMEABLE)) {
                return BlockWeight(0.5f)
            }
            return BlockWeight(10f)
        }
    }
}
