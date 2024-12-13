import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, Linkage, computeHierarchy}
import de.hpi.fgis.dendrotime.structures.strategies.*

import java.io.File
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try, Using}

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

object AdvancedPreClusteringStrategy {
  class IntraClusterGen(clusterIds: Array[Int]) extends WorkGenerator[Int] with FCFSMixin[Int] {
    override protected val tsIds: IndexedSeq[Int] = clusterIds
  }

  class InterClusterGen(cluster1Ids: Array[Int], cluster2Ids: Array[Int], medoid1: Int, medoid2: Int) extends WorkGenerator[Int] {
    private var i = 0
    private var j = 0
    private var count = 0

    override def sizeIds: Int = cluster1Ids.length + cluster2Ids.length

    override def sizeTuples: Int = cluster1Ids.length * cluster2Ids.length - 1

    override def index: Int = count

    override def hasNext: Boolean = index < sizeTuples

    override def next(): (Int, Int) = {
      if !hasNext then
        throw new NoSuchElementException(s"InterClusterGen has no (more) work {i=$index/$sizeTuples}")

      var result = (cluster1Ids(i), cluster2Ids(j))
      if result._1 == medoid1 && result._2 == medoid2 then
        inc()
        next()
      else
        if result._2 < result._1 then
          result = result.swap

        inc()
        count += 1
        result
    }

    private def inc(): Unit = {
      j += 1
      if j == cluster2Ids.length then
        i += 1
        j = 0
    }
  }

