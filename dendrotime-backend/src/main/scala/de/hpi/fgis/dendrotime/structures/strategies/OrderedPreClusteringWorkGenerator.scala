package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist

import java.io.File
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.math.Ordered.orderingToOrdered
import scala.reflect.ClassTag
import scala.util.Using


object OrderedPreClusteringWorkGenerator {
  class IntraClusterGen[T : Numeric](clusterIds: Array[T]) extends WorkGenerator[T] with FCFSMixin[T] {
    override protected val tsIds: IndexedSeq[T] = ArraySeq.unsafeWrapArray(clusterIds)
  }

  class PreClusterIntraClusterGen(preClusters: Array[Array[Int]], n: Int) extends WorkGenerator[Int] {
    private var iCluster = 0
    private var count = 0
    private var currentIntraClusterGen = new IntraClusterGen[Int](nextPreCluster)

    override def sizeIds: Int = n

    override def sizeTuples: Int = preClusters.map{ cluster =>
      val n = cluster.length
      if n == 1 then 0
      else n * (n-1) / 2
    }.sum

    override def index: Int = count

    override def hasNext: Boolean = index < sizeTuples

    override def next(): (Int, Int) = {
      if !hasNext then
        throw new NoSuchElementException(s"PreClusterIntraClusterGen has no (more) work {i=$index/$sizeTuples}")

      if !currentIntraClusterGen.hasNext then
        val clusterIds = nextPreCluster
        currentIntraClusterGen = new IntraClusterGen(clusterIds)
      val result = currentIntraClusterGen.next()
      count += 1
      result
    }

    private def nextPreCluster: Array[Int] = {
      var preCluster = preClusters(iCluster)
      while preCluster.length < 2 && iCluster < preClusters.length - 1 do
        iCluster += 1
        preCluster = preClusters(iCluster)
      if preCluster.length < 2 || iCluster >= preClusters.length then
        throw new NoSuchElementException("PreClusterIntraClusterGen could not find a valid cluster")
      else
        //        println(s"  intra: selecting next cluster $iCluster (${preCluster.mkString(", ")})")
        iCluster += 1
        preCluster
    }
  }

  class PreClusterMedoidPairGenerator(preClusterMedoids: Array[Int]) extends WorkGenerator[Int] with FCFSMixin[Int] {
    override protected val tsIds: IndexedSeq[Int] = ArraySeq.unsafeWrapArray(preClusterMedoids)
  }

  class InterClusterGen[T : Numeric](cluster1Ids: Array[T], cluster2Ids: Array[T], medoid1: T, medoid2: T) extends WorkGenerator[T] {
    private var i = 0
    private var j = 0
    private var count = 0

    override def sizeIds: Int = cluster1Ids.length + cluster2Ids.length

    override def sizeTuples: Int = cluster1Ids.length * cluster2Ids.length - 1

    override def index: Int = count

    override def hasNext: Boolean = index < sizeTuples

