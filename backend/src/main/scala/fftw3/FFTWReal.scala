package fftw3

import com.sun.jna.*

import java.nio.IntBuffer
import fftw3.FFTW3Library as FFTW
import FFTW.INSTANCE as fftw

import scala.util.Using


object FFTWReal {

  private final type FFTWProvider = Array[Int] => FFTWReal

  final given defaultFfftwProvider: FFTWProvider = new FFTWReal(_)

  private final val SIZE_OF_DOUBLE: Int = 8

  /** Convolve array `b` over array `b` using the FFTW native library.
   *
   * @param a First array (length n)
   *          The first array to convolve.
   * @param b Second array (length m)
   *          The second array to convolve.
   * @return The convolution of a and b of length n + m - 1.
   */
  def fftwConvolve(a: Array[Double], b: Array[Double])(using fftProvider: FFTWProvider): Array[Double] = {
    if a.length == 0 || b.length == 0 then
      return Array.empty

    val na = a.length
    val nb = b.length
    val n = nextFastLength(na + nb - 1)

    val aPad = Array.ofDim[Double](n)
    val bPad = Array.ofDim[Double](n)
    Array.copy(a, 0, aPad, 0, a.length)

    // compute reverse conjugate of b and copy to padded array
    for i <- b.indices do
      bPad(i) = b(b.length - i - 1)

    Using.resource(fftProvider(Array(n))) { fft =>
      // convert to Fourier space
      val sp1 = fft.forwardTransform(aPad)
      val sp2 = fft.forwardTransform(bPad)

      // multiply in Fourier space
      multiplyFourierArrays(sp1, sp2, sp1)

      // convert back to real space and remove padding
      val result = fft.backwardTransform(sp1)
      result.slice(0, na + nb - 1)
    }
  }

  private def nextFastLength(n: Int): Int = {
    val FACTORS = Array(2, 3, 5)
    var m = n
    while true do
      var r = m
      for f <- FACTORS do
        while r > 1 && r % f == 0 do
          r /= f
      if r == 1 then
        return m
      else
        m += 1
    m
  }

  private def multiplyFourierArrays(src1: Array[Double], src2: Array[Double], dst: Array[Double]): Unit = {
    for (i <- 0 until src1.length / 2) {
      // src and dst arrays might be aliased; create temporary variables
      val re = src1(2 * i + 0) * src2(2 * i + 0) - src1(2 * i + 1) * src2(2 * i + 1)
      val im = src1(2 * i + 0) * src2(2 * i + 1) + src1(2 * i + 1) * src2(2 * i + 0)
      dst(2 * i + 0) = re
      dst(2 * i + 1) = im
    }
  }
}


// Note that arrays are packed in row major order, so the last index is the fastest varying.
// Thus, if indices are computed as (i = Lx*y + x) then one should use dim(Ly, Lx)
final class FFTWReal(dims: Array[Int], flags: Int = FFTW.FFTW_ESTIMATE) extends AutoCloseable {
  // optimization ideas:
  // - store wisdom to disk on close and load from disk on open (if exists) (survives JVM restarts):
  //   int fftw_export_wisdom_to_filename(const char *filename);
  //   int fftw_import_wisdom_from_filename(const char *filename);
  // - store transformed result in the input array (if possible) to avoid copying

  import FFTWReal.*

  /** Number of dimensions. */
  val rank: Int = dims.length

  /** Number of doubles in real-space array */
  val n: Int = dims.product

  /** number of doubles in reciprocal space array */
  val nRecip: Int = 2 * (dims.slice(0, rank - 1).product * (dims(rank - 1) / 2 + 1)) // fftw compresses last index

  private val dimensions = dims.map(_.toDouble)
  private val inBytes = SIZE_OF_DOUBLE * n
  private val outBytes = SIZE_OF_DOUBLE * nRecip

  private val in = fftw.fftw_malloc(new NativeLong(inBytes))
  private val out = fftw.fftw_malloc(new NativeLong(outBytes))
  private val inbuf = in.getByteBuffer(0, inBytes).asDoubleBuffer()
  private val outbuf = out.getByteBuffer(0, outBytes).asDoubleBuffer()

  private val planForward = fftw.fftw_plan_dft_r2c(dims.length, IntBuffer.wrap(dims), inbuf, outbuf, flags | FFTW.FFTW_DESTROY_INPUT)
  private val planBackward = fftw.fftw_plan_dft_c2r(dims.length, IntBuffer.wrap(dims), outbuf, inbuf, flags | FFTW.FFTW_DESTROY_INPUT)

  def forwardTransform(a: Array[Double]): Array[Double] = {
    require(a.length == n)

    val ap = Array.ofDim[Double](nRecip)
    forward(a, ap)
    ap
  }

  def backwardTransform(a: Array[Double]): Array[Double] = {
    require(a.length == nRecip, s"Expected length $nRecip, got ${a.length}")

    val ap = Array.ofDim[Double](n)
    backward(a, ap)
    ap
  }

//  def convolve(a: Array[Double], b: Array[Double]): Array[Double] = {
//    require(a.length == n && b.length == n)
//
//    val ap = Array.ofDim[Double](nRecip)
//    val bp = Array.ofDim[Double](nRecip)
//    forward(a, ap)
//    forward(b, bp)
//    // conjugateFourierArray(bp, bp) // affects sign: c(j) = \sum_i a(i) b(i-j)
//    multiplyFourierArrays(ap, bp, ap)
//    val dst = Array.ofDim[Double](n)
//    backward(ap, dst)
//    dst
//  }

  override def close(): Unit =
    destroy()

  @inline
  private def forward(src: Array[Double], dst: Array[Double]): Unit = {
    inbuf.clear()
    inbuf.put(src)
    fftw.fftw_execute(planForward)
    outbuf.rewind()
    outbuf.get(dst)

    // continuum normalization: f(k) = \int dx^d f(x) e^(i k x)
    val scale = dimensions.product / dims.product
    for (i <- dst.indices) dst(i) *= scale
  }

  @inline
  private def backward(src: Array[Double], dst: Array[Double]): Unit = {
    outbuf.clear()
    outbuf.put(src)
    fftw.fftw_execute(planBackward)
    inbuf.rewind()
    inbuf.get(dst)

    // continuum normalization: f(x) = (2 Pi)^(-d) \int dk^d f(k) e^(- i k x)
    val scale = 1 / dimensions.product
    for (i <- dst.indices) dst(i) *= scale
  }

  private def destroy(): Unit = {
    fftw.fftw_destroy_plan(planForward)
    fftw.fftw_destroy_plan(planBackward)
    fftw.fftw_free(in)
    fftw.fftw_free(out)
  }
}
