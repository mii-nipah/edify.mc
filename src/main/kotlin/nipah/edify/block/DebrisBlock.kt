package nipah.edify.block

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import nipah.edify.chunks.ChunkDebris
import nipah.edify.chunks.getDebrisStateAt
import nipah.edify.chunks.moveDebrisTo
import nipah.edify.chunks.removeDebrisData
import nipah.edify.ref.IntRef
import nipah.edify.utils.*

class DebrisBlock(properties: Properties): Block(
    properties
        .destroyTime(3.0f)
        .explosionResistance(7.0f)
        .sound(SoundType.ANCIENT_DEBRIS)
        .lightLevel { 7 }
        .randomTicks()
) {
    enum class Kind(private val id: String): StringRepresentable {
        Woody("woody"),
        Rocky("rocky"),
        Metallic("metallic"),
        Dirty("dirty"),
        Misc("misc");

        override fun getSerializedName(): String {
            return id
        }
    }

    companion object {
        val layersRange = 1..3

        val layerShapes = listOf<VoxelShape>(
            box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0),
            box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0),
            box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),
        )

        val layers: IntegerProperty =
            IntegerProperty.create("layers", layersRange.first, layersRange.last)
        val kind: EnumProperty<Kind> =
            EnumProperty.create("kind", Kind::class.java)
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(layers, 1)
                .setValue(kind, Kind.Misc)
        )
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return layerShapes[state.getValue(layers) - 1]
    }

    override fun useShapeForLightOcclusion(state: BlockState): Boolean {
        return true
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        builder.add(layers, kind)
    }

    fun representingBlockState(state: BlockState): BlockState {
        return when (state.getValue(kind)) {
            Kind.Woody -> Blocks.OAK_LOG.defaultBlockState()
            Kind.Rocky -> Blocks.COBBLESTONE.defaultBlockState()
            Kind.Metallic -> Blocks.IRON_BLOCK.defaultBlockState()
            Kind.Dirty -> Blocks.DIRT.defaultBlockState()
            Kind.Misc -> Blocks.GLASS.defaultBlockState()
        }
    }

    override fun getDestroyProgress(
        state: BlockState, player: Player, level: BlockGetter, pos: BlockPos,
    ): Float {
        val base = super.getDestroyProgress(state, player, level, pos)

        val mainHandItem = player.mainHandItem
        val toolMultiplier = if (mainHandItem.isEmpty.not()) {
            player.mainHandItem.getDestroySpeed(representingBlockState(state))
        }
        else {
            1.0f
        }

        val multiplier = when (state.getValue(kind)) {
            Kind.Woody -> 1.2f + (toolMultiplier - 1.0f)
            Kind.Rocky -> 0.7f + (toolMultiplier - 1.0f)
            Kind.Metallic -> 0.5f + (toolMultiplier - 1.0f)
            Kind.Dirty -> 3.0f + (toolMultiplier - 1.0f)
            Kind.Misc -> 1.0f
        }

        val slower = when (state.getValue(layers)) {
            1 -> 1.0f
            2 -> 0.5f
            3 -> 0.33f
            else -> 1.0f
        }

        return base * (multiplier * slower)
    }

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        val below = pos.below()
        val belowState = level.getBlockState(below)
        if (belowState.isAir || belowState.isEmpty || belowState.block is LiquidBlock) {
            level.moveDebrisTo(pos, below)
            return
        }
        if (belowState.block !is DebrisBlock) {
            return
        }
        level.moveDebrisTo(pos, below)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        super.onRemove(state, level, pos, newState, movedByPiston)
        if (level.isClientSide) return
        if (state.block !is DebrisBlock) return
        if (newState.block is DebrisBlock) return
        val oldState = state
        val debris = level.getDebrisStateAt(pos) ?: return
        for (state in debris.toBlockStateList()) {
            dropResources(
                state,
                level,
                pos
            )
        }
        level.removeDebrisData(pos)
    }

    override fun getPistonPushReaction(state: BlockState): PushReaction? {
        return PushReaction.BLOCK
    }
}

fun ChunkDebris.Entry.toBlockState(): BlockState {
    val mostUsedStateCount = IntRef(0)
    val leastUsedStateCount = IntRef(0)
    val mostUsedStateId = largestStackSizeId(mostUsedStateCount)
    val leastUsedStateId = smallestStackSizeId(leastUsedStateCount)

    val mostUsedState = Block.stateById(mostUsedStateId)
        ?: return Blocks.AIR.defaultBlockState()
    val differenceInPercent = 1.0f - (leastUsedStateCount.value / mostUsedStateCount.value.toFloat())
    val kind = when {
        differenceInPercent < 0.3f -> DebrisBlock.Kind.Misc
        
        mostUsedState.isLogLike()
                || mostUsedState.isPlankLike()
                || mostUsedState.has(BlockTags.LEAVES) -> DebrisBlock.Kind.Woody

        mostUsedState.isStoneLike() -> DebrisBlock.Kind.Rocky
        mostUsedState.isHeavy() -> DebrisBlock.Kind.Metallic
        mostUsedState.isDirtLike() -> DebrisBlock.Kind.Dirty
        else -> DebrisBlock.Kind.Misc
    }
    val layer = when (rawStates.size) {
        in 1..3 -> 1
        in 4..10 -> 2
        else -> 3
    }
    return ModBlocks.debris.defaultBlockState()
        .setValue(DebrisBlock.kind, kind)
        .setValue(DebrisBlock.layers, layer)
}
