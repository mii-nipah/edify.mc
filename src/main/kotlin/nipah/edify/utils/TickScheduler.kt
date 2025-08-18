package nipah.edify.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

object TickScheduler {
    private val clientTasks = CopyOnWriteArrayList<Task<Minecraft>>()
    private val clientNextTickTasks = CopyOnWriteArrayList<(Minecraft) -> Unit>()

    private val serverTasks = CopyOnWriteArrayList<Task<MinecraftServer>>()
    private val serverNextTickTasks = CopyOnWriteArrayList<(MinecraftServer) -> Unit>()

    fun scheduleServer(ticks: Int, action: (MinecraftServer) -> Unit) {
        if (ticks == 1) {
            serverNextTickTasks.add(action)
            return
        }
        serverTasks.add(Task(ticks, action))
    }

    fun scheduleClient(ticks: Int, action: (Minecraft) -> Unit) {
        if (ticks == 1) {
            clientNextTickTasks.add(action)
            return
        }
        clientTasks.add(Task(ticks, action))
    }

    object ServerDispatcher: CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            scheduleServer(ticks = 1) {
                block.run()
            }
        }
    }

    object ClientDispatcher: CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            scheduleClient(ticks = 1) {
                block.run()
            }
        }
    }

    val serverScope = CoroutineScope(
        SupervisorJob() + ServerDispatcher
    )
    val clientScope = CoroutineScope(
        SupervisorJob() + ClientDispatcher
    )

    private val toRemoveClient = ArrayList<Task<Minecraft>>()
    private val toRemoveServer = ArrayList<Task<MinecraftServer>>()

    private fun <T> processTasks(
        tasks: MutableList<Task<T>>,
        toRemove: MutableList<Task<T>>,
        pass: T,
    ) {
        toRemove.clear()
        // decrement and run
        for (t in tasks) {
            if (t.ticksLeft <= 1) {
                t.action(pass)
                toRemove.add(t)
            }
            else {
                t.ticksLeft--
            }
        }
        // remove completed tasks
        for (t in toRemove) {
            tasks.remove(t)
        }
    }

    private fun <T> processNextTickTasks(
        nextTickTasks: MutableList<(T) -> Unit>,
        pass: T,
    ) {
        for (task in nextTickTasks) {
            task(pass)
        }
        nextTickTasks.clear()
    }

    @SubscribeEvent
    fun onServerTick(ev: ServerTickEvent.Post) {
        val server = ev.server

        // Process server tasks
        processTasks(serverTasks, toRemoveServer, server)
        processNextTickTasks(serverNextTickTasks, server)
    }

    @SubscribeEvent
    fun onClientTick(ev: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()

        // Process client tasks
        processTasks(clientTasks, toRemoveClient, client)
        processNextTickTasks(clientNextTickTasks, client)
    }

    private class Task<T>(var ticksLeft: Int, var action: (T) -> Unit)
}

suspend fun nextClientTick() = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    TickScheduler.ClientDispatcher.dispatch(cont.context) { cont.resume(Unit) { cause, _, _ -> } }
}

suspend fun nextServerTick() = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    TickScheduler.ServerDispatcher.dispatch(cont.context) { cont.resume(Unit) { cause, _, _ -> } }
}

