package nipah.edify.spatial

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import nipah.edify.types.WorldBlock

data class SparseSpatialGrid(
    private val cellSize: Int,
    private val cells: Long2ObjectOpenHashMap<MutableList<WorldBlock>> = Long2ObjectOpenHashMap(32, 0.75f),
) {
    private fun keyOf(x: Int, y: Int, z: Int): Long {
        val cx = x.floorDiv(cellSize)
        val cy = y.floorDiv(cellSize)
        val cz = z.floorDiv(cellSize)
        return BlockPos.asLong(cx, cy, cz)
    }

    fun keyOf(pos: BlockPos): Long = keyOf(pos.x, pos.y, pos.z)

    fun set(value: WorldBlock) {
        val k = keyOf(value.pos)
        val bucket = cells.computeIfAbsent(k) { mutableListOf() }
        // Optional: avoid duplicates if equals() would match
        // if (!bucket.contains(value)) bucket.add(value) else return
        bucket.add(value)
    }

    fun remove(value: WorldBlock) {
        val k = keyOf(value.pos)
        val bucket = cells[k] ?: return
        bucket.remove(value)
        if (bucket.isEmpty()) cells.remove(k)
    }

    fun get(at: BlockPos) = get(keyOf(at))

    fun get(key: Long): List<WorldBlock>? {
        val bucket = cells[key] ?: return null
        return if (bucket.isEmpty()) null else bucket
    }

    fun clear() {
        cells.clear()
    }

    // Nice-to-have for moving bodies that cross cell boundaries
    fun move(oldPos: BlockPos, value: WorldBlock) {
        val oldKey = keyOf(oldPos)
        val newKey = keyOf(value.pos)
        if (oldKey == newKey) return
        cells[oldKey]?.let {
            it.remove(value)
            if (it.isEmpty()) cells.remove(oldKey)
        }
        cells.computeIfAbsent(newKey) { mutableListOf() }.add(value)
    }
}
