package de.hpi.fgis.bloomfilter.mutable

import de.hpi.fgis.bloomfilter.CanGenerateHashFrom
import de.hpi.fgis.bloomfilter.mutable.CuckooFilter
import org.scalacheck.Test.Parameters
import org.scalacheck.commands.Commands
import org.scalacheck.{Arbitrary, Gen, Prop, Properties}
import org.scalatest.{Inspectors, matchers}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.language.adhocExtensions

class CuckooFilterSpec extends Properties("CuckooFilter") with matchers.should.Matchers with Inspectors {

  property("for Long") = new CuckooFilterCommands[Long].property()
  property("for String") = new CuckooFilterCommands[String].property()
  property("for Array[Byte]") = new CuckooFilterCommands[Array[Byte]].property()

  override def overrideParameters(p: Parameters): Parameters =
    super.overrideParameters(p).withMinSuccessfulTests(100)

  class CuckooFilterCommands[T: Arbitrary](using CanGenerateHashFrom[T]) extends Commands {
    type Sut = CuckooFilter[T]

    case class State(expectedItems: Long, addedItems: Long)

    override def canCreateNewSut(newState: State, initSuts: Iterable[State], runningSuts: Iterable[Sut]): Boolean =
      initSuts.isEmpty && runningSuts.isEmpty

    override def destroySut(sut: Sut): Unit =
      sut.dispose()

    override def genInitialState: Gen[State] =
      Gen.chooseNum[Long](1, 100000).map(State(_, 0))

    override def newSut(state: State): Sut =
      CuckooFilter[T](state.expectedItems)

    def initialPreCondition(state: State): Boolean = true

    def genCommand(state: State): Gen[Command] =
      for {
        item <- Arbitrary.arbitrary[T]
      } yield commandSequence(AddItem(item), CheckItem(item), RemoveItem(item))

    case class AddItem(item: T) extends UnitCommand {
      def run(sut: Sut): Unit = sut.synchronized(sut.add(item))

      def nextState(state: State): State =
        state.copy(addedItems = state.addedItems + 1)

      def preCondition(state: State): Boolean =
        state.addedItems < state.expectedItems

      def postCondition(state: State, success: Boolean): Prop = success
    }

    case class RemoveItem(item: T) extends SuccessCommand {
      type Result = Boolean

      def run(sut: Sut): Boolean = sut.synchronized:
        sut.remove(item)
        !sut.mightContain(item)

      def nextState(state: State): State =
        state.copy(addedItems = state.addedItems - 1)

      def preCondition(state: State): Boolean =
        state.addedItems < state.expectedItems

      def postCondition(state: State, success: Boolean): Prop = success
    }

    case class CheckItem(item: T) extends SuccessCommand {
      type Result = Boolean

      def run(sut: Sut): Boolean = sut.synchronized(sut.mightContain(item))

      def nextState(state: State): State = state

      def preCondition(state: State): Boolean =
        state.addedItems < state.expectedItems

      def postCondition(state: State, result: Boolean): Prop = result
    }
  }

  property("strange case") = Prop {
    val lst = List(-1L, 0L)
    val cf = CuckooFilter[Long](lst.size)
    lst.foreach(cf.add)

    lst.forall(cf.mightContain)
  }

  property("strange case #2") = Prop {
    val lst = List(0L, 0, 0, 0, 0, 0, 0, 0, 4)
    // the x3 size factor here enables 4 to end up in a different bucket than the 3 0's, their bucket overflows after the first four inserts
    val cf = CuckooFilter[Long](lst.size * 3)
    lst.foreach(cf.add)

    lst.forall(cf.mightContain)
  }

  private val gen = Gen.listOf(Arbitrary.arbLong.arbitrary)

  property("supports java serialization") = Prop.forAll(gen) { lst =>
    val sz = math.max(lst.size, 1)
    // we add n x3 factor to reduce probability for buckets overflowing during inserts
    val sut = CuckooFilter[Long](sz * 3)
    try
      lst foreach sut.add
      val bos = new ByteArrayOutputStream
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(sut)
      oos.close()
      val bis = new ByteArrayInputStream(bos.toByteArray)
      val ois = new ObjectInputStream(bis)
      val deserialized = ois.readObject()
      ois.close()

      deserialized should not be null
      deserialized should be(a[CuckooFilter[Long]])
      val sut2 = deserialized.asInstanceOf[CuckooFilter[Long]]
      try
        forAll(lst) { k =>
          withClue(k):
            // we use a realxed condition here,
            // the reason for this is potential (and actual) buckets overflowing in the underlying UnsafeTable16.
            // a different aproach might be generating the keys in a way that limits them according to number of buckets and number of tags in each bucket.
            sut2.mightContain(k) shouldEqual sut.mightContain(k)
        }
        Prop.passed
      finally sut2.dispose()
    finally sut.dispose()
  }
}
