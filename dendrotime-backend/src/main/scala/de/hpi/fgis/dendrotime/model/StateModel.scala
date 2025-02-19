package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.structures.{QualityTrace, Status}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

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

    def progressFromClusteringState(status: Status, progress: Int, state: ClusteringState): ProgressMessage =
      CurrentProgress(
        state = status,
        progress = progress,
        hierarchy = state.hierarchy,
        steps = state.qualityTrace.indices,
        timestamps = state.qualityTrace.timestamps,
        hierarchySimilarities = state.qualityTrace.similarities,
        hierarchyQualities = state.qualityTrace.gtSimilarities,
        clusterQualities = state.qualityTrace.clusterQualities
      )
  }

  final case class ClusteringState(
                                    hierarchy: Hierarchy = Hierarchy.empty,
                                    qualityTrace: QualityTrace = QualityTrace.empty,
                                  )

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

    given RootJsonFormat[ProgressMessage] = new RootJsonFormat[ProgressMessage] {
      override def write(obj: ProgressMessage): JsValue = obj match {
        case ProgressMessage.Unchanged => JsObject.empty
        case cp: ProgressMessage.CurrentProgress => summon[RootJsonFormat[ProgressMessage.CurrentProgress]].write(cp)
      }

      override def read(json: JsValue): ProgressMessage = json match {
        case obj: JsObject if obj.fields.isEmpty =>
          ProgressMessage.Unchanged
        case obj: JsObject if obj.fields.contains("state") =>
          summon[RootJsonFormat[ProgressMessage.CurrentProgress]].read(json)
        case _ =>
          throw DeserializationException("Invalid progress message")
      }
    }
  }
}
