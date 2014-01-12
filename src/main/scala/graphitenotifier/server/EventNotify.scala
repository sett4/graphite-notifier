package graphitenotifier.server

import scala.util.matching.Regex
import akka.actor.{ActorRef, Props, Actor}

class BaseNotifier(val notifierProps: List[Props]) extends Actor {
  val notifiers: List[ActorRef] = notifierProps.map( (props) => {
    context.actorOf(props)
  })

  def receive = {
    case _ =>
    // DO NOTHING
  }

  @throws(classOf[Exception])
  override def preStart(): Unit = {
  }
}

abstract class Notifier(pathPattern: Regex, level: Level) extends Actor {
  def receive = {
    case event: Event => {
      pathPattern.findFirstIn(event.metric.path) match {
        case s: Some[String] => if (event.level >= level) notify(event)
        case _ =>
      }
    }
    case _ =>
  }

  def notify(event: Event)
}

class ConsoleNotifier(pathPattern: Regex = ".*".r, level: Level = Level.OK) extends Notifier(pathPattern, level) {
  def notify(event: Event) = {
    println(event)
  }
}
