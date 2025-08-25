package nipah.edify.block.datagen

import net.minecraft.data.PackOutput
import net.neoforged.neoforge.client.model.generators.BlockStateProvider
import net.neoforged.neoforge.client.model.generators.ConfiguredModel
import net.neoforged.neoforge.common.data.ExistingFileHelper
import nipah.edify.block.DebrisBlock
import nipah.edify.block.ModBlocks
import nipah.edify.modId

class DebrisBlockStatesGen(out: PackOutput, fs: ExistingFileHelper): BlockStateProvider(out, modId, fs) {
    override fun registerStatesAndModels() {
        val debris = ModBlocks.debris

        val vb = getVariantBuilder(debris)
        for (kind in DebrisBlock.Kind.entries) {
            for (layer in DebrisBlock.layersRange) {
                val model = models().getExistingFile(modLoc("block/debris_${kind.serializedName}_${layer}"))
                vb.partialState()
                    .with(DebrisBlock.kind, kind)
                    .with(DebrisBlock.layers, layer)
                    .addModels(ConfiguredModel(model))
                    .modelForState()
            }
        }
    }
}
