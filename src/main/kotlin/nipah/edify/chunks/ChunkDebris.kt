package nipah.edify.chunks

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.util.INBTSerializable
import nipah.edify.attachment.ModAttachments
import nipah.edify.block.DebrisBlock
import nipah.edify.block.toBlockState
import nipah.edify.ref.IntRef
import nipah.edify.utils.blockcastRay
import nipah.edify.utils.preventNextUniversalEventFromRemovingBlock
import org.jetbrains.annotations.UnknownNullability
import kotlin.math.max

class ChunkDebris: INBTSerializable<CompoundTag> {
    @JvmInline
    value class Entry(val rawStates: IntArrayList = IntArrayList()) {
        companion object {
            fun read(tag: CompoundTag): Entry {
                val statesTag = tag.getCompound("states")
                val states = IntArrayList(statesTag.size())
                for (key in statesTag.allKeys) {
                    val stateId = statesTag.getInt(key)
                    states.add(stateId)
                }
                return Entry(states)
            }
        }

        fun write(tag: CompoundTag) {
            val statesTag = CompoundTag()
            for ((i, state) in rawStates.iterator().withIndex()) {
                statesTag.putInt(i.toString(), state)
            }
            tag.put("states", statesTag)
        }

        fun toBlockStateList() =
            rawStates.map { Block.stateById(it) }

        inline fun forEachSlotById(hits: Int = 1, action: (stateId: Int, slotIndex: Int) -> Unit) {
            var match0 = -1
            var match0Count = 0
            var match1 = -1
            var match1Count = 0
            var match2 = -1
            var match2Count = 0
            var match3 = -1
            var match3Count = 0
            for (state in rawStates.iterator()) {
                if (match0 == -1) {
                    match0 = state
                    action(state, 0)
                    match0Count++
                    continue
                }
                if (state == match0) {
                    if (match0Count < hits) {
                        action(state, 0)
                        match0Count++
                    }
                    continue
                }
                if (match1 == -1) {
                    match1 = state
                    action(state, 1)
                    match1Count++
                    continue
                }
                if (state == match1) {
                    if (match1Count < hits) {
                        action(state, 1)
                        match1Count++
                    }
                    continue
                }
                if (match2 == -1) {
                    match2 = state
                    action(state, 2)
                    match2Count++
                    continue
                }
                if (state == match2) {
                    if (match2Count < hits) {
                        action(state, 2)
                        match2Count++
                    }
                    continue
                }
                if (match3 == -1) {
                    match3 = state
                    action(state, 3)
                    match3Count++
                    continue
                }
                if (state == match3) {
                    if (match3Count < hits) {
                        action(state, 3)
                        match3Count++
                    }
                    continue
                }
            }
        }

        inline fun forEachSlot(hits: Int = 1, action: (state: BlockState, slotIndex: Int) -> Unit) {
            var match0Id: Int = -1
            var match0: BlockState? = null
            var match0Count = 0
            var match1Id: Int = -1
            var match1: BlockState? = null
            var match1Count = 0
            var match2Id: Int = -1
            var match2: BlockState? = null
            var match2Count = 0
            var match3Id: Int = -1
            var match3: BlockState? = null
            var match3Count = 0
            for (state in rawStates.iterator()) {
                if (match0Id == -1) {
                    match0 = Block.stateById(state)
                    match0Id = state
                    action(match0, 0)
                    match0Count++
                    continue
                }
                if (state == match0Id) {
                    if (match0Count < hits) {
                        action(match0!!, 0)
                        match0Count++
                    }
                    continue
                }
                if (match1Id == -1) {
                    match1 = Block.stateById(state)
                    match1Id = state
                    action(match1, 1)
                    match1Count++
                    continue
                }
                if (state == match1Id) {
                    if (match1Count < hits) {
                        action(match1!!, 1)
                        match1Count++
                    }
                    continue
                }
                if (match2Id == -1) {
                    match2 = Block.stateById(state)
                    match2Id = state
                    action(match2, 2)
                    match2Count++
                    continue
                }
                if (state == match2Id) {
                    if (match2Count < hits) {
                        action(match2!!, 2)
                        match2Count++
                    }
                    continue
                }
                if (match3Id == -1) {
                    match3 = Block.stateById(state)
                    match3Id = state
                    action(match3, 3)
                    match3Count++
                    continue
                }
                if (state == match3Id) {
                    if (match3Count < hits) {
                        action(match3!!, 3)
                        match3Count++
                    }
                    continue
                }
            }
        }

        fun countSlots(): Int {
            var match0 = -1
            var match1 = -1
            var match2 = -1
            var match3 = -1
            var slots = 0
            for (state in rawStates.iterator()) {
                if (match0 == -1) {
                    match0 = state
                    slots++
                    continue
                }
                if (state == match0) {
                    continue
                }
                if (match1 == -1) {
                    match1 = state
                    slots++
                    continue
                }
                if (state == match1) {
                    continue
                }
                if (match2 == -1) {
                    match2 = state
                    slots++
                    continue
                }
                if (state == match2) {
                    continue
                }
                if (match3 == -1) {
                    match3 = state
                    slots++
                    continue
                }
                if (state == match3) {
                    continue
                }
                break
            }
            return slots
        }

        fun freeSlots(): Int {
            return 3 - countSlots()
        }

        fun smallestStackSizeId(count: IntRef? = null): Int {
            var match0 = -1
            var count0 = 0
            var match1 = -1
            var count1 = 0
            var match2 = -1
            var count2 = 0
            var match3 = -1
            var count3 = 0

            forEachSlotById(Int.MAX_VALUE) { slotId, slotIndex ->
                when (slotIndex) {
                    0 -> {
                        match0 = slotId
                        count0++
                    }

                    1 -> {
                        match1 = slotId
                        count1++
                    }

                    2 -> {
                        match2 = slotId
                        count2++
                    }

                    3 -> {
                        match3 = slotId
                        count3++
                    }
                }
            }
            val smallestCount = minOf(count0, count1, count2, count3)
            return when (smallestCount) {
                count0 -> {
                    count?.value = count0
                    match0
                }

                count1 -> {
                    count?.value = count1
                    match1
                }

                count2 -> {
                    count?.value = count2
                    match2
                }

                else -> {
                    count?.value = count3
                    match3
                }
            }
        }

        fun largestStackSizeId(count: IntRef? = null): Int {
            var match0 = -1
            var match1 = -1
            var match2 = -1
            var match3 = -1
            var count0 = 0
            var count1 = 0
            var count2 = 0
            var count3 = 0
            for (state in rawStates.iterator()) {
                if (match0 == -1) {
                    match0 = state
                    count0++
                    continue
                }
                if (state == match0) {
                    count0++
                    continue
                }
                if (match1 == -1) {
                    match1 = state
                    count1++
                    continue
                }
                if (state == match1) {
                    count1++
                    continue
                }
                if (match2 == -1) {
                    match2 = state
                    count2++
                    continue
                }
                if (state == match2) {
                    count2++
                    continue
                }
                if (match3 == -1) {
                    match3 = state
                    count3++
                    continue
                }
                if (state == match3) {
                    count3++
                    continue
                }
            }
            val largestCount = max(count0, max(count1, max(count2, count3)))
            if (largestCount == count0) {
                count?.value = count0
                return match0
            }
            if (largestCount == count1) {
                count?.value = count1
                return match1
            }
            if (largestCount == count2) {
                count?.value = count2
                return match2
            }
            count?.value = count3
            return match3
        }

        fun hasEmptySlot(of: BlockState): Boolean {
            val slots = countSlots()
            if (slots < 3) {
                return true
            }

            val stateId = Block.getId(of)
            for (state in rawStates.iterator()) {
                if (state == stateId) {
                    return true
                }
            }
            return false
        }
    }

