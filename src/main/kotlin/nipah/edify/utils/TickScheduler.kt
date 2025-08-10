package nipah.edify.utils

import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

object TickScheduler {
    private val tasks: MutableList<Task> = CopyOnWriteArrayList<Task>()

    fun schedule(ticks: Int, action: Consumer<MinecraftServer>) {
        tasks.add(Task(ticks, action))
    }

    private val toRemove: MutableList<Task?> = ArrayList<Task?>()

    @SubscribeEvent
    fun onServerTick(ev: ServerTickEvent.Post) {
        val server = ev.server
        toRemove.clear()
        // decrement and run
        for (t in tasks) {
            if (t.ticksLeft <= 1) {
                t.action.accept(server)
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

    private class Task(var ticksLeft: Int, var action: Consumer<MinecraftServer>)
}
