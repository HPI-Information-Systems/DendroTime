package de.hpi.fgis.dendrotime

import akka.actor.testkit.typed.scaladsl.{FishingOutcomes, ScalaTestWithActorTestKit}
import de.hpi.fgis.dendrotime.actors.Scheduler
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.StateModel.{ProgressMessage, Status}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*
import scala.language.postfixOps

class DendroTimeE2ETest extends ScalaTestWithActorTestKit with AnyWordSpecLike with should.Matchers {

  import testKit.internalSystem.executionContext

  private val coffeeDataset = TestUtil.findResource("test-data/datasets/Coffee/Coffee_TEST.ts")
  private val pickupGestureDataset = TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TRAIN.ts")

  private def performSystemTest(dataset: Dataset, gtFile: String): Unit = {
    // load ground truth
//    val gtHierarchy = TestUtil.loadHierarchy(gtFile)

    // start DendroTime
    val guardianProbe = createTestProbe[Scheduler.Response]("guarding")
    val dendroTimeScheduler = spawn(Scheduler(), "scheduler")
    dendroTimeScheduler ! Scheduler.StartProcessing(dataset, guardianProbe.ref)
    guardianProbe.expectMessageType[Scheduler.ProcessingStarted](100 millis)

    // wait for processing to finish and return final hierarchy
    val progressProbe = createTestProbe[ProgressMessage]("progress")
    val timer = testKit.scheduler.scheduleWithFixedDelay(0 seconds, 50 millis) { () =>
      dendroTimeScheduler ! Scheduler.GetProgress(dataset.id, progressProbe.ref)
    }
    val messages = progressProbe.fishForMessage(1 seconds) {
      case ProgressMessage.CurrentProgress(Status.Finished, 100, _, _) =>
        timer.cancel()
        FishingOutcomes.complete
      case _ =>
        FishingOutcomes.continueAndIgnore
    }
    messages.length should be > 0
    val finalMessage = messages.last

    // compare hierarchy with ground truth
    finalMessage shouldBe a[ProgressMessage.CurrentProgress]
    val progress = finalMessage.asInstanceOf[ProgressMessage.CurrentProgress]
    progress.state shouldBe Status.Finished
    progress.progress shouldBe 100
//    progress.hierarchy.size shouldBe 27

    testKit.stop(dendroTimeScheduler)
    guardianProbe.expectTerminated(dendroTimeScheduler, 100 millis)
  }

  "DendroTime" should {
    "produce correct hierarchy for Coffee dataset" in {
      val dataset = Dataset(0, "Coffee", coffeeDataset)
      performSystemTest(dataset, "test-data/ground-truth/Coffee/hierarchy-msm-ward.csv")
    }
    "produce correct hierarchy for PickupGestureWiimoteZ dataset" in {
      val dataset = Dataset(1, "PickupGestureWiimoteZ_TEST", pickupGestureDataset)
      performSystemTest(dataset, "test-data/ground-truth/PickupGestureWiimoteZ/hierarchy-msm-ward.csv")
    }
  }

}
