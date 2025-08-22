package nipah.edify.tags

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import nipah.edify.modId

object ModTags {
    val floating = TagKey.create(
        Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(modId, "floating")
    )
}
