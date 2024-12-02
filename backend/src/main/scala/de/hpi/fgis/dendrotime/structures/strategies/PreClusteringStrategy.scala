package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist

import java.io.File
import scala.collection.mutable
import scala.util.Using

object PreClusteringStrategy {
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

  object EmptyGen extends WorkGenerator[Int] {

    override def sizeIds: Int = 0

    override def sizeTuples: Int = 0

    override def index: Int = 0

    override def next(): (Int, Int) = throw new NoSuchElementException("EmptyGen has no work")

    override def hasNext: Boolean = false
  }

  private enum State {
    case IntraCluster, Medoids, InterCluster
  }
}

/**
 * Takes a pre-clustering as input, then generates the following TS pairs:
 * 1. All pairs of TSs within the same cluster (one pre-cluster after the other).
 * 2. Then computes the precluster medoids and generates all pairs of precluster medoids.
 * 3. All pairs of TSs between two different clusters (one pre-cluster after the other). The order of the clusters is
 * determined by the medoid distance between the clusters (from close to far away).
 */
class PreClusteringStrategy(ids: Array[Int],
                            preLabels: Array[Int],
                            wDists: PDist
                           ) extends WorkGenerator[Int] {

  import PreClusteringStrategy.*

  //  println("INITIALIZAING PreClusteringStrategy")
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
  private val debugMessages = mutable.ArrayBuffer.empty[(Int, String, String)]

  override def sizeIds: Int = n

  override def sizeTuples: Int = n * (n - 1) / 2

  override def index: Int = count

  override def hasNext: Boolean = count < sizeTuples

  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"PreClusteringStrategy has no (more) work {i=$iCluster/$sizeTuples}"
      )

    val nextPair = state match {
      case State.IntraCluster =>
        nextIntraClusterPair()
      case State.Medoids =>
        nextMedoidPair()
      case State.InterCluster =>
        nextInterClusterPair()
    }
    count += 1
    nextPair
  }

  def getPreClustersForMedoids(medoid1: Int, medoid2: Int): Option[(Array[Int], Array[Int])] = {
    if state != State.Medoids then
      None
    else
      val cluster1 = preClusters.find { case (_, ids) => ids.contains(medoid1) }
      val cluster2 = preClusters.find { case (_, ids) => ids.contains(medoid2) }
      cluster1.flatMap(x => cluster2.map(y => x._2 -> y._2))
  }

  def storeDebugMessages(debugFile: File): Unit = {
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

  private def nextInterClusterGen: InterClusterGen = {
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
    new InterClusterGen(c1, c2, m1, m2)
  }

  private def switchState(newState: State): Unit = {
    newState match {
      case State.IntraCluster =>
        throw new IllegalArgumentException("Cannot switch to IntraCluster state because it is the initial state")

      case State.Medoids =>
        //        println("SWITCHING to medoids state")
        debugMessages.addOne((count, "STATE", "Finished intra-cluster / start medoids"))
        iCluster = 0
        jCluster = 1
        currentIntraClusterGen = EmptyGen
        currentInterClusterGen = EmptyGen

      case State.InterCluster =>
        //        println("SWITCHING to inter cluster state")
        val queue = preClusters.keys.toSeq.combinations(2).map(pair => (pair(0), pair(1))).toArray
        queue.sortInPlaceBy((i, j) => wDists.apply(preClusterMedoids(i), preClusterMedoids(j)))
        interClusterQueue = Some(queue)

        debugMessages.addOne((count, "STATE", "Finished medoids / start inter-cluster"))
        iCluster = 0
        jCluster = 0
        currentIntraClusterGen = EmptyGen
        currentInterClusterGen = nextInterClusterGen
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
          count -= 1
          next()
      }
    else
      currentIntraClusterGen.next()
  }

  private def nextMedoidPair(): (Int, Int) = {
    if iCluster >= preClusterMedoids.length - 1 && jCluster >= preClusterMedoids.length then
      switchState(State.InterCluster)
      count -= 1
      next()

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

  private def nextInterClusterPair(): (Int, Int) = {
    if !currentInterClusterGen.hasNext then
      debugMessages.addOne((count, "INTER", f"Finished inter-cluster $iCluster -> $jCluster"))
      currentInterClusterGen = nextInterClusterGen

    currentInterClusterGen.next()
  }

  private def computeClusterMedoid(clusterId: Int): Unit = {
    val ids = preClusters(clusterId)
    var currentMedoid = ids.head
    var currentMinDist = Double.MaxValue
    var i = 0
    while i < ids.length do
      val dist = ids.withFilter(_ != ids(i)).map(id => wDists(ids(i), id)).sum
      if dist < currentMinDist then
        currentMedoid = ids(i)
        currentMinDist = dist
      i += 1
    preClusterMedoids(clusterId) = currentMedoid
  }
}
