import de.hpi.fgis.dendrotime.clustering.PDist
import de.hpi.fgis.dendrotime.clustering.PDist.PDistImpl

import scala.collection.mutable
import scala.util.Random

def createQueue(mapping: Map[Long, Int], dists: PDist): Array[(Long, Long)] = {
  val ids = mapping.keys.toArray
  val builder = mutable.ArrayBuilder.make[(Double, Long, Long)]
  builder.sizeHint(dists.n * (dists.n - 1) / 2)
  for i <- 0 until ids.length - 1 do
    for j <- i + 1 until ids.length do
      var pair = ids(i) -> ids(j)
      if pair._2 < pair._1 then
        pair = pair.swap
      val distance = dists(mapping(pair._1), mapping(pair._2))
      builder += ((distance, pair._1, pair._2))
  val work = builder.result()
  work.sortInPlaceBy(_._1)
  val queue = work.map(t => (t._2, t._3))
  queue
}

def arraySortByLookup(mapping: Map[Long, Int], dists: PDist): Array[(Long, Long)] = {
  val work = Array.ofDim[(Long, Long)](dists.n * (dists.n - 1) / 2)
  var i = 0
  while i < dists.n - 1 do
    var j = i + 1
    while j < dists.n do
      val idLeft = i.toLong
      val idRight = j.toLong
      work(PDist.index(i, j, dists.n)) = (idLeft, idRight)
      j += 1
    i += 1

  work.sortInPlaceBy((id1, id2) => dists(mapping(id1), mapping(id2)))
  work
}

def arraySortByIndex(mapping: Map[Long, Int], dists: PDist): Array[(Long, Long)] = {
  val reverseMapping = mapping.map(_.swap)

  val indices = Array.ofDim[(Int, Int)](dists.n * (dists.n - 1) / 2)
  var i = 0
  while i < dists.n - 1 do
    var j = i + 1
    while j < dists.n do
      indices(PDist.index(i, j, dists.n)) = (i, j)
      j += 1
    i += 1
  indices.sortInPlaceBy(dists.apply)

  val work = Array.ofDim[(Long, Long)](dists.n * (dists.n - 1) / 2)
  var k = 0
  while k < indices.length do
    val (i, j) = indices(k)
    work(k) = (reverseMapping(i), reverseMapping(j))
    k += 1
  work
}

def arraySortByInplace(mapping: Map[Long, Int], dists: PDist): Array[(Long, Long)] = {
  val ids = mapping.keys.toArray
  val data = Array.ofDim[(Double, Long, Long)](dists.n * (dists.n - 1) / 2)
  var k = 0
  var i = 0
  while i < dists.n - 1 do
    var j = i + 1
    while j < dists.n do
      var pair = ids(i) -> ids(j)
      if pair._2 < pair._1 then
        pair = pair.swap
      val dist = dists(mapping(pair._1), mapping(pair._2))
      data(k) = (dist, pair._1, pair._2)
      j += 1
      k += 1
    i += 1
  data.sortInPlaceBy(_._1)

  data.map(t => (t._2, t._3))
}

println("name\tprep\tsort (in ms)")
for n <- 500 :: 1_000 :: 2_000 :: 3_000 :: Nil do
  val mapping = Array.tabulate(n)(i => i.toLong -> i).toMap
  val dists = PDist.apply(Array.fill[Double](n, n){Math.random()}, n)

  println(s"-----  N = $n [${dists.length}]  ------")
  System.gc()
  var t0 = System.nanoTime()
  val queue = createQueue(mapping, dists)
  var t1 = System.nanoTime()
  var d = (t1 - t0) / 1_000_000
  println(s"createQueue\t$d")

  System.gc()
  t0 = System.nanoTime()
  val queue2 = arraySortByLookup(mapping, dists)
  t1 = System.nanoTime()
  d = (t1 - t0) / 1_000_000
  println(s"arraySortByLookup\t$d")
  require(queue.sameElements(queue2), s"queue and queue2 are not equal")

  System.gc()
  t0 = System.nanoTime()
  val queue3 = arraySortByIndex(mapping, dists)
  t1 = System.nanoTime()
  d = (t1 - t0) / 1_000_000
  println(s"arraySortByIndex\t$d")
  require(queue.sameElements(queue3), s"queue and queue3 are not equal")

  System.gc()
  t0 = System.nanoTime()
  val queue4 = arraySortByInplace(mapping, dists)
  t1 = System.nanoTime()
  d = (t1 - t0) / 1_000_000
  println(s"arraySortByInplace\t$d")
  require(queue.sameElements(queue4), s"queue and queue4 are not equal")