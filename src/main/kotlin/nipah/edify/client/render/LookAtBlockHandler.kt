package nipah.edify.client.render

//@EventBusSubscriber(value = [Dist.CLIENT])
//object LookAtBlockHandler {
//    private val mc = Minecraft.getInstance()
//
//    @SubscribeEvent
//    fun onClientTick(event: ClientTickEvent.Post) {
//        val player = mc.player ?: return
//        val hit = mc.hitResult ?: return
//
//        if (hit.type == HitResult.Type.BLOCK) {
//            val blockHit = hit as BlockHitResult
//            val pos = blockHit.blockPos
//            val chunkPos = ChunkPos(pos)
//            val chunk = mc.level?.getChunk(chunkPos.x, chunkPos.z) ?: return
//            val chunkData = WorldData.chunkData[chunkPos] ?: return
//            val lpos = chunk.worldToLocalPos(pos)
//            val foundation = chunkData.foundationAt(lpos.x, lpos.y, lpos.z)
//            if (foundation) {
//                Gizmos.block(
//                    pos,
//                    Gizmos.Color.green,
//                )
//            }
//        }
//    }
//}