    override def next(): (T, T) = {
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

  class PreClusterInterClusterGen(interClusterQueue: Array[(Int, Int)],
                                  preClusters: Array[Array[Int]],
                                  preClusterMedoids: Array[Int]) extends WorkGenerator[Int] {

    private val cleanedQueue = interClusterQueue.filter { case (i, j) =>
      preClusters(i).length > 1 || preClusters(j).length > 1
    }
    private val totalPairs = cleanedQueue.map{ case (i, j) =>
      preClusters(i).length * preClusters(j).length - 1
    }.sum
    private var iCluster = 0
    private var count = 0
    private var currentInterClusterGen = nextInterClusterGen

    override def sizeIds: Int = -1

    override def sizeTuples: Int = totalPairs

    override def index: Int = count

    override def hasNext: Boolean = count < totalPairs

    override def next(): (Int, Int) = {
      if !currentInterClusterGen.hasNext then
        currentInterClusterGen = nextInterClusterGen

      count += 1
      currentInterClusterGen.next()
    }

    private def nextInterClusterGen: InterClusterGen[Int] = {
      val (i, j) = cleanedQueue(iCluster)
      val c1 = preClusters(i)
      val c2 = preClusters(j)
      val m1 = preClusterMedoids(i)
      val m2 = preClusterMedoids(j)
      iCluster += 1
      new InterClusterGen(c1, c2, m1, m2)
    }
  }

  enum State {
    case IntraCluster, Medoids, InterCluster
  }

  def createInterClusterQueue(preClusters: Array[Array[Int]], preClusterMedoids: Array[Int], wDists: PDist): Array[(Int, Int)] = {
    val queue = preClusters.indices.combinations(2).map(pair => (pair(0), pair(1))).toArray
    queue.sortInPlaceBy((i, j) => wDists(preClusterMedoids(i), preClusterMedoids(j)))
    queue
  }

  def computePreClusterMedoid(ids: Array[Int], wDists: PDist): Int = {
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
    currentMedoid
  }

  def apply[T : Numeric : ClassTag](
                                     mapping: Map[T, Int],
                                     preClusters: Array[Array[Int]],
                                     wDists: PDist
                                   ): OrderedPreClusteringWorkGenerator[T] =
    new OrderedPreClusteringWorkGenerator[T](mapping, preClusters, wDists)
}

/**
 * Takes a pre-clustering as input, then generates the following TS pairs:
 * 1. All pairs of TSs within the same cluster (one pre-cluster after the other).
 * 2. Then computes the precluster medoids and generates all pairs of precluster medoids.
 * 3. All pairs of TSs between two different clusters (one pre-cluster after the other). The order of the clusters is
 * determined by the medoid distance between the clusters (from close to far away).
 */
class OrderedPreClusteringWorkGenerator[T: Numeric : ClassTag](
                                                                mapping: Map[T, Int],
                                                                preClusters: Array[Array[Int]],
                                                                wDists: PDist
                                                              ) extends PreClusteringWorkGenerator[T] {

  import OrderedPreClusteringWorkGenerator.*

  private val debugMessages = mutable.ArrayBuffer.empty[(Int, String, String)]
  private val reverseMapping: Map[Int, T] = mapping.map(_.swap)
  private val n = mapping.size
  private val preClusterMedoids = Array.fill[Int](preClusters.length){-1}
  { // initialize the singleton cluster medoids
    var i = 0
    while i < preClusters.length do
      if preClusters(i).length < 2 then
        preClusterMedoids(i) = preClusters(i).head
      i += 1
  }
//  println("INITIALIZAING OrderedPreClusteringWorkGenerator")
//  println(s" Time series: $n")
//  println(s" PreClusters (${preClusters.length}):\n" +
//    preClusters.zipWithIndex.map{ case (ids, label) => s"  $label: ${ids.mkString(", ")}"}.mkString("\n")
//  )
//  println("SWITCHING to intra cluster state")
  private var count = 0
  private var state = State.IntraCluster
  private var currentGen: WorkGenerator[Int] = {
    val gen = new PreClusterIntraClusterGen(preClusters, n)
    if gen.hasNext then
      gen
    else
      nextState
  }

  override def sizeIds: Int = n

  override def sizeTuples: Int = n * (n - 1) / 2

  override def index: Int = count

  override def hasNext: Boolean = count < sizeTuples

  override def next(): (T, T) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"OrderedPreClusteringWorkGenerator has no (more) work {i=$index/$sizeTuples}"
      )

    while !currentGen.hasNext do
      currentGen = nextState

    val nextPair = currentGen.next()
    count += 1
    val result = (reverseMapping(nextPair._1), reverseMapping(nextPair._2))
    if result._2 < result._1 then
      result.swap
    else
      result
  }

  override def getPreClustersForMedoids(medoid1: T, medoid2: T): Option[(Array[T], Array[T], T)] = {
    if state != State.Medoids then
      None
    else
      val cluster1 = preClusters.find { ids => ids.contains(mapping(medoid1)) }
      val cluster2 = preClusters.find { ids => ids.contains(mapping(medoid2)) }
      cluster1.flatMap(x => cluster2.map(y => (x.map(reverseMapping(_)), y.map(reverseMapping(_)), 1.asInstanceOf[T])))
  }

  override def storeDebugMessages(debugFile: File): Unit = {
    Using.resource(new java.io.PrintWriter(debugFile)) { writer =>
      writer.println("index,type,message")
      debugMessages.foreach { case (i, t, msg) =>
        writer.println(f"$i,$t,$msg")
      }
    }
  }

  def setMedoid(id: Int, medoid: T): Unit = {
    preClusterMedoids(id) = mapping(medoid)
  }

  private def nextState: WorkGenerator[Int] = {
    state match {
      case State.IntraCluster =>
        state = State.Medoids
        //        println(s"SWITCHING to medoids state ($count/$sizeTuples)")
        for i <- preClusters.indices if preClusterMedoids(i) == -1 do
          preClusterMedoids(i) = computePreClusterMedoid(preClusters(i), wDists)
        debugMessages.addOne((count, "STATE", "Finished intra-cluster / start medoids"))
        new PreClusterMedoidPairGenerator(preClusterMedoids)

      case State.Medoids =>
        state = State.InterCluster
        //        println(s"SWITCHING to inter cluster state  ($count/$sizeTuples)")
        debugMessages.addOne((count, "STATE", "Finished medoids / start inter-cluster"))
        val queue = createInterClusterQueue(preClusters, preClusterMedoids, wDists)
        new PreClusterInterClusterGen(queue, preClusters, preClusterMedoids)

      case State.InterCluster =>
        throw new IllegalStateException(s"There is no other state after $state! Generator should have been finished ($count/$sizeTuples).")
    }
  }
}
