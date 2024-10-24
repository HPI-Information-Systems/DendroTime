package de.hpi.fgis.dendrotime.clustering.metrics

object AdjustedRandScore {

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
  def apply[T <: AnyVal](trueLabels: Array[T], predLabels: Array[T])(using Ordering[T]): Double = {
    require(trueLabels.length == predLabels.length, "Both label arrays must have the same length.")

    val classes = trueLabels.distinct.sorted.zipWithIndex.toMap
    val clusters = predLabels.distinct.sorted.zipWithIndex.toMap
    val contingency = Array.ofDim[Long](classes.size, clusters.size)
    for i <- trueLabels.indices do
      val trueClass = classes(trueLabels(i))
      val predClass = clusters(predLabels(i))
      contingency(trueClass)(predClass) += 1

    // use BigInt to avoid overflow/underflow
    val n = BigInt(trueLabels.length)
    val sumSquares = contingency.flatten.foldLeft(BigInt(0))((sum, x) => sum + x*x)
    val nC = Array.ofDim[BigInt](classes.size)
    for i <- 0 until classes.size do
      nC(i) = contingency(i).sum
    val nK = Array.ofDim[BigInt](clusters.size)
    for j <- 0 until clusters.size do
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
}
