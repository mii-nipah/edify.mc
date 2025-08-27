package nipah.edify.utils

inline fun loop(block: () -> Boolean) {
    while (true) {
        if (block().not()) {
            break
        }
    }
}

sealed class LoopControl {
    object Continue: LoopControl()
    object Break: LoopControl()
}
