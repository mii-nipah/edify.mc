package nipah.edify

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import nipah.edify.client.render.createBatch
import nipah.edify.utils.findNeighbor
import nipah.edify.utils.forEachNeighbor
import nipah.edify.utils.worldToLocalPos

object WorldData {
    private val chunkData = mutableMapOf<ChunkPos, ChunkData>()

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
        val mapped = ChunkData(chunkPos, chunk)
        chunkData[chunkPos] = mapped
    }

    private fun mapGroup(
        chunks: ChunkAccess,
        seed: BlockPos,
        visited: MutableSet<Long>,
        group: MutableSet<Long>,
        limit: Int = 100_000,
    ) {
        val seedLong = seed.asLong()
        if (seedLong in visited) return

        fun inFoundation(pos: BlockPos): Boolean {
            val chunk = chunks.at(pos) ?: return false
            val lpos = chunk.worldToLocalPos(pos)
            val cdata = chunkData[chunk.pos] ?: return false
            return cdata.foundationAt(lpos.x, lpos.y, lpos.z)
                    || lpos.findNeighbor { npos ->
                val neighborData = chunkData[chunk.pos] ?: return@findNeighbor false
                neighborData.foundationAt(npos.x, npos.y, npos.z)
            } != null
        }

        var iter = 0

        val metaGroup = mutableSetOf<Long>()

        val toVisit = ArrayDeque<BlockPos>()
        toVisit.add(seed)
        while (toVisit.isNotEmpty()) {
            iter++
            if (iter >= limit) {
                return
            }
            val pos = toVisit.removeFirst()
            val longPos = pos.asLong()
            if (longPos in visited) continue
            val chunk = chunks.at(pos) ?: continue
            val block = chunk.getBlockState(pos)
            if (block.isAir || block.isEmpty) continue
            val inFoundation = inFoundation(pos)
            visited.add(longPos)
            if (inFoundation.not()) {
                metaGroup.add(longPos)
            }
            else {
                return
            }

            pos.forEachNeighbor { pos ->
                if (pos.asLong() in visited) return@forEachNeighbor
                toVisit.add(pos)
            }
        }
        group.addAll(metaGroup)
    }

    private fun onBlockRemoved(removed: BlockPos, chunk: LevelChunk) {
        val toRemove = mutableSetOf<Long>()
        val visited = mutableSetOf<Long>()
        val chunks = ChunkAccess(chunk.level!!)
        mapGroup(chunks, removed, visited, toRemove)
        removed.forEachNeighbor { npos ->
            mapGroup(chunks, npos, visited, toRemove)
        }
        if (toRemove.isEmpty()) return
        fallingFun(chunks, toRemove.map { longPos ->
            BlockPos.of(longPos)
        })
    }

    private fun fallingFun(chunks: ChunkAccess, fall: List<BlockPos>) {
        if (fall.isEmpty()) return

        val level = chunks.level

        createBatch(
            level,
            fall
        )

        for (bpos in fall) {
            val chunk = chunks.at(bpos) ?: continue
            val block = chunk.getBlockState(bpos)
            if (block.isAir || block.isEmpty) {
                continue
            }
            level.destroyBlock(bpos, false)
        }
    }

    fun unloadChunkData(chunkPos: ChunkPos) {
        chunkData.remove(chunkPos)
    }
}
