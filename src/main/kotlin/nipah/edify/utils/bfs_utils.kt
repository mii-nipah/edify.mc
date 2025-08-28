package nipah.edify.utils

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import net.minecraft.core.BlockPos

data class BfsBox(
    val limit: Int,
    val scanPerTick: Int,
) {
    sealed class ScanResult {
        object Limit: ScanResult()
        object Ok: ScanResult()
        object Cancelled: ScanResult()
    }

    sealed class ScanCommand {
        object Continue: ScanCommand()
        object VisitNeighbors: ScanCommand()
        object VisitNeighborFaceOrEdge: ScanCommand()
        object VisitNeighborWithCorners: ScanCommand()
        data class VisitCustom(val fn: (pos: BlockPos, enqueue: (BlockPos) -> Unit) -> Unit): ScanCommand()
        object Yield: ScanCommand()
        object Cancel: ScanCommand()
        data class Stop(val with: ScanResult): ScanCommand()
    }

    private val toVisit = LongArrayFIFOQueue(limit * 2)
    private val toVisitSet = LongOpenHashSet(limit * 4, 0.4f)
    private val visited = LongOpenHashSet(limit * 5, 0.4f)

    fun clear() {
        toVisit.clear()
        toVisitSet.clear()
        visited.clear()
    }

    var isRunning = false
        private set

    suspend fun scan(
        seed: BlockPos,
        action: (BlockPos) -> ScanCommand,
    ): ScanResult {
        try {
            isRunning = true
            return realScan(seed, action)
        }
        finally {
            isRunning = false
        }
    }

    private suspend fun realScan(
        seed: BlockPos,
        action: (BlockPos) -> ScanCommand,
    ): ScanResult {
        toVisit.enqueue(seed.asLong())
        toVisitSet.add(seed.asLong())

        var tickScans = 0

        val pos = BlockPos.MutableBlockPos()
        val npos = BlockPos.MutableBlockPos()

        val context = currentCoroutineContext()
        while (toVisit.isNotEmpty()) {
            context.ensureActive()

            if (visited.size >= limit || toVisitSet.size > limit) {
                return ScanResult.Limit
            }

            tickScans++
            if (tickScans >= scanPerTick) {
                tickScans = 0
                yield()
            }

            val longPos = toVisit.dequeueLong()
            if (visited.add(longPos).not()) {
                continue
            }
            pos.set(longPos)

            val next = action(pos)
            when (next) {
                is ScanCommand.Continue -> continue
                is ScanCommand.Yield -> {
                    yield()
                    continue
                }

                is ScanCommand.Cancel -> {
                    return ScanResult.Cancelled
                }

                is ScanCommand.VisitNeighbors -> {
                    pos.forEachNeighborNoAlloc(npos) { npos ->
                        val nlong = npos.asLong()
                        if (toVisitSet.add(nlong)) {
                            toVisit.enqueue(nlong)
                        }
                    }
                    continue
                }

                is ScanCommand.VisitNeighborFaceOrEdge -> {
                    pos.forEachNeighborFaceOrEdgeNoAlloc(npos) { npos ->
                        val nlong = npos.asLong()
                        if (toVisitSet.add(nlong)) {
                            toVisit.enqueue(nlong)
                        }
                    }
                    continue
                }

                is ScanCommand.VisitNeighborWithCorners -> {
                    pos.forEachNeighborWithCornersNoAlloc(npos) { npos ->
                        val nlong = npos.asLong()
                        if (toVisitSet.add(nlong)) {
                            toVisit.enqueue(nlong)
                        }
                    }
                    continue
                }

                is ScanCommand.VisitCustom -> {
                    next.fn(pos) { bpos ->
                        val nlong = bpos.asLong()
                        if (toVisitSet.add(nlong)) {
                            toVisit.enqueue(nlong)
                        }
                    }
                    continue
                }

                is ScanCommand.Stop -> {
                    return next.with
                }
            }
        }
        return ScanResult.Ok
    }
}
