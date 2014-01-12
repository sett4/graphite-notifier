package graphitenotifier

import akka.actor.{Props, ActorSystem}
import java.net.InetSocketAddress
import graphitenotifier.server.{ConsoleNotifier, Level, Check, PlaintextServer}

object Main {
 def main(args: Array[String]) {
   val actorSystem = ActorSystem.create
   val checks = List(new Check(".*".r, (d) => if (d>50) Level.CRITICAL else Level.OK ))
   val notifierProps = List(Props(classOf[ConsoleNotifier], ".*".r, Level.OK))

   val server = actorSystem.actorOf(Props(classOf[PlaintextServer], new InetSocketAddress("localhost", 1234), checks, notifierProps))
 }
}

