package graphitenotifier.server

import akka.actor._
import akka.io._
import akka.util.ByteString
import java.net.InetSocketAddress

import java.util.Date
import graphitenotifier.Metric
import akka.io.IO
import scala.util.Success
import scala.collection.mutable
import scala.collection.mutable.{SynchronizedMap, HashMap}
import graphitenotifier.notifier.BaseNotifier
import graphitenotifier.{Check, CheckResult}


class PlaintextServer(val inetSocketAddress: InetSocketAddress, val checks: List[Check], val notifierProps: List[Props]) extends Actor {

  import Tcp._
  import context.system

  val notifier = context.actorOf(Props(classOf[BaseNotifier], notifierProps))
  val liveEvents = new HashMap[String, CheckResult] with SynchronizedMap[String, CheckResult]


  override def preStart = {
    IO(Tcp) ! Bind(self, inetSocketAddress)
  }

  def receive = {
    case b @ Bound(localAddress) ⇒
      // do some logging or setup ...

    case CommandFailed(_: Bind) ⇒ context stop self

    case Connected(remote, local) ⇒ {
      val connection = sender

      val handler = context.actorOf(Props(classOf[PlaintextHandler],checks, liveEvents, notifier.path))
      connection ! Register(handler)
    }
  }

}

class PlaintextHandler(val checks: List[Check], val liveEvents: mutable.SynchronizedMap[String, CheckResult], val notifierPath: ActorPath) extends Actor {
  import Tcp._

  val pipelineStages = new EventStage(liveEvents) >> new CheckResultStage(checks) >> new MetricStage >> new StringStage >> new ByteStringStage
  val pipeLineInjector = PipelineFactory.buildWithSinkFunctions(new PipelineContext {}, pipelineStages)(
      self ! _, // command
      _ match {
        case Success(event) => context.actorSelection(notifierPath / "*") ! event  // event
        case _ =>
      }
  )

  def receive = {
    case Received(data) ⇒ {
      pipeLineInjector.injectEvent(data)
    }
    case Tcp.PeerClosed ⇒ {
      context stop self
    }
  }
}

class ByteStringStage extends DelimiterFraming(1024, delimiter = ByteString("\n") ) {
}

class StringStage extends SymmetricPipelineStage[PipelineContext, String, ByteString] {
  def apply(context:PipelineContext) = new SymmetricPipePair[String, ByteString] {

    override def commandPipeline = { line: String =>
      context.singleCommand(ByteString(line))
    }

    override def eventPipeline: (ByteString) => Iterable[this.type#Result] = { line: ByteString =>
      context.singleEvent(line.utf8String)
    }
  }
}

class MetricStage extends SymmetricPipelineStage[PipelineContext, Metric, String] {
  def apply(context: PipelineContext) = new SymmetricPipePair[Metric, String] {
    override def commandPipeline = { metricUpdate: Metric =>
      context.singleCommand(metricUpdate.toString)
    }
    override def eventPipeline = {
      line: String => {
        val cols = line.trim.split(" ")
        /* TODO Error handling */
        val path: String = cols(0)
        val value = cols(1) toDouble
        val timestamp: Date = new Date(java.lang.Long.parseLong(cols(2))*1000)
        context.singleEvent(Metric(path, value, timestamp))
      }
    }
  }
}
