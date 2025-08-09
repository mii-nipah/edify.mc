package nipah.edify

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.util.INBTSerializable
import org.jetbrains.annotations.UnknownNullability

class ChunkData: INBTSerializable<CompoundTag> {
    

    override fun serializeNBT(p: HolderLookup.Provider): @UnknownNullability CompoundTag {
        TODO("Not yet implemented")
    }

    override fun deserializeNBT(p: HolderLookup.Provider, p1: CompoundTag) {
        TODO("Not yet implemented")
    }
}
