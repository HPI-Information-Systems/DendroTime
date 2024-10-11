package de.hpi.fgis.bloomfilter.mutable._128bit

import de.hpi.fgis.bloomfilter.mutable._128bit.BloomFilter
import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}
import org.scalatest.matchers.should

import java.io.*
import scala.language.adhocExtensions

class BloomFilterSerializationSpec extends Properties("BloomFilter_128bit") with should.Matchers {

  def genListElems[A](max: Long)(implicit aGen: Gen[A]): Gen[List[A]] =
    Gen.posNum[Int].map(_ % max).flatMap(i => Gen.listOfN(math.min(i, Int.MaxValue).toInt, aGen))

  private val gen = for {
    size <- Gen.oneOf[Long](1, 1000 /*, Int.MaxValue.toLong + 1*/)
    indices <- genListElems[Long](size)(Gen.chooseNum(0, size))
  } yield (size, indices)

  property("writeTo & readFrom") = forAll(gen) {
    case (size: Long, indices: List[Long]) =>
      val initial = BloomFilter[Long](size, 0.01)
      indices.foreach(initial.add)

      val file = File.createTempFile("bloomFilterSerialized", ".tmp")
      val out = new BufferedOutputStream(new FileOutputStream(file), 10 * 1000 * 1000)
      initial.writeTo(out)
      out.close()
      val in = new BufferedInputStream(new FileInputStream(file), 10 * 1000 * 1000)
      val sut = BloomFilter.readFrom[Long](in)
      in.close()

      sut.approximateElementCount() shouldEqual initial.approximateElementCount()

      val result = indices.forall(sut.mightContain)

      file.delete()
      initial.dispose()
      sut.dispose()

      result
  }
}
