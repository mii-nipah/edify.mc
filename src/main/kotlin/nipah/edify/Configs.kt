package nipah.edify

import net.neoforged.neoforge.common.ModConfigSpec
import nipah.edify.utils.withSection

enum class CollapseMode {
    PHYSICS,
    ACE_OF_SPADES,
}

object Configs {
    val startup: Startup
    val startupSpec: ModConfigSpec
    val common: Server
    val commonSpec: ModConfigSpec

    init {
        val startupPair = ModConfigSpec.Builder()
            .configure(::Startup)
        startup = startupPair.left
        startupSpec = startupPair.right

        val commonPair = ModConfigSpec.Builder()
            .configure(::Server)
        common = commonPair.left
        commonSpec = commonPair.right
    }

    class Startup(builder: ModConfigSpec.Builder) {
        class Threading(builder: ModConfigSpec.Builder) {
            var threads: ModConfigSpec.IntValue private set
            var threadsTicksPerSecondPerIteration: ModConfigSpec.IntValue private set
            var sleepWhenServerIsNotAllowing: ModConfigSpec.IntValue private set
            var numberOfDirectlyProcessedOperationsPerSleep: ModConfigSpec.IntValue private set
            var timeToSleepBetweenBatchesOfDirectOperations: ModConfigSpec.IntValue private set

            init {
                builder.withSection("threading", "Options that control how your CPU will be used") {
                    threads = run {
                        comment("Number of threads to use for operation scheduling.")
                        comment("Increase this if you have a high core count and want faster operations.")
                        comment("Decrease this if you experience lag spikes when starting many operations.")
                        defineInRange("threads", 1, 1, 128)
                    }
                    threadsTicksPerSecondPerIteration = run {
                        comment("Number of ticks to process per iteration of each thread.")
                        comment("This is basically a regulation to how much will a thread wait in between processing schedules when they are available and the server signaled ready processing.")
                        defineInRange("threadsTicksPerSecondPerIteration", 20, 1, 1000)
                    }
                    sleepWhenServerIsNotAllowing = run {
                        comment("Number of milliseconds to sleep when the server is not allowing threaded operations.")
                        comment("Increase this if you want to reduce CPU usage when the server is busy, at the cost of responsiveness.")
                        defineInRange("sleepWhenServerIsNotAllowing", 100, 0, 10_000)
                    }
                    numberOfDirectlyProcessedOperationsPerSleep = run {
                        comment("Number of operations to process directly when the server is allowing threaded operations.")
                        comment("This is basically a way to make the processor sleep for a bit when it's processing many operations in a short period of time, decreasing changes of throttling.")
                        comment("Increase this if you want more responsivity at the cost of possible lag spikes.")
                        defineInRange("numberOfDirectlyProcessedOperationsPerSleep", 10, 1, 100_000)
                    }
                    timeToSleepBetweenBatchesOfDirectOperations = run {
                        comment("Number of milliseconds to sleep between batches of directly processed operations.")
                        comment("Increase this if you want to reduce CPU usage when the server is busy, at the cost of responsiveness.")
                        defineInRange("timeToSleepBetweenBatchesOfDirectOperations", 250, 15, 100_000)
                    }
                }
            }
        }

        val threading = Threading(builder)
    }

    class Server(builder: ModConfigSpec.Builder) {
        class ChunkEvents(builder: ModConfigSpec.Builder) {
            var ticksToBatchRemovalOperations: ModConfigSpec.IntValue private set

            init {
                builder.withSection("chunk_events", "Options that control chunk event handling") {
                    ticksToBatchRemovalOperations = run {
                        comment("Number of server ticks to wait before processing queued block removal operations.")
                        comment("Increase this if you want to reduce the number of concurrent scans for floating structures by batching more removals together.")
                        comment("This should somewhat also reduce memory usage as less group scans will be needed to be instantiated, but will also increase processing time for individual removals.")
                        defineInRange("ticksToBatchRemovalOperations", 5, 1, 1000)
                    }
                }
            }
        }

        class WorldData(builder: ModConfigSpec.Builder) {
            var maxConcurrentScans: ModConfigSpec.IntValue private set

            init {
                builder.withSection("world_data", "Options that control world data handling") {
                    maxConcurrentScans = run {
                        comment("Maximum number of concurrent group scans that can be performed.")
                        comment("Increase this if you have a high core count and want faster operations.")
                        comment("Decrease this if you experience lag spikes when starting many operations.")
                        comment("The more you have, the higher your memory usage will be, so consider this seriously.")
                        comment("On the other hand, having more will make operations be processed more reliably and possibly faster.")
                        defineInRange("maxConcurrentScans", 10, 1, 1000)
                    }
                }
            }
        }

        class GroupScan(builder: ModConfigSpec.Builder) {
            var limit: ModConfigSpec.IntValue private set
            var scanPerTick: ModConfigSpec.IntValue private set
            var blocksPerFloatingSupports: ModConfigSpec.IntValue private set
            var floatingSupportsNaturalIslandLimit: ModConfigSpec.IntValue private set

