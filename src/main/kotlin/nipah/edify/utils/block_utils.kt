package nipah.edify.utils

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SnowLayerBlock
import net.minecraft.world.level.block.state.BlockState
import nipah.edify.tags.ModTags

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
    val mutPos = BlockPos.MutableBlockPos()
    forEachNeighborNoAlloc(mutPos, func)
}

fun test() {
    val pos = BlockPos(0, 0, 0)
    val mutPos = BlockPos.MutableBlockPos()
    pos.forEachNeighborNoAlloc(mutPos) { npos ->
        println(npos)
    }
}

inline val BlockPos.neighborSize get() = 6

inline fun BlockPos.forEachNeighborNoAlloc(
    with: BlockPos.MutableBlockPos,
    func: (BlockPos) -> Unit,
) {
    val x = this.x
    val y = this.y
    val z = this.z

    with.set(x, y + 1, z)
    func(with)
    with.set(x, y, z - 1)
    func(with)
    with.set(x, y, z + 1)
    func(with)
    with.set(x + 1, y, z)
    func(with)
    with.set(x - 1, y, z)
    func(with)
    with.set(x, y - 1, z)
    func(with)
}

inline fun BlockPos.forEachHorizontalNeighborNoAlloc(func: (BlockPos) -> Unit) =
    forEachHorizontalNeighborNoAlloc(BlockPos.MutableBlockPos(), func)

inline val BlockPos.neighborHorizontalSize get() = 4

inline fun BlockPos.forEachHorizontalNeighborNoAlloc(
    with: BlockPos.MutableBlockPos,
    func: (BlockPos) -> Unit,
) {
    val x = this.x
    val y = this.y
    val z = this.z

    with.set(x, y, z - 1)
    func(with)
    with.set(x, y, z + 1)
    func(with)
    with.set(x + 1, y, z)
    func(with)
    with.set(x - 1, y, z)
    func(with)
}

inline val BlockPos.neighborVerticalSize get() = 2

inline fun BlockPos.forEachVerticalNeighborNoAlloc(func: (BlockPos) -> Unit) =
    forEachVerticalNeighborNoAlloc(BlockPos.MutableBlockPos(), func)

inline fun BlockPos.forEachVerticalNeighborNoAlloc(
    with: BlockPos.MutableBlockPos,
    func: (BlockPos) -> Unit,
) {
    val x = this.x
    val y = this.y
    val z = this.z

    with.set(x, y + 1, z)
    func(with)
    with.set(x, y - 1, z)
    func(with)
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

inline val BlockPos.neighborFaceOrEdgeSize get() = 18

inline fun BlockPos.forEachNeighborFaceOrEdgeNoAlloc(
    with: BlockPos.MutableBlockPos,
    body: (BlockPos.MutableBlockPos) -> Unit,
) {
    val x0 = this.x
    val y0 = this.y
    val z0 = this.z
    for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
        if ((dx or dy or dz) == 0) continue        // skip self (0,0,0)
        if (dx != 0 && dy != 0 && dz != 0) continue // skip corners (±1,±1,±1)
        with.set(x0 + dx, y0 + dy, z0 + dz)
        body(with)
    }
}

inline fun BlockPos.forEachNeighborFaceOrEdgeNoAlloc(
    body: (BlockPos.MutableBlockPos) -> Unit,
) = forEachNeighborFaceOrEdgeNoAlloc(BlockPos.MutableBlockPos(), body)

inline val BlockPos.neighborWithCornersSize get() = 26

inline fun BlockPos.forEachNeighborWithCornersNoAlloc(body: (BlockPos.MutableBlockPos) -> Unit) {
    forEachNeighborWithCornersNoAlloc(BlockPos.MutableBlockPos(), body)
}

inline fun BlockPos.forEachNeighborWithCornersNoAlloc(with: BlockPos.MutableBlockPos, body: (BlockPos.MutableBlockPos) -> Unit) {
    val m = BlockPos.MutableBlockPos()
    val x0 = this.x;
    val y0 = this.y;
    val z0 = this.z
    for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
        if ((dx or dy or dz) == 0) continue // skip self
        m.set(x0 + dx, y0 + dy, z0 + dz)
        body(m)
    }
}

val OFFSETS_UP_FIRST: IntArray = intArrayOf(
    // faces (6): up, N, S, W, E, down
    0, 1, 0, 0, 0, -1, 0, 0, 1, -1, 0, 0, 1, 0, 0, 0, -1, 0,
    // up edges (4)
    1, 1, 0, -1, 1, 0, 0, 1, 1, 0, 1, -1,
    // up corners (4)
    1, 1, 1, 1, 1, -1, -1, 1, 1, -1, 1, -1,
    // horizontal diagonals (4)
    1, 0, 1, 1, 0, -1, -1, 0, 1, -1, 0, -1,
    // down edges (4)
    1, -1, 0, -1, -1, 0, 0, -1, 1, 0, -1, -1,
    // down corners (4)
    1, -1, 1, 1, -1, -1, -1, -1, 1, -1, -1, -1
)

