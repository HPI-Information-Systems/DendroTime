package fftw3

import scala.collection.mutable

trait FFTWProvider extends (Int => FFTWReal)

object FFTWProvider {
  /** `FFTWReal.fftwConvolve` takes care of closing the created instances automatically! */
  final given defaultFfftwProvider: FFTWProvider = n => new FFTWReal(Array(n))

  def localCaching(cacheSize: Int): FFTWProvider = new LocalCachingFFTWProvider(cacheSize)

  private sealed trait CacheEntry

  private case object Empty extends CacheEntry

  private final case class Occupied(size: Int, fftw: FFTWReal) extends CacheEntry

  /** This class is not thread-safe, but still needs to be correctly disposed of! */
  final class LocalCachingFFTWProvider private[FFTWProvider](cacheSize: Int) extends FFTWProvider with AutoCloseable {
    private val cache = Array.fill[CacheEntry](cacheSize)(Empty)
    private val lastAccessed = Array.fill(cacheSize)(0L)
    private val lut = mutable.HashMap.empty[Int, Int]

    def apply(size: Int): FFTWReal = lut.get(size) match {
      case Some(idx) =>
        lastAccessed(idx) = System.nanoTime()
        cache(idx) match {
          case Occupied(_, fftw) => fftw
          case Empty =>
            //            println(s"FFTW-CACHE: Adding FFTW instance for size $size at index $idx")
            val fftw = new FFTWReal(Array(size))
            cache(idx) = Occupied(size, fftw)
            lut(size) = idx
            fftw
        }
      case None =>
        val idx = lastAccessed.lazyZip(lastAccessed.indices).minBy(_._1)._2
        cache(idx) match {
          case Occupied(oldSize, fftw) =>
            //            println(s"FFTW-CACHE: Replacing cache entry $idx (size=$oldSize, age=${lastAccessed(idx)}) FFTW instance for size $size")
            fftw.close()
            lut -= oldSize
          case Empty =>
          //            println(s"FFTW-CACHE: Adding FFTW instance for size $size at index $idx")
        }
        val fftw = new FFTWReal(Array(size))
        cache(idx) = Occupied(size, fftw)
        lastAccessed(idx) = System.nanoTime()
        lut(size) = idx
        fftw
    }

    override def close(): Unit = {
      //      println(s"FFTW-CACHE: Closing ${lut.size} cached FFTW instances")
      var i = 0
      while i < cacheSize do {
        cache(i) match {
          case Occupied(_, fftw) =>
            fftw.close()
            cache(i) = Empty
          case Empty =>
        }
        lastAccessed(i) = 0L
        i += 1
      }
      lut.clear()
    }
  }
}
