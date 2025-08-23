package nipah.edify.events

import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.Event

sealed class UniversalBlockEvent: Event() {
    class BlockRemovedBatch(
        val level: ServerLevel,
        val blocks: LongArray,
    ): UniversalBlockEvent()
}
