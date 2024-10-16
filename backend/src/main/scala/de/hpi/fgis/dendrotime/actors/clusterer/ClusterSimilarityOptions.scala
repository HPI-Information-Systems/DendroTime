package de.hpi.fgis.dendrotime.actors.clusterer

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}

object ClusterSimilarityOptions {
  /**
   * Similarity function for two BloomFilters representing a level of the hierarchy.
   */
  sealed trait Similarity extends ((BloomFilter[Int], BloomFilter[Int]) => Double)

  object Similarity {
    def apply(name: String): Similarity = name match {
      case "set-jaccard" => SetJaccardSimilarity
      case "level-jaccard" => LevelJaccardSimilarity
      case "level-equality" => LevelEqualitySimilarity
      case _ => throw new IllegalArgumentException(s"Unsupported similarity function: $name")
    }

    object SetJaccardSimilarity extends Similarity {
      def apply(bf1: BloomFilter[Int], bf2: BloomFilter[Int]): Double = LevelEqualitySimilarity(bf1, bf2)
    }

    object LevelJaccardSimilarity extends Similarity {
      def apply(bf1: BloomFilter[Int], bf2: BloomFilter[Int]): Double = {
        val intersection = bf1 & bf2
        val union = bf1 | bf2
        intersection.approximateElementCount.toDouble / union.approximateElementCount
      }
    }

    object LevelEqualitySimilarity extends Similarity {
      def apply(bf1: BloomFilter[Int], bf2: BloomFilter[Int]): Double = if bf1 == bf2 then 1 else 0
    }
  }

  /**
   * Aggregation function for hierarchy similarities from all levels to a single value for the hierarchy.
   */
  sealed trait Aggregation extends ((Array[Double], Array[Int]) => Double)

  object Aggregation {
    def apply(name: String, decayingFactor: Option[Double] = None): Aggregation = name match {
      case "average" => AverageAggregation
      case "size-weighted" => SizeWeightedAggregation
      case "decaying" => decayingFactor match {
        case Some(f) => DecayingAggregation(f)
        case _ => DecayingAggregation()
      }
      case _ => throw new IllegalArgumentException(s"Unsupported aggregation function: $name")
    }

    /**
     * Simple unweighted average of all level similarities.
     *
     * Contains optimized code using Array operations to avoid unnecessary object creation.
     */
    object AverageAggregation extends Aggregation {
      def apply(similarities: Array[Double], cards: Array[Int]): Double =
        var similarity = 0.0
        for i <- similarities.indices do
          similarity += similarities(i)
        similarity / similarities.length
    }

    /**
     * Aggregation function for hierarchy similarities that weighs the similarities by the size of the
     * clusters logarithmic (small clusters get lower weight).
     *
     * Contains optimized code using Array operations to avoid unnecessary object creation.
     */
    object SizeWeightedAggregation extends Aggregation {
      def apply(similarities: Array[Double], cards: Array[Int]): Double =
        var similarity = 0.0
        var totalWeights = 0.0
        for i <- similarities.indices do
          val w = math.log(1 + cards(i))
          similarity += similarities(i) * w
          totalWeights += w
        similarity / totalWeights
    }

    /**
     * Aggregation function for hierarchy similarities that weighs the similarities by a decaying factor.
     * Early levels are weighted less than later levels because early cluster merges are not as important for the
     * final clustering as later merges. Decay function with parameters \lambda (decay factor) and N (number of
     * levels):
     * $$
     * f(x) = \frac{e^{\lambda x} - 1}{e^{\lambda N} - 1}
     * $$
     *
     * Contains optimized code using Array operations to avoid unnecessary object creation.
     *
     * @param decay decay factor, higher values increase steepness of decay
     * @see [[https://www.wolframalpha.com/input?i=f%28x%29+%3D+%28e%5E%7B1%2F10*x%7D+-+1%29%2F%28e%5E5+-+1%29+plot+with+x+from+-1+to+50+and+y+from+0+to+2 WolframAlpha plot for decay 0.1]]
     */
    case class DecayingAggregation(decay: Double = 0.1) extends Aggregation {
      def apply(similarities: Array[Double], cards: Array[Int]): Double =
        val n = similarities.length
        var similarity = 0.0
        var totalWeights = 0.0
        for i <- 0 until n do
          val w = weightFunction(i, n)
          similarity += similarities(i) * w
          totalWeights += w
        similarity / totalWeights

      private def weightFunction(x: Int, n: Int): Double =
        Math.exp(decay * x) - 1 / Math.exp(decay * n) - 1
    }
  }
}

case class ClusterSimilarityOptions(
                                     bfOptions: BloomFilterOptions,
                                     similarity: ClusterSimilarityOptions.Similarity,
                                     aggregation: ClusterSimilarityOptions.Aggregation,
                                     cardLowerBound: Int,
                                     cardUpperBound: Int,
                                   )
