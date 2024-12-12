package de.hpi.fgis.dendrotime.structures.strategies

import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{CutTree, Hierarchy, Linkage, computeHierarchy}

import java.io.File
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Using

object RecursivePreClusteringStrategy {
  private class IntraPreClustersGen(preClusters: scala.collection.Map[Int, PreCluster]) extends WorkGenerator[Int] {
    private var count = 0
    private var iCluster = 0
    private var currentIntraClusterGen: WorkGenerator[Int] = nextPreCluster match {
      case Some(cluster) => cluster.gen
      case None => EmptyGen
    }

    override def sizeIds: Int = preClusters.values.map(_.size).sum

    override def sizeTuples: Int = preClusters.values.map(_.size).map(s => s * (s - 1) / 2).sum

    override def index: Int = count

    override def hasNext: Boolean = count < sizeTuples

    override def next(): (Int, Int) = {
      if !hasNext then
        throw new NoSuchElementException(
          s"IntraPreClustersGen has no (more) work {i=$iCluster/${preClusters.size}}"
        )
      if !currentIntraClusterGen.hasNext then
        nextPreCluster match {
          case Some(cluster) =>
            currentIntraClusterGen = cluster.gen
            count += 1
            currentIntraClusterGen.next()
          case None =>
            next()
        }
      else
        count += 1
        currentIntraClusterGen.next()
    }

    private def nextPreCluster: Option[PreCluster] = {
      var preCluster = preClusters(iCluster)
      while preCluster.size < 2 && iCluster < preClusters.size - 1 do
        iCluster += 1
        preCluster = preClusters(iCluster)
      if preCluster.size < 2 || iCluster >= preClusters.size then
        None
      else
        iCluster += 1
        Some(preCluster)
    }
  }

  private class IntraClusterGen(clusterIds: Array[Int]) extends WorkGenerator[Int] with FCFSMixin[Int] {
    override protected val tsIds: IndexedSeq[Int] = clusterIds
  }

  private object EmptyGen extends WorkGenerator[Int] {

    override def sizeIds: Int = 0

    override def sizeTuples: Int = 0

    override def index: Int = 0

    override def next(): (Int, Int) = throw new NoSuchElementException("EmptyGen has no work")

    override def hasNext: Boolean = false
  }

  private enum State {
    case IntraCluster, Medoids, SplitMedoids
  }

  extension (internal: mutable.BitSet) {
    private def contains(pair: (Int, Int), n: Int): Boolean = internal.contains(PDist.index(pair._1, pair._2, n))
    private def +=(pair: (Int, Int), n: Int): Unit = internal += PDist.index(pair._1, pair._2, n)
  }

  private sealed trait PreCluster {
    def id: Int

    def tsIds: Array[Int]

    def size: Int = tsIds.length

    def contains(elem: Int): Boolean = tsIds.contains(elem)

    def gen: IntraClusterGen = new IntraClusterGen(tsIds)

    def withMedoid(dists: PDist): PreClusterWithMedoid = {
      var currentMedoid = tsIds.head
      var currentMinDist = tsIds.tail.map(id => dists(currentMedoid, id)).sum
      var i = 1
      while i < tsIds.length do
        val dist = tsIds.withFilter(_ != tsIds(i)).map(id => dists(tsIds(i), id)).sum
        if dist < currentMinDist then
          currentMedoid = tsIds(i)
          currentMinDist = dist
        i += 1

      PreClusterWithMedoid(id, tsIds, currentMedoid)
    }
  }
  private case class InitialPreCluster(id: Int, tsIds: Array[Int]) extends PreCluster

