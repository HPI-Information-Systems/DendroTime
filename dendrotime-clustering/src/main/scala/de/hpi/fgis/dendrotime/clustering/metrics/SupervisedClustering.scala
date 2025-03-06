package de.hpi.fgis.dendrotime.clustering.metrics

import org.apache.commons.math3.special.Gamma

import scala.collection.{IndexedSeq, mutable}
import scala.reflect.ClassTag
import org.apache.commons.math3.util.FastMath

object SupervisedClustering {

  /**
   * Computes the Adjusted Rand Score (ARI) between two labelings. Alias for `adjustedRandScore()`.
   *
   * @param trueLabels Ground truth class labels as a reference.
   * @param predLabels Predicted cluster labels to evaluate.
   * @tparam T type of the labels (e.g. String, Int)
   * @return Similarity score between -0.5 and 1.0. A random labeling has an ARI of 0.0. 1.0 is perfect.
   */
  def ari[T : Ordering](trueLabels: IndexedSeq[T], predLabels: IndexedSeq[T]): Double =
    adjustedRandScore(trueLabels, predLabels)

  /**
   * Computes the Adjusted Rand Score (ARI) between two labelings.
   *
   * This implementation is based on the scikit-learn implementation:
   * https://github.com/scikit-learn/scikit-learn/blob/429d67aa4ca853ab7aa5358cf4e7637345e51e08/sklearn/metrics/cluster/_supervised.py#L348
   *
   * @param trueLabels Ground truth class labels as a reference.
   * @param predLabels Predicted cluster labels to evaluate.
   * @tparam T type of the labels (e.g. String, Int)
   * @return Similarity score between -0.5 and 1.0. A random labeling has an ARI of 0.0. 1.0 is perfect.
   */
  def adjustedRandScore[T : Ordering](trueLabels: IndexedSeq[T], predLabels: IndexedSeq[T]): Double = {
    require(
      trueLabels.length == predLabels.length,
      s"Both label arrays must have the same length (${trueLabels.length} != ${predLabels.length})."
    )
    val (contingency, nClasses, nClusters) = contingencyMatrix(trueLabels, predLabels)

    // use BigInt to avoid overflow/underflow
    val n = BigInt(trueLabels.length)
    val sumSquares = contingency.flatten.foldLeft(BigInt(0))((sum, x) => sum + x*x)
    val nC = Array.ofDim[BigInt](nClasses)
    for i <- 0 until nClasses do
      nC(i) = contingency(i).sum
    val nK = Array.ofDim[BigInt](nClusters)
    for j <- 0 until nClusters do
      nK(j) = contingency.foldLeft(0L)((a, b) => a + b(j))

    // 1,1
    val tp = sumSquares - n
    // 0,1
    val fp = contingency.map(row => row.zip(nK).map(_ * _).sum).sum - sumSquares
    // 1,0
    val fn = contingency.zip(nC).map((row, nCj) => row.map(_ * nCj).sum).sum - sumSquares
    // 0,0
    val tn = n*n - fp - fn - sumSquares

    if fn == 0 && fp == 0 then
      1.0
    else
      (BigDecimal(tp * tn - fn * fp) / BigDecimal((tp + fn) * (fn + tn) + (tp + fp) * (fp + tn))).doubleValue * 2.0
  }

  /**
   * Computes the Adjusted Mutual Information score (AMI) between two labelings. Alias for `adjustedMutualInformation()`.
   */
  def ami[T : Ordering](trueLabels: IndexedSeq[T], predLabels: IndexedSeq[T]): Double =
    adjustedMutualInformation(trueLabels, predLabels)

  /**
   * Computes the Adjusted Mutual Information score (AMI) between two labelings. Alias for `adjustedMutualInformation()`.
   */
  def ami(trueLabels: IndexedSeq[Int], predLabels: IndexedSeq[Int]): Double =
    adjustedMutualInformation(trueLabels, predLabels)

