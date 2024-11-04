package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, Linkage, computeHierarchy}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * All models related to the state of one clustering job and their marshalling.
 */
object StateModel {
  sealed trait ProgressMessage

  object ProgressMessage extends DefaultJsonProtocol {
    case object Unchanged extends ProgressMessage
    final case class CurrentProgress(
                                      state: Status,
                                      progress: Int,
                                      hierarchy: Hierarchy,
                                      steps: Seq[Int],
                                      timestamps: Seq[Long],
                                      hierarchySimilarities: Seq[Double],
                                      hierarchyQualities: Seq[Double] = Seq.empty,
                                      clusterQualities: Seq[Double] = Seq.empty,
                                    ) extends ProgressMessage
//    final case class StateUpdate(id: Long, newState: Status) extends ProgressMessage
//    final case class ProgressUpdate(id: Long, progress: Int) extends ProgressMessage

    def progressFromClusteringState(status: Status, progress: Int, state: ClusteringState): ProgressMessage =
      CurrentProgress(
        state=status,
        progress=progress,
        hierarchy=state.hierarchy,
        steps=state.qualityTrace.indices,
        timestamps=state.qualityTrace.timestamps,
        hierarchySimilarities=state.qualityTrace.similarities,
        hierarchyQualities=state.qualityTrace.gtSimilarities,
        clusterQualities=state.qualityTrace.clusterQualities
      )
  }
  
  final case class ClusteringState(
                                    hierarchy: Hierarchy = Hierarchy.empty,
                                    qualityTrace: QualityTrace = QualityTrace.empty,
                                  )

  final case class QualityTrace private (
                                 indices: Seq[Int],
                                 timestamps: Seq[Long],
                                 similarities: Seq[Double],
                                 gtSimilarities: Seq[Double],
                                 clusterQualities: Seq[Double],
                               ) {
    def hasGtSimilarities: Boolean = gtSimilarities.nonEmpty
    def hasClusterQualities: Boolean = clusterQualities.nonEmpty
    def size: Int = indices.length
  }

  object QualityTrace {
    def newBuilder: QualityTraceBuilder = new QualityTraceBuilder

    def empty: QualityTrace = QualityTrace(
      indices=Seq.empty,
      timestamps=IndexedSeq.empty,
      similarities=IndexedSeq.empty,
      gtSimilarities=IndexedSeq.empty,
      clusterQualities=IndexedSeq.empty
    )

    final class QualityTraceBuilder private[QualityTrace] {
      private val nComputations: mutable.ArrayBuffer[Int] = mutable.ArrayBuffer.empty
      private val timestamps: mutable.ArrayBuffer[Long] = mutable.ArrayBuffer.empty
      private val similarities: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty
      private val gtSimilarities: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty
      private val clusterQualities: mutable.ArrayBuffer[Double] = mutable.ArrayBuffer.empty

      def addStep(index: Int, similarity: Double): this.type =
        addStep(index, System.currentTimeMillis(), similarity)

      def addStep(index: Int, timestamp: Long, similarity: Double): this.type = {
        nComputations += index
        timestamps += timestamp
        similarities += similarity
        this
      }

      def addStep(index: Int, similarity: Double, gtSimilarity: Double, clusterQuality: Double): this.type =
        addStep(index, System.currentTimeMillis(), similarity, gtSimilarity, clusterQuality)

      def addStep(index: Int, timestamp: Long, similarity: Double, gtSimilarity: Double, clusterQuality: Double): this.type = {
        fillMissingClusterQualities()
        fillMissingClusterQualities()
        nComputations += index
        timestamps += timestamp
        similarities += similarity
        gtSimilarities += gtSimilarity
        clusterQualities += clusterQuality
        this
      }

      def withGtSimilarity(gtSimilarity: Double): this.type = {
        if nComputations.length == gtSimilarities.length then
          throw new IllegalStateException("Cannot add ground truth similarity without adding a new step first")
        fillMissingGtSimilarities(offset = -1)
        gtSimilarities += gtSimilarity
        this
      }

      def withClusterQuality(clusterQuality: Double): this.type = {
        if nComputations.length == clusterQualities.length then
          throw new IllegalStateException("Cannot add cluster quality without adding a new step first")
        fillMissingClusterQualities(offset = -1)
        clusterQualities += clusterQuality
        this
      }

      def result(): QualityTrace = QualityTrace(
        indices=nComputations.toArray,
        timestamps=timestamps.toArray,
        similarities=similarities.toArray,
        gtSimilarities=gtSimilarities.toArray,
        clusterQualities=clusterQualities.toArray
      )

