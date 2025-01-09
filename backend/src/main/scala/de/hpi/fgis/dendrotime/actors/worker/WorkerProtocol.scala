package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol
import de.hpi.fgis.dendrotime.model.TimeSeriesModel.LabeledTimeSeries

import scala.collection.AbstractIterator

object WorkerProtocol {
  sealed trait Command

  private type TsId = Int

  case class UseSupplier(supplier: ActorRef[DispatchWork]) extends Command

  private[worker] case class TimeSeriesLoaded(timeseries: Map[TsId, LabeledTimeSeries]) extends Command

  sealed trait CheckCommand extends AbstractIterator[(TsId, TsId)] with Command {
    val isApproximate: Boolean
    def isFull: Boolean = !isApproximate
    def medoidsFor: Option[(Array[TsId], Array[TsId])] = None
  }

  private final class Check1(t1: TsId, t2: TsId,
                       override val isApproximate: Boolean) extends CheckCommand {
    private var done = false
    override val size: Int = 1
    override val knownSize: Int = 1

    override def hasNext: Boolean = !done

    override def next(): (TsId, TsId) = {
      done = true
      (t1, t2)
    }
  }

  private final class Check2(p1t1: TsId, p1t2: TsId, p2t1: TsId, p2t2: TsId,
                       override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 2
    override val knownSize: Int = 2

    override def hasNext: Boolean = idx < 2

    override def next(): (TsId, TsId) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
      }
      idx += 1
      res
    }
  }

  private final class Check3(p1t1: TsId, p1t2: TsId, p2t1: TsId, p2t2: TsId, p3t1: TsId, p3t2: TsId,
                             override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 3
    override val knownSize: Int = 3

    override def hasNext: Boolean = idx < 3

    override def next(): (TsId, TsId) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
        case 2 => (p3t1, p3t2)
      }
      idx += 1
      res
    }
  }

  private final class Check4(p1t1: TsId, p1t2: TsId, p2t1: TsId, p2t2: TsId,
                             p3t1: TsId, p3t2: TsId, p4t1: TsId, p4t2: TsId,
                             override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 4
    override val knownSize: Int = 4

    override def hasNext: Boolean = idx < 4

    override def next(): (TsId, TsId) = {
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

  private final class CheckN(ids: Array[(TsId, TsId)], override val isApproximate: Boolean) extends CheckCommand {
    private val it = ids.iterator
    override val size: Int = ids.length
    override val knownSize: Int = ids.length

    override def hasNext: Boolean = it.hasNext

    override def next(): (TsId, TsId) = it.next()
  }

  object CheckApproximate {
    def apply(t1: TsId, t2: TsId): CheckCommand =
      new Check1(t1, t2, isApproximate = true)

    def apply(t1: TsId, t2: TsId, t3: TsId, t4: TsId): CheckCommand =
      new Check2(t1, t2, t3, t4, isApproximate = true)

    def apply(t1: TsId, t2: TsId, t3: TsId, t4: TsId, t5: TsId, t6: TsId): CheckCommand =
      new Check3(t1, t2, t3, t4, t5, t6, isApproximate = true)

    def apply(t1: TsId, t2: TsId, t3: TsId, t4: TsId, t5: TsId, t6: TsId, t7: TsId, t8: TsId): CheckCommand =
      new Check4(t1, t2, t3, t4, t5, t6, t7, t8, isApproximate = true)

    def apply(pairs: Array[(TsId, TsId)]): CheckCommand =
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
    def apply(t1: TsId, t2: TsId): CheckCommand =
      new Check1(t1, t2, isApproximate = false)

    def apply(t1: TsId, t2: TsId, t3: TsId, t4: TsId): CheckCommand =
      new Check2(t1, t2, t3, t4, isApproximate = false)

    def apply(t1: TsId, t2: TsId, t3: TsId, t4: TsId, t5: TsId, t6: TsId): CheckCommand =
      new Check3(t1, t2, t3, t4, t5, t6, isApproximate = false)

    def apply(t1: TsId, t2: TsId, t3: TsId, t4: TsId, t5: TsId, t6: TsId, t7: TsId, t8: TsId): CheckCommand =
      new Check4(t1, t2, t3, t4, t5, t6, t7, t8, isApproximate = false)

    def apply(pairs: Array[(TsId, TsId)]): CheckCommand =
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

  final case class CheckMedoids(m1: TsId, m2: TsId,
                                ids1: Array[TsId], ids2: Array[TsId],
                                justBroadcast: Boolean = false) extends CheckCommand {
    private var done = false
    override val size: Int = 1
    override val knownSize: Int = 1
    override val isApproximate: Boolean = false

    override def hasNext: Boolean = !done

    override def next(): (TsId, TsId) = {
      done = true
      (m1, m2)
    }

    override def medoidsFor: Option[(Array[TsId], Array[TsId])] = Some((ids1, ids2))
  }
}
