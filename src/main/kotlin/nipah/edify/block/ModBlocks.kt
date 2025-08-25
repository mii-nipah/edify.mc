package nipah.edify.block

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredRegister
import nipah.edify.modId
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModBlocks {
    val REGISTRY = DeferredRegister.createBlocks(modId)

    val debris by REGISTRY.register("debris") { ->
        DebrisBlock(
            BlockBehaviour.Properties.of()
        )
    }
}
