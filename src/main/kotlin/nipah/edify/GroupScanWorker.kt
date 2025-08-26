package nipah.edify

import net.minecraft.core.BlockPos
import nipah.edify.pooling.LazyRingPool

data class GroupScanWorker(val chunks: ChunkAccess) {
    private val pool = LazyRingPool(
        { GroupScan(chunks) },
        amount = 30,
    )

    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        pool.borrowIn { group ->
            if (group.isRunning) {
                return null
            }
            return group.scan(seed)
        }
    }
}
