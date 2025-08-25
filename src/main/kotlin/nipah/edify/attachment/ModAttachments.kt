package nipah.edify.attachment

import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import nipah.edify.chunks.ChunkDebris
import nipah.edify.modId

object ModAttachments {
    val REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, modId)

    val debris = REGISTRY.register("chunk_debris") { ->
        AttachmentType.serializable { -> ChunkDebris() }.build()
    }
}
