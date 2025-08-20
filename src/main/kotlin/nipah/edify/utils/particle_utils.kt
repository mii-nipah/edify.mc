package nipah.edify.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel

fun <T: ParticleOptions> ServerLevel.sendParticlesAt(
    pos: BlockPos,
    particle: T,
    count: Int = 5,
    spread: Double = 0.25,
    speed: Double = 0.01,
) {
    sendParticles(
        particle,
        pos.x + 0.5,
        pos.y + 0.5,
        pos.z + 0.5,
        count,
        spread, spread, spread,
        speed
    )
}
