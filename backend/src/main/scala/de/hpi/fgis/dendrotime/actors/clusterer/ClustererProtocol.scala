package de.hpi.fgis.dendrotime.actors.clusterer

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.clustering.PDist

import scala.annotation.switch
import scala.collection.AbstractIterator

object ClustererProtocol {
  sealed trait Command

  case class Initialize(n: Int) extends Command

  case class RegisterApproxDistMatrixReceiver(receiver: ActorRef[ApproxDistanceMatrix]) extends Command

  case class ApproxDistanceMatrix(distances: PDist)

  private[clusterer] case object GetDistances extends Command

  private[clusterer] case object ReportStatus extends Command

  sealed trait DistanceResult extends AbstractIterator[(Int, Int, Double)] with Command {
    val isApproximate: Boolean
    val isFull: Boolean = !isApproximate
  }

  private final class DistanceResult1(t1: Int, t2: Int, dist: Double,
                                      override val isApproximate: Boolean) extends DistanceResult {
    private var done = false

    override val size: Int = 1

    override val knownSize: Int = 1

    override def hasNext: Boolean = !done

    override def next(): (Int, Int, Double) = {
      done = true
      (t1, t2, dist)
    }
  }

  private final class DistanceResult2(t1a: Int, t1b: Int, dist1: Double,
                                      t2a: Int, t2b: Int, dist2: Double,
                                      override val isApproximate: Boolean) extends DistanceResult {
    private var idx = 0

    override val size: Int = 2

    override val knownSize: Int = 2

    override def hasNext: Boolean = idx < 2

    override def next(): (Int, Int, Double) = {
      val res = (idx: @switch) match {
        case 0 => (t1a, t1b, dist1)
        case 1 => (t2a, t2b, dist2)
      }
      idx += 1
      res
    }
  }

  private final class DistanceResult3(t1a: Int, t1b: Int, dist1: Double,
                                      t2a: Int, t2b: Int, dist2: Double,
                                      t3a: Int, t3b: Int, dist3: Double,
                                      override val isApproximate: Boolean) extends DistanceResult {
    private var idx = 0

    override val size: Int = 3

    override val knownSize: Int = 3

    override def hasNext: Boolean = idx < 3

    override def next(): (Int, Int, Double) = {
      val res = (idx: @switch) match {
        case 0 => (t1a, t1b, dist1)
        case 1 => (t2a, t2b, dist2)
        case 2 => (t3a, t3b, dist3)
      }
      idx += 1
      res
    }
  }

  private final class DistanceResult4(t1a: Int, t1b: Int, dist1: Double,
                                      t2a: Int, t2b: Int, dist2: Double,
                                      t3a: Int, t3b: Int, dist3: Double,
                                      t4a: Int, t4b: Int, dist4: Double,
                                      override val isApproximate: Boolean) extends DistanceResult {
    private var idx = 0

    override val size: Int = 4

    override val knownSize: Int = 4

    override def hasNext: Boolean = idx < 4

    override def next(): (Int, Int, Double) = {
      val res = (idx: @switch) match {
        case 0 => (t1a, t1b, dist1)
        case 1 => (t2a, t2b, dist2)
        case 2 => (t3a, t3b, dist3)
        case 3 => (t4a, t4b, dist4)
      }
      idx += 1
      res
    }
  }

  private final class DistanceResultN(tas: Array[Int], tbs: Array[Int], dists: Array[Double],
                                      override val isApproximate: Boolean) extends DistanceResult {
    private var idx = 0

    override val size: Int = tas.length

    override val knownSize: Int = tas.length

    override def hasNext: Boolean = idx < tas.length

    override def next(): (Int, Int, Double) = {
      val res = (tas(idx), tbs(idx), dists(idx))
      idx += 1
      res
    }
  }

  object ApproximateDistance {
    def apply(t1: Int, t2: Int, dist: Double): DistanceResult =
      new DistanceResult1(t1, t2, dist, isApproximate = true)

