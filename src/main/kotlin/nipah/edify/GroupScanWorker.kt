package nipah.edify

import net.minecraft.core.BlockPos
import nipah.edify.pooling.LazyRingPool

data class GroupScanWorker(
    val chunks: ChunkAccess,
    val maxConcurrentScans: Int = 10,
) {
    private val pool = LazyRingPool(
        { GroupScan(chunks) },
        amount = maxConcurrentScans,
    )

    fun isAvailable() = run {
        pool.isEmpty || pool.canGrow || pool.any { it.isRunning.not() }
    }

    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        pool.borrowIn { group ->
            if (group.isRunning) {
                return null
            }
            return group.scan(seed)
        }
    }
}
