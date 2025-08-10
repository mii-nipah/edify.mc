package nipah.edify.utils

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.util.INBTSerializable
import org.jetbrains.annotations.UnknownNullability

class Array3d<T>(
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    init: () -> T,
): INBTSerializable<CompoundTag> {
    private val emptyItem = init
    private val data: Array<Any?> = Array(sizeX * sizeY * sizeZ) { i ->
        init()
    }

    private fun index(x: Int, y: Int, z: Int): Int {
        require(x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ) {
            "Index out of bounds ($x, $y, $z)"
        }
        return (z * sizeY + y) * sizeX + x
    }

    operator fun get(index3d: Int): T {
        @Suppress("UNCHECKED_CAST")
        return data[index3d] as T
    }

    operator fun get(x: Int, y: Int, z: Int): T {
        @Suppress("UNCHECKED_CAST")
        return data[index(x, y, z)] as T
    }

    operator fun set(x: Int, y: Int, z: Int, value: T) {
        data[index(x, y, z)] = value
    }

    override fun serializeNBT(p0: HolderLookup.Provider): @UnknownNullability CompoundTag {
        val tag = CompoundTag()
        tag.putInt("sizeX", sizeX)
        tag.putInt("sizeY", sizeY)
        tag.putInt("sizeZ", sizeZ)
        val dataList = net.minecraft.nbt.ListTag()
        for (i in data.indices) {
            val item = this[i]
            if (item !is INBTSerializable<*>) {
                throw IllegalArgumentException("Item at index $i does not implement INBTSerializable")
            }
            val valueTag = item.serializeNBT(p0)
            dataList.add(valueTag)
        }
        tag.put("data", dataList)
        return tag
    }

    override fun deserializeNBT(p0: HolderLookup.Provider, p1: CompoundTag) {
        val sizeX = p1.getInt("sizeX")
        val sizeY = p1.getInt("sizeY")
        val sizeZ = p1.getInt("sizeZ")
        if (sizeX != this.sizeX || sizeY != this.sizeY || sizeZ != this.sizeZ) {
            throw IllegalArgumentException("Size mismatch: expected ($sizeX, $sizeY, $sizeZ), got (${this.sizeX}, ${this.sizeY}, ${this.sizeZ})")
        }
        val dataList = p1.getList("data", 10) // 10 is the type ID for CompoundTag
        for (i in data.indices) {
            val valueTag = dataList.getCompound(i)
            val value = emptyItem() as? INBTSerializable<CompoundTag>
                ?: throw IllegalArgumentException("Item at index $i does not implement INBTSerializable")
            value.deserializeNBT(p0, valueTag)
            data[i] = value
        }
    }
}
