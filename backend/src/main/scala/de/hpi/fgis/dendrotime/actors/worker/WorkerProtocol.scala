package de.hpi.fgis.dendrotime.actors.worker

import akka.actor.typed.ActorRef
import de.hpi.fgis.dendrotime.actors.coordinator.strategies.StrategyProtocol.DispatchWork
import de.hpi.fgis.dendrotime.actors.tsmanager.TsmProtocol

import scala.collection.AbstractIterator

object WorkerProtocol {
  sealed trait Command

  case class UseSupplier(supplier: ActorRef[DispatchWork]) extends Command

  private[worker] case class GetTimeSeriesResponse(msg: TsmProtocol.GetTimeSeriesResponse) extends Command

  sealed trait CheckCommand extends AbstractIterator[(Long, Long)] with Command {
    val isApproximate: Boolean
    def isFull: Boolean = !isApproximate
    def medoidsFor: Option[(Array[Long], Array[Long])] = None

    def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries
  }

  private final class Check1(t1: Long, t2: Long,
                       override val isApproximate: Boolean) extends CheckCommand {
    private var done = false
    override val size: Int = 1
    override val knownSize: Int = 1

    override def hasNext: Boolean = !done

    override def next(): (Long, Long) = {
      done = true
      (t1, t2)
    }

    override def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries =
      TsmProtocol.GetTimeSeries(t1, t2, replyTo)
  }

