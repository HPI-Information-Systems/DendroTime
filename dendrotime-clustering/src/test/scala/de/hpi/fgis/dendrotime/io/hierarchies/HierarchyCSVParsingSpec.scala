package de.hpi.fgis.dendrotime.io.hierarchies

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

import java.io.{BufferedReader, FileReader}
import java.nio.file.Files
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

class HierarchyCSVParsingSpec extends AnyWordSpec with should.Matchers {

  extension (lines: Array[String]) {
    def getCsvValue(i: Int, j: Int): Double = lines(i).split(",")(j).toDouble
    def parsedRow(i: Int): Array[Double] = lines(i).split(",").map(_.toDouble)
  }

  "HierarchyCSVReader" should {
    "parse a hierarchy CSV file" in {
      val hierarchy = HierarchyCSVReader.parse(Fixtures.hierarchyFile)
      hierarchy should not be null
      hierarchy.n shouldBe 135
      hierarchy.length shouldBe 134
      hierarchy(hierarchy.length - 1).cardinality shouldBe 135
      for i <- hierarchy.indices do
        hierarchy(i) shouldEqual Fixtures.hierarchy(i)
    }

    "parse a file written with HierarchyCSVWriter" in {
      val file = Files.createTempFile("tmp-hierarchy", ".csv").toFile
      file.deleteOnExit()
      HierarchyCSVWriter.write(file.getCanonicalPath, Fixtures.hierarchy)

      val hierarchy = HierarchyCSVReader.parse(file)
      hierarchy shouldEqual Fixtures.hierarchy
    }
  }

  "HierarchyCSVWriter" should {
    "write a CSV file" in {
      val file = Files.createTempFile("tmp-hierarchy", ".csv").toFile
      file.deleteOnExit()
      HierarchyCSVWriter.write(file, Fixtures.hierarchy)

      val reader = new BufferedReader(new FileReader(file))
      val lines = reader.lines().collect(Collectors.toList()).asScala.toArray
      reader.close()

      val indices = Fixtures.hierarchy.indices
      val lastIndex = Fixtures.hierarchy.length - 1

      lines.length shouldBe indices.length
      lines.getCsvValue(lastIndex, 3) shouldBe Fixtures.hierarchy(lastIndex).cardinality
      for i <- indices do
        val values = lines.parsedRow(i)
        val expected = Fixtures.hierarchy(i)
        values(0) shouldBe expected.cId1
        values(1) shouldBe expected.cId2
        values(2) shouldBe expected.distance
        values(3) shouldBe expected.cardinality
    }
  }
}
