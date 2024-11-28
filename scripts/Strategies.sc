import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.structures.strategies.{FCFSMixin, FIFOMixin, TsErrorMixin, WorkGenerator}

class GtBestTsFCFSOrderStrategy(tsOrder: Array[Int]) extends WorkGenerator[Int] with FCFSMixin[Int] {
  override protected val tsIds: IndexedSeq[Int] = tsOrder
}

class GtBestTsFIFOOrderStrategy(tsOrder: Array[Int]) extends WorkGenerator[Int] with FIFOMixin[Int] {
  override protected val tsIds: IndexedSeq[Int] = tsOrder
}

class GtLargestPairErrorStrategy(aDists: PDist, fDists: PDist, ids: Array[Int]) extends WorkGenerator[Int] {
  val data = (
    for {
      i <- 0 until ids.length - 1
      j <- i + 1 until ids.length
      idLeft = ids(i)
      idRight = ids(j)
      approx = aDists(idLeft, idRight)
      full = fDists(idLeft, idRight)
      error = Math.abs(approx - full)
    } yield (error, idLeft, idRight)
    ).sortBy(_._1)
    .map(t => (t._2, t._3))
    .reverse
    .toArray
  var i = 0

  override def sizeIds: Int = aDists.n
  override def sizeTuples: Int = aDists.size
  override def index: Int = i
  override def hasNext: Boolean = i < data.length
  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"GtLargestPairErrorStrategy has no (more) work {i=$i, data.length=${data.length}}"
      )
    else
      val result = data(i)
      i += 1
      result
  }
}

class GtLargestTsErrorStrategy(aDists: PDist, fDists: PDist, ids: Array[Int])
  extends WorkGenerator[Int] with TsErrorMixin(aDists.n, aDists.length) {

  override protected val errors: scala.collection.IndexedSeq[Double] = createErrorArray()
  private val tsIds = ids.sortBy(id => -errors(id))
  private var i = 0

  override def sizeIds: Int = aDists.n
  override def sizeTuples: Int = aDists.size
  override def index: Int = i
  override def hasNext: Boolean = i < aDists.length
  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"GtLargestTsErrorStrategy has no (more) work {i=$i/$sizeTuples}"
      )

    val result = nextLargestErrorPair(tsIds)
    i += 1
    if result._2 < result._1 then
      result.swap
    else
      result
  }

  private def createErrorArray(): Array[Double] = {
    val n = ids.length
    val errors = Array.ofDim[Double](n)
    var i = 0
    var j = 1
    while i < n - 1 && j < n do
      val idLeft = ids(i)
      val idRight = ids(j)
      val approx = aDists(idLeft, idRight)
      val full = fDists(idLeft, idRight)
      errors(i) += Math.abs(approx - full)
      errors(j) += Math.abs(approx - full)
      j += 1
      if j == n then
        i += 1
        j = i + 1

    i = 0
    while i < n do
      errors(i) /= n
      i += 1
    errors
  }
}

object PreClusteringStrategy {
  class IntraClusterGen(clusterIds: Array[Int]) extends WorkGenerator[Int] with FCFSMixin[Int] {
    override protected val tsIds: IndexedSeq[Int] = clusterIds
  }

  class InterClusterGen(cluster1Ids: Array[Int], cluster2Ids: Array[Int]) extends WorkGenerator[Int] {
    private var i = 0
    private var j = 0

    override def sizeIds: Int = cluster1Ids.length + cluster2Ids.length

    override def sizeTuples: Int = cluster1Ids.length * cluster2Ids.length

    override def index: Int = i * j

    override def hasNext: Boolean = i < cluster1Ids.length && j < cluster2Ids.length

    override def next(): (Int, Int) = {
      if !hasNext then
        throw new NoSuchElementException(s"InterClusterGen has no (more) work {i=$index/$sizeTuples}")

      var result = (cluster1Ids(i), cluster2Ids(j))
      if result._2 < result._1 then
        result = result.swap

      j += 1
      if j == cluster2Ids.length then
        i += 1
        j = 0
      result
    }
  }

