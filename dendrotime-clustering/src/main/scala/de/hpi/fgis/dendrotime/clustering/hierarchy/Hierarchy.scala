package de.hpi.fgis.dendrotime.clustering.hierarchy

object Hierarchy {
  /** A node in the hierarchy that merges two clusters at a certain distance. */
  final case class Node(idx: Int, cId1: Int, cId2: Int,
                        distance: Double,
                        cardinality: Int = 0
                       )

  private final def nodeFromArray(idx: Int, arr: Array[Double]): Node =
    Node(idx, arr(0).toInt, arr(1).toInt, arr(2), arr(3).toInt)

  def empty: Hierarchy = Hierarchy(Array.empty, 0)

  /** Create a new hierarchy using the builder pattern. */
  def newBuilder(n: Int): HierarchyBuilder = new HierarchyBuilder(n)

  /** Create a hierarchy from an existing 2D array representation. */
  def fromArray(z: Array[Array[Double]]): Hierarchy = Hierarchy(z, z.length + 1)

  class HierarchyBuilder private[Hierarchy](n: Int) extends IndexedSeq[Hierarchy.Node] {
    private val z = Array.ofDim[Double](n - 1, 4)
    private var i = 0

    /** Return node at location i. */
    override def apply(i: Int): Node = nodeFromArray(i, z(i))

    /** Current length of the hierarchy. */
    override def length: Int = i

    /** Add a new node at the next free location. */
    def add(node: Node): this.type = add(node.cId1, node.cId2, node.distance, node.cardinality)

    /** Add a new entry at the next free location. */
    def add(elem1: Int, elem2: Int, distance: Double, cardinality: Int = 0): this.type = {
      z(i) = Array(elem1, elem2, distance, cardinality)
      i += 1
      this
    }

    /** Replace the node at location i. */
    def update(i: Int, node: Node): this.type = update(i, node.cId1, node.cId2, node.distance, node.cardinality)

    /** Replace the entry at location i with the supplied values. */
    def update(i: Int, elem1: Int, elem2: Int, distance: Double, cardinality: Int = 0): this.type = {
      z(i) = Array(elem1, elem2, distance, cardinality)
      this
    }

    /** Sort the cluster merge operations by their distance (low to high). */
    def sort(): this.type = {
      z.sortInPlaceBy(_(2))
      this
    }

    /** Return the immutable Hierarchy */
    def build(): Hierarchy = {
      Hierarchy(z, n)
    }
  }
}

/**
 * Stores a hierarchy of a hierarchical agglomerative clustering in a compact form.
 *
 * This is inspired by the result representation used in SciPy's `scipy.hierarchy.linkage` function:
 *
 * `Z[i]` will tell us which clusters were merged in the i-th iteration:
 *
 *   - Z[i, 0]: first item
 *   - Z[i, 1]: second item
 *   - Z[i, 2]: distance between Z[i, 0] and Z[i, 1]
 *   - Z[i, 3]: number of original observations in the newly formed cluster (cardinality)
 */
case class Hierarchy private(private val z: Array[Array[Double]], n: Int)
  extends Iterable[Hierarchy.Node] with IndexedSeq[Hierarchy.Node] {

  def cId1(i: Int): Int = z(i)(0).toInt

  def cId2(i: Int): Int = z(i)(1).toInt

  def distance(i: Int): Double = z(i)(2)

  def cardinality(i: Int): Int = z(i)(3).toInt

  def backingArray: Array[Array[Double]] = z

  override def iterator: Iterator[Hierarchy.Node] =
    z.iterator.zipWithIndex.map((arr, i) => Hierarchy.nodeFromArray(i, arr))

  override def length: Int = z.length

  override def apply(i: Int): Hierarchy.Node = Hierarchy.nodeFromArray(i, z(i))

  override def toString(): String = {
    val s = StringBuilder("Hierarchy:\n    C1 C2 SIZE DISTANCE\n")

    for i <- z.indices.take(9) do
      val node = this (i)
      s.append("%02d  %2d %2d %4d %8.6f\n".formatted(i, node.cId1, node.cId2, node.cardinality, node.distance))

    if z.length > 10 then
      s.append("    ...\n")
      val node = this (z.length - 1)
      s.append("%02d  %2d %2d %4d %8.6f\n".formatted(node.idx, node.cId1, node.cId2, node.cardinality, node.distance))
    s.result
  }
}
