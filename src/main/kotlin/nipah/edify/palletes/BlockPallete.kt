package nipah.edify.palletes

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

class BlockPalette(private val states: MutableList<BlockState> = mutableListOf()) {
    private val index = mutableMapOf<BlockState, Int>()

    fun getOrAdd(state: BlockState): Int {
        return index.getOrPut(state) {
            val i = states.size
            states.add(state)
            i
        }
    }

    fun getState(i: Int): BlockState = states[i]

    fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(states.size)
        for (s in states) buf.writeVarInt(Block.getId(s))
    }

    companion object {
        fun read(buf: FriendlyByteBuf): BlockPalette {
            val size = buf.readVarInt()
            val states = MutableList(size) { Block.stateById(buf.readVarInt()) }
            val p = BlockPalette()
            p.states.addAll(states)
            states.forEachIndexed { i, s -> p.index[s] = i }
            return p
        }
    }
}
