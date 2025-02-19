package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.io.TimeSeries.LabeledTimeSeries

import scala.collection.AbstractIterator

object WorkerProtocol {
  sealed trait Command

  case class UseSupplier(supplier: ActorRef[DispatchWork]) extends Command

  private[worker] case class TimeSeriesLoaded(timeseries: IndexedSeq[LabeledTimeSeries]) extends Command

  sealed trait CheckCommand extends AbstractIterator[(Int, Int)] with Command {
    val isApproximate: Boolean
    def isFull: Boolean = !isApproximate
    def medoidsFor: Option[(Array[Int], Array[Int])] = None
  }

  private final class Check1(t1: Int, t2: Int,
                       override val isApproximate: Boolean) extends CheckCommand {
    private var done = false
    override val size: Int = 1
    override val knownSize: Int = 1

    override def hasNext: Boolean = !done

    override def next(): (Int, Int) = {
      done = true
      (t1, t2)
    }
  }

  private final class Check2(p1t1: Int, p1t2: Int, p2t1: Int, p2t2: Int,
                       override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 2
    override val knownSize: Int = 2

    override def hasNext: Boolean = idx < 2

    override def next(): (Int, Int) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
      }
      idx += 1
      res
    }
  }

  private final class Check3(p1t1: Int, p1t2: Int, p2t1: Int, p2t2: Int, p3t1: Int, p3t2: Int,
                             override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 3
    override val knownSize: Int = 3

    override def hasNext: Boolean = idx < 3

    override def next(): (Int, Int) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
        case 2 => (p3t1, p3t2)
      }
      idx += 1
      res
    }
  }

  private final class Check4(p1t1: Int, p1t2: Int, p2t1: Int, p2t2: Int,
                             p3t1: Int, p3t2: Int, p4t1: Int, p4t2: Int,
                             override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 4
    override val knownSize: Int = 4

    override def hasNext: Boolean = idx < 4

    override def next(): (Int, Int) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
        case 2 => (p3t1, p3t2)
        case 3 => (p4t1, p4t2)
      }
      idx += 1
      res
    }
  }

  private final class CheckN(ids: Array[(Int, Int)], override val isApproximate: Boolean) extends CheckCommand {
    private val it = ids.iterator
    override val size: Int = ids.length
    override val knownSize: Int = ids.length

    override def hasNext: Boolean = it.hasNext

    override def next(): (Int, Int) = it.next()
  }

  object CheckApproximate {
    def apply(t1: Int, t2: Int): CheckCommand =
      new Check1(t1, t2, isApproximate = true)

    def apply(t1: Int, t2: Int, t3: Int, t4: Int): CheckCommand =
      new Check2(t1, t2, t3, t4, isApproximate = true)

    def apply(t1: Int, t2: Int, t3: Int, t4: Int, t5: Int, t6: Int): CheckCommand =
      new Check3(t1, t2, t3, t4, t5, t6, isApproximate = true)

    def apply(t1: Int, t2: Int, t3: Int, t4: Int, t5: Int, t6: Int, t7: Int, t8: Int): CheckCommand =
      new Check4(t1, t2, t3, t4, t5, t6, t7, t8, isApproximate = true)

    def apply(pairs: Array[(Int, Int)]): CheckCommand =
      if pairs.length == 1 then
        new Check1(pairs(0)._1, pairs(0)._2, isApproximate = true)
      else if pairs.length == 2 then
        new Check2(pairs(0)._1, pairs(0)._2, pairs(1)._1, pairs(1)._2, isApproximate = true)
      else if pairs.length == 3 then
        new Check3(pairs(0)._1, pairs(0)._2, pairs(1)._1, pairs(1)._2, pairs(2)._1, pairs(2)._2, isApproximate = true)
      else if pairs.length == 4 then
        new Check4(pairs(0)._1, pairs(0)._2, pairs(1)._1, pairs(1)._2, pairs(2)._1, pairs(2)._2, pairs(3)._1, pairs(3)._2, isApproximate = true)
      else
        new CheckN(pairs, isApproximate = true)
  }

  object CheckFull {
    def apply(t1: Int, t2: Int): CheckCommand =
      new Check1(t1, t2, isApproximate = false)

    def apply(t1: Int, t2: Int, t3: Int, t4: Int): CheckCommand =
      new Check2(t1, t2, t3, t4, isApproximate = false)

    def apply(t1: Int, t2: Int, t3: Int, t4: Int, t5: Int, t6: Int): CheckCommand =
      new Check3(t1, t2, t3, t4, t5, t6, isApproximate = false)

    def apply(t1: Int, t2: Int, t3: Int, t4: Int, t5: Int, t6: Int, t7: Int, t8: Int): CheckCommand =
      new Check4(t1, t2, t3, t4, t5, t6, t7, t8, isApproximate = false)

    def apply(pairs: Array[(Int, Int)]): CheckCommand =
      if pairs.length == 1 then
        new Check1(pairs(0)._1, pairs(0)._2, isApproximate = false)
      else if pairs.length == 2 then
        new Check2(pairs(0)._1, pairs(0)._2, pairs(1)._1, pairs(1)._2, isApproximate = false)
      else if pairs.length == 3 then
        new Check3(pairs(0)._1, pairs(0)._2, pairs(1)._1, pairs(1)._2, pairs(2)._1, pairs(2)._2, isApproximate = false)
      else if pairs.length == 4 then
        new Check4(pairs(0)._1, pairs(0)._2, pairs(1)._1, pairs(1)._2, pairs(2)._1, pairs(2)._2, pairs(3)._1, pairs(3)._2, isApproximate = false)
      else
        new CheckN(pairs, isApproximate = false)
  }

  final case class CheckMedoids(m1: Int, m2: Int,
                                ids1: Array[Int], ids2: Array[Int],
                                justBroadcast: Boolean = false) extends CheckCommand {
    private var done = false
    override val size: Int = 1
    override val knownSize: Int = 1
    override val isApproximate: Boolean = false

    override def hasNext: Boolean = !done

    override def next(): (Int, Int) = {
      done = true
      (m1, m2)
    }

    override def medoidsFor: Option[(Array[Int], Array[Int])] = Some((ids1, ids2))
  }
}