  /**
   * Computes the Adjusted Mutual Information score (AMI) between two labelings.
   *
   * This implementation is based on the scikit-learn implementation:
   * https://github.com/scikit-learn/scikit-learn/blob/d666202a9349893c1bd106cc9ee0ff0a807c7cf3/sklearn/metrics/cluster/_supervised.py#L934
   * We only implement the 'arithmetic' average method.
   *
   * This metric is independent of the absolute values of the labels, and symmetric.
   *
   * **Calculating the AMI is an order of magnitude slower than calculating the ARI!**
   *
   * @param trueLabels Ground truth class labels as a reference.
   * @param predLabels Predicted cluster labels to evaluate.
   * @return Similarity score between -0.5 and 1.0. A random labeling has an ARI of 0.0. 1.0 is perfect.
   */
  def adjustedMutualInformation[T : Ordering](trueLabels: IndexedSeq[T], predLabels: IndexedSeq[T]): Double = {
    val trueLabelsInt = trueLabels.map(_.hashCode())
    val predLabelsInt = predLabels.map(_.hashCode())
    adjustedMutualInformation(trueLabelsInt, predLabelsInt)
  }

  /**
   * Computes the Adjusted Mutual Information score (AMI) between two labelings.
   *
   * This implementation is based on the scikit-learn implementation:
   * https://github.com/scikit-learn/scikit-learn/blob/d666202a9349893c1bd106cc9ee0ff0a807c7cf3/sklearn/metrics/cluster/_supervised.py#L934
   * We only implement the 'arithmetic' average method.
   *
   * This metric is independent of the absolute values of the labels, and symmetric.
   *
   * **Calculating the AMI is an order of magnitude slower than calculating the ARI!**
   *
   * @param trueLabels Ground truth class labels as a reference.
   * @param predLabels Predicted cluster labels to evaluate.
   * @return Similarity score between -0.5 and 1.0. A random labeling has an ARI of 0.0. 1.0 is perfect.
   */
  def adjustedMutualInformation(trueLabels: IndexedSeq[Int], predLabels: IndexedSeq[Int]): Double = {
    require(
      trueLabels.length == predLabels.length,
      s"Both label arrays must have the same length (${trueLabels.length} != ${predLabels.length})."
    )
    val (contingency, nClasses, nClusters) = contingencyMatrix(trueLabels, predLabels)

    // special case where the data is not split --> perfect match
    if nClasses == 1 && nClusters == 1 || nClasses == 0 && nClusters == 0 then
      1.0

    else
      val mi = mutualInformationScore(contingency, nClasses, nClusters)
      val emi = expectedMutualInformation(contingency, trueLabels.length, nClasses, nClusters)
      val entropyTrue = entropy(trueLabels)
      val entropyPred = entropy(predLabels)
      val normalizer = generalizedAverage(entropyTrue, entropyPred)

      // Avoid 0.0 / 0.0 when expectation equals maximum, i.e.a perfect match.
      // normalizer should always be >= emi, but because of floating -point
      // representation, sometimes emi is slightly larger. Correct this
      // by preserving the sign.
      var denominator = normalizer - emi
      if denominator < 0 then
        denominator = FastMath.min(denominator, -Double.MinPositiveValue)
      else
        denominator = FastMath.max(denominator, Double.MinPositiveValue)
      (mi - emi) / denominator
  }

  private def mutualInformationScore(contingency: Array[Array[Long]], nClasses: Int, nClusters: Int): Double = {
    val nzx = contingency.map(_.count(_ != 0))
    val nzy = contingency.transpose.map(_.count(_ != 0))
    val sum = contingency.flatten.sum
    val pi = contingency.map(_.sum)
    val pj = contingency.transpose.map(_.sum)

    // any labelling containing a single cluster (zero entropy) implies MI = 0
    if pi.length == 1 || pj.length == 1 then
      0.0
    else
      val outerLogSum = FastMath.log(pi.sum.toDouble) + FastMath.log(pj.sum.toDouble)
      val mis =
        for
          i <- 0 until nClasses
          if nzx(i) != 0
          j <- 0 until nClusters
          if nzy(j) != 0
        yield
          val outer = -FastMath.log((pi(i) * pj(j)).toDouble) + outerLogSum
          val v = contingency(i)(j)
          // should be guaranteed != 0
          val logNm = FastMath.log(v.toDouble)
          val nm = v / sum
          val result = nm * (logNm - FastMath.log(sum.toDouble)) + nm * outer
          if FastMath.abs(result) < Double.MinPositiveValue then
            0.0
          else
            result
      val misSum = mis.sum
      if misSum < 0.0 then
        0.0
      else
        misSum
  }

