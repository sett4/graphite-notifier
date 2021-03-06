package graphitenotifier.server

import org.scalatest._

import org.scalatest.matchers._

import java.util.Date
import akka.testkit.{ImplicitSender, TestKit}
import akka.actor._
import graphitenotifier.Metric
import akka.io.{PipelineFactory, PipelineContext}
import akka.util.ByteString
import graphitenotifier.{Check, CheckResult, Event, Level, State}

class MetricPipelineStageSpec(_system: ActorSystem) extends TestKit(_system)
with ImplicitSender with WordSpecLike with ShouldMatchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "level" should {
    "have 3 elements" in {
      Level.values.length should be(3)
    }
  }
  "level" should {
    "comparable" in {
      Level.OK < Level.WARNING should be (true)
      Level.WARNING < Level.CRITICAL should be (true)
    }
  }

  "checker" should {
    "accepts regexp" in {
      val checker = new Check("""hoge""".r, v => Level.CRITICAL)
      val timestamp = new Date()
      val m: Metric = Metric("org.test.hoge", 100, timestamp)
      checker.check(Metric("org.test.hoge", 100, timestamp)).get.metric.path should be ("org.test.hoge")
      checker.check(Metric("org.hoge.test", 100, timestamp)).get.metric.path should be ("org.hoge.test")
      checker.check(Metric("hoge.test", 100, timestamp)).get.metric.path should be ("hoge.test")
    }
  }

  "StringStage" should {
    "split multiline to a line" in {
      val pipelinePort = {
        val ctx = new PipelineContext {}
        PipelineFactory.buildFunctionTriple(ctx, new ByteStringStage())
      }
      val multiline = ByteString("org.test.hoge 123 1388350836\norg.test.hoge 123 1388350836\n")
      val list: List[ByteString] = pipelinePort.events(multiline)._1.toList
      list.size should be (2)
    }
  }

  "MetricStage" should {
    "convert 1 line graphite plaintext protocol" in {
      val pipelinePort = {
        val ctx = new PipelineContext {}
        PipelineFactory.buildFunctionTriple(ctx, new MetricStage())
      }
      val metrics: Iterable[Metric] = pipelinePort.events("org.test.hoge 123 1388350836")._1
      metrics.size should be (1)
      metrics.head.path should be ("org.test.hoge")
      metrics.head.value should be (123)
      metrics.head.timestamp should be (new Date(1388350836L*1000L))
    }
  }

  "checkResult stage" should {
    "convert to checkResult" in {
      val checkerList = List(new Check(".*".r, v => if (v > 50) { Level.CRITICAL } else if (v > 30) { Level.WARNING } else { Level.OK}))
      val pipelinePort = {
        val ctx = new PipelineContext {}
        PipelineFactory.buildFunctionTriple(ctx, new CheckResultStage(checkerList))
      }

      val criticalMetric = Metric("hoge", 100, new Date())
      val warningMetric = Metric("hoge", 40, new Date())
      val safeMetric = Metric("hoge", 0, new Date())
      pipelinePort.events(criticalMetric)._1.head.level should be (Level.CRITICAL)
      pipelinePort.events(safeMetric)._1.head.level should be (Level.OK)
      pipelinePort.events(warningMetric)._1.head.level should be (Level.WARNING)
      pipelinePort.events(criticalMetric)._1.head.level should be (Level.CRITICAL)

   }
  }

  "event stage" should {
    val criticalMetric = Metric("hoge", 100, new Date())
    val warningMetric = Metric("hoge", 40, new Date())
    val safeMetric = Metric("hoge", 0, new Date())
    val fatalResult = CheckResult(criticalMetric, Level.CRITICAL)
    val warningResult = CheckResult(warningMetric, Level.WARNING)
    val safeResult = CheckResult(safeMetric, Level.OK)

    "convert to Event" in {
      val pipelinePort = {
        val ctx = new PipelineContext {}
        val liveEvents = scala.collection.mutable.Map[String, CheckResult]()
        PipelineFactory.buildFunctionTriple(ctx, new EventStage(liveEvents))
      }

      pipelinePort.events(safeResult)._1.size should be (0)

      val comeEvent: Event = pipelinePort.events(fatalResult)._1.head
      comeEvent.state should be (State.FAIL)
      comeEvent.level should be (Level.CRITICAL)

      val com2Event: Event = pipelinePort.events(warningResult)._1.head
      com2Event.state should be (State.LEVEL_CHANGED)
      com2Event.level should be (Level.WARNING)

      val recoverEvent: Event = pipelinePort.events(safeResult)._1.head
      recoverEvent.state should be (State.RECOVER)
      recoverEvent.level should be (Level.OK)

      val events: Iterable[Event] = pipelinePort.events(safeResult)._1
      events.size should be (0)
   }
  }
}