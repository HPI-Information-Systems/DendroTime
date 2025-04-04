package de.hpi.fgis.bloomfilter.mutable

import org.scalacheck.Test.Parameters
import org.scalacheck.commands.Commands
import org.scalacheck.{Gen, Prop, Properties}
import org.scalatest.{Inspectors, matchers}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import scala.language.adhocExtensions

class UnsafeBitArraySpec extends Properties("UnsafeBitArray") with matchers.should.Matchers with Inspectors {

  property("set & get") = new UnsafeBitArrayCommands().property()
  property("serializable") = serializationProp

  override def overrideParameters(p: Parameters): Parameters =
    super.overrideParameters(p).withMinSuccessfulTests(100)

  class UnsafeBitArrayCommands extends Commands {
    type Sut = UnsafeBitArray

    case class State(size: Long)

    override def canCreateNewSut(newState: State, initSuts: Iterable[State], runningSuts: Iterable[Sut]): Boolean =
      initSuts.isEmpty && runningSuts.isEmpty

    override def destroySut(sut: Sut): Unit =
      sut.dispose()

    override def genInitialState: Gen[State] =
      Gen.chooseNum[Long](1, Int.MaxValue * 2L).map(State.apply)

    override def newSut(state: State): Sut =
      new UnsafeBitArray(state.size)

    def initialPreCondition(state: State): Boolean = true

    def genCommand(state: State): Gen[Command] =
      for {
        i <- Gen.choose[Long](0, state.size - 1)
      } yield commandSequence(SetItem(i), GetItem(i))

    case class SetItem(i: Long) extends UnitCommand {
      def run(sut: Sut): Unit = sut.synchronized(sut.set(i))

      def nextState(state: State): State = state

      def preCondition(state: State) = true

      def postCondition(state: State, success: Boolean): Prop = success
    }

    case class GetItem(i: Long) extends SuccessCommand {
      type Result = Boolean

      def run(sut: Sut): Boolean = sut.synchronized(sut.get(i))

      def nextState(state: State): State = state

      def preCondition(state: State) = true

      def postCondition(state: State, result: Boolean): Prop = result
    }
  }

  def serializationProp: Prop =
    case class State(sz: Int, included: Set[Long])
    val genState = for {
      size <- Gen.posNum[Long]
      included <- Gen.listOf(Gen.choose(0L, size - 1))
    } yield
      State(size.toInt, included.toSet)

    Prop.forAll(genState) { case State(sz, included) =>
      val bits = new UnsafeBitArray(sz)
      try
        included.foreach(bits.set)

        val bos = new ByteArrayOutputStream()
        val oos = new ObjectOutputStream(bos)
        oos.writeObject(bits)
        oos.close()
        val bis = new ByteArrayInputStream(bos.toByteArray)
        val ois = new ObjectInputStream(bis)
        val deserialized = ois.readObject()
        ois.close()

        deserialized should not be null
        deserialized should be(a[UnsafeBitArray])
        val deserializedBits = deserialized.asInstanceOf[UnsafeBitArray]
        try
          deserializedBits.numberOfBits shouldEqual bits.numberOfBits
          forAll(0L until bits.numberOfBits) { idx =>
            bits.get(idx) shouldEqual deserializedBits.get(idx)
          }
        finally
          deserializedBits.dispose()
      finally
        bits.dispose()
      Prop.passed
    }
}
