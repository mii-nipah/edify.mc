package nipah.edify.entities

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.neoforged.neoforge.registries.DeferredRegister
import nipah.edify.modId

object ModEntities {
    val REGISTRY = DeferredRegister.create(
        BuiltInRegistries.ENTITY_TYPE,
        modId
    )

    val fallingStructure = REGISTRY.register("falling_structure") { ->
        EntityType.Builder
            .of({ et, lvl ->
                FallingStructureEntity(et, lvl)
            }, MobCategory.MISC)
            .sized(2.0f, 2.0f)
            .clientTrackingRange(128)
            .updateInterval(1)
            .build("falling_structure")
    }

    // If you get an "overload resolution ambiguity" error, include the arrow at the start of the closure.
//    val EXAMPLE_BLOCK by REGISTRY.register("example_block") { ->
//        Block(BlockBehaviour.Properties.of().lightLevel { 15 }.strength(3.0f))
//    }
}