            init {
                builder.withSection("group_scan", "Options that control group scanning") {
                    limit = run {
                        comment("Maximum number of blocks to scan in a single group scan operation.")
                        comment("Increase this if you want to be able to scan larger structures, at the cost of higher memory usage and longer times to complete scans for non-floating structures.")
                        comment("Decrease this if you want to reduce memory usage and lag spikes, at the cost of being unable to scan larger structures.")
                        comment("Having this number to high can mean having false positives in some specific circumstances. But having it too low will mean some floating structures will not be detected at all.")
                        comment("This is the hard stop that makes this system work, so tune it with consideration of how it works.")
                        defineInRange("limit", 100_000, 1_000, 1_000_000_000)
                    }
                    scanPerTick = run {
                        comment("Number of blocks to scan per server tick.")
                        comment("Increase this if you want faster scans, at the cost of higher CPU usage and possibly lag spikes.")
                        comment("Decrease this if you want to reduce CPU usage and lag spikes, at the cost of slower scans.")
                        comment("If you have a slow CPU, it should be fine to set this to a lower value, as the scan will then be less likely to interrupt normal server operations, and it will give time to other scans in the queue to also make progress, and will help the ones that have floating structures to complete faster (as they have less blocks to scan overall).")
                        comment("Limit / 20 should be a good value.")
                        defineInRange("scanPerTick", 5_000, 100, 50_000)
                    }
                    blocksPerFloatingSupports = run {
                        comment("Number of blocks that can be supported by a single floating support.")
                        comment("Increase this if you want to reduce the number of floating supports needed for large structures.")
                        defineInRange("blocksPerFloatingSupports", 3, 1, 100)
                    }
                    floatingSupportsNaturalIslandLimit = run {
                        comment("Maximum number of blocks in a natural island.")
                        comment("It's an optimization number that helps a bit in the end dimension for the large floating islands, as it will stop scanning after a sufficiently high number of those blocks have been found.")
                        defineInRange("floatingSupportsNaturalIslandLimit", 5_000, 100, 100_000)
                    }
                }
            }
        }

        class ChunkData(builder: ModConfigSpec.Builder) {
            var nonBedrockFoundationChance: ModConfigSpec.DoubleValue private set

            init {
                builder.withSection("chunk_data", "Options that control chunk data handling") {
                    nonBedrockFoundationChance = run {
                        comment("Chance for a non-bedrock block, connected to bedrock, to be considered a foundation block.")
                        comment("Foundation blocks are used as 'hard stops' for group scans, they help control the grow size of scans in the natural generated world.")
                        comment("Higher values will lead to more naturally generated blocks in the world being considered foundational, which will mean things like cutting entire mountains will be more likely to not work. But will also make the scans substantially faster for natural terrain, and will make it way more responsive for player structure in return.")
                        comment("Set this to 0.0 to only consider bedrock blocks as foundation blocks.")
                        comment("Set this to 1.0 to consider all naturally generated blocks as foundation blocks.")
                        comment("Apart from saying 'all blocks will be considered', in reality the upper you go the sparse the generation should be. But with higher values the amount of blocks considered foundation will be really high, so it will make it almost the same outcome.")
                        defineInRange("nonBedrockFoundationChance", 0.007, 0.0, 1.0)
                    }
                }
            }
        }

        class StructuralIntegrity(builder: ModConfigSpec.Builder) {
            var enabled: ModConfigSpec.BooleanValue private set

            init {
                builder.withSection("structural_integrity", "Options that control structural integrity simulation") {
                    enabled = run {
                        comment("Whether structural integrity simulation is enabled.")
                        comment("When enabled, structures that are overstressed will progressively break apart.")
                        comment("Disable this if you only want floating structure collapse without stress simulation.")
                        define("enabled", true)
                    }
                }
            }
        }

        class Collapse(builder: ModConfigSpec.Builder) {
            var mode: ModConfigSpec.EnumValue<CollapseMode> private set
            var useDebris: ModConfigSpec.BooleanValue private set
            var aceOfSpadesDelay: ModConfigSpec.IntValue private set

            init {
                builder.withSection("collapse", "Options that control how floating structures collapse") {
                    mode = run {
                        comment("The collapse mode for floating structures.")
                        comment("PHYSICS: structures fall with gravity, collide with terrain, and interact with entities.")
                        comment("ACE_OF_SPADES: structures briefly appear as a floating model, then break apart in place.")
                        defineEnum("mode", CollapseMode.PHYSICS)
                    }
                    useDebris = run {
                        comment("Whether collapsed blocks produce debris piles.")
                        comment("When disabled, collapsed blocks are fully destroyed with no debris or item drops.")
                        define("useDebris", true)
                    }
                    aceOfSpadesDelay = run {
                        comment("Number of server ticks the floating structure model is visible before breaking apart in ACE_OF_SPADES mode.")
                        defineInRange("aceOfSpadesDelay", 40, 1, 200)
                    }
                }
            }
        }

        val chunkEvents = ChunkEvents(builder)
        val worldData = WorldData(builder)
        val groupScan = GroupScan(builder)
        val chunkData = ChunkData(builder)
        val structuralIntegrity = StructuralIntegrity(builder)
        val collapse = Collapse(builder)
    }
}
