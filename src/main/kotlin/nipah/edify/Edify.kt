package nipah.edify

import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import nipah.edify.block.ModBlocks
import nipah.edify.gizmos.Depth
import nipah.edify.gizmos.Gizmos
import nipah.edify.utils.TickScheduler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist


/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(modId)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object Edify {

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(modId)

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.REGISTRY.register(MOD_BUS)
        NeoForge.EVENT_BUS.register(TickScheduler::class.java)

        val obj = runForDist(clientTarget = {
            MOD_BUS.addListener(::onClientSetup)
            Minecraft.getInstance()
        }, serverTarget = {
            MOD_BUS.addListener(::onServerSetup)
            "test"
        })

        println(obj)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Hello! This is working!")
    }

    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Pre) {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val lvl = mc.level ?: return
        val cam = mc.gameRenderer.mainCamera
        val eye = cam.position

        val hit = mc.hitResult ?: return
        when (hit.type) {
            net.minecraft.world.phys.HitResult.Type.BLOCK -> {
                val bhr = hit as net.minecraft.world.phys.BlockHitResult
                val hitPos = bhr.location
                // line to target
                Gizmos.line(eye, hitPos, 0xFFFF5555.toInt(), ttl = 1)
                // outline the block
                val box = net.minecraft.world.phys.AABB(bhr.blockPos)
                    .inflate(0.002) // avoid Z-fighting
                Gizmos.box(box, 0x80FFFFFF.toInt(), depth = Depth.XRAY, ttl = 1)
            }

            else -> {}
        }
    }
}
