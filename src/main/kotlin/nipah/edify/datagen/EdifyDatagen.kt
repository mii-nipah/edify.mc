package nipah.edify.datagen

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent
import nipah.edify.block.datagen.DebrisBlockStatesGen
import nipah.edify.block.datagen.DebrisModelsGen

@EventBusSubscriber
object EdifyDatagen {
    @SubscribeEvent
    fun gather(e: GatherDataEvent) {
        val gen = e.generator
        val out = gen.packOutput
        val fs = e.existingFileHelper

        gen.addProvider(e.includeClient(), DebrisModelsGen(out, fs))
        gen.addProvider(e.includeClient(), DebrisBlockStatesGen(out, fs))
    }
}
