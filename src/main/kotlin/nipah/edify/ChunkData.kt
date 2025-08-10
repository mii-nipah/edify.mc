package nipah.edify

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.neoforge.common.util.INBTSerializable
import nipah.edify.utils.Array3d
import org.jetbrains.annotations.UnknownNullability

class ChunkData(level: LevelChunk): INBTSerializable<CompoundTag> {
    val data = Array3d<BlockData>(
        16,
        level.height,
        16
    ) { BlockData.Air }

    override fun serializeNBT(p: HolderLookup.Provider): @UnknownNullability CompoundTag {
        TODO("Not yet implemented")
    }

    override fun deserializeNBT(p: HolderLookup.Provider, p1: CompoundTag) {
        TODO("Not yet implemented")
    }
}
