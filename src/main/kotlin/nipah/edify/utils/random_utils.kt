package nipah.edify.utils

import kotlin.random.Random

fun Random.nextChance(chance: Float): Boolean {
    return this.nextFloat() < chance
}

fun Random.nextChance(chance: Double): Boolean {
    return this.nextDouble() < chance
}

fun Random.nextChance(chance: Int): Boolean {
    return this.nextInt(100) < chance
}

fun Random.nextChance(chance: Long): Boolean {
    return this.nextLong(100) < chance
}

fun Random.nextChance(chance: Number): Boolean {
    return when (chance) {
        is Float -> this.nextChance(chance)
        is Double -> this.nextChance(chance)
        is Int -> this.nextChance(chance)
        is Long -> this.nextChance(chance)
        else -> throw IllegalArgumentException("Unsupported chance type: ${chance::class.java}")
    }
}

fun Random.nextChance(chance: IntRange): Boolean {
    return this.nextInt(chance.first, chance.last + 1) < chance.last
}

fun Random.nextChance(chance: LongRange): Boolean {
    return this.nextLong(chance.first, chance.last + 1) < chance.last
}

fun Random.nextChance(chance: ClosedRange<Int>): Boolean {
    return this.nextInt(chance.start, chance.endInclusive + 1) < chance.endInclusive
}

fun Random.nextChance(chance: ClosedFloatingPointRange<Float>): Boolean {
    return this.nextFloat() in chance
}
