package nipah.edify.utils

import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

inline fun ItemStack.has(tag: TagKey<Item>) =
    `is`(tag)

fun ItemStack.isAxe() = has(ItemTags.AXES)
fun ItemStack.isPickaxe() = has(ItemTags.PICKAXES)
fun ItemStack.isShovel() = has(ItemTags.SHOVELS)
fun ItemStack.isHoe() = has(ItemTags.HOES)
fun ItemStack.isSword() = has(ItemTags.SWORDS)