  private final class Check2(p1t1: Long, p1t2: Long, p2t1: Long, p2t2: Long,
                       override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 2
    override val knownSize: Int = 2

    override def hasNext: Boolean = idx < 2

    override def next(): (Long, Long) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
      }
      idx += 1
      res
    }

    override def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries =
      // deduplicate time series IDs before creating the request
      if (p1t1 == p2t1 && p1t2 == p2t2) || (p1t1 == p2t2 && p1t2 == p2t1) then
        TsmProtocol.GetTimeSeries(p1t1, p1t2, replyTo)
      else if p1t1 == p2t1 then
        TsmProtocol.GetTimeSeries(p1t1, p1t2, p2t2, replyTo)
      else if p1t1 == p2t2 then
        TsmProtocol.GetTimeSeries(p1t1, p1t2, p2t1, replyTo)
      else if p1t2 == p2t1 then
        TsmProtocol.GetTimeSeries(p1t1, p1t2, p2t2, replyTo)
      else if p1t2 == p2t2 then
        TsmProtocol.GetTimeSeries(p1t1, p1t2, p2t1, replyTo)
      else
        TsmProtocol.GetTimeSeries(p1t1, p1t2, p2t1, p2t2, replyTo)
  }

  private final class Check3(p1t1: Long, p1t2: Long, p2t1: Long, p2t2: Long, p3t1: Long, p3t2: Long,
                             override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 3
    override val knownSize: Int = 3

    override def hasNext: Boolean = idx < 3

    override def next(): (Long, Long) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
        case 2 => (p3t1, p3t2)
      }
      idx += 1
      res
    }

    override def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries =
      // deduplicate time series IDs before creating the request
      val distinctIds = Set(p1t1, p1t2, p2t1, p2t2, p3t1, p3t2)
      val it = distinctIds.iterator
      if distinctIds.size == 2 then
        TsmProtocol.GetTimeSeries(it.next(), it.next(), replyTo)
      else if distinctIds.size == 3 then
        TsmProtocol.GetTimeSeries(it.next(), it.next(), it.next(), replyTo)
      else if distinctIds.size == 4 then
        TsmProtocol.GetTimeSeries(it.next(), it.next(), it.next(), it.next(), replyTo)
      else
        TsmProtocol.GetTimeSeries(it.toArray, replyTo)
  }

  private final class Check4(p1t1: Long, p1t2: Long, p2t1: Long, p2t2: Long,
                             p3t1: Long, p3t2: Long, p4t1: Long, p4t2: Long,
                             override val isApproximate: Boolean) extends CheckCommand {
    private var idx = 0
    override val size: Int = 4
    override val knownSize: Int = 4

    override def hasNext: Boolean = idx < 4

    override def next(): (Long, Long) = {
      val res = idx match {
        case 0 => (p1t1, p1t2)
        case 1 => (p2t1, p2t2)
        case 2 => (p3t1, p3t2)
        case 3 => (p4t1, p4t2)
      }
      idx += 1
      res
    }

    override def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries =
      // deduplicate time series IDs before creating the request
      val distinctIds = Set(p1t1, p1t2, p2t1, p2t2, p3t1, p3t2, p4t1, p4t2)
      val it = distinctIds.iterator
      if distinctIds.size == 2 then
        TsmProtocol.GetTimeSeries(it.next(), it.next(), replyTo)
      else if distinctIds.size == 3 then
        TsmProtocol.GetTimeSeries(it.next(), it.next(), it.next(), replyTo)
      else if distinctIds.size == 4 then
        TsmProtocol.GetTimeSeries(it.next(), it.next(), it.next(), it.next(), replyTo)
      else
        TsmProtocol.GetTimeSeries(it.toArray, replyTo)
  }

  private final class CheckN(ids: Array[(Long, Long)], override val isApproximate: Boolean) extends CheckCommand {
    private val it = ids.iterator
    override val size: Int = ids.length
    override val knownSize: Int = ids.length

    override def hasNext: Boolean = it.hasNext

    override def next(): (Long, Long) = it.next()

    override def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries =
      val distinctIds = ids.flatMap { case (t1, t2) => Set(t1, t2) }.distinct
      if distinctIds.length == 2 then
        TsmProtocol.GetTimeSeries(distinctIds(0), distinctIds(1), replyTo)
      else if distinctIds.length == 3 then
        TsmProtocol.GetTimeSeries(distinctIds(0), distinctIds(1), distinctIds(2), replyTo)
      else if distinctIds.length == 4 then
        TsmProtocol.GetTimeSeries(distinctIds(0), distinctIds(1), distinctIds(2), distinctIds(3), replyTo)
      else
        TsmProtocol.GetTimeSeries(distinctIds, replyTo)
  }

  object CheckApproximate {
    def apply(t1: Long, t2: Long): CheckCommand =
      new Check1(t1, t2, isApproximate = true)

    def apply(t1: Long, t2: Long, t3: Long, t4: Long): CheckCommand =
      new Check2(t1, t2, t3, t4, isApproximate = true)

    def apply(t1: Long, t2: Long, t3: Long, t4: Long, t5: Long, t6: Long): CheckCommand =
      new Check3(t1, t2, t3, t4, t5, t6, isApproximate = true)

    def apply(t1: Long, t2: Long, t3: Long, t4: Long, t5: Long, t6: Long, t7: Long, t8: Long): CheckCommand =
      new Check4(t1, t2, t3, t4, t5, t6, t7, t8, isApproximate = true)

    def apply(pairs: Array[(Long, Long)]): CheckCommand =
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
    def apply(t1: Long, t2: Long): CheckCommand =
      new Check1(t1, t2, isApproximate = false)

    def apply(t1: Long, t2: Long, t3: Long, t4: Long): CheckCommand =
      new Check2(t1, t2, t3, t4, isApproximate = false)

    def apply(t1: Long, t2: Long, t3: Long, t4: Long, t5: Long, t6: Long): CheckCommand =
      new Check3(t1, t2, t3, t4, t5, t6, isApproximate = false)

    def apply(t1: Long, t2: Long, t3: Long, t4: Long, t5: Long, t6: Long, t7: Long, t8: Long): CheckCommand =
      new Check4(t1, t2, t3, t4, t5, t6, t7, t8, isApproximate = false)

    def apply(pairs: Array[(Long, Long)]): CheckCommand =
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

  final case class CheckMedoids(m1: Long, m2: Long, ids1: Array[Long], ids2: Array[Long]) extends CheckCommand {
    private var done = false
    override val size: Int = 1
    override val knownSize: Int = 1
    override val isApproximate: Boolean = false

    override def hasNext: Boolean = !done

    override def next(): (Long, Long) = {
      done = true
      (m1, m2)
    }

    override def tsRequest(replyTo: ActorRef[TsmProtocol.GetTimeSeriesResponse]): TsmProtocol.GetTimeSeries = {
      val distinctIds = ids1.concat(ids2).distinct
      if distinctIds.length == 2 then
        TsmProtocol.GetTimeSeries(distinctIds(0), distinctIds(1), replyTo)
      else if distinctIds.length == 3 then
        TsmProtocol.GetTimeSeries(distinctIds(0), distinctIds(1), distinctIds(2), replyTo)
      else if distinctIds.length == 4 then
        TsmProtocol.GetTimeSeries(distinctIds(0), distinctIds(1), distinctIds(2), distinctIds(3), replyTo)
      else
        TsmProtocol.GetTimeSeries(distinctIds, replyTo)
    }

    override def medoidsFor: Option[(Array[Long], Array[Long])] = Some((ids1, ids2))
  }
}