  object EmptyGen extends WorkGenerator[Int] {

    override def sizeIds: Int = 0

    override def sizeTuples: Int = 0

    override def index: Int = 0

    override def next(): (Int, Int) = throw new NoSuchElementException("EmptyGen has no work")

    override def hasNext: Boolean = false
  }
}

/**
 * Takes a pre-clustering as input, then generates the following TS pairs:
 * 1. All pairs of TSs within the same cluster (one pre-cluster after the other)
 * 2. All pairs of TSs between two different clusters (one pre-cluster after the other). The order of the clusters is
 *    determined by the centroid distance between the clusters (from close to far away) !!not yet implemented!!.
 */
// FIXME: select inter-cluster pairs based on distance between their centroids!
class PreClusteringStrategy(ids: Array[Int], preLabels: Array[Int]) extends WorkGenerator[Int] {
  import PreClusteringStrategy.*

  val preClusters = ids.groupBy(preLabels.apply)
//  println(s"PreClusteringStrategy: ${preClusters.map{case (k, ids) => k -> ids.mkString(", ")}.mkString("\n")}")
//  println(s"Starting with ${preClusters(0).mkString(" ")}")
  private val n = ids.length
  private var iCluster = -1  // we first inc the counter before using it
  private var jCluster = 1
  private var currentIntraClusterGen: Option[WorkGenerator[Int]] = nextPreCluster.map(new IntraClusterGen(_))
  private var currentInterClusterGen: WorkGenerator[Int] = EmptyGen

  override def sizeIds: Int = n
  override def sizeTuples: Int = n * (n-1) / 2
  override def index: Int = iCluster
  override def hasNext: Boolean = iCluster < preClusters.size && jCluster < preClusters.size || currentInterClusterGen.hasNext
  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"PreClusteringStrategy has no (more) work {i=$iCluster/$sizeTuples}"
      )

    currentIntraClusterGen match {
      case Some(gen) =>
        if !gen.hasNext then
//          println(s" $iCluster: depleted")
          nextPreCluster match {
            case Some(clusterIds) =>
              val newGen = new IntraClusterGen(clusterIds)
//              println(s"NEXT intra $iCluster: ${clusterIds.mkString(" ")}")
              currentIntraClusterGen = Some(newGen)
//              println(s" $iCluster: next intra")
              newGen.next()
            case None =>
              currentIntraClusterGen = None
              iCluster = 0
              jCluster = iCluster + 1
//              println(s"SWITCHING to inter: $iCluster -> $jCluster (${preClusters(iCluster).length} x ${preClusters(jCluster).length})")
              currentInterClusterGen = new InterClusterGen(preClusters(iCluster), preClusters(jCluster))
              jCluster += 1
              next()
          }
        else
//          println(s" $iCluster: next intra")
          gen.next()

      case None =>
        if !currentInterClusterGen.hasNext then
//          println(s"depleted, NEXT inter: $iCluster -> $jCluster (${preClusters(iCluster).length} x ${preClusters(jCluster).length})")
          currentInterClusterGen = new InterClusterGen(preClusters(iCluster), preClusters(jCluster))
//          println(s"   ${currentInterClusterGen.sizeTuples} pairs")
          jCluster += 1
          if jCluster == preClusters.size then
            iCluster += 1
            jCluster = iCluster + 1

//        println(s" ${iCluster -1} -> ${jCluster -1}: next inter")
        currentInterClusterGen.next()
    }
  }

  private def nextPreCluster: Option[Array[Int]] = {
    iCluster += 1
    if iCluster == preClusters.size then
      None
    else
      var preCluster = preClusters(iCluster)
      while preCluster.length < 2 && iCluster < preClusters.size do
        iCluster += 1
        preCluster = preClusters(iCluster)
      if iCluster == preClusters.size then
        None
      else
        Some(preClusters(iCluster))
  }
}