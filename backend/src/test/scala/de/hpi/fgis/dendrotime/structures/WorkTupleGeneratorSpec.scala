package de.hpi.fgis.dendrotime.structures

import org.scalatest.Assertion
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec

class WorkTupleGeneratorSpec extends AnyWordSpec with should.Matchers {

  "The WorkTupleGnerator" should {
    "generate the correct tuples even when more IDs are added" in {
      val gen = new WorkTupleGenerator

      def testNext(id1: Long, id2: Long): Assertion = {
        gen.hasNext shouldBe true
        gen.next() shouldEqual(id1, id2)
      }

      gen.hasNext shouldBe false
      gen.addOne(0L)
      gen.hasNext shouldBe false

      gen.addOne(1L)
      testNext(0L, 1L)
      gen.hasNext shouldBe false
      gen.index shouldBe 1

      gen.addAll(Seq(2L, 3L))
      gen.index shouldBe 1

      testNext(0L, 2L)
      testNext(1L, 2L)
      testNext(0L, 3L)
      testNext(1L, 3L)
      testNext(2L, 3L)
      gen.hasNext shouldBe false

      gen.addOne(4L)
      gen.sizeIds shouldBe 5
      gen.sizeTuples shouldBe 10

      testNext(0L, 4L)
      testNext(1L, 4L)
      testNext(2L, 4L)
      testNext(3L, 4L)
      gen.hasNext shouldBe false
    }
  }
}
