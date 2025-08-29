package nipah.edify

import net.minecraft.core.BlockPos
import nipah.edify.pooling.LazyWorkerPool

data class GroupScanWorker(
    val chunks: ChunkAccess,
    val maxConcurrentScans: Int = 10,
) {
    private val pool = LazyWorkerPool(
        { GroupScan(chunks) },
        amount = maxConcurrentScans,
    )

    fun isAvailable() = pool.isAvailable()

    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        return pool.borrowIn { group ->
            if (group.isRunning) {
                return null
            }
            return group.scan(seed)
        }
    }
}
