package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.state.BlockState

fun BlockState.isOf(block: Block): Boolean {
    return this.`is`(block)
}

fun BlockState.has(tag: TagKey<Block>): Boolean {
    return this.`is`(tag)
}

fun BlockState.hasAny(vararg tags: TagKey<Block>): Boolean {
    for (tag in tags) {
        if (this.`is`(tag)) return true
    }
    return false
}

inline fun BlockPos.forEachNeighbor(func: (BlockPos) -> Unit) {
    func(this.north())
    func(this.south())
    func(this.east())
    func(this.west())
    func(this.above())
    func(this.below())
}

inline fun BlockPos.forEachNeighborTopBottom(func: (BlockPos) -> Unit) {
    func(this.above())
    func(this.north())
    func(this.south())
    func(this.east())
    func(this.west())
    func(this.below())
}

inline fun BlockPos.forEachNeighborNoAlloc(func: (BlockPos) -> Unit) {
    val pos = asLong()
    val north = BlockPos.offset(pos, Direction.NORTH)
    val south = BlockPos.offset(pos, Direction.SOUTH)
    val east = BlockPos.offset(pos, Direction.EAST)
    val west = BlockPos.offset(pos, Direction.WEST)
    val above = BlockPos.offset(pos, Direction.UP)
    val below = BlockPos.offset(pos, Direction.DOWN)
    val mutPos = BlockPos.MutableBlockPos()
    mutPos.set(north)
    func(mutPos)
    mutPos.set(south)
    func(mutPos)
    mutPos.set(east)
    func(mutPos)
    mutPos.set(west)
    func(mutPos)
    mutPos.set(above)
    func(mutPos)
    mutPos.set(below)
    func(mutPos)
}

inline fun BlockPos.findNeighbor(func: (BlockPos) -> Boolean): BlockPos? {
    var res = func(this.north())
    if (res) return this.north()
    res = func(this.south())
    if (res) return this.south()
    res = func(this.east())
    if (res) return this.east()
    res = func(this.west())
    if (res) return this.west()
    res = func(this.above())
    if (res) return this.above()
    res = func(this.below())
    if (res) return this.below()
    return null
}

inline fun BlockPos.findNeighborNoAlloc(func: (BlockPos) -> Boolean): BlockPos? {
    val pos = asLong()
    val mutPos = BlockPos.MutableBlockPos()
    val north = BlockPos.offset(pos, Direction.NORTH)
    mutPos.set(north)
    var res = func(mutPos)
    if (res) return mutPos
    val south = BlockPos.offset(pos, Direction.SOUTH)
    mutPos.set(south)
    res = func(mutPos)
    if (res) return mutPos
    val east = BlockPos.offset(pos, Direction.EAST)
    mutPos.set(east)
    res = func(mutPos)
    if (res) return mutPos
    val west = BlockPos.offset(pos, Direction.WEST)
    mutPos.set(west)
    res = func(mutPos)
    if (res) return mutPos
    val above = BlockPos.offset(pos, Direction.UP)
    mutPos.set(above)
    res = func(mutPos)
    if (res) return mutPos
    val below = BlockPos.offset(pos, Direction.DOWN)
    mutPos.set(below)
    res = func(mutPos)
    if (res) return mutPos
    return null
}

fun BlockPos.collectNeighbors(): MutableList<BlockPos> {
    val neighbors = mutableListOf<BlockPos>()
    this.forEachNeighbor { neighbors.add(it) }
    return neighbors
}

fun BlockPos.collectNeighborsTopBottom(): MutableList<BlockPos> {
    val neighbors = mutableListOf<BlockPos>()
    this.forEachNeighbor { neighbors.add(it) }
    return neighbors
}

fun BlockState.holdsNoHeight(level: LevelReader, pos: BlockPos): Boolean {
    val state = this

    if (state.isAir) return true
    if (state.canBeReplaced()) return true

    // Top face not a full supporting face → no support (torches, buttons, fences, walls, panes, etc.)
    if (!Block.isFaceFull(state.getBlockSupportShape(level, pos), Direction.UP)) return true

    // Your “weak support” policy (tune as you like)
    if (state.`is`(BlockTags.LEAVES)) return true
    if (state.`is`(BlockTags.RAILS)) return true
    if (state.`is`(BlockTags.WOOL_CARPETS)) return true
    if (state.`is`(BlockTags.FLOWERS) || state.`is`(BlockTags.SAPLINGS)) return true
    if (state.`is`(Blocks.COBWEB)) return true
    if (state.`is`(Blocks.SCAFFOLDING)) return true
    if (!state.fluidState.isEmpty) return true
    if (state.block is SnowLayerBlock && state.getValue(SnowLayerBlock.LAYERS) < 8) return true

    return false
}

