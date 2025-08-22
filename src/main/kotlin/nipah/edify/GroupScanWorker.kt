package nipah.edify

import net.minecraft.core.BlockPos
import nipah.edify.pooling.RingPool

data class GroupScanWorker(val chunks: ChunkAccess) {
    private val pool = RingPool(
        { GroupScan(chunks) },
        amount = 5,
    )

    suspend fun scan(seed: List<BlockPos>): List<BlockPos>? {
        pool.borrowIn { group ->
            return group.scan(seed)
        }
    }
}
