package de.hpi.fgis.bloomfilter.hashing

import de.hpi.fgis.bloomfilter.hashing.YonikMurmurHash3.LongPair
import de.hpi.fgis.bloomfilter.hashing.{MurmurHash3Generic, YonikMurmurHash3}
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

import scala.language.adhocExtensions

object MurmurHash3ScalaVsJavaSpec extends Properties("MurmurHash3ScalaVsJavaSpec") {

  property("murmurhash3_x64_128") = forAll { (key: Array[Byte]) =>
    val tuple = MurmurHash3Generic.murmurhash3_x64_128(key, 0, key.length, 0)
    val pair = new LongPair
    YonikMurmurHash3.murmurhash3_x64_128(key, 0, key.length, 0, pair)
    pair.val1 == tuple._1 && pair.val2 == tuple._2
  }
}
