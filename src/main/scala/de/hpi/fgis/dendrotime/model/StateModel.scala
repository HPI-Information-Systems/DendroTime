package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

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
                                    hierarchy: Hierarchy
                                    ) extends ProgressMessage
//    final case class StateUpdate(id: Long, newState: Status) extends ProgressMessage
//    final case class ProgressUpdate(id: Long, progress: Int) extends ProgressMessage
  }

  sealed trait Status

  object Status {
    case object Initializing extends Status
    case object Approximating extends Status
    case object ComputingFullDistances extends Status
    case object Finalizing extends Status
  }


  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

    given RootJsonFormat[Hierarchy.Node] = jsonFormat5(Hierarchy.Node.apply)

//    given RootJsonFormat[Hierarchy] = jsonFormat[List[Hierarchy.Node], Hierarchy](itr => {
//      val h = Hierarchy.newBuilder(itr.size)
//      itr.foreach(h.add)
//      h.sort()
//      h.build()
//    }, "hierarchy")
    given RootJsonFormat[Hierarchy] = new RootJsonFormat[Hierarchy] {
      override def write(obj: Hierarchy): JsValue = JsObject(
        "hierarchy" -> JsArray(obj.map(summon[RootJsonFormat[Hierarchy.Node]].write).toVector)
      )
      
      override def read(json: JsValue): Hierarchy = json match {
        case JsObject(fields) if fields.contains("hierarchy") =>
          val nodes = fields("hierarchy").convertTo[Vector[Hierarchy.Node]]
          val h = Hierarchy.newBuilder(nodes.size)
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
      }

      override def read(json: JsValue): Status = json match {
        case JsString("Initializing") => Status.Initializing
        case JsString("Approximating") => Status.Approximating
        case JsString("ComputingFullDistances") => Status.ComputingFullDistances
        case JsString("Finalizing") => Status.Finalizing
        case _ => throw DeserializationException("Invalid status")
      }
    }

    given RootJsonFormat[ProgressMessage.CurrentProgress] = jsonFormat3(ProgressMessage.CurrentProgress.apply)
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
