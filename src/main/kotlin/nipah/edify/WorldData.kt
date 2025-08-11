package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.utils.*

object WorldData {
    private val chunks = mutableSetOf<ChunkPos>()

    private val bedrock = BlockGroup.Bedrock()
    private val natural = BlockGroup.Natural()
    private val groups = mutableListOf<BlockGroup.Group>()

    init {
        ChunkEvents.listenToBatchedBlockChanges { changes ->
            val added = changes
                .mapNotNull { it.takeIf { it.third is BlockChangeKind.Placed }?.second }
            val removed = changes
                .filter { it.third is BlockChangeKind.Broken }
            val landed = changes
                .filter { it.third is BlockChangeKind.Landed }

            for (bpos in added) {
                val group = groupAt(bpos)
                    ?: groupAt(bpos.below())
                    ?: groupAt(bpos.north())
                    ?: groupAt(bpos.south())
                    ?: groupAt(bpos.east())
                    ?: groupAt(bpos.west())
                if (group == null) {
                    continue
                }
                group.blocks.add(bpos)
            }
            for ((chunk, bpos, _) in removed) {
                onBlockRemoved(bpos, chunk)
            }
            for ((chunk, bpos, _) in landed) {
                onBlockLanded(bpos, chunk)
            }
        }
    }

    fun groupAt(pos: BlockPos): BlockGroup? {
        if (pos in bedrock.blocks) return bedrock
        if (pos in natural.blocks) return natural
        for (group in groups) {
            if (pos in group.blocks) return group
        }
        return null
    }

    fun directGroupAt(pos: BlockPos): BlockGroup? {
        for (group in groups) {
            if (pos in group.blocks) return group
        }
        return null
    }

    fun mapChunk(chunk: LevelChunk) {
        val chunkPos = chunk.pos
        if (chunkPos in chunks) {
            return
        }
        val visited = mutableSetOf<BlockPos>()
        chunk.forEachBlock { lpos ->
            val pos = chunk.localToWorldPos(lpos)
            if (pos in visited) return@forEachBlock
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty) {
                visited.add(pos)
                return@forEachBlock
            }
            if (block.isOf(Blocks.BEDROCK)) {
                bedrock.blocks.add(pos)
            }
            else if (block.hasAny(BlockTags.DIRT, BlockTags.STONE_ORE_REPLACEABLES)) {
                natural.blocks.add(pos)
            }
            else {
                return@forEachBlock
            }
            visited.add(pos)
        }
        chunk.forEachBlock { lpos ->
            val pos = chunk.localToWorldPos(lpos)
            if (pos in visited) return@forEachBlock
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty) {
                visited.add(pos)
                return@forEachBlock
            }
            if (block.isOf(Blocks.BEDROCK)) {
                return@forEachBlock
            }
            else if (block.hasAny(BlockTags.DIRT, BlockTags.STONE_ORE_REPLACEABLES)) {
                return@forEachBlock
            }
            else {
                mapGroup(chunk, pos, visited)
            }
        }
    }

    private fun mapGroup(chunk: LevelChunk, seed: BlockPos, visited: MutableSet<BlockPos>): BlockGroup {
        val group =
            groupAt(seed)
                ?: groupAt(seed.below())
                ?: groupAt(seed.north())
                ?: groupAt(seed.south())
                ?: groupAt(seed.east())
                ?: groupAt(seed.west())
                ?: BlockGroup.Group(id = groups.size + 1).also { groups.add(it) }
        if (seed in visited) return group
        val toVisit = ArrayDeque<BlockPos>()
        toVisit.add(seed)
        while (toVisit.isNotEmpty()) {
            val pos = toVisit.removeFirst()
            if (pos in visited) continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty) continue
            val blockGroup = groupAt(pos)
            if (blockGroup != null && blockGroup != group) continue
            visited.add(pos)
            group.blocks.add(pos)

            pos.forEachNeighbor { pos ->
                if (!chunk.isInBounds(pos)) {
                    return@forEachNeighbor
                }
                if (groupAt(pos) == group) {
                    return@forEachNeighbor
                }
                toVisit.add(pos)
            }
        }
        return group
    }

    private fun onBlockLanded(landed: BlockPos, chunk: LevelChunk) {
        val g = groupAt(landed)
            ?: groupAt(landed.below())
            ?: groupAt(landed.north())
            ?: groupAt(landed.south())
            ?: groupAt(landed.east())
            ?: groupAt(landed.west())
            ?: return
        g.blocks.add(landed)

        reprocessArea(landed, g, chunk)
    }

    private fun onBlockRemoved(removed: BlockPos, chunk: LevelChunk) {
        val g = groupAt(removed)
            ?: return
        g.blocks.remove(removed)

        val ogIsNatureOrBedrock = g is BlockGroup.Bedrock || g is BlockGroup.Natural

        reprocessArea(removed, g, chunk)
        fallingFun(chunk)
    }

    private fun reprocessArea(pos: BlockPos, group: BlockGroup, chunk: LevelChunk) {
        val toVisit = ArrayDeque<BlockPos>()
        pos.forEachNeighbor { pos ->
            if (pos in group.blocks) {
                toVisit.add(pos)
            }
        }
        val visited = mutableSetOf<BlockPos>()

        group.blocks.clear()

        val mounting = mutableListOf<BlockGroup>()
        toVisit.forEach { pos ->
            val resulting = mapGroup(chunk, pos, visited)
            mounting.add(resulting)
        }
        mounting.distinct()

        for (mount in mounting) {
            val touching =
                touches(mount).minByOrNull { g ->
                    when (g) {
                        is BlockGroup.Bedrock -> 0
                        is BlockGroup.Natural -> 1
                        is BlockGroup.Group -> 2
                    }
                } ?: continue
            if (touching == mount) continue
            if (touching is BlockGroup.Natural || touching is BlockGroup.Bedrock) {
                natural.blocks.addAll(mount.blocks)
            }
            else {
                touching.blocks.addAll(mount.blocks)
            }
            groups.remove(mount)
        }
    }

    private fun fallingFun(chunk: LevelChunk) {
        val level = chunk.level ?: return
        for (group in groups) {
            for (bpos in group.blocks) {
                val block = chunk.getBlockState(bpos)
                if (block.isAir || block.isEmpty) {
                    continue
                }
                FallingBlockEntity.fall(level, bpos, block)
            }
            group.blocks.clear()
        }
        groups.removeIf { it.blocks.isEmpty() }
    }

    fun touches(group: BlockGroup): Set<BlockGroup> {
        val touched = mutableSetOf<BlockGroup>()
        for (block in group.blocks) {
            block.forEachNeighbor { neighbor ->
                val neighborGroup = groupAt(neighbor)
                    ?: return@forEachNeighbor
                touched.add(neighborGroup)
            }
        }
        return touched
    }

    fun unloadChunkData(chunk: LevelChunk, chunkPos: ChunkPos) {
        chunks.remove(chunkPos)
        chunk.forEachBlock { lpos ->
            val pos = chunk.localToWorldPos(lpos)
            if (pos in bedrock.blocks) {
                bedrock.blocks.remove(pos)
            }
            else if (pos in natural.blocks) {
                natural.blocks.remove(pos)
            }
            else {
                val group = directGroupAt(pos)
                if (group != null) {
                    group.blocks.remove(pos)
                    if (group.blocks.isEmpty()) {
                        groups.remove(group)
                    }
                }
            }
        }
    }
}
