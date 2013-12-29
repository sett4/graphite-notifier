package graphitenotifier

import akka.actor.Actor
import scala.util.Success
import java.util.Date

case class Metric(path: String, value: Double, timestamp: Date) {
  def toPlaintext = {
    "%s\t%f\t%s" format(path, value, timestamp)
  }
}

class MetricDispatcher extends Actor {
  def receive = {
    case Success(update: Metric) => {
      print(this.toString+": ")
      println(update)
    }
  }
}
