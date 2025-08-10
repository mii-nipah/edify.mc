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
        NeoForge.EVENT_BUS.register(TickScheduler)
        NeoForge.EVENT_BUS.register(ChunkEvents)

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
                val bpos = bhr.blockPos

                val chunk = lvl.getChunkAt(bpos)
                val chunkData = WorldData.getChunkData(chunk)
                val blockData = chunkData.getBlockSafe(bpos.x, bpos.y, bpos.z)

                val color = when (blockData) {
                    is BlockData.Air -> 0x80FFFFFF.toInt()
                    is BlockData.Bedrock -> 0x80FF0000.toInt()
                    is BlockData.Foundation -> 0x8000FF00.toInt()
                    is BlockData.Group -> {
                        when (blockData.id % 4) {
                            1 -> 0x800000FF.toInt()
                            2 -> 0x80FFFF00.toInt()
                            3 -> 0x80FF00FF.toInt()
                            else -> 0x80808080.toInt()
                        }
                    }

                    is BlockData.Deferred -> 0x8000FFFF.toInt()
                }

                // outline the block
                val box = net.minecraft.world.phys.AABB(bpos)
                    .inflate(0.002) // avoid Z-fighting
                Gizmos.box(box, color, depth = Depth.XRAY, ttl = 1)
            }

            else -> {}
        }
    }
}
