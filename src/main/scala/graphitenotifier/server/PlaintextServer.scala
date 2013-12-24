package graphitenotifier.server

import akka.actor.{Props, ActorSystem}
import akka.actor.{ Actor, ActorRef, Props }
import akka.io._
import akka.util.ByteString
import java.net.InetSocketAddress

import scala.util.Success
import graphitenotifier.Metric


class PlaintextServer(inetSocketAddress: InetSocketAddress, metricDispatcherProps: Props) extends Actor {

  import Tcp._
  import context.system

  override def preStart = {
    IO(Tcp) ! Bind(self, inetSocketAddress)
  }

  def receive = {
    case b @ Bound(localAddress) ⇒
      // do some logging or setup ...

    case CommandFailed(_: Bind) ⇒ context stop self

    case Connected(remote, local) ⇒ {
      val connection = sender
      val metricDispatcher = context.actorOf(metricDispatcherProps)
      val handler = context.actorOf(Props(classOf[PlaintextHandler],metricDispatcher))
      connection ! Register(handler)
    }
  }

}

class PlaintextHandler(metricDispatcher: ActorRef) extends Actor {
  import Tcp._

  val pipelineStages = new MetricStage >> new StringStage >> new ByteStringStage
  val pipeLineInjector = PipelineFactory.buildWithSinkFunctions(new PipelineContext {}, pipelineStages)(
      self ! _, // command
      metricDispatcher ! _  // event
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
        val cols = line.split("\t")
        /* TODO Error handling */
        val path: String = cols(0)
        val value = cols(1) toDouble
        val timestamp: Long = java.lang.Long.parseLong(cols(2))
        context.singleEvent(Metric(path, value, timestamp))
      }
    }
  }
}
