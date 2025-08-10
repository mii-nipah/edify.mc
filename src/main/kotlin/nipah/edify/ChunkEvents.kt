package nipah.edify

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import nipah.edify.utils.TickScheduler

object ChunkEvents {
    @SubscribeEvent
    fun onChunkLoad(e: ChunkEvent.Load) {
        TickScheduler.schedule(10) {
            val pos = e.chunk.pos
            val chunk = e.level.chunkSource.getChunkNow(pos.x, pos.z)
            if (chunk != null) {
                WorldData.getChunkData(chunk)
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(e: ChunkEvent.Unload) {
        val pos = e.chunk.pos
        WorldData.unloadChunkData(pos)
    }
}
