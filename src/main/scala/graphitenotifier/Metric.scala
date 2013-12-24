package graphitenotifier

import akka.actor.Actor
import scala.util.Success

case class Metric(path: String, value: Double, timestamp: Long) {
  def toPlaintext = {
    "%s\t%f\t%d" format(path, value, timestamp)
  }
}

class MetricDispatcher extends Actor {
  def receive = {
    case Success(line: String) => {
      print(this.toString+": ")
      println(line)
    }
    case Success(update: Metric) => {
      print(this.toString+": ")
      println(update)
    }
  }
}
