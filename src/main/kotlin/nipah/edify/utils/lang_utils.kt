package nipah.edify.utils

inline fun loop(block: () -> Boolean) {
    while (true) {
        if (block().not()) {
            break
        }
    }
}