  private case class PreClusterWithMedoid(id: Int, tsIds: Array[Int], medoid: Int) extends PreCluster {
    @transient
    private var _hierarchy: Option[Hierarchy] = None

    private def hierarchy(dists: PDist, linkage: Linkage) = _hierarchy match {
      case Some(value) => value
      case None =>
        val subDists = dists.subPDistOf(tsIds)
        val subHierarchy = computeHierarchy(subDists, linkage)
        _hierarchy = Some(subHierarchy)
        subHierarchy
    }

    override def withMedoid(dists: PDist): PreClusterWithMedoid = this

    def maxMergeDistance(dists: PDist, linkage: Linkage): Double = {
      if size < 2 then
        0.0
      else
        val h = hierarchy(dists: PDist, linkage: Linkage)
        h.distance(h.size - 1)
    }

    def split(dists: PDist, linkage: Linkage, maxId: Int): Seq[PreClusterWithMedoid] = {
      val splitN = 2
      if this.size < 2 then
        throw new IllegalArgumentException("Cannot split a cluster with less than 2 elements")
      else if this.size == 2 then
        val otherId = tsIds.find(_ != medoid).get
        Seq(copy(tsIds = Array(medoid)), copy(id = maxId, tsIds = Array(otherId), medoid = otherId))
      else
        val subHierarchy = hierarchy(dists, linkage)
        val labels = CutTree(subHierarchy, splitN)
        val uniqueLabels = labels.distinct
        val clusters = Array.fill(splitN)(mutable.ArrayBuilder.make[Int])
        val sumDists = Array.fill(splitN)(mutable.ArrayBuilder.make[Double])
        var i = 0
        while i < tsIds.length do
          var j = 0
          while j < splitN do
            if labels(i) == uniqueLabels(j) then
              sumDists(j) += tsIds.map(id => dists(tsIds(i), id)).sum
              clusters(j) += tsIds(i)
            j += 1
          i += 1

        val cluster1 = clusters(0).result()
        val cluster2 = clusters(1).result()
        val medoid1 = cluster1(sumDists(0).result().zipWithIndex.minBy(_._1)._2)
        val medoid2 = cluster2(sumDists(1).result().zipWithIndex.minBy(_._1)._2)
        if cluster1.contains(medoid) then
          if medoid1 != medoid then
            Seq(
              copy(tsIds = cluster1, medoid = medoid1),
              copy(id = maxId, tsIds = Array(medoid)),
              copy(id = maxId + 1, tsIds = cluster2, medoid = medoid2)
            )
          else
            Seq(copy(tsIds = cluster1, medoid = medoid1), copy(id = maxId, tsIds = cluster2, medoid = medoid2))
        else
          if medoid2 != medoid then
            Seq(
              copy(tsIds = cluster2, medoid = medoid2),
              copy(id = maxId, tsIds = Array(medoid)),
              copy(id = maxId + 1, tsIds = cluster1, medoid = medoid1)
            )
          else
            Seq(copy(tsIds = cluster2, medoid = medoid2), copy(id = maxId, tsIds = cluster1, medoid = medoid1))
    }
  }
}

/**
 * Takes a pre-clustering as input, then generates the following TS pairs:
 * 1. All pairs of TSs within the same cluster (one pre-cluster after the other).
 * 2. Then computes the precluster medoids and generates all pairs of precluster medoids (medoid distances are
 *    broadcasted to all inter-precluster pairs.
 * 3. Computes dendrograms for each precluster, selects precluster with the largest merge distance and splits it into
 *    two new preclusters. Then, computes the new precluster medoids and generates all pairs between the new medoids
 *    and the other precluster medoids. Repeats this step until all time series are in their own precluster and, thus,
 *    all pairs are generated.
 */
