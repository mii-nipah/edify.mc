package nipah.edify

import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.gui.ConfigurationScreen
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import nipah.edify.attachment.ModAttachments
import nipah.edify.block.ModBlocks
import nipah.edify.entities.ModEntities
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
class Edify(private val container: ModContainer) {
    companion object {
        val LOGGER: Logger = LogManager.getLogger(modId)

        @SubscribeEvent
        @JvmStatic
        fun onCommonSetup(event: FMLCommonSetupEvent) {
            LOGGER.log(Level.INFO, "Hello! This is working!")
        }
    }

    // the logger for our mod

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        container.registerConfig(ModConfig.Type.STARTUP, Configs.startupSpec)
        container.registerConfig(ModConfig.Type.COMMON, Configs.commonSpec)

        // Register the KDeferredRegister to the mod-specific event bus
        ModBlocks.REGISTRY.register(MOD_BUS)
        ModEntities.REGISTRY.register(MOD_BUS)
        ModAttachments.REGISTRY.register(MOD_BUS)
        NeoForge.EVENT_BUS.register(TickScheduler)
        NeoForge.EVENT_BUS.register(ChunkEvents)
//        WorldData.groupAt(BlockPos.ZERO)

        val obj = runForDist(clientTarget = {
            MOD_BUS.addListener(::onClientSetup)
            container.registerExtensionPoint(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent -> ConfigurationScreen(container, parent) }
            )
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
}
