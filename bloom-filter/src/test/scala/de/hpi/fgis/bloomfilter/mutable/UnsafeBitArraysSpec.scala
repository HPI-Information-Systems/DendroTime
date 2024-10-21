package de.hpi.fgis.bloomfilter.mutable

import org.scalacheck.Prop.*
import org.scalacheck.Test.Parameters
import org.scalacheck.{Gen, Properties}

import scala.language.adhocExtensions

class UnsafeBitArraysSpec extends Properties("UnsafeBitArray") {

  override def overrideParameters(p: Parameters): Parameters =
    super.overrideParameters(p).withMinSuccessfulTests(100)

  private def genListElems[A](max: Long)(aGen: Gen[A]): Gen[List[A]] =
    Gen
      .posNum[Long]
      .map(i => (i % max) + 1)
      .flatMap(i => Gen.listOfN(math.min(i, Int.MaxValue).toInt, aGen))

  private val genUnion = for {
    size <- Gen.oneOf[Long](1, 1000, Int.MaxValue, Int.MaxValue * 2L)
    indices <- genListElems[Long](size)(Gen.chooseNum(0, size-1))
    thatIndices <- genListElems[Long](size)(Gen.chooseNum(0, size-1))
  } yield (size, indices, thatIndices)

  private val genIntersection = for {
    size <- Gen.oneOf[Long](2, 1000, Int.MaxValue, Int.MaxValue * 2L)
    indices <- genListElems(size)(Gen.chooseNum(0L, size/2-1))
    thatIndices <- genListElems(size)(Gen.chooseNum(size/2L, size-1))
    commonIndices <- genListElems(size)(Gen.chooseNum(0L, size-1))
  } yield (size, indices, thatIndices, commonIndices)

  property("|") = forAllNoShrink(genUnion) {
    case (size: Long, indices: List[Long], thatIndices: List[Long]) =>
      val array = new UnsafeBitArray(size)
      indices.foreach(array.set)

      val thatArray = new UnsafeBitArray(size)
      thatIndices.foreach(thatArray.set)

      val sut = array | thatArray
      val allIndices = (indices ++ thatIndices).toSet
      val result = allIndices.forall(sut.get) && allIndices.size == sut.getBitCount

      array.dispose()
      thatArray.dispose()
      sut.dispose()

      result
}

  property("&") = forAllNoShrink(genIntersection) {
    case (size: Long, indices: List[Long], thatIndices: List[Long], commonIndices: List[Long]) =>
      val array = new UnsafeBitArray(size)
      indices.foreach(array.set)

      val thatArray = new UnsafeBitArray(size)
      thatIndices.foreach(thatArray.set)

      commonIndices.foreach(x => {
        array.set(x)
        thatArray.set(x)
      })
      val sut = array & thatArray
      val result = commonIndices.forall(sut.get) && commonIndices.toSet.size == sut.getBitCount

      array.dispose()
      thatArray.dispose()
      sut.dispose()

      result
  }
}
