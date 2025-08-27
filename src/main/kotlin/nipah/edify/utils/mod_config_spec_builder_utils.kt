package nipah.edify.utils

import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun ModConfigSpec.Builder.withSection(name: String, comment: String, block: ModConfigSpec.Builder.() -> Unit) {
    contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    try {
        push(name)
        comment(comment)
        block()
    }
    finally {
        pop()
    }
}
