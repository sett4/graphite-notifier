package graphitenotifier

import akka.actor.{Props, ActorSystem}
import java.net.InetSocketAddress
import graphitenotifier.server.PlaintextServer

object Main {
 def main(args: Array[String]) {
   val actorSystem = ActorSystem.create
   val metricDispatcher = Props[MetricDispatcher]
   val server = actorSystem.actorOf(Props(classOf[PlaintextServer], new InetSocketAddress("localhost", 1234), metricDispatcher))
 }
}