package nipah.edify.tags

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.*
import net.neoforged.neoforge.common.data.BlockTagsProvider
import net.neoforged.neoforge.common.data.ExistingFileHelper
import java.util.concurrent.CompletableFuture

class BuildingBlockTagsGen(gen: PackOutput, lookup: CompletableFuture<HolderLookup.Provider>, helper: ExistingFileHelper): BlockTagsProvider(gen, lookup, "edify", helper) {
    override fun addTags(provider: HolderLookup.Provider) {
        val auto = tag(ModTags.building)

        val blocks = provider.lookupOrThrow(Registries.BLOCK)
        for (ref in blocks.listElements()) {
            val block = ref.value()
            val id = ref.key().location()
            if (shouldInclude(block, id)) {
                // TagAppender supports adding by key (recommended in datagen)
                auto.add(ref.key())
            }
        }

        // Final composition: AUTO + FORCE_INCLUDE (runtime will check FORCE_EXCLUDE)
        tag(ModTags.building)
    }

    private fun shouldInclude(block: Block, id: ResourceLocation): Boolean {
        // 1) Shapes that are almost always “building”
        when (block) {
            is SlabBlock,
            is StairBlock,
            is WallBlock,
            is FenceBlock, is FenceGateBlock,
            is DoorBlock, is TrapDoorBlock,
            is RotatedPillarBlock,              // logs/pillars/basalt/quartz pillar
            is StainedGlassPaneBlock, is StainedGlassBlock, is TintedGlassBlock, is IronBarsBlock,
                -> return true
        }

        // 2) Obvious non-structural / unstable
        when (block) {
            is LiquidBlock,
            is FallingBlock,                     // sand/gravel/concrete_powder
            is BushBlock, is LeavesBlock,
            is CarpetBlock, is BedBlock,
            is BaseRailBlock,
            is TorchBlock, is LanternBlock,
            is ScaffoldingBlock,
                -> return false
        }

        // 3) Name heuristics to catch full cubes from mods (stone/masonry/metals/ceramics)
        val p = id.path
        val excludeTokens = listOf("concrete_powder", "_ore", "ore/", "raw_", "crop", "leaves", "sapling", "mushroom", "bud", "cluster", "amethyst")
        if (excludeTokens.any { it in p }) return false

        val includeTokens = listOf(
            "stone", "cobblestone", "deepslate", "andesite", "diorite", "granite",
            "basalt", "blackstone", "tuff", "calcite", "sandstone", "red_sandstone",
            "end_stone", "purpur", "brick", "bricks", "masonry", "tile", "tiles",
            "planks", "wood", "stripped", "log", "quartz",
            "terracotta", "glazed_terracotta", "concrete", // (not powder)
            "prismarine", "obsidian", "slate", "marble", "limestone",
            "copper", "iron_block", "gold_block", "steel", "lead", "tin", "zinc", "nickel", "aluminum", "aluminium", "silver", "uranium"
        )
        return includeTokens.any { it in p }
    }
}
