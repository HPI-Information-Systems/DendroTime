package de.hpi.fgis.dendrotime.evaluator

import de.hpi.fgis.bloomfilter.{BloomFilter, BloomFilterOptions}


object BFHolder {
  def initialBfs(n: Int)(using BloomFilterOptions): BFHolder = {
    val initialBfs = Array.tabulate(n)(i =>
      BloomFilter[Int](n + n - 1) += i
    )
    apply(initialBfs)
  }

  def apply(bloomFilters: Array[BloomFilter[Int]]): BFHolder = new BFHolder(bloomFilters)
}

class BFHolder private(_bfs: Array[BloomFilter[Int]]) extends AutoCloseable {
  private var closed = false

  def bfs: Array[BloomFilter[Int]] =
    if closed then throw new IllegalStateException("BFHolder is closed")
    else _bfs

  override def close(): Unit = _bfs.foreach { bf =>
    bf.dispose()
    closed = true
  }
}
