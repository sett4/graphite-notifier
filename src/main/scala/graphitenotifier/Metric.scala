package graphitenotifier

import akka.actor.Actor
import scala.util.Success
import java.util.Date
import scala.util.matching.Regex

case class Metric(path: String, value: Double, timestamp: Date) {
  def toPlaintext = {
    "%s\t%f\t%s" format(path, value, timestamp)
  }
}

case class CheckResult(metric: Metric, level: Level)

object Check {
  type ValueToLevelFunc = (Double) => Level
}

case class Check(pathPattern: Regex, toLevel: Check.ValueToLevelFunc) {
  def check(m: Metric): Option[CheckResult] = {
    pathPattern.findFirstIn(m.path) match {
      case s: Some[String] => Some(CheckResult(m, toLevel(m.value)))
      case _ => None
    }
  }
}

object Level {
  case object CRITICAL extends Level(80)
  case object WARNING extends Level(50)
  case object OK extends Level(0)

  val values = Array(CRITICAL, WARNING, OK)
  def valueOf(name: String): Level = {
    values.filter( _.name == name).head
  }
}

sealed abstract class Level(val levelOrder: Int) extends Ordered[Level] {
  val name = toString
  def compare(that: Level): Int = { this.levelOrder  - that.levelOrder }
}

object State {
  case object FAIL extends State
  case object LEVEL_CHANGED extends State
  case object STILL extends State
  case object RECOVER extends State

  def values = Array(FAIL, LEVEL_CHANGED, STILL, RECOVER)
  def valueOf(name: String): State = {
    values.filter( _.name == name).head
  }
}

sealed abstract class State {
  val name = toString
}

case class Event(metric: Metric, level: Level, state: State)

object CheckOp {
  case object GREATER_THAN extends CheckOp("greaterThan")
  case object LESS_THAN extends CheckOp("lessThan")
  val values = Array(GREATER_THAN, LESS_THAN)

  def valueOf(name: String): CheckOp = {
    values.filter({ _.label.equals(name) }).head
  }
}

sealed abstract class CheckOp(val label: String) {
  val name = toString
}