    private val debris = Long2ObjectOpenHashMap<Entry>(5_000)

    fun storeDebris(pos: BlockPos, state: BlockState): Boolean {
        val entry = debris.getOrDefault(pos.asLong(), Entry())
        if (!entry.hasEmptySlot(state)) {
            return false
        }
        entry.rawStates.add(Block.getId(state))
        debris[pos.asLong()] = entry
        return true
    }

    fun storeDebrisEntry(pos: BlockPos, entry: Entry) {
        debris[pos.asLong()] = entry
    }

    fun removeDebris(pos: BlockPos) {
        debris.remove(pos.asLong())
    }

    fun getDebrisState(pos: BlockPos): Entry? {
        val entry = debris[pos.asLong()]
        if (entry == null) {
            return null
        }
        return entry
    }

    override fun serializeNBT(p: HolderLookup.Provider): @UnknownNullability CompoundTag {
        val tag = CompoundTag()
        val debrisTag = CompoundTag()
        for ((posLong, entry) in debris.long2ObjectEntrySet()) {
            val entryTag = CompoundTag()
            entry.write(entryTag)
            debrisTag.put(posLong.toString(), entryTag)
        }
        tag.put("debris", debrisTag)
        return tag
    }

    override fun deserializeNBT(p: HolderLookup.Provider, p1: CompoundTag) {
        debris.clear()
        val debrisTag = p1.getCompound("debris")
        for (key in debrisTag.allKeys) {
            val posLong = key.toLongOrNull() ?: continue
            val entryTag = debrisTag.getCompound(key)
            val entry = Entry.read(entryTag)
            debris[posLong] = entry
        }
    }
}