  class InterestingInterClusterGen(cluster1Ids: Array[Int], cluster2Ids: Array[Int],
                                   medoid1: Int, medoid2: Int,
                                   wDists: PDist) extends WorkGenerator[Int] {
    private var i = 0 //cluster1Ids.length
    private var j = 0 //cluster2Ids.length
    private var interClusterGen: Option[InterClusterGen] = None
    private var count = 0

    override def sizeIds: Int = cluster1Ids.length + cluster2Ids.length

    override def sizeTuples: Int = cluster1Ids.length - 1 + cluster2Ids.length - 1 + cluster1Ids.length * cluster2Ids.length - 1

    override def index: Int = count

    override def hasNext: Boolean = index < sizeTuples && (interClusterGen.isEmpty || interClusterGen.get.hasNext)

    override def next(): (Int, Int) = {
      if !hasNext then
        throw new NoSuchElementException(s"InterestingInterClusterGen has no (more) work {i=$index/$sizeTuples}")

      if i < cluster1Ids.length then
        val nextId1 = cluster1Ids(i)
        if nextId1 == medoid1 then
          i += 1
          next()
        else
          val result =
            if nextId1 > medoid2 then (medoid2, nextId1)
            else (nextId1, medoid2)
          i += 1
          count += 1
          result

      else if j < cluster2Ids.length then
        val nextId2 = cluster2Ids(j)
        if nextId2 == medoid2 then
          j += 1
          next()
        else
          val result =
            if nextId2 > medoid1 then (medoid1, nextId2)
            else (nextId2, medoid1)
          j += 1
          count += 1
          result

      else if interClusterGen.isEmpty then
//        val dists1 = wDists.subPDistOf(cluster1Ids)
//        val mean1Dist = dists1.sum / cluster1Ids.length
//        val std1Dist = Math.sqrt(dists1.map(d => Math.pow(d - mean1Dist, 2)).sum / cluster1Ids.length)
        val interesting1 = cluster1Ids.filter{ id =>
          val localMedoidDist = wDists(medoid1, id)
          val otherMedoidDist = wDists(medoid2, id)
          otherMedoidDist < localMedoidDist * 2
//          localMedoidDist > mean1Dist
        }
//        val dists2 = wDists.subPDistOf(cluster2Ids)
//        val mean2Dist = dists2.sum / cluster2Ids.length
//        val std2Dist = Math.sqrt(dists2.map(d => Math.pow(d - mean2Dist, 2)).sum / cluster2Ids.length)
        val interesting2 = cluster2Ids.filter{ id =>
          val localMedoidDist = wDists(medoid2, id)
          val otherMedoidDist = wDists(medoid1, id)
          otherMedoidDist < localMedoidDist * 2
//          localMedoidDist > mean2Dist
        }
        // just compare the TSs in the shell of the clusters (max distance to medoid within their cluster)
        // does not work at all!
//        val percentile1 = cluster1Ids.map(id => wDists(medoid1, id)).sorted.apply(Math.floorDiv(cluster1Ids.length*6, 10))
//        val percentile2 = cluster2Ids.map(id => wDists(medoid2, id)).sorted.apply(Math.floorDiv(cluster2Ids.length*6, 10))
//        val interesting1 = cluster1Ids.filter{ id =>
//          id == medoid1 || wDists(medoid1, id) > percentile1
//        }
//        val interesting2 = cluster2Ids.filter{ id =>
//          id == medoid2 || wDists(medoid2, id) > percentile2
//        }

        // special case that converges to the simple strategy:
//        val interesting1 = cluster1Ids
//        val interesting2 = cluster2Ids
        println(s" interesting pairs: ${interesting1.length} x ${interesting2.length}")
        val gen = new InterClusterGen(interesting1, interesting2, medoid1, medoid2)
        interClusterGen = Some(gen)
        count += 1
        gen.next()

      else
        val result = interClusterGen.get.next()
        count += 1
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

  private enum State {
    case IntraCluster, Medoids, InterestingInterCluster, InterCluster
  }

  extension (internal: mutable.BitSet) {
    private def contains(pair: (Int, Int), n: Int): Boolean = internal.contains(PDist.index(pair._1, pair._2, n))
    private def +=(pair: (Int, Int), n: Int): Unit = internal += PDist.index(pair._1, pair._2, n)
  }
}

/**
 * Takes a pre-clustering as input, then generates the following TS pairs:
 * 1. All pairs of TSs within the same cluster (one pre-cluster after the other).
 * 2. Then computes the precluster medoids and generates all pairs of precluster medoids.
 * 3. Sort precluster medoids ascending
 * 4. Generate interesting subsets for each pair of preclusters:
 *    a. generate all pairs between TSs from one precluster to the other medoid
 *    b. compute TSs that have a shorter distance to the other medoid (they are pot. in the wrong precluster)
 *    c. generate all pairs of wrongly preclustered TSs
 * 3. All remaining pairs of TSs between two different clusters (one pre-cluster after the other).
 */
class AdvancedPreClusteringStrategy(ids: Array[Int],
                            preLabels: Array[Int],
                            wDists: PDist
                           ) extends PreClusteringWorkGenerator[Int] {

  import AdvancedPreClusteringStrategy.*

  //  println("INITIALIZAING AdvancedPreClusteringStrategy")
  private val preClusters = ids.groupBy(preLabels.apply)
  //  println(s" Time series: ${ids.length}")
  //  println(s" PreClusters (${preClusters.size}):\n" +
  //    preClusters.toSeq.sortBy(_._1).map{ case (label, ids) => s"  $label: ${ids.mkString(", ")}"}.mkString("\n")
  //  )
  private val preClusterMedoids = Array.ofDim[Int](preClusters.size)
  { // initialize the singleton cluster medoids
    var i = 0
    while i < preClusters.size do
      if preClusters(i).length < 2 then
        preClusterMedoids(i) = preClusters(i).head
      i += 1
  }
  //  println("SWITCHING to intra cluster state")
  private val n = ids.length
  private var count = 0
  private var state = State.IntraCluster
  private var iCluster = 0
  private var jCluster = iCluster
  private var currentIntraClusterGen: WorkGenerator[Int] = nextPreCluster match {
    case Some(clusterIds) =>
      new IntraClusterGen(clusterIds)
    case None =>
      switchState(State.Medoids)
      EmptyGen
  }
  private var currentInterClusterGen: WorkGenerator[Int] = EmptyGen
  private var interClusterQueue: Option[Array[(Int, Int)]] = None
  private val processed = mutable.BitSet.empty
  processed.sizeHint(sizeTuples)
  private val debugMessages = mutable.ArrayBuffer.empty[(Int, String, String)]

  override def sizeIds: Int = n

  override def sizeTuples: Int = n * (n - 1) / 2

  override def index: Int = count

  override def hasNext: Boolean = count < sizeTuples

  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"AdvancedPreClusteringStrategy has no (more) work {i=$iCluster/$sizeTuples}"
      )

    var nextPair: (Int, Int) = null
    while
     nextPair = state match {
        case State.IntraCluster =>
          nextIntraClusterPair()
        case State.Medoids =>
          nextMedoidPair()
        case State.InterestingInterCluster =>
          nextInterestingInterClusterPair()
        case State.InterCluster =>
          nextInterClusterPair()
      }
      processed.contains(nextPair, n)
    do ()
    count += 1
    processed += (nextPair, n)
    nextPair
  }

