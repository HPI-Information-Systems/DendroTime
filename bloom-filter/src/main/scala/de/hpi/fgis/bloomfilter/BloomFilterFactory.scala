package de.hpi.fgis.bloomfilter

import de.hpi.fgis.bloomfilter.mutable.UnsafeBitArray
import org.apache.commons.math3.util.FastMath.{ceil, log}

import java.io.{DataInputStream, InputStream}

/**
 * Mixin for BloomFilter factories that provides common functionality.
 */
trait BloomFilterFactory {
  protected def optimalNumberOfBits(numberOfItems: Long, falsePositiveRate: Double): Long =
    ceil(-1 * numberOfItems * log(falsePositiveRate) / log(2) / log(2)).toLong

  protected def optimalNumberOfHashes(numberOfItems: Long, numberOfBits: Long): Int =
    ceil(numberOfBits / numberOfItems * log(2)).toInt

  protected def prepareBitArray(in: InputStream): BFArguments =
    val din = new DataInputStream(in)
    val numberOfBits = din.readLong()
    val numberOfHashes = din.readInt()
    val bits = new UnsafeBitArray(numberOfBits)
    bits.readFrom(in)
    BFArguments(numberOfBits, numberOfHashes, bits)

  protected case class BFArguments(nBits: Long, nHashes: Int, bits: UnsafeBitArray)
}