class RecursivePreClusteringStrategy(ids: Array[Int],
                                     preLabels: Array[Int],
                                     wDists: PDist,
                                     linkage: Linkage
                                    ) extends PreClusteringWorkGenerator[Int] {
  import RecursivePreClusteringStrategy.*

  println("INITIALIZING RecursivePreClusteringStrategy")
  private val initialPreClusters: Map[Int, PreCluster] =
    ids.groupBy(preLabels.apply).map{ case (id, tsIds) => id -> InitialPreCluster(id, tsIds) }
  private val preClusters: mutable.Map[Int, PreClusterWithMedoid] = mutable.Map.empty
  println(s" Time series: ${ids.length}")
  println(s" PreClusters (${initialPreClusters.size}):\n" +
    initialPreClusters.toSeq.sortBy(_._1).map{ case (label, c) => s"  $label: ${c.tsIds.mkString(", ")}"}.mkString("\n")
  )
  println("SWITCHING to intra cluster state")
  private val n = ids.length
  private var count = 0
  private var state = State.IntraCluster
  private var iCluster = 0
  private var jCluster = iCluster
  private var skipMedoid = -1
  private var activeClusterPair = (iCluster, jCluster)
  private val intraPreClustersGen = new IntraPreClustersGen(initialPreClusters)
  private val debugMessages = mutable.ArrayBuffer.empty[(Int, String, String)]
  private val processed = mutable.BitSet.empty
  processed.sizeHint(sizeTuples)

  override def sizeIds: Int = n
  override def sizeTuples: Int = n * (n-1) / 2
  override def index: Int = count
  override def hasNext: Boolean = count < sizeTuples
  override def next(): (Int, Int) = {
    if !hasNext then
      throw new NoSuchElementException(
        s"RecursivePreClusteringStrategy has no (more) work {i=$iCluster/$sizeTuples}"
      )

    val nextPair = state match {
      case State.IntraCluster =>
        nextIntraClusterPair()
      case State.Medoids =>
        nextMedoidPair()
      case State.SplitMedoids =>
        nextSplitMedoidPair()
    }
    count += 1
    processed += (nextPair, n)
    nextPair
  }

  override def getPreClustersForMedoids(medoid1: Int, medoid2: Int): Option[(Array[Int], Array[Int], Int)] = {
//    if state == State.Medoids || state == State.SplitMedoids then
    if state == State.Medoids then
      val (i, j) = activeClusterPair
      val cluster1 = preClusters(i)
      val cluster2 = preClusters(j)
      if cluster1.size < 2 && cluster2.size < 2 then
        None
      else if cluster1.medoid < cluster2.medoid then
        require(cluster1.medoid == medoid1, s"Cluster $i does not contain medoid $medoid1")
        require(cluster2.medoid == medoid2, s"Cluster $j does not contain medoid $medoid2")
        Some((cluster1.tsIds, cluster2.tsIds, skipMedoid))
      else
        require(cluster2.medoid == medoid1, s"Cluster $j does not contain medoid $medoid1")
        require(cluster1.medoid == medoid2, s"Cluster $i does not contain medoid $medoid1")
        Some((cluster2.tsIds, cluster1.tsIds, skipMedoid))
    else
      None
  }

  override def storeDebugMessages(debugFile: File): Unit = {
    Using.resource(new java.io.PrintWriter(debugFile)) { writer =>
      writer.println("index,type,message")
      debugMessages.foreach { case (i, t, msg) =>
        writer.println(f"$i,$t,$msg")
      }
    }
  }

  private def switchState(newState: State): Unit = {
    newState match {
      case State.IntraCluster =>
        throw new IllegalArgumentException("Cannot switch to IntraCluster state because it is the initial state")

      case State.Medoids =>
        println(s"SWITCHING to medoids state ($count / $sizeTuples)")
        // compute medoids
        var i = 0
        while i < initialPreClusters.size do
          preClusters(i) = initialPreClusters(i).withMedoid(wDists)
          i += 1
        debugMessages.addOne((count, "STATE", "Finished intra-cluster / start medoids"))
        iCluster = 0
        jCluster = 1

      case State.SplitMedoids =>
        println(s"SWITCHING to split-medoids state ($count / $sizeTuples)")
        debugMessages.addOne((count, "STATE", "Finished (split-)medoids / start split-medoids"))
        val (splitId, splitCluster) = preClusters.maxBy {
          case (_, cluster) => cluster.maxMergeDistance(wDists, linkage)
        }
        //        println(s" splitting cluster $splitId with ${splitCluster.size} elements and medoid ${splitCluster.medoid}")
        val newClusters = splitCluster.split(wDists, linkage, preClusters.size)
        newClusters.foreach { cluster =>
          preClusters(cluster.id) = cluster
        }
        //        val tmp = newClusters.map(c => s"${c.id} (n=${c.size}, medoid=${c.medoid})")
        //        println(s" new clusters: ${tmp.mkString(", ")}")
        val newCluster1 = newClusters.head
        val newCluster2 = newClusters.last
        if newClusters.size == 3 then
          skipMedoid = newClusters(1).medoid
        else
          skipMedoid = -1
        iCluster = if splitCluster.medoid != newCluster1.medoid then newCluster1.id else newCluster2.id
        jCluster = 0
    }
    state = newState
  }

  private def nextIntraClusterPair(): (Int, Int) = {
    if !intraPreClustersGen.hasNext then
      switchState(State.Medoids)
      count -= 1
      next()
    else
      intraPreClustersGen.next()
  }

  private def nextMedoidPair(): (Int, Int) = {
    if iCluster >= preClusters.size - 1 && jCluster >= preClusters.size then
      switchState(State.SplitMedoids)
      count -= 1
      next()

    else
      var result = (preClusters(iCluster).medoid, preClusters(jCluster).medoid)
      if result._2 < result._1 then
        result = result.swap
      activeClusterPair = (iCluster, jCluster)

      jCluster += 1
      if jCluster == preClusters.size then
        iCluster += 1
        jCluster = iCluster + 1

      result
  }

  @tailrec
  private def nextSplitMedoidPair(): (Int, Int) = {
    if jCluster >= preClusters.size then
      switchState(State.SplitMedoids)
      count -= 1
      next()
    else
      var result = (preClusters(iCluster).medoid, preClusters(jCluster).medoid)
      if result._2 < result._1 then
        result = result.swap
      activeClusterPair = (iCluster, jCluster)
      jCluster += 1
      if jCluster == preClusters.size && iCluster != preClusters.size - 1 then
        iCluster = preClusters.size - 1
        jCluster = 0

      if result._1 != result._2 && !processed.contains(result, n) then
        result
      else
        nextSplitMedoidPair()
  }
}
