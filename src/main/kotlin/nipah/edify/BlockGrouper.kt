package nipah.edify

import net.minecraft.core.BlockPos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class BlockGrouper {
    val foundation = BlockGroup.Foundation()
    val groups = mutableListOf<BlockGroup.Group>()
    private val toGroups: ConcurrentMap<BlockPos, BlockGroup> = ConcurrentHashMap()

    fun clear() {
        foundation.blocks.clear()
        groups.clear()
        toGroups.clear()
    }

    fun groupAt(pos: BlockPos): BlockGroup? {
        return toGroups[pos]
    }

    fun setGroupAt(pos: BlockPos, group: BlockGroup) {
        toGroups[pos] = group
        when (group) {
            is BlockGroup.Foundation -> foundation.blocks.add(pos)
            is BlockGroup.Group -> {
                group.blocks.add(pos)
                groups.add(group)
            }
        }
    }
}
