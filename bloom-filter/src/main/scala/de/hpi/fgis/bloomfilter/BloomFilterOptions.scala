package de.hpi.fgis.bloomfilter

case class BloomFilterOptions(
                               hashSize: BloomFilterOptions.BFHashSize,
                               falsePositiveRate: Double,
                               BFType: BloomFilterOptions.BFType = BFType.BloomFilter
                             ) {
  require(falsePositiveRate > 0.0 && falsePositiveRate < 1.0, "False positive rate must be in the range (0, 1)")
}

object BloomFilterOptions {
  /** Murmurhash version, either 64bit or 128bit version are supported. */
  enum BFHashSize {
    case BFH64, BFH128
  }

  /** Type of BloomFilter. */
  enum BFType {
    // CuckooFilter is not yet implemented/supported
    case BloomFilter //, CuckooFilter
  }

  /** Standard bloom filter with 64-bit Murmurhash and a false positive rate of 0.01. */
//  given DEFAULT_OPTIONS: BloomFilterOptions = BloomFilterOptions(BFHashSize.BFH64, 0.01)
}
