package de.hpi.fgis.dendrotime.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.hierarchy.{Hierarchy, Linkage, computeHierarchy}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.tailrec

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
                                    similarities: Seq[Double]
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
    case object Finished extends Status
    
    given Ordering[Status] = Ordering.by {
      case Initializing => 0
      case Approximating => 1
      case ComputingFullDistances => 2
      case Finalizing => 3
      case Finished => 4
    }
  }

  // FIXME: Is too expensive to serialize!! Maybe perform the transformation in the Browser instead?
  sealed trait DendrogramTree {
    def id: Int
    def distance: Double
    def size: Int
    def left: Option[DendrogramTree]
    def right: Option[DendrogramTree]
    def children: Seq[DendrogramTree] = Seq(left, right).flatten
  }

  object DendrogramTree {
    case class Cluster(id: Int,
                       distance: Double,
                       size: Int,
                       private val _left: DendrogramTree,
                       private val _right: DendrogramTree) extends DendrogramTree {
      override def left: Some[DendrogramTree] = Some(_left)
      override def right: Some[DendrogramTree] = Some(_right)
    }
    case class Leaf(id: Int) extends DendrogramTree {
      override def left: None.type = None
      override def right: None.type = None
      override def distance: Double = 0.0
      override def size: Int = 1
    }

    def fromHierarchy(h: Hierarchy): DendrogramTree = {
      val nodes = h.iterator
      val tree = Array.ofDim[DendrogramTree](h.n + h.size)
      for (i <- 0 until h.n) {
        tree(i) = Leaf(i)
      }
      while (nodes.hasNext) {
        val node = nodes.next()
        val cluster = Cluster(
          id=h.n + node.idx,
          distance=node.distance,
          size=node.cardinality,
          _left=tree(node.cId1),
          _right=tree(node.cId2)
        )
        tree(h.n + node.idx) = cluster
      }
      tree.last
    }
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

    given RootJsonFormat[DendrogramTree] = new RootJsonFormat[DendrogramTree] {
      override def write(obj: DendrogramTree): JsValue = obj match {
        case l : DendrogramTree.Leaf =>
          JsObject(
            "type" -> JsString("DendrogramTree.Leaf"),
            "id" -> JsNumber(l.id),
            "distance" -> JsNumber(l.distance),
            "size" -> JsNumber(l.size),
            "children" -> JsArray.empty
          )
        case DendrogramTree.Cluster(id, distance, size, left, right) =>
          JsObject(
            "type" -> JsString("DendrogramTree.Cluster"),
            "id" -> JsNumber(id),
            "distance" -> JsNumber(distance),
            "size" -> JsNumber(size),
            "children" -> JsArray(
              write(left),
              write(right)
            )
          )
      }

      override def read(json: JsValue): DendrogramTree = json match {
        case JsObject(fields) if fields.contains("type") =>
          fields("type") match {
            case JsString("DendrogramTree.Leaf") =>
              val id = fields("id").convertTo[Int]
              DendrogramTree.Leaf(id)
            case JsString("DendrogramTree.Cluster") =>
              val id = fields("id").convertTo[Int]
              val distance = fields("distance").convertTo[Double]
              val size = fields("size").convertTo[Int]
              val children = fields("children").convertTo[Vector[DendrogramTree]]
              val left = children(0)
              val right = children(1)
              DendrogramTree.Cluster(id, distance, size, left, right)
            case _ => throw DeserializationException(s"Invalid dendrogram tree: type ${fields("type")} not known")
          }
        case _ => throw DeserializationException("Invalid dendrogram tree: type field missing")
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

    given RootJsonFormat[ProgressMessage.CurrentProgress] = jsonFormat4(ProgressMessage.CurrentProgress.apply)
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

@main
def main(): Unit = {
  val inf = Double.PositiveInfinity
  val distances = Array(
    Array(0.0, 0.1, 0.2, inf),
    Array(0.1, 0.0, inf, inf),
    Array(0.2, inf, 0.0, inf),
    Array(inf, inf, inf, 0.0),
  )
  val dists = PDist(distances)
  val linkage = Linkage.CompleteLinkage
  val hierarchy = computeHierarchy(dists, linkage)
  println(hierarchy)
  val tree = StateModel.DendrogramTree.fromHierarchy(hierarchy)
  println(tree)

  import StateModel.JsonSupport

  val jsonSupport = new JsonSupport {}
  import jsonSupport.given

  val serialized = summon[RootJsonFormat[StateModel.DendrogramTree]].write(tree)
  println(serialized)
}