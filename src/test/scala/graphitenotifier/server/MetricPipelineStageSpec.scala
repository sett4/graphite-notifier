package graphitenotifier.server

import org.scalatest._

import org.scalatest.matchers.ShouldMatchers
import java.util.Date
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import graphitenotifier.Metric
import akka.io.{PipelineFactory, PipelineContext}


class MetricPipelineStageSpec(_system: ActorSystem) extends TestKit(_system)
with ImplicitSender with WordSpec with ShouldMatchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "level" should {
    "have 4 elements" in {
      Level.values.length should be(4)
    }
  }
  "level" should {
    "comparable" in {
      Level.SAFE < Level.WARN should be (true)
      Level.WARN < Level.CRITICAL should be (true)
      Level.CRITICAL < Level.FATAL should be (true)
    }
  }

  "checker" should {
    "accepts regexp" in {
      val checker = new Check("""hoge""".r, v => Level.FATAL)
      val timestamp = new Date()
      val m: Metric = Metric("org.test.hoge", 100, timestamp)
      checker.check(Metric("org.test.hoge", 100, timestamp)).get.metric.path should be ("org.test.hoge")
      checker.check(Metric("org.hoge.test", 100, timestamp)).get.metric.path should be ("org.hoge.test")
      checker.check(Metric("hoge.test", 100, timestamp)).get.metric.path should be ("hoge.test")
    }
  }

  "metric to checkResult stage" should {
    "convert to checkResult" in {
      val checkerList = List(new Check(".*".r, v => if (v > 50) { Level.FATAL } else { Level.SAFE}))
      val pipelinePort = {
        val ctx = new PipelineContext {}
        PipelineFactory.buildFunctionTriple(ctx, new CheckResultStage(checkerList))
      }

      val fatalMetric = Metric("hoge", 100, new Date())
      val safeMetric = Metric("hoge", 0, new Date())
      pipelinePort.events(fatalMetric)._1.head.level should be (Level.FATAL)
      pipelinePort.events(safeMetric)._1.head.level should be (Level.SAFE)
      pipelinePort.events(fatalMetric)._1.head.level should be (Level.FATAL)

   }
  }

  "event stage" should {
    val fatalMetric = Metric("hoge", 100, new Date())
    val safeMetric = Metric("hoge", 0, new Date())
    val fatalResult = CheckResult(fatalMetric, Level.FATAL)
    val safeResult = CheckResult(safeMetric, Level.SAFE)

    "convert to Event" in {
      val pipelinePort = {
        val ctx = new PipelineContext {}
        val liveEvents = scala.collection.mutable.Map[String, CheckResult]()
        PipelineFactory.buildFunctionTriple(ctx, new EventStage(liveEvents))
      }

      pipelinePort.events(safeResult)._1.size should be (0)

      val comeEvent: Event = pipelinePort.events(fatalResult)._1.head
      comeEvent.state should be (State.FAIL)
      comeEvent.level should be (Level.FATAL)

      val recoverEvent: Event = pipelinePort.events(safeResult)._1.head
      recoverEvent.state should be (State.RECOVER)
      recoverEvent.level should be (Level.SAFE)

      val events: Iterable[Event] = pipelinePort.events(safeResult)._1
      events.size should be (0)
   }
  }
}