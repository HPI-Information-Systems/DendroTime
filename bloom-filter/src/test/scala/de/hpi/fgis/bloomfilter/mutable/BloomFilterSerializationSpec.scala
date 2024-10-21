package de.hpi.fgis.bloomfilter.mutable

import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}
import org.scalatest.matchers.should.Matchers

import java.io.*
import scala.language.adhocExtensions

class BloomFilterSerializationSpec extends Properties("BloomFilter64") with Matchers {
  private def genListElems[A](max: Long)(implicit aGen: Gen[A]) =
    Gen
      .posNum[Int]
      .map(_ % max)
      .flatMap(i => Gen.listOfN(math.min(i, Int.MaxValue).toInt, aGen))

  private val gen = for {
    size <- Gen.oneOf[Long](1, 1000 /*, Int.MaxValue.toLong + 1*/)
    indices <- genListElems[Long](size)(Gen.chooseNum(0, size - 1))
  } yield (size, indices)

  property("writeTo & readFrom") = forAll(gen) {
    case (size: Long, indices: List[Long]) =>
      val initial = BloomFilter64[Long](size, 0.01)
      indices.foreach(initial.add)

      val file = File.createTempFile("bloomFilterSerialized", ".tmp")
      val out =
        new BufferedOutputStream(new FileOutputStream(file), 10 * 1000 * 1000)
      initial.writeTo(out)
      out.close()
      val in =
        new BufferedInputStream(new FileInputStream(file), 10 * 1000 * 1000)
      val sut = BloomFilter64.readFrom[Long](in)
      in.close()

      sut.approximateElementCount shouldEqual initial.approximateElementCount

      val result = indices.forall(sut.mightContain)

      file.delete()
      initial.dispose()
      sut.dispose()

      result
  }

  property("supports java serialization") = forAll(gen) {
    case (size, indices) =>
      val initial = BloomFilter64[Long](size, 0.01)
      indices.foreach(initial.add)
      val file = File.createTempFile("bloomFilterSerialized", ".tmp")
      val out =
        new BufferedOutputStream(new FileOutputStream(file), 10 * 1000 * 1000)
      val oos = new ObjectOutputStream(out)
      oos.writeObject(initial)
      oos.close()
      out.close()
      val in =
        new BufferedInputStream(new FileInputStream(file), 10 * 1000 * 1000)
      val ois = new ObjectInputStream(in)
      val desrialized = ois.readObject()
      ois.close()
      in.close()

      desrialized should not be null
      desrialized should be(a[BloomFilter64[Long]])
      val sut = desrialized.asInstanceOf[BloomFilter64[Long]]

      sut.numberOfBits shouldEqual initial.numberOfBits
      sut.numberOfHashes shouldEqual initial.numberOfHashes
      sut.approximateElementCount shouldEqual initial.approximateElementCount

      val result = indices.forall(sut.mightContain)

      file.delete()
      initial.dispose()
      sut.dispose()

      result
    }
}
