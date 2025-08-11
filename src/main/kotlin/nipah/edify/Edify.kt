package nipah.edify

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import nipah.edify.block.ModBlocks
import nipah.edify.client.ClientWorldData
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
        WorldData.groupAt(BlockPos.ZERO)

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
                val bpos = bhr.blockPos

                val blockGroup = ClientWorldData.groupAtOrRequest(bpos)

                val color = when (blockGroup) {
                    is BlockGroup.Bedrock -> 0x80FF0000.toInt()
                    is BlockGroup.Natural -> 0x8000FF00.toInt()
                    is BlockGroup.Group -> {
                        when (blockGroup.id % 4) {
                            1 -> 0x800000FF.toInt()
                            2 -> 0x80FFFF00.toInt()
                            3 -> 0x80FF00FF.toInt()
                            else -> 0x80808080.toInt()
                        }
                    }

                    null -> 0x80000000.toInt()
                }
                val groupId =
                    if (blockGroup is BlockGroup.Group) blockGroup.id
                    else null

                // outline the block
                val box = net.minecraft.world.phys.AABB(bpos)
                    .inflate(0.002) // avoid Z-fighting
                Gizmos.box(box, color, depth = Depth.XRAY, ttl = 1)
                if (groupId != null) {
                    Gizmos.text(groupId.toString(), bpos.center.add(0.0, 0.5, 0.0), color, true)
                }
            }

            else -> {}
        }
    }
}