  override def getPreClustersForMedoids(medoid1: Int, medoid2: Int): Option[(Array[Int], Array[Int], Int)] = {
    if state != State.Medoids then
      None
    else
      val cluster1 = preClusters.find { case (_, ids) => ids.contains(medoid1) }
      val cluster2 = preClusters.find { case (_, ids) => ids.contains(medoid2) }
      cluster1.flatMap(x => cluster2.map(y => (x._2, y._2, -1)))
  }

  override def storeDebugMessages(debugFile: File): Unit = {
    Using.resource(new java.io.PrintWriter(debugFile)) { writer =>
      writer.println("index,type,message")
      debugMessages.foreach { case (i, t, msg) =>
        writer.println(f"$i,$t,$msg")
      }
    }
  }

  private def nextPreCluster: Option[Array[Int]] = {
    if iCluster >= preClusters.size then
      None
    else
      var preCluster = preClusters(iCluster)
      while preCluster.length < 2 && iCluster < preClusters.size - 1 do
        iCluster += 1
        preCluster = preClusters(iCluster)
      if preCluster.length < 2 || iCluster >= preClusters.size then
        None
      else
        iCluster += 1
        Some(preCluster)
  }

  private def nextInterClusterGen(f: (Array[Int], Array[Int], Int, Int) => WorkGenerator[Int]): WorkGenerator[Int] = {
    val queue = interClusterQueue.get
    var ij = queue(iCluster)
    var c1 = preClusters(ij._1)
    var c2 = preClusters(ij._2)
    while c1.length == 1 && c2.length == 1 && iCluster < queue.length do
      //      println(s" skipping singleton clusters $iCluster and $jCluster")
      iCluster += 1
      ij = queue(iCluster)
      c1 = preClusters(ij._1)
      c2 = preClusters(ij._2)
    val m1 = preClusterMedoids(ij._1)
    val m2 = preClusterMedoids(ij._2)

    //    println(s" NEXT inter: $iCluster -> $jCluster (${c1.length} x ${c2.length}) (${c1.mkString(", ")}) (${c2.mkString(", ")})")
    iCluster += 1
    f(c1, c2, m1, m2)
  }

  private def switchState(newState: State): Unit = {
    newState match {
      case State.IntraCluster =>
        throw new IllegalArgumentException("Cannot switch to IntraCluster state because it is the initial state")

      case State.Medoids =>
        println(s"SWITCHING to medoids state ($count/$sizeTuples)")
        debugMessages.addOne((count, "STATE", "Finished intra-cluster / start medoids"))
        iCluster = 0
        jCluster = 1
        currentIntraClusterGen = EmptyGen
        currentInterClusterGen = EmptyGen

      case State.InterestingInterCluster =>
        println(s"SWITCHING to interesting inter cluster state ($count/$sizeTuples)")
        debugMessages.addOne((count, "STATE", "Finished medoids / start interesting inter-cluster"))
        val queue = preClusters.keys.toSeq.combinations(2).map(pair => (pair(0), pair(1))).toArray
        val stds = preClusters.values.map{ ids =>
          val dists = wDists.subPDistOf(ids)
          val mean = dists.sum / ids.length
          Math.sqrt(dists.map(d => Math.pow(d - mean, 2)).sum / ids.length)
        }.toArray
        queue.sortInPlaceBy((i, j) => - stds(i) - stds(j))
//        queue.sortInPlaceBy((i, j) => -preClusters(i).length - preClusters(j).length)
//        queue.sortInPlaceBy((i, j) => wDists.apply(preClusterMedoids(i), preClusterMedoids(j)))
//        queue.sortInPlaceBy{(i, j) =>
//          val d = wDists.apply(preClusterMedoids(i), preClusterMedoids(j))
//          val std = (stds(i) + stds(j))/2
//          d + 2 * std
//        }
        interClusterQueue = Some(queue)
        iCluster = 0
        jCluster = 0
        currentIntraClusterGen = EmptyGen
        currentInterClusterGen = nextInterClusterGen(new InterestingInterClusterGen(_, _, _, _, wDists))

      case State.InterCluster =>
        println(s"SWITCHING to inter cluster state  ($count/$sizeTuples)")
        debugMessages.addOne((count, "STATE", "Finished interesting inter-cluster / start inter-cluster"))
        val queue = preClusters.keys.toSeq.combinations(2).map(pair => (pair(0), pair(1))).toArray
        queue.sortInPlaceBy((i, j) => wDists.apply(preClusterMedoids(i), preClusterMedoids(j)))
        interClusterQueue = Some(queue)
        iCluster = 0
        jCluster = 0
        currentIntraClusterGen = EmptyGen
        currentInterClusterGen = nextInterClusterGen(new InterClusterGen(_, _, _, _))
    }
    state = newState
  }