      def clear(): Unit = {
        nComputations.clear()
        timestamps.clear()
        similarities.clear()
        gtSimilarities.clear()
        clusterQualities.clear()
      }

      private def fillMissingGtSimilarities(offset: Int = 0): Unit = {
        if gtSimilarities.length < nComputations.length + offset then
          val missing = nComputations.length + offset - gtSimilarities.length
          gtSimilarities ++= Seq.fill(missing)(0.0)
      }

      private def fillMissingClusterQualities(offset: Int = 0): Unit = {
        if clusterQualities.length < nComputations.length + offset then
          val missing = nComputations.length + offset - clusterQualities.length
          clusterQualities ++= Seq.fill(missing)(0.0)
      }
    }
  }

  sealed trait Status

  object Status {
    case object Initializing extends Status
    case object Approximating extends Status
    case object ComputingFullDistances extends Status
    case object Finalizing extends Status
    case object Finished extends Status
    
    given Ordering[Status] = Ordering.by {
      case Initializing => 0
      case Approximating => 1
      case ComputingFullDistances => 2
      case Finalizing => 3
      case Finished => 4
    }
  }

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

    given RootJsonFormat[Hierarchy.Node] = jsonFormat5(Hierarchy.Node.apply)

    given RootJsonFormat[Hierarchy] = new RootJsonFormat[Hierarchy] {
      override def write(obj: Hierarchy): JsValue = JsObject(
        "hierarchy" -> JsArray(obj.map(summon[RootJsonFormat[Hierarchy.Node]].write).toVector),
        "n" -> JsNumber(obj.n)
      )

      override def read(json: JsValue): Hierarchy = json match {
        case JsObject(fields) if fields.contains("hierarchy") =>
          val n = fields("n").convertTo[Int]
          val nodes = fields("hierarchy").convertTo[Vector[Hierarchy.Node]]
          val h = Hierarchy.newBuilder(n)
          nodes.foreach(h.add)
          h.sort()
          h.build()
        case _ => throw DeserializationException("Invalid hierarchy")
      }
    }

    given JsonFormat[Status] = new JsonFormat[Status] {
      override def write(obj: Status): JsValue = obj match {
        case Status.Initializing =>
          JsString("Initializing")
        case Status.Approximating =>
          JsString("Approximating")
        case Status.ComputingFullDistances =>
          JsString("ComputingFullDistances")
        case Status.Finalizing =>
          JsString("Finalizing")
        case Status.Finished =>
          JsString("Finished")
      }

      override def read(json: JsValue): Status = json match {
        case JsString("Initializing") => Status.Initializing
        case JsString("Approximating") => Status.Approximating
        case JsString("ComputingFullDistances") => Status.ComputingFullDistances
        case JsString("Finalizing") => Status.Finalizing
        case JsString("Finished") => Status.Finished
        case _ => throw DeserializationException("Invalid status")
      }
    }

    given RootJsonFormat[ProgressMessage.CurrentProgress] = jsonFormat8(ProgressMessage.CurrentProgress.apply)
//    given RootJsonFormat[ProgressMessage.StateUpdate] = jsonFormat2(ProgressMessage.StateUpdate.apply)
//    given RootJsonFormat[ProgressMessage.ProgressUpdate] = jsonFormat2(ProgressMessage.ProgressUpdate.apply)

    given RootJsonFormat[ProgressMessage] = new RootJsonFormat[ProgressMessage] {
      override def write(obj: ProgressMessage): JsValue = obj match {
        case ProgressMessage.Unchanged => JsObject.empty
        case cp: ProgressMessage.CurrentProgress => summon[RootJsonFormat[ProgressMessage.CurrentProgress]].write(cp)
//        case su: ProgressMessage.StateUpdate => summon[RootJsonFormat[ProgressMessage.StateUpdate]].write(su)
//        case pu: ProgressMessage.ProgressUpdate => summon[RootJsonFormat[ProgressMessage.ProgressUpdate]].write(pu)
      }

      override def read(json: JsValue): ProgressMessage = json match {
        case obj: JsObject if obj.fields.isEmpty =>
          ProgressMessage.Unchanged
        case obj: JsObject if obj.fields.contains("state") =>
          summon[RootJsonFormat[ProgressMessage.CurrentProgress]].read(json)
//        case obj: JsObject if obj.fields.contains("newState") =>
//          summon[RootJsonFormat[ProgressMessage.StateUpdate]].read(json)
//        case obj: JsObject if obj.fields.contains("progress") =>
//          summon[RootJsonFormat[ProgressMessage.ProgressUpdate]].read(json)
        case _ =>
          throw DeserializationException("Invalid progress message")
      }
    }
  }
}