fun Level.setDebrisAt(pos: BlockPos, state: BlockState, depth: Int = 0): Boolean {
    if (depth > 10) return false

    val onGroup = blockcastRay(pos, BlockPos(0, -1, 0), 50) ?: return false
    val pos = onGroup.immutable()

    val hasDown = getBlockState(pos.below()).block is DebrisBlock
    if (hasDown) {
        return this.setDebrisAt(pos.below(), state, depth + 1)
    }
    val hasUp = getBlockState(pos.above()).block is DebrisBlock
    if (hasUp) {
        return this.setDebrisAt(pos.above(), state, depth + 1)
    }

    val chunk = this.getChunkAt(pos)
    val debris = chunk.getData(ModAttachments.debris)
    if (debris.storeDebris(pos, state).not()) {
        val north = pos.north()
        val south = pos.south()
        val west = pos.west()
        val east = pos.east()
        val candidates = listOf(north, south, west, east).filter { this.getBlockState(it).isAir }
        if (candidates.isEmpty()) return false
        val nextPos = candidates.random()
        return this.setDebrisAt(nextPos, state, depth + 1)
    }
    val entry = debris.getDebrisState(pos) ?: return false
    setBlockAndUpdate(pos, entry.toBlockState())
    chunk.isUnsaved = true
    return true
}

fun Level.moveDebrisTo(from: BlockPos, to: BlockPos): Boolean {
    val fromChunk = this.getChunkAt(from)
    val toChunk = this.getChunkAt(to)
    val fromDebris = fromChunk.getData(ModAttachments.debris)

    val fromEntry = fromDebris.getDebrisState(from) ?: return false
    if (fromEntry.rawStates.isEmpty) {
        fromDebris.removeDebris(from)
        preventNextUniversalEventFromRemovingBlock()
        removeBlock(from, false)
        fromChunk.isUnsaved = true
        return false
    }

    val toDebris = toChunk.getData(ModAttachments.debris)
    val toEntry = toDebris.getDebrisState(to)
    if (toEntry == null || toEntry.rawStates.isEmpty) {
        toDebris.storeDebrisEntry(to, fromEntry)
        fromDebris.removeDebris(from)
        toChunk.isUnsaved = true
        fromChunk.isUnsaved = true
        setBlockAndUpdate(to, fromEntry.toBlockState())
        preventNextUniversalEventFromRemovingBlock()
        removeBlock(from, false)
        return true
    }
    val fromSlots = run {
        val list: MutableList<BlockState> = mutableListOf()
        fromEntry.forEachSlot { state, slotIndex ->
            list.add(state)
        }
        list
    }
    if (fromSlots.all { state -> toEntry.hasEmptySlot(state) }.not()) {
        return false
    }
    for (state in fromSlots) {
        toDebris.storeDebris(to, state)
    }
    fromDebris.removeDebris(from)
    toChunk.isUnsaved = true
    fromChunk.isUnsaved = true
    val newToEntry = toDebris.getDebrisState(to) ?: return false
    setBlockAndUpdate(to, newToEntry.toBlockState())
    preventNextUniversalEventFromRemovingBlock()
    removeBlock(from, false)
    return true
}

fun Level.removeDebrisData(pos: BlockPos) {
    val chunk = this.getChunkAt(pos)
    val debris = chunk.getData(ModAttachments.debris)
    debris.removeDebris(pos)
    chunk.isUnsaved = true
}

fun Level.removeDebrisAt(pos: BlockPos) {
    val blockState = this.getBlockState(pos)
    if (blockState.block is DebrisBlock) {
        this.removeBlock(pos, false)
    }
    this.removeDebrisData(pos)
}

fun Level.getDebrisStateAt(pos: BlockPos): ChunkDebris.Entry? {
    val chunk = this.getChunkAt(pos)
    val debris = chunk.getData(ModAttachments.debris)
    return debris.getDebrisState(pos)
}