    def apply(t1a: Int, t1b: Int, dist1: Double,
              t2a: Int, t2b: Int, dist2: Double): DistanceResult =
      new DistanceResult2(t1a, t1b, dist1, t2a, t2b, dist2, isApproximate = true)

    def apply(t1a: Int, t1b: Int, dist1: Double,
              t2a: Int, t2b: Int, dist2: Double,
              t3a: Int, t3b: Int, dist3: Double): DistanceResult =
      new DistanceResult3(t1a, t1b, dist1, t2a, t2b, dist2, t3a, t3b, dist3, isApproximate = true)

    def apply(t1a: Int, t1b: Int, dist1: Double,
              t2a: Int, t2b: Int, dist2: Double,
              t3a: Int, t3b: Int, dist3: Double,
              t4a: Int, t4b: Int, dist4: Double): DistanceResult =
      new DistanceResult4(t1a, t1b, dist1, t2a, t2b, dist2, t3a, t3b, dist3, t4a, t4b, dist4, isApproximate = true)

    def apply(tas: Array[Int], tbs: Array[Int], dists: Array[Double]): DistanceResult =
      (tas.length: @switch) match {
        case 1 => new DistanceResult1(tas(0), tbs(0), dists(0), isApproximate = true)
        case 2 => new DistanceResult2(tas(0), tbs(0), dists(0), tas(1), tbs(1), dists(1), isApproximate = true)
        case 3 => new DistanceResult3(
          tas(0), tbs(0), dists(0), tas(1), tbs(1), dists(1),
          tas(2), tbs(2), dists(2), isApproximate = true
        )
        case 4 => new DistanceResult4(
          tas(0), tbs(0), dists(0), tas(1), tbs(1), dists(1),
          tas(2), tbs(2), dists(2), tas(3), tbs(3), dists(3),
          isApproximate = true
        )
        case _ => new DistanceResultN(tas, tbs, dists, isApproximate = true)
      }
  }

  object FullDistance {
    def apply(t1: Int, t2: Int, dist: Double): DistanceResult =
      new DistanceResult1(t1, t2, dist, isApproximate = false)

    def apply(t1a: Int, t1b: Int, dist1: Double,
              t2a: Int, t2b: Int, dist2: Double): DistanceResult =
      new DistanceResult2(t1a, t1b, dist1, t2a, t2b, dist2, isApproximate = false)

    def apply(t1a: Int, t1b: Int, dist1: Double,
              t2a: Int, t2b: Int, dist2: Double,
              t3a: Int, t3b: Int, dist3: Double): DistanceResult =
      new DistanceResult3(t1a, t1b, dist1, t2a, t2b, dist2, t3a, t3b, dist3, isApproximate = false)

    def apply(t1a: Int, t1b: Int, dist1: Double,
              t2a: Int, t2b: Int, dist2: Double,
              t3a: Int, t3b: Int, dist3: Double,
              t4a: Int, t4b: Int, dist4: Double): DistanceResult =
      new DistanceResult4(t1a, t1b, dist1, t2a, t2b, dist2, t3a, t3b, dist3, t4a, t4b, dist4, isApproximate = false)

    def apply(tas: Array[Int], tbs: Array[Int], dists: Array[Double]): DistanceResult =
      (tas.length: @switch) match {
        case 1 => new DistanceResult1(tas(0), tbs(0), dists(0), isApproximate = false)
        case 2 => new DistanceResult2(tas(0), tbs(0), dists(0), tas(1), tbs(1), dists(1), isApproximate = false)
        case 3 => new DistanceResult3(
          tas(0), tbs(0), dists(0), tas(1), tbs(1), dists(1),
          tas(2), tbs(2), dists(2), isApproximate = false
        )
        case 4 => new DistanceResult4(
          tas(0), tbs(0), dists(0), tas(1), tbs(1), dists(1),
          tas(2), tbs(2), dists(2), tas(3), tbs(3), dists(3),
          isApproximate = false
        )
        case _ => new DistanceResultN(tas, tbs, dists, isApproximate = false)
      }
  }
}
