package nipah.edify.types

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.utils.*

sealed class BlockStrength(
    val willPut: Float,
    val willBreak: Float,
    val willExplode: Float,
    val intensity: (BlockWeight) -> Float = { w -> w.value },
) {
    companion object {
        fun of(state: BlockState): BlockStrength {
            if (state.isOf(Blocks.BEDROCK)) {
                return Unbreakable
            }
            if (state.isHeavy()) {
                return VeryHeavy
            }
            if (state.isDirtLike()) {
                return Fragile
            }
            if (state.isNonSupporting()) {
                return Breakable
            }
            if (state.isStoneLike()) {
                return Heavy
            }
            if (state.isLogLike()) {
                return Lightweight
            }
            if (state.isPlankLike()) {
                return LightweightStructural
            }
            if (state.isExplosive()) {
                return Explosive
            }
            return Lightweight
        }
    }

    data object Unbreakable: BlockStrength(0f, 0f, 0f, { 0f })

    data object Fragile: BlockStrength(
        willPut = 0.9f,
        willBreak = 0.5f,
        willExplode = 0.01f,
        intensity = { weight -> 0.05f * weight.value }
    )

    data object Lightweight: BlockStrength(
        willPut = 0.7f,
        willBreak = 0.3f,
        willExplode = 0.1f,
        intensity = { weight -> 0.1f * weight.value }
    )

    data object LightweightStructural: BlockStrength(
        willPut = 0.5f,
        willBreak = 0.5f,
        willExplode = 0.05f,
        intensity = { weight -> 0.2f * weight.value }
    )

    data object Breakable: BlockStrength(
        willPut = 0.1f,
        willBreak = 0.9f,
        willExplode = 0.015f,
        intensity = { weight -> 0.1f * weight.value }
    )

    data object Heavy: BlockStrength(
        willPut = 0.2f,
        willBreak = 0.7f,
        willExplode = 0.07f,
        intensity = { weight -> 0.5f * weight.value }
    )

    data object VeryHeavy: BlockStrength(
        willPut = 0.1f,
        willBreak = 0.5f,
        willExplode = 0.5f,
        intensity = { weight -> 1.3f * weight.value }
    )

    data object Explosive: BlockStrength(
        willPut = 0.01f,
        willBreak = 0.01f,
        willExplode = 0.9f,
        intensity = { weight -> 3f * weight.value }
    )
}
