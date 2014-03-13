package graphitenotifier.notifier

import scala.util.matching.Regex
import akka.actor.{ActorRef, Props, Actor}
import graphitenotifier.{State, Event, Level}

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

abstract class Notifier(pathPattern: Regex, level: Level = Level.OK) extends Actor {
  private var lastLevel: Level = Level.OK
  def receive = {
    case event: Event => {
      pathPattern.findFirstIn(event.metric.path) match {
        case s: Some[String] => if (lastLevel >= level || event.level >= level) {
          notify(event)
          lastLevel = event.level
        }
        case _ =>
      }
    }
    case _ =>
  }

  def notify(event: Event)
}



