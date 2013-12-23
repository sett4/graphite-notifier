import akka.actor.{Props, ActorSystem}
import akka.actor.{ Actor, ActorRef, Props }
import akka.io._
import akka.util.ByteString
import java.net.InetSocketAddress

import scala.util.{Success}


class Server extends Actor {

  import Tcp._
  import context.system

  override def preStart = {
    IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 1234))
  }

  def receive = {
    case b @ Bound(localAddress) ⇒
      // do some logging or setup ...

    case CommandFailed(_: Bind) ⇒ context stop self

    case Connected(remote, local) ⇒ {
      val connection = sender
      val updateDispatcher = context.actorOf(Props[UpdateParser])
      val handler = context.actorOf(Props(new GraphitePlaintextHandler(updateDispatcher)))
      connection ! Register(handler)
    }
  }

}

class UpdateParser extends Actor {
  def receive = {
    case Success(line: String) => {
      print(this.toString+": ")
      println(line)
    }
    case Success(update: Update) => {
      print(this.toString+": ")
      println(update)
    }
  }
}

class GraphitePlaintextHandler(updateDispatcher: ActorRef) extends Actor {
  import Tcp._

  val pipeLineStages = new UpdateStage >> new LineStage >> new ByteStringStage
  val pipeLineInjector = PipelineFactory.buildWithSinkFunctions(new PipelineContext {}, pipeLineStages)(
      self ! _, // command
      updateDispatcher ! _  // event
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

class LineStage extends SymmetricPipelineStage[PipelineContext, String, ByteString] {
  def apply(context:PipelineContext) = new SymmetricPipePair[String, ByteString] {

    override def commandPipeline = { line: String =>
      context.singleCommand(ByteString(line))
    }

    override def eventPipeline: (ByteString) => Iterable[this.type#Result] = { line: ByteString =>
      context.singleEvent(line.utf8String)
    }
  }
}

case class Update(metric: String, value: Double, timestamp: Long) {
  def toPlaintext = {
    "%s\t%f\t%d" format(metric, value, timestamp)
  }
}

class UpdateStage extends SymmetricPipelineStage[PipelineContext, Update, String] {
  def apply(context: PipelineContext) = new SymmetricPipePair[Update, String] {
    override def commandPipeline = { metricUpdate: Update =>
      context.singleCommand(metricUpdate.toString)
    }
    override def eventPipeline = {
      line: String => {
        val cols = line.split("\t")
        /* TODO ここのエラーハンドリング */
        val metric: String = cols(0)
        val value = cols(1) toDouble
        val timestamp: Long = java.lang.Long.parseLong(cols(2))
        context.singleEvent(Update(metric, value, timestamp))
      }
    }
  }
}

object Main {
 def main(args: Array[String]) {
    val actorSystem = ActorSystem.create
    actorSystem.actorOf(Props[Server], name = classOf[Server].getSimpleName)
  }
}