  private def expectedMutualInformation(contingency: Array[Array[Long]], n: Int, nClasses: Int, nClusters: Int): Double = {
    val a = contingency.map(_.sum.toInt)
    val b = contingency.transpose.map(_.sum.toInt)

    // any labelling with zero entropy implies EMI = 0
    if a.length == 1 || b.length == 1 then
      0.0

    else
      var emi = 0.0
      // There are three major terms to the EMI equation, which are multiplied to and then summed over varying nij values.
      // While nijs[0] will never be used, having it simplifies the indexing.
      val nijs = Array.tabulate(Math.max(a.max, b.max) + 1)(_.toDouble)
      nijs(0) = 1.0
      // Stops divide by zero warnings. As it's not used, no issue.
      // term1 is nij / N
      val term1 = nijs.map(_ / n)
      // term2 is log((N * nij) / (a * b)) == log(N * nij) - log(a * b)
      val logA = a.map(e => Math.log(e.toDouble))
      val logB = b.map(e => Math.log(e.toDouble))
      // term2 uses log(N * nij) = log(N) + log(nij)
      val logNnij = nijs.map(Math.log(n) + Math.log(_))
      // term3 is large, and involved many factorials. Calculate these in log space to stop overflows
      val gln_a = gammalnMap(a, _ + 1)
      val gln_b = gammalnMap(b, _ + 1)
      val gln_Na = gammalnMap(a, n - _ + 1)
      val gln_Nb = gammalnMap(b, n - _ + 1)
      val gln_Nnij = gammalnMap(nijs, _ + 1).zip(gammalnMap(Range(0, n), _ + 1)).map(_ + _)

      // emi itself is a summation over the various values.
      for i <- 0 until nClasses do
        for j <- 0 until nClusters do
          val start = Math.max(1, a(i) - n + b(j))
          val end = Math.min(a(i), b(j)) + 1
          for nij <- start until end do
            val term2 = logNnij(nij) - logA(i) - logB(j)
            // Numerators are positive, denominators are negative
            val gln = gln_a(i) + gln_b(j) + gln_Na(i) + gln_Nb(j)
              - gln_Nnij(nij) - Gamma.logGamma(a(i) - nij + 1)
              - Gamma.logGamma(b(j) - nij + 1)
              - Gamma.logGamma(n - a(i) - b(j) + nij + 1)
            val term3 = Math.exp(gln)
            emi += (term1(nij) * term2 * term3)
      emi
  }

  /** Approximate gammaln with x * log(x) */
  private def gammalnMap[T](x: IndexedSeq[T], fn: Double => Double)(using num: Numeric[T]): IndexedSeq[Double] =
    x.map{ e =>
      val et = fn(num.toDouble(e))
      et * Math.log(et)
    }

  private def entropy(x: IndexedSeq[Int]): Double = {
    if x.isEmpty then
      1.0

    else
      val pi = mutable.HashMap.empty[Int, Int]
      for i <- x do
        if !pi.contains(i) then
          pi(i) = 0
        pi(i) += 1

      // single cluster --> zero entropy
      if pi.size == 1 then
        1.0

      else
        // log(a / b) should be calculated as log (a) - log(b) for possible loss of precision
        val sum = pi.values.sum.toDouble
        val sumLog = Math.log(sum)
        pi.values.map(_ / sum).zip(pi.values.map(e => Math.log(e) - sumLog)).map(_ * _).sum * - 1.0
  }

  /** Only mean ('arithmetic') is currently supported! */
  @inline
  private def generalizedAverage(d1: Double, d2: Double): Double = (d1 + d2) / 2.0

  private def contingencyMatrix[T : Ordering](trueLabels: IndexedSeq[T], predLabels: IndexedSeq[T]): (Array[Array[Long]], Int, Int) = {
    val classes = trueLabels.distinct.sorted.zipWithIndex.toMap
    val clusters = predLabels.distinct.sorted.zipWithIndex.toMap
    val contingency = Array.ofDim[Long](classes.size, clusters.size)
    for i <- trueLabels.indices do
      val trueClass = classes(trueLabels(i))
      val predClass = clusters(predLabels(i))
      contingency(trueClass)(predClass) += 1
    (contingency, classes.size, clusters.size)
  }
}