fun BlockState.isLogLike(): Boolean {
    return hasAny(
        BlockTags.LOGS,
        BlockTags.LOGS_THAT_BURN,
        BlockTags.OAK_LOGS,
        BlockTags.BIRCH_LOGS,
        BlockTags.SPRUCE_LOGS,
        BlockTags.JUNGLE_LOGS,
        BlockTags.ACACIA_LOGS,
        BlockTags.DARK_OAK_LOGS,
        BlockTags.CHERRY_LOGS,
        BlockTags.MANGROVE_LOGS,
        BlockTags.OVERWORLD_NATURAL_LOGS,
    )
}

fun BlockState.isPlankLike(): Boolean {
    return has(
        BlockTags.PLANKS,
    )
}

fun BlockState.isStoneLike(): Boolean {
    val inTags = hasAny(
        BlockTags.STONE_BRICKS,
        BlockTags.NEEDS_STONE_TOOL,
        BlockTags.STONE_ORE_REPLACEABLES,
        BlockTags.REDSTONE_ORES,
        BlockTags.BASE_STONE_OVERWORLD,
        BlockTags.BASE_STONE_NETHER,
    )
    if (inTags) return true
    return this.isOf(Blocks.COBBLESTONE)
            || this.isOf(Blocks.MOSSY_COBBLESTONE)
            || this.isOf(Blocks.GRANITE)
            || this.isOf(Blocks.DIORITE)
            || this.isOf(Blocks.ANDESITE)
            || this.isOf(Blocks.TUFF)
            || this.isOf(Blocks.DRIPSTONE_BLOCK)
}

fun BlockState.isDirtLike(): Boolean {
    return hasAny(
        BlockTags.DIRT,
        BlockTags.SAND,
        BlockTags.NYLIUM,
        BlockTags.CONVERTABLE_TO_MUD,
    )
}

fun BlockState.isNonSupporting(): Boolean {
    return hasAny(
        BlockTags.BUTTONS,
        BlockTags.FENCES,
        BlockTags.WALLS,
        BlockTags.PRESSURE_PLATES,
        BlockTags.SLABS,
        BlockTags.STAIRS,
        BlockTags.TRAPDOORS,
        BlockTags.WOODEN_TRAPDOORS,
        BlockTags.DOORS,
        BlockTags.WOODEN_DOORS,
        BlockTags.WOODEN_BUTTONS,
        BlockTags.WOODEN_PRESSURE_PLATES,
        BlockTags.WOOL_CARPETS,
        BlockTags.BAMBOO_BLOCKS,
        BlockTags.CORAL_PLANTS,
        BlockTags.CLIMBABLE,
        BlockTags.CROPS,
        BlockTags.FLOWERS,
        BlockTags.SAPLINGS,
        BlockTags.SMALL_FLOWERS,
        BlockTags.TALL_FLOWERS,
        BlockTags.CAVE_VINES,
        BlockTags.RAILS,
        BlockTags.BEDS,
        BlockTags.SNOW,
        BlockTags.LEAVES,
    )
}

fun BlockState.isExplosive(): Boolean {
    val tagged = hasAny(
        BlockTags.FIRE,
        BlockTags.CAMPFIRES,
        BlockTags.SOUL_FIRE_BASE_BLOCKS,
    )
    if (tagged) return true
    return this.isOf(Blocks.TNT)
            || this.isOf(Blocks.CREEPER_HEAD)
            || this.isOf(Blocks.CREEPER_WALL_HEAD)
}

fun BlockState.isHeavy(): Boolean {
    return this.isOf(Blocks.OBSIDIAN)
            || this.isOf(Blocks.CRYING_OBSIDIAN)
            || this.isOf(Blocks.ANCIENT_DEBRIS)
            || this.isOf(Blocks.NETHERITE_BLOCK)
            || this.isOf(Blocks.DRAGON_EGG)
            || this.isOf(Blocks.END_PORTAL_FRAME)
            || this.isOf(Blocks.BEDROCK)
            || this.isOf(Blocks.BARRIER)
            || this.isOf(Blocks.IRON_BLOCK)
            || this.isOf(Blocks.GOLD_BLOCK)
            || this.isOf(Blocks.DIAMOND_BLOCK)
            || this.isOf(Blocks.EMERALD_BLOCK)
}
