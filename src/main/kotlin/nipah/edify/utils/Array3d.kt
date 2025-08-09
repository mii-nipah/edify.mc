package nipah.edify.utils

class Array3D<T>(
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    init: (x: Int, y: Int, z: Int) -> T,
) {
    private val data: Array<Any?> = Array(sizeX * sizeY * sizeZ) { i ->
        val x = i % sizeX
        val y = (i / sizeX) % sizeY
        val z = i / (sizeX * sizeY)
        init(x, y, z)
    }

    private fun index(x: Int, y: Int, z: Int): Int {
        require(x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ) {
            "Index out of bounds ($x, $y, $z)"
        }
        return (z * sizeY + y) * sizeX + x
    }

    operator fun get(x: Int, y: Int, z: Int): T {
        @Suppress("UNCHECKED_CAST")
        return data[index(x, y, z)] as T
    }

    operator fun set(x: Int, y: Int, z: Int, value: T) {
        data[index(x, y, z)] = value
    }
}

