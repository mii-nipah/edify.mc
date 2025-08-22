package nipah.edify.entities

import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn
import net.neoforged.neoforge.network.PacketDistributor
import nipah.edify.client.render.FallingBatch
import nipah.edify.client.render.FallingBatchClient
import nipah.edify.network.FallingStructureBlockRemovedPacket
import nipah.edify.palletes.BlockPalette
import nipah.edify.types.WorldBlock
import nipah.edify.types.to
import nipah.edify.utils.toCopyOnWriteArrayList
import org.joml.Quaternionf
import java.util.concurrent.CopyOnWriteArrayList

class FallingStructureEntity(type: EntityType<FallingStructureEntity>, level: Level): Entity(type, level), IEntityWithComplexSpawn {
    companion object {
        val toRender = CopyOnWriteArrayList<FallingBatchClient>()

        private val ORIGIN = SynchedEntityData.defineId(
            FallingStructureEntity::class.java,
            EntityDataSerializers.BLOCK_POS
        )
        private val ROTATION = SynchedEntityData.defineId(
            FallingStructureEntity::class.java,
            EntityDataSerializers.QUATERNION
        )
    }

    init {
        noPhysics = true
    }

    var origin: BlockPos
        get() = entityData.get(ORIGIN)
        set(value) = entityData.set(ORIGIN, value)

    var rotation: Quaternionf
        get() = entityData.get(ROTATION)
        set(value) = entityData.set(ROTATION, value)

    lateinit var data: FallingBatch
    lateinit var dataClient: FallingBatchClient

    fun setSpawnData(data: FallingBatch) {
        this.data = data
        this.setPos(data.pos.x.toDouble(), data.pos.y.toDouble(), data.pos.z.toDouble())
        this.setDeltaMovement(data.vel.x.toDouble(), data.vel.y.toDouble(), data.vel.z.toDouble())
    }

    fun syncData() {
        if (this.level().isClientSide) return
//        val deltaPos = data.pos.toVec3() - position()
//        move(MoverType.SELF, deltaPos)
        setPos(data.pos.x.toDouble(), data.pos.y.toDouble(), data.pos.z.toDouble())
        setDeltaMovement(data.vel.x.toDouble(), data.vel.y.toDouble(), data.vel.z.toDouble())
        entityData.set(ORIGIN, data.origin)
        entityData.set(ROTATION, data.rotation)
        boundingBox = data.aabb
    }

    override fun tick() {
        if (this.level().isClientSide) return
        super.tick()
        data.tick()
        val removedBlocks = data.tickServer(level() as ServerLevel)
        if (removedBlocks.isNotEmpty()) {
            val packet = FallingStructureBlockRemovedPacket(
                id,
                removedBlocks
            )
            PacketDistributor.sendToPlayersTrackingEntity(this, packet)
        }
        syncData()

        var toRemove = data.blocks.isEmpty()
        if (data.pos.y < -100f || data.pos.y.isNaN()) toRemove = true
        if (toRemove) {
            data.close()
            this.remove(RemovalReason.DISCARDED)
        }
    }

    override fun defineSynchedData(sync: SynchedEntityData.Builder) {
        sync.define(ORIGIN, BlockPos.ZERO)
        sync.define(ROTATION, Quaternionf())
    }

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        if (this.level().isClientSide.not()) return

        dataClient.pos = position().toVector3f()
        dataClient.vel = deltaMovement.toVector3f()
        if (key == ROTATION) {
            dataClient.rotation = entityData.get(ROTATION)
        }

        boundingBox = FallingBatch.computeAabb(
            dataClient.blocks,
            dataClient.origin,
            dataClient.pos,
            dataClient.rotation
        ).inflate(0.5)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {

    }

    override fun addAdditionalSaveData(tag: CompoundTag) {

    }

    override fun shouldBeSaved() = false

    override fun writeSpawnData(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(data.origin)
        buf.writeVector3f(data.pos)
        buf.writeVector3f(data.vel)
        buf.writeQuaternion(data.rotation)

        val palette = BlockPalette()
        for ((_, state) in data.blocks) {
            palette.getOrAdd(state)
        }
        palette.write(buf)

        buf.writeVarInt(data.blocks.size)
        for ((pos, state) in data.blocks) {
            val idx = palette.getOrAdd(state)
            buf.writeVarInt(idx)
            buf.writeBlockPos(pos)
        }
    }

    override fun readSpawnData(buf: RegistryFriendlyByteBuf) {
        val origin = buf.readBlockPos()
        val pos = buf.readVector3f()
        val vel = buf.readVector3f()
        val rotation = buf.readQuaternion()

        val palette = BlockPalette.read(buf)
        val blocksSize = buf.readVarInt()
        val blocks = mutableListOf<WorldBlock>()

        repeat(blocksSize) {
            val idx = buf.readVarInt()
            val state = palette.getState(idx)
            val pos = buf.readBlockPos()
            blocks.add(pos to state)
        }

        dataClient = FallingBatchClient(
            origin = origin,
            pos = pos,
            rotation = rotation,
            vel = vel,
            blocks = blocks.toCopyOnWriteArrayList()
        )
        boundingBox = FallingBatch.computeAabb(
            dataClient.blocks,
            dataClient.origin,
            dataClient.pos,
            dataClient.rotation
        ).inflate(0.5)
        this.origin = origin
        this.rotation = rotation
    }

    fun removeBlockClient(pos: LongArrayList) {
        if (::dataClient.isInitialized.not()) return
        dataClient.blocks.removeIf { it.pos.asLong() in pos }
        dataClient.invalidate()
        boundingBox = FallingBatch.computeAabb(
            dataClient.blocks,
            dataClient.origin,
            dataClient.pos,
            dataClient.rotation
        )
    }

    override fun onClientRemoval() {
        super.onClientRemoval()
        if (::dataClient.isInitialized.not()) return
        dataClient.close()
    }
}
