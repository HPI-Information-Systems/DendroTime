package de.hpi.fgis.dendrotime.structures

import scala.collection.mutable


object WorkQueue {

  def empty[T]: WorkQueue[T] = WorkQueue()
}

class WorkQueue[T] private {

  private val workQueue: mutable.Queue[T] = mutable.Queue.empty
  private val pendingSet: mutable.Set[T] = mutable.Set.empty
  var sizeWork: Int = 0
  var sizePending: Int = 0

  def size: Int = sizeWork + sizePending

  def sizeHint(size: Int): Unit = {
    workQueue.ensureSize(size)
    pendingSet.sizeHint(size)
  }

  /**
   * Tests whether the work queue is empty (this includes pending responses and the actual work queue).
   *
   * @return `true` if the work queue contains no elements, `false` otherwise.
   */
  def isEmpty: Boolean = pendingSet.isEmpty && workQueue.isEmpty

  /**
   * Tests whether the work queue is not empty (this includes pending responses and the actual work queue).
   *
   * @return `true` if the work queue or the pending set contains at least one element, `false` otherwise.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Tests whether there is still work in the queue.
   */
  def hasWork: Boolean = workQueue.nonEmpty

  /**
   * Tests whether there is no work in the queue.
   */
  def noWork: Boolean = !hasWork

  /**
   * Tests whether there are still pending responses.
   */
  def hasPending: Boolean = pendingSet.nonEmpty

  /**
   * Tests whether there are no pending responses.
   */
  def noPending: Boolean = !hasPending

  /**
   * Dequeues the first item from the work queue and puts it into the pending set.
   *
   * @throws java.util.NoSuchElementException when the queue is empty
   * @return the first element in the queue
   */
  def dequeue(): T =
    val item = workQueue.dequeue
    pendingSet += item
    sizeWork -= 1
    sizePending += 1
    item

  /**
   * Creates a new queue with element added at the end of the old queue. The pending set is not changed.
   *
   * @param item the element to insert
   */
  def enqueue(item: T): WorkQueue[T] =
    workQueue.enqueue(item)
    sizeWork += 1
    this

  /**
   * Creates a new queue with all elements provided by an `Iterable` object added at the end of the old queue. The
   * pending set is not changed. The elements are appended in the order they are given out by the iterator.
   *
   * @param items an iterable object
   */
  def enqueueAll(items: Iterable[T]): WorkQueue[T] =
    workQueue.enqueueAll(items)
    sizeWork += items.size
    this

  /**
   * Creates a new queue with a given element removed from the pending set. The actual work queue is not changed.
   *
   * @param item the element to be removed
   * @return the queue
   */
  def removePending(item: T): WorkQueue[T] =
    pendingSet -= item
    sizePending -= 1
    this

  override def toString: String =
    "WorkQueue(" +
      s"work=$sizeWork," +
      s"pending=${sizePending}" +
      ")"
}