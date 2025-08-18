package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.utils.*

object WorldData {
    private val grouper = BlockGrouper()
    fun groupAt(pos: BlockPos): BlockGroup? {
        return grouper.groupAt(pos)
    }

    fun setGroupAt(pos: BlockPos, group: BlockGroup): BlockGroup {
        grouper.setGroupAt(pos, group)
        return group
    }

    init {
        ChunkEvents.listenToBatchedBlockChanges { changes ->
            val added = changes
                .mapNotNull { it.takeIf { it.third is BlockChangeKind.Placed }?.second }
            val removed = changes
                .filter { it.third is BlockChangeKind.Broken }
            val landed = changes
                .filter { it.third is BlockChangeKind.Landed }

            for ((chunk, bpos, _) in removed) {
                onBlockRemoved(bpos, chunk)
            }
        }
    }

    fun mapChunk(chunk: LevelChunk) {
        val chunkPos = chunk.pos
        val visited = mutableSetOf<BlockPos>()
        val foundation = grouper.foundation
        chunk.forEachBlock { lpos ->
            val pos = chunk.localToWorldPos(lpos)
            if (pos in visited) return@forEachBlock
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty) {
                visited.add(pos)
                return@forEachBlock
            }
            if (block.isOf(Blocks.BEDROCK)) {
                grouper.setGroupAt(pos, foundation)
                visited.add(pos)
                return@forEachBlock
            }
        }
        chunk.forEachBlock { lpos ->
            val pos = chunk.localToWorldPos(lpos)
            mapGroup(chunk, pos, visited)
        }
    }

    private fun mapGroup(
        chunk: LevelChunk,
        seed: BlockPos,
        visited: MutableSet<BlockPos>,
    ) {
        if (seed in visited) return
        val group =
            groupAt(seed)
                ?: groupAt(seed.above())
                ?: groupAt(seed.below())
                ?: groupAt(seed.north())
                ?: groupAt(seed.south())
                ?: groupAt(seed.east())
                ?: groupAt(seed.west())
                ?: setGroupAt(seed, BlockGroup.Group())
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
    }

    private fun onBlockLanded(landed: BlockPos, chunk: LevelChunk) {
        val g = groupAt(landed)
            ?: groupAt(landed.above())
            ?: groupAt(landed.below())
            ?: groupAt(landed.north())
            ?: groupAt(landed.south())
            ?: groupAt(landed.east())
            ?: groupAt(landed.west())
            ?: return
        g.blocks.add(landed)

//        reprocessArea(landed, g, chunk)
    }

    private fun onBlockRemoved(removed: BlockPos, chunk: LevelChunk) {
        val g = groupAt(removed)
            ?: return
        g.blocks.remove(removed)

        mapGroup(chunk, removed, mutableSetOf())

//        reprocessArea(removed, g, chunk)
        fallingFun(chunk)
    }

    private fun fallingFun(chunk: LevelChunk) {
        val level = chunk.level ?: return
        for (group in grouper.groups) {
            for (bpos in group.blocks) {
                val block = chunk.getBlockState(bpos)
                if (block.isAir || block.isEmpty) {
                    continue
                }
                FallingBlockEntity.fall(level, bpos, block)
            }
            group.blocks.clear()
        }
        grouper.groups.removeIf { it.blocks.isEmpty() }
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
//        chunks.remove(chunkPos)
//        chunk.forEachBlock { lpos ->
//            val pos = chunk.localToWorldPos(lpos)
//            if (pos in bedrock.blocks) {
//                bedrock.blocks.remove(pos)
//            }
//            else if (pos in natural.blocks) {
//                natural.blocks.remove(pos)
//            }
//            else {
//                val group = directGroupAt(pos)
//                if (group != null) {
//                    group.blocks.remove(pos)
//                    if (group.blocks.isEmpty()) {
//                        groups.remove(group)
//                    }
//                }
//            }
//        }
    }
}
