package nipah.edify.types

abstract class NeighborCheck {
    data object Left: NeighborCheck()
    data object Right: NeighborCheck()
    data object Front: NeighborCheck()
    data object Back: NeighborCheck()
    data object LeftFront: NeighborCheck()
    data object LeftBack: NeighborCheck()
    data object RightFront: NeighborCheck()
    data object RightBack: NeighborCheck()

    data object None: NeighborCheck()
}
