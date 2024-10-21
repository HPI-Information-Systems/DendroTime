package de.hpi.fgis.bloomfilter.mutable._128bit

import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}
import org.scalatest.matchers.should

import java.io.*
import scala.language.adhocExtensions

class BloomFilterSerializationSpec extends Properties("BloomFilter_128bit") with should.Matchers {

  private def genListElems[A](max: Long)(implicit aGen: Gen[A]) =
    Gen.posNum[Int].map(_ % max).flatMap(i => Gen.listOfN(math.min(i, Int.MaxValue).toInt, aGen))

  private val gen = for {
    size <- Gen.oneOf[Long](1, 1000 /*, Int.MaxValue.toLong + 1*/)
    indices <- genListElems[Long](size)(Gen.chooseNum(0, size))
  } yield (size, indices)

  property("writeTo & readFrom") = forAll(gen) {
    case (size: Long, indices: List[Long]) =>
      val initial = BloomFilter128[Long](size, 0.01)
      indices.foreach(initial.add)

      val file = File.createTempFile("bloomFilterSerialized", ".tmp")
      val out = new BufferedOutputStream(new FileOutputStream(file), 10 * 1000 * 1000)
      initial.writeTo(out)
      out.close()
      val in = new BufferedInputStream(new FileInputStream(file), 10 * 1000 * 1000)
      val sut = BloomFilter128.readFrom[Long](in)
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
      val initial = BloomFilter128[Long](size, 0.01)
      indices.foreach(initial.add)
      val file = File.createTempFile("bloomFilterSerialized", ".tmp")
      val out = new BufferedOutputStream(new FileOutputStream(file), 10 * 1000 * 1000)
      val oos = new ObjectOutputStream(out)
      oos.writeObject(initial)
      oos.close()
      out.close()
      val in = new BufferedInputStream(new FileInputStream(file), 10 * 1000 * 1000)
      val ois = new ObjectInputStream(in)
      val deserialized = ois.readObject()
      ois.close()
      in.close()

      deserialized should not be null
      deserialized should be(a[BloomFilter128[Long]])
      val sut = deserialized.asInstanceOf[BloomFilter128[Long]]

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