inline fun BlockPos.forEachNeighborWithCornersUpFirstNoAlloc(npos: BlockPos.MutableBlockPos, body: (BlockPos.MutableBlockPos) -> Unit) {
    val m = npos
    val x0 = this.x;
    val y0 = this.y;
    val z0 = this.z
    var i = 0
    while (i < OFFSETS_UP_FIRST.size) {
        val dx = OFFSETS_UP_FIRST[i];
        val dy = OFFSETS_UP_FIRST[i + 1];
        val dz = OFFSETS_UP_FIRST[i + 2]
        m.set(x0 + dx, y0 + dy, z0 + dz)
        body(m)
        i += 3
    }
}

inline fun BlockPos.forEachNeighborWithCornersUpFirstNoAlloc(body: (BlockPos.MutableBlockPos) -> Unit) =
    forEachNeighborWithCornersUpFirstNoAlloc(BlockPos.MutableBlockPos(), body)

fun BlockPos.collectNeighborsWithCornersUpFirst(): MutableList<BlockPos> {
    val neighbors = mutableListOf<BlockPos>()
    this.forEachNeighborWithCornersUpFirstNoAlloc { neighbors.add(BlockPos(it)) }
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

private val logLikeCache = Int2BooleanOpenHashMap(1024)
fun BlockState.isLogLike(): Boolean {
    val id = Block.getId(this)
    if (logLikeCache.containsKey(id))
        return logLikeCache.getOrDefault(id, false)
    val result = hasAny(
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
    logLikeCache.put(id, result)
    return result
}

fun BlockState.isPlankLike(): Boolean {
    return has(
        BlockTags.PLANKS,
    )
}

private val stoneLikeCache = Int2BooleanOpenHashMap(1024)
fun BlockState.isStoneLike(): Boolean {
    val id = Block.getId(this)
    if (stoneLikeCache.containsKey(id))
        return stoneLikeCache.getOrDefault(id, false);
    val result = run {
        val inTags = hasAny(
            BlockTags.STONE_BRICKS,
            BlockTags.NEEDS_STONE_TOOL,
            BlockTags.STONE_ORE_REPLACEABLES,
            BlockTags.REDSTONE_ORES,
            BlockTags.BASE_STONE_OVERWORLD,
            BlockTags.BASE_STONE_NETHER,
        )
        if (inTags) return@run true
        return@run this.isOf(Blocks.COBBLESTONE)
                || this.isOf(Blocks.MOSSY_COBBLESTONE)
                || this.isOf(Blocks.GRANITE)
                || this.isOf(Blocks.DIORITE)
                || this.isOf(Blocks.ANDESITE)
                || this.isOf(Blocks.TUFF)
                || this.isOf(Blocks.DRIPSTONE_BLOCK)
    }
    stoneLikeCache.put(id, result)
    return result
}

private val dirtLikeCache = Int2BooleanOpenHashMap(1024)
fun BlockState.isDirtLike(): Boolean {
    val id = Block.getId(this)
    if (dirtLikeCache.containsKey(id))
        return dirtLikeCache.getOrDefault(id, false);
    val result = hasAny(
        BlockTags.DIRT,
        BlockTags.SAND,
        BlockTags.NYLIUM,
        BlockTags.CONVERTABLE_TO_MUD,
    )
    dirtLikeCache.put(id, result)
    return result
}

private val nonSupportingCache = Int2BooleanOpenHashMap(1024)
fun BlockState.isNonSupporting(): Boolean {
    if (canBeReplaced()) {
        return true
    }
    val id = Block.getId(this)
    if (nonSupportingCache.containsKey(id))
        return nonSupportingCache.getOrDefault(id, false);
    val result = hasAny(
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
    nonSupportingCache.put(id, result)
    return result
}

private val explosiveCache = Int2BooleanOpenHashMap(1024)
fun BlockState.isExplosive(): Boolean {
    val id = Block.getId(this)
    if (explosiveCache.containsKey(id))
        return explosiveCache.getOrDefault(id, false);
    val result = run {
        val tagged = hasAny(
            BlockTags.FIRE,
            BlockTags.CAMPFIRES,
            BlockTags.SOUL_FIRE_BASE_BLOCKS,
        )
        if (tagged) return@run true
        return@run this.isOf(Blocks.TNT)
                || this.isOf(Blocks.CREEPER_HEAD)
                || this.isOf(Blocks.CREEPER_WALL_HEAD)
    }
    explosiveCache.put(id, result)
    return result
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

private val floatingCache = Int2BooleanOpenHashMap(1024)
fun BlockState.isFloating(): Boolean {
    val id = Block.getId(this)
    if (floatingCache.containsKey(id))
        return floatingCache.getOrDefault(id, false);
    val result = this.has(ModTags.floating)
    floatingCache.put(id, result)
    return result
}
