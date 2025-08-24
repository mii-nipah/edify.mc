package nipah.edify.utils

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.util.INBTSerializable
import org.jetbrains.annotations.UnknownNullability

class IntArray3d(
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
): INBTSerializable<CompoundTag> {
    private val data: IntArray = IntArray(sizeX * sizeY * sizeZ) { 0 }

    private inline fun index(x: Int, y: Int, z: Int): Int {
        return (z * sizeY + y) * sizeX + x
    }

    operator fun get(index3d: Int): Int {
        return data[index3d]
    }

    operator fun get(x: Int, y: Int, z: Int): Int {
        return data[index(x, y, z)]
    }

    operator fun set(x: Int, y: Int, z: Int, value: Int) {
        data[index(x, y, z)] = value
    }

    fun boundedGet(x: Int, y: Int, z: Int): Int? {
        if ((x or y or z or (sizeX - 1 - x) or (sizeY - 1 - y) or (sizeZ - 1 - z)) < 0) return null
        return data[index(x, y, z)]
    }

    fun boundedContainsValue(x: Int, y: Int, z: Int, value: Int): Boolean {
        if ((x or y or z or (sizeX - 1 - x) or (sizeY - 1 - y) or (sizeZ - 1 - z)) < 0) return false
        return data[index(x, y, z)] == value
    }

    override fun serializeNBT(p0: HolderLookup.Provider): @UnknownNullability CompoundTag {
        val tag = CompoundTag()
        tag.putInt("sizeX", sizeX)
        tag.putInt("sizeY", sizeY)
        tag.putInt("sizeZ", sizeZ)
        tag.putIntArray("data", data)
        return tag
    }

    override fun deserializeNBT(p0: HolderLookup.Provider, p1: CompoundTag) {
        val sizeX = p1.getInt("sizeX")
        val sizeY = p1.getInt("sizeY")
        val sizeZ = p1.getInt("sizeZ")
        if (sizeX != this.sizeX || sizeY != this.sizeY || sizeZ != this.sizeZ) {
            throw IllegalArgumentException("Size mismatch: expected ($sizeX, $sizeY, $sizeZ), got (${this.sizeX}, ${this.sizeY}, ${this.sizeZ})")
        }
        val dat = p1.getIntArray("data")
        if (dat.size != data.size) {
            throw IllegalArgumentException("Data size mismatch: expected ${data.size}, got ${dat.size}")
        }
        for (i in dat.indices) {
            data[i] = dat[i]
        }
    }
}
