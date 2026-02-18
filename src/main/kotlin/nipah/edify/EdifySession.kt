package nipah.edify

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minecraft.server.MinecraftServer
import nipah.edify.client.render.BatchRenderer
import nipah.edify.entities.FallingStructureEntity
import nipah.edify.gizmos.Gizmos
import nipah.edify.mixin_runtime.Level_AnyBlockRemovedMixinRuntime
import nipah.edify.utils.TickScheduler

class EdifySession(val server: MinecraftServer) : AutoCloseable {
    companion object {
        var current: EdifySession? = null
            private set

        fun start(server: MinecraftServer) {
            current?.close()
            current = EdifySession(server)
        }

        fun stop() {
            current?.close()
            current = null
        }
    }

    val scope = CoroutineScope(SupervisorJob() + TickScheduler.ServerDispatcher)
    val worldData = WorldData(scope)
    val batchRenderer = BatchRenderer()

    override fun close() {
        scope.cancel()
        batchRenderer.close()
        TickScheduler.drainServerQueues()
        GroupScan.currentlyScanning.clear()
        Gizmos.reset()
        FallingStructureEntity.toRender.clear()
        Level_AnyBlockRemovedMixinRuntime.reset()
    }
}

val session get() = EdifySession.current
