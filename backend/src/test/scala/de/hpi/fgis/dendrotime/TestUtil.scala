package de.hpi.fgis.dendrotime

import de.hpi.fgis.dendrotime.clustering.hierarchy.Hierarchy
import de.hpi.fgis.dendrotime.io.TsParser
import de.hpi.fgis.dendrotime.io.hierarchies.HierarchyCSVReader
import org.scalactic.{Equality, TolerantNumerics, TripleEquals}

import java.io.File
import scala.collection.mutable

object TestUtil {
  /** Defines triple-equals implicits to mixin. */
  trait ImplicitEqualitySupport extends TripleEquals {
    given Equality[Double] = TolerantNumerics.tolerantDoubleEquality(1e-9)

    given Equality[Array[Array[Double]]] = (a: Array[Array[Double]], b: Any) => b match {
      case bArray: Array[Array[Double]] =>
        a.length == bArray.length && a.zip(bArray).forall { case (x, y) => x.corresponds(y)(_ === _) }
      case _ => false
    }

    given Equality[Hierarchy] = (a: Hierarchy, b: Any) => {
      b match {
        case bHierarchy: Hierarchy =>
          a.n == bHierarchy.n && a.length === bHierarchy.length &&
            a.indices.forall { i =>
              val x = a(i)
              val y = bHierarchy(i)
              x.idx === y.idx && x.cId1 === y.cId1 && x.cId2 === y.cId2
                && x.distance === y.distance && x.cardinality === y.cardinality
            }
        case _ => false
      }
    }
  }

  /** Import triple-equals implicits. */
  object ImplicitEqualities extends ImplicitEqualitySupport

  /** Returns the path to a file in the test-resources folder. */
  def findResource(filepath: String): String =
    getClass.getClassLoader.getResource(filepath).getPath

  /** Load an existing hierarchy (CSV) from disk using DendroTime production code. */
  def loadHierarchy(filepath: String): Hierarchy =
    HierarchyCSVReader().parse(findResource(filepath))

  /** Load time series from a .ts-file using DendroTime production code. */
  def loadDataset(path: String, maxTimeseries: Option[Int] = None): Array[Array[Double]] = {
    val timeseries = mutable.ArrayBuilder.make[Array[Double]]

    val parser = TsParser(TsParser.TsParserSettings(
      parseMetadata = false,
      tsLimit = maxTimeseries
    ))
    parser.parse(new File(path), new TsParser.TsProcessor {
      override def processUnivariate(data: Array[Double], label: String): Unit = {
        timeseries += data
      }
    })
    timeseries.result()
  }
}
