package de.hpi.fgis.dendrotime.actors.coordinator

import scala.annotation.tailrec
import scala.collection.immutable.Queue


object WorkQueue {

  def empty[T]: WorkQueue[T] = WorkQueue(Queue.empty, Set.empty, Set.empty)

  def from[T](initialItems: T*): WorkQueue[T] = from(initialItems)

  def from[T](candidates: IterableOnce[T]): WorkQueue[T] =
    WorkQueue(Queue.from(candidates), Set.from(candidates), Set.empty)
}

case class WorkQueue[T] private(
                              workQueue: Queue[T],
                              workSet: Set[T],
                              pendingSet: Set[T]
                            ) {

  def size: Int = sizeWork + sizePending

  def sizeWork: Int = workSet.size

  def sizePending: Int = pendingSet.size

  /**
   * Tests whether the work queue is empty (this includes pending responses and the actual work queue).
   *
   * @return `true` if the work queue contains no elements, `false` otherwise.
   */
  def isEmpty: Boolean = workSet.isEmpty && pendingSet.isEmpty

  /**
   * Tests whether the work queue is not empty (this includes pending responses and the actual work queue).
   *
   * @return `true` if the work queue or the pending set contains at least one element, `false` otherwise.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Tests whether there is still work in the queue.
   */
  def hasWork: Boolean = workSet.nonEmpty

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
   * @return a tuple with the first element in the queue, and the new queue with the element put into the pending set
   */
  def dequeue(): (T, WorkQueue[T]) =
    val (item, newQueue) = internalDequeue(workQueue)
    val newObj = copy(
      workQueue = newQueue,
      workSet = workSet - item,
      pendingSet = pendingSet + item
    )
    (item, newObj)

  /**
   * Jumps over all items in the queue that were already removed (from the work set).
   * This is a performance optimization (compared to actually removing them from the queue on removal)!
   */
  @tailrec
  private final def internalDequeue(queue: Queue[T]): (T, Queue[T]) =
    val (item, newQueue) = queue.dequeue
    if (workSet.contains(item))
      (item, newQueue)
    else
      internalDequeue(newQueue)

  /**
   * Creates a new queue with element added at the end of the old queue. The pending set is not changed.
   *
   * @param item the element to insert
   */
  def enqueue(item: T): WorkQueue[T] =
    copy(
      workQueue = workQueue.enqueue(item),
      workSet = workSet + item
    )

  /**
   * Creates a new queue with all elements provided by an `Iterable` object added at the end of the old queue. The
   * pending set is not changed. The elements are appended in the order they are given out by the iterator.
   *
   * @param items an iterable object
   */
  def enqueueAll(items: Iterable[T]): WorkQueue[T] =
    copy(
      workQueue = workQueue.enqueueAll(items),
      workSet = workSet ++ items
    )

  /**
   * Creates a new queue with a given element removed from the pending set. The actual work queue is not changed.
   *
   * @param item the element to be removed
   * @return a new queue that contains all elements of the current pending set but that does not contain `elem`.
   */
  def removePending(item: T): WorkQueue[T] =
    copy(
      pendingSet = pendingSet - item
    )

  /**
   * Tests whether the `item` is contained either in the work queue or the pending set.
   * This is an improved inclusion test that was optimized for performance using sets instead of
   * iterating through the actual queue (`O(1)`).
   */
  def contains(item: T): Boolean = workSet.contains(item) || pendingSet.contains(item)

  /**
   * Tests whether the `item` is contained in the actual work queue.
   *
   * @see [[de.hpi.fgis.dendrotime.actors.coordinator.WorkQueue#contains]]
   */
  def containsWork(item: T): Boolean = workSet.contains(item)

  /**
   * Tests whether the `item` is contained in the pending set.
   *
   * @see [[de.hpi.fgis.dendrotime.actors.coordinator.WorkQueue#contains]]
   */
  def containsPending(item: T): Boolean = pendingSet.contains(item)

  override def toString: String =
    "WorkQueue(" +
      s"queue=${workQueue.size}," +
      s"work=${workSet.size}," +
      s"pending=${pendingSet.size}" +
      ")"
}