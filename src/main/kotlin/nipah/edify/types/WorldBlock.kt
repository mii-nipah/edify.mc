package nipah.edify.types

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

data class WorldBlock(
    val pos: BlockPos,
    val state: BlockState,
)

infix fun BlockPos.to(state: BlockState) = WorldBlock(this, state)
fun List<WorldBlock>.toBlockPosList() = this.map { it.pos }
fun List<WorldBlock>.toBlockStateList() = this.map { it.state }
fun List<WorldBlock>.toBlockPosSet() = this.map { it.pos }.toSet()
