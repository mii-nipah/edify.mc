package nipah.edify.mixin_runtime

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import nipah.edify.events.UniversalBlockEvent

@EventBusSubscriber
object Level_AnyBlockRemovedMixinRuntime {
    private var shouldPreventPosting = false
    fun preventPostingNext() {
        shouldPreventPosting = true
    }

    private var collectedPerTick = Object2ObjectOpenHashMap<ServerLevel, LongOpenHashSet>()

    @SubscribeEvent
    fun onServerTick(e: ServerTickEvent.Post) {
        for ((level, collected) in collectedPerTick) {
            if (collected.isEmpty()) continue
            NeoForge.EVENT_BUS.post(
                UniversalBlockEvent.BlockRemovedBatch(
                    level,
                    collected.toLongArray()
                )
            )
            collected.clear()
        }
    }

    fun onAnyBlockRemoved(
        level: ServerLevel,
        pos: BlockPos,
    ) {
        if (shouldPreventPosting) {
            shouldPreventPosting = false
            return
        }
        val collected = collectedPerTick.computeIfAbsent(level) { LongOpenHashSet() }
        collected.add(pos.asLong())
    }
}
