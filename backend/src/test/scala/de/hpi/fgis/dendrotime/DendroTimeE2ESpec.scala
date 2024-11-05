package de.hpi.fgis.dendrotime

import akka.actor.testkit.typed.scaladsl.{FishingOutcomes, LogCapturing, ScalaTestWithActorTestKit}
import de.hpi.fgis.dendrotime.actors.Scheduler
import de.hpi.fgis.dendrotime.model.DatasetModel.Dataset
import de.hpi.fgis.dendrotime.model.ParametersModel.DendroTimeParams
import de.hpi.fgis.dendrotime.model.StateModel.{ProgressMessage, Status}
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.FileNotFoundException
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class DendroTimeE2ESpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with should.Matchers with LogCapturing {

  import TestUtil.ImplicitEqualities.given
  import testKit.internalSystem.executionContext

  private val coffeeDatasetTest = TestUtil.findResource("test-data/datasets/Coffee/Coffee_TEST.ts")
  private val coffeeDatasetTrain = TestUtil.findResource("test-data/datasets/Coffee/Coffee_TRAIN.ts")
  private val pickupGestureDatasetTest = TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TEST.ts")
  private val pickupGestureDatasetTrain = TestUtil.findResource("test-data/datasets/PickupGestureWiimoteZ/PickupGestureWiimoteZ_TRAIN.ts")

  private def performSystemTest(dataset: Dataset, params: DendroTimeParams, gtFile: String): Unit = {
    // load ground truth
    Try {
       TestUtil.loadHierarchy(gtFile)
    } match {
      case Success(gtHierarchy) =>
        // start DendroTime
        val guardianProbe = createTestProbe[Scheduler.Response]()
        val dendroTimeScheduler = spawn(Scheduler(), s"scheduler-${dataset.id}")
        dendroTimeScheduler ! Scheduler.StartProcessing(dataset, params, guardianProbe.ref)
        guardianProbe.expectMessageType[Scheduler.ProcessingStarted](100 millis)

        // wait for processing to finish and return final hierarchy
        val progressProbe = createTestProbe[ProgressMessage]()
        val timer = testKit.scheduler.scheduleWithFixedDelay(0 seconds, 200 millis) { () =>
          dendroTimeScheduler ! Scheduler.GetProgress(dataset.id, progressProbe.ref)
        }
        val messages = progressProbe.fishForMessage(60 seconds) {
          case ProgressMessage.CurrentProgress(Status.Finished, 100, _, _, _, _, _, _) =>
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
        progress.hierarchy shouldEqual gtHierarchy

        testKit.stop(dendroTimeScheduler)
        guardianProbe.expectTerminated(dendroTimeScheduler, 100 millis)
      case Failure(_: FileNotFoundException) =>
        cancel(s"Ground truth file not found: $gtFile")
      case Failure(e) =>
        fail(e)
    }
  }

  for (metric, i) <- Seq("msm", "sbd").zipWithIndex do
    for (linkage, j) <- Seq("single", "complete", "average", "ward").zipWithIndex do
      s"DendroTime with $metric distance and $linkage linkage" should {
        "produce correct hierarchy for Coffee dataset" in {
          val dataset = Dataset(i*(j+1)+j, "Coffee", coffeeDatasetTest, Some(coffeeDatasetTrain))
          val params = DendroTimeParams(metric, linkage)
          performSystemTest(dataset, params, s"test-data/ground-truth/Coffee/hierarchy-$metric-$linkage.csv")
        }
        "produce correct hierarchy for PickupGestureWiimoteZ dataset" in {
          val dataset = Dataset(100+i*(j+1)+j, "PickupGestureWiimoteZ", pickupGestureDatasetTest, Some(pickupGestureDatasetTrain))
          val params = DendroTimeParams(metric, linkage)
          performSystemTest(dataset, params, s"test-data/ground-truth/PickupGestureWiimoteZ/hierarchy-$metric-$linkage.csv")
        }
      }
}
