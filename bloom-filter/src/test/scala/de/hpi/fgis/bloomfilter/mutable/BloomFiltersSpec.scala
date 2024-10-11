package de.hpi.fgis.bloomfilter.mutable

import de.hpi.fgis.bloomfilter.CanGenerateHashFrom
import de.hpi.fgis.bloomfilter.mutable.BloomFilter
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.forAll
import org.scalacheck.Test.Parameters
import org.scalacheck.commands.Commands
import org.scalacheck.{Arbitrary, Gen, Prop, Properties}

import scala.language.adhocExtensions

class BloomFiltersSpec extends Properties("BloomFilters") {

  val maxNumElems = 10

  def genListOfMaxTenElems[A](implicit aGen: Gen[A]): Gen[List[A]] =
    Gen.posNum[Int].map(_ % maxNumElems).flatMap(Gen.listOfN(_, aGen))

  property("union") = forAll(genListOfMaxTenElems(arbitrary[Long]), genListOfMaxTenElems(arbitrary[Long])) {
    (leftElements: List[Long], rightElements: List[Long]) =>
      val leftBloomFilter = BloomFilter[Long](maxNumElems, 0.01)
      leftElements foreach leftBloomFilter.add
      val rightBloomFilter = BloomFilter[Long](maxNumElems, 0.01)
      rightElements foreach rightBloomFilter.add
      val unionBloomFilter = leftBloomFilter `union` rightBloomFilter
      val result =
        (leftElements ++ rightElements) forall unionBloomFilter.mightContain
      leftBloomFilter.dispose()
      rightBloomFilter.dispose()
      unionBloomFilter.dispose()
      result
  }

  property("intersect") = forAll(genListOfMaxTenElems(arbitrary[Long]), genListOfMaxTenElems(arbitrary[Long])) {
    (leftElements: List[Long], rightElements: List[Long]) =>
      val leftBloomFilter = BloomFilter[Long](maxNumElems, 0.01)
      leftElements foreach leftBloomFilter.add
      val rightBloomFilter = BloomFilter[Long](maxNumElems, 0.01)
      rightElements foreach rightBloomFilter.add
      val unionBloomFilter = leftBloomFilter `intersect` rightBloomFilter
      val intersectElems = leftElements.toSet intersect rightElements.toSet
      val result = intersectElems forall unionBloomFilter.mightContain
      leftBloomFilter.dispose()
      rightBloomFilter.dispose()
      unionBloomFilter.dispose()
      result
  }
}