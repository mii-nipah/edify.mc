package nipah.edify.utils

import net.minecraft.world.entity.player.Player

fun Player.isUsingAxe(): Boolean {
    val item = this.mainHandItem
    return item != null && item.isAxe()
}

fun Player.isUsingPickaxe(): Boolean {
    val item = this.mainHandItem
    return item != null && item.isPickaxe()
}

fun Player.isUsingShovel(): Boolean {
    val item = this.mainHandItem
    return item != null && item.isShovel()
}
