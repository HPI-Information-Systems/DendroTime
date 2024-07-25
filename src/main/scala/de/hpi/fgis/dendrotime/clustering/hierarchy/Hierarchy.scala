package de.hpi.fgis.dendrotime.clustering.hierarchy

object Hierarchy {
  /** A node in the hierarchy that merges two clusters at a certain distance. */
  final case class Node(idx: Int, elem1: Int, elem2: Int,
                        distance: Double,
                        cardinality: Int = 0
                       )

  private final def nodeFromArray(idx: Int, arr: Array[Double]): Node =
    Node(idx, arr(0).toInt, arr(1).toInt, arr(2), arr(3).toInt)

  def newBuilder(n: Int): HierarchyBuilder = new HierarchyBuilder(n)

  class HierarchyBuilder private[Hierarchy] (n: Int) {
    private val z = Array.ofDim[Double](n - 1, 4)
    private var i = 0
    
    def apply(i: Int): Node = nodeFromArray(i, z(i))
    
    def add(node: Node): Unit = add(node.elem1, node.elem2, node.distance, node.cardinality)
    
    def add(elem1: Int, elem2: Int, distance: Double, cardinality: Int = 0): Unit = {
      z(i) = Array(elem1, elem2, distance, cardinality)
      i += 1
    }
    
    def update(i: Int, node: Node): Unit = update(i, node.elem1, node.elem2, node.distance, node.cardinality)
    
    def update(i: Int, elem1: Int, elem2: Int, distance: Double, cardinality: Int = 0): Unit = {
      z(i) = Array(elem1, elem2, distance, cardinality)
    }

    def sort(): Unit = {
      z.sortInPlaceBy(_(2))
    }

//    def fillCardinalities(): Unit = ???

    def build(): Hierarchy = {
      Hierarchy(z)
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
case class Hierarchy private (z: Array[Array[Double]])
  extends Iterable[Hierarchy.Node] with IndexedSeq[Hierarchy.Node] {

  def elem1(i: Int): Int = z(i)(0).toInt

  def elem2(i: Int): Int = z(i)(1).toInt

  def distance(i: Int): Double = z(i)(2)

  def cardinality(i: Int): Int = z(i)(3).toInt

  override def iterator: Iterator[Hierarchy.Node] =
    z.iterator.zipWithIndex.map((arr, i) => Hierarchy.nodeFromArray(i, arr))

  override def length: Int = z.length

  override def apply(i: Int): Hierarchy.Node = Hierarchy.nodeFromArray(i, z(i))
}
