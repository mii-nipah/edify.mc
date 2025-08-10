package nipah.edify

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.util.INBTSerializable
import org.jetbrains.annotations.UnknownNullability

sealed class BlockData(): INBTSerializable<CompoundTag> {
    object Air: BlockData() {
        override fun serializeNBT(p0: HolderLookup.Provider): @UnknownNullability CompoundTag {
            return CompoundTag().apply {
                putInt("type", 0)
            }
        }

        override fun deserializeNBT(p0: HolderLookup.Provider, p1: CompoundTag) {
            p1.getInt("type").let { type ->
                if (type != 0) {
                    throw IllegalArgumentException("Invalid type for Air block: $type")
                }
            }
        }
    }

    object Bedrock: BlockData() {
        override fun serializeNBT(p0: HolderLookup.Provider): @UnknownNullability CompoundTag {
            return CompoundTag().apply {
                putInt("type", 1)
            }
        }

        override fun deserializeNBT(p0: HolderLookup.Provider, p1: CompoundTag) {
            p1.getInt("type").let { type ->
                if (type != 1) {
                    throw IllegalArgumentException("Invalid type for Bedrock block: $type")
                }
            }
        }
    }

    object Foundation: BlockData() {
        override fun serializeNBT(p0: HolderLookup.Provider): @UnknownNullability CompoundTag {
            return CompoundTag().apply {
                putInt("type", 2)
            }
        }

        override fun deserializeNBT(p0: HolderLookup.Provider, p1: CompoundTag) {
            p1.getInt("type").let { type ->
                if (type != 2) {
                    throw IllegalArgumentException("Invalid type for Foundation block: $type")
                }
            }
        }
    }

    data class Group(var id: Int, var limit: Int, var pressure: Int = 0): BlockData() {
        override fun toString(): String {
            return "Group(id=$id, pressure=$pressure) | limit=$limit"
        }

        override fun serializeNBT(p0: HolderLookup.Provider): @UnknownNullability CompoundTag {
            val self = this
            return CompoundTag().apply {
                putInt("type", 3)
                putInt("id", self.id)
                putInt("limit", self.limit)
                putInt("pressure", self.pressure)
            }
        }

        override fun deserializeNBT(p0: HolderLookup.Provider, p1: CompoundTag) {
            p1.getInt("type").let { type ->
                if (type != 3) {
                    throw IllegalArgumentException("Invalid type for Group block: $type")
                }
                id = p1.getInt("id")
                limit = p1.getInt("limit")
                pressure = p1.getInt("pressure")
            }
        }
    }
}