  private def nextIntraClusterPair(): (Int, Int) = {
    if !currentIntraClusterGen.hasNext then
      debugMessages.addOne((count, "INTRA", f"Finished intra-cluster ${iCluster - 1}"))
      computeClusterMedoid(iCluster - 1)
      //      println(s" ${iCluster - 1}: depleted, computed medoid for (${preClusters(iCluster - 1).mkString(", ")}): ${preClusterMedoids(iCluster - 1)}")
      nextPreCluster match {
        case Some(clusterIds) =>
          currentIntraClusterGen = new IntraClusterGen(clusterIds)
          currentIntraClusterGen.next()
        case None =>
          switchState(State.Medoids)
          nextMedoidPair()
      }
    else
      currentIntraClusterGen.next()
  }

  private def nextMedoidPair(): (Int, Int) = {
    if iCluster >= preClusterMedoids.length - 1 && jCluster >= preClusterMedoids.length then
      switchState(State.InterestingInterCluster)
      nextInterestingInterClusterPair()

    else
      var result = (preClusterMedoids(iCluster), preClusterMedoids(jCluster))
      if result._2 < result._1 then
        result = result.swap

      jCluster += 1
      if jCluster == preClusterMedoids.length then
        iCluster += 1
        jCluster = iCluster + 1

      result
  }

  @tailrec
  private def nextInterestingInterClusterPair(): (Int, Int) = {
    if !currentInterClusterGen.hasNext then
      println(s"Finished interesting inter-cluster ${iCluster - 1} -> ${interClusterQueue.get.apply(iCluster-1)}")
      debugMessages.addOne((count, "INTER", f"Finished interesting inter-cluster ${iCluster - 1}"))
      if iCluster >= interClusterQueue.get.length then
        switchState(State.InterCluster)
      else
        currentInterClusterGen = nextInterClusterGen(new InterestingInterClusterGen(_, _, _, _, wDists))

    Try {
      currentInterClusterGen.next()
    } match {
      case Success(pair) =>
        pair
      case Failure(_) =>
        nextInterestingInterClusterPair()
    }
  }

  private def nextInterClusterPair(): (Int, Int) = {
    if !currentInterClusterGen.hasNext then
      println(s"Finished inter-cluster ${iCluster - 1} -> ${interClusterQueue.get.apply(iCluster-1)} ($count/$sizeTuples)")
      debugMessages.addOne((count, "INTER", f"Finished inter-cluster ${iCluster - 1}"))
      currentInterClusterGen = nextInterClusterGen(new InterClusterGen(_, _, _, _))

    currentInterClusterGen.next()
  }

  private def computeClusterMedoid(clusterId: Int): Unit = {
    val ids = preClusters(clusterId)
    var currentMedoid = ids.head
    var currentMinDist = ids.tail.map(id => wDists(currentMedoid, id)).sum
    var i = 1
    while i < ids.length do
      val dist = ids.withFilter(_ != ids(i)).map(id => wDists(ids(i), id)).sum
      if dist < currentMinDist then
        currentMedoid = ids(i)
        currentMinDist = dist
      i += 1
    //    println(s"MEDOID: $clusterId -> $currentMedoid")
    preClusterMedoids(clusterId) = currentMedoid
  }
}
