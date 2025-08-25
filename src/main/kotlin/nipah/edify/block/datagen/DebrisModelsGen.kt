package nipah.edify.block.datagen

import net.minecraft.data.PackOutput
import net.neoforged.neoforge.client.model.generators.BlockModelProvider
import net.neoforged.neoforge.client.model.generators.ModelFile
import net.neoforged.neoforge.common.data.ExistingFileHelper
import nipah.edify.block.DebrisBlock
import nipah.edify.modId

class DebrisModelsGen(out: PackOutput, fs: ExistingFileHelper): BlockModelProvider(out, modId, fs) {
    override fun registerModels() {
        // Geometry parents with a texture variable "tex"
        makeLayerGeo("debris_layer1_geo", 6f)
        makeLayerGeo("debris_layer2_geo", 12f)
        makeLayerGeo("debris_layer3_geo", 16f)

        // Per-kind wrappers point to geo parent and bind "tex"
        val kinds = DebrisBlock.Kind.entries.map { it.serializedName }
        val parentByLayer = mapOf(1 to "debris_layer1_geo", 2 to "debris_layer2_geo", 3 to "debris_layer3_geo")

        for (k in kinds) for (l in 1..3) {
            withExistingParent("debris_${k}_${l}", modLoc("block/${parentByLayer[l]}"))
                .texture("tex", modLoc("block/debris_$k"))
        }
    }

    private fun makeLayerGeo(name: String, height: Float) {
        getBuilder(name)
            .parent(ModelFile.UncheckedModelFile(mcLoc("block/block")))
            .texture("all", "#tex")
            .texture("particle", "#tex")
            .element().from(0f, 0f, 0f).to(16f, height, 16f)
            .allFaces { dir, b -> b.texture("#all") }
            .end()
    }
}
