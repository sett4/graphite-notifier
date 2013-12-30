package graphitenotifier.server


import scala.util.matching.Regex
import java.util.Date
import akka.io._
import graphitenotifier.Metric
import scala.Some

class CheckResultStage(val checks: List[Check]) extends SymmetricPipelineStage[PipelineContext, CheckResult, Metric] {
  val EMPTY_CHECK_RESULT = CheckResult(Metric("", 0, new Date(0)), Level.SAFE)

  def apply(ctx: PipelineContext): PipePair[CheckResult, Metric, CheckResult, Metric] = new SymmetricPipePair[CheckResult, Metric] {
    def commandPipeline = { cr: CheckResult => ctx.singleCommand(cr.metric) }

    def eventPipeline = (m: Metric) => {
      val results = checks.map( c => c.check(m) ).filter( _ != None )
      results.map (cr => Left(cr.get))
    }
  }
}

case class NextState(checkResult: CheckResult)
case class UpdateState(checkResult: CheckResult, state: State)

//class EventStateHolder extends Actor {
//  val STILL_INTERVAL: Long = 60*10*1000
//  val EMPTY_CHECK_RESULT = CheckResult(Metric("", 0, new Date(0)), Level.SAFE)
//
//  val liveEvents = scala.collection.mutable.Map[String, CheckResult]()
//
//  def receive = {
//    case NextState(cr) => sender ! newState(cr)
//    case UpdateState(cr, s) => {
//      if (s == State.RECOVER) {
//        liveEvents.remove(cr.metric.path)
//      } else {
//        liveEvents.put(cr.metric.path, cr)
//      }
//    }
//  }
//
//  def newState(checkResult: CheckResult): Option[State] = {
//    liveEvents.get(checkResult.metric.path) match {
//      case None if checkResult.level > Level.SAFE => {
//        Some(State.COME)
//      } // 新たに発生した
//      case Some(CheckResult(_, l)) if l != checkResult.level && checkResult.level == Level.SAFE =>  {
//        Some(State.RECOVER)
//      }// 回復してた
//      case Some(CheckResult(m, checkResult.level)) if timeElapsed(checkResult.metric.timestamp, m.timestamp, STILL_INTERVAL) => {
//        Some(State.STILL)
//      } // 状態変わってないけど続いてる
//      case _ => None // 変わってない。殆どがここを通過するはず
//    }
//  }
//
//  def timeElapsed(now: Date, orig: Date, elapsed: Long) = {
//    (now.getTime - orig.getTime) > elapsed
//  }
//}

class EventStage(liveEvents: scala.collection.mutable.Map[String, CheckResult]) extends SymmetricPipelineStage[PipelineContext, Event, CheckResult] {
  val STILL_INTERVAL: Long = 60*10*1000

  def apply(ctx: PipelineContext): PipePair[Event, CheckResult, Event, CheckResult] = new SymmetricPipePair[Event, CheckResult] {
    def commandPipeline = { event: Event => ctx.singleCommand(CheckResult(event.metric, event.level))}

    def eventPipeline = (cr: CheckResult) => {
      nextState(cr) match {
        case Some(s: State) => {
          if (s == State.RECOVER) {
            liveEvents.remove(cr.metric.path)
          } else {
            liveEvents.put(cr.metric.path, cr)
          }
          ctx.singleEvent(Event(cr.metric, cr.level, s))
        }
        case _ => Nil
      }
    }
  }

  def nextState(checkResult: CheckResult): Option[State] = {
    liveEvents.get(checkResult.metric.path) match {
      case None if checkResult.level > Level.SAFE => {
        Some(State.FAIL)
      } // 新たに発生した
      case Some(CheckResult(_, l)) if l != checkResult.level && checkResult.level == Level.SAFE =>  {
        Some(State.RECOVER)
      }// 回復してた
      case Some(CheckResult(m, checkResult.level)) if timeElapsed(checkResult.metric.timestamp, m.timestamp, STILL_INTERVAL) => {
        Some(State.STILL)
      } // 状態変わってないけど続いてる
      case _ => None // 変わってない。殆どがここを通過するはず
    }
  }

  def timeElapsed(now: Date, orig: Date, elapsed: Long) = {
    (now.getTime - orig.getTime) > elapsed
  }


}

case class CheckResult(metric: Metric, level: Level)

object Check {
  type ValueToLevelFunc = (Double) => Level
}

class Check(pathPattern: Regex, getLevel: Check.ValueToLevelFunc) {
  def check(m: Metric): Option[CheckResult] = {
    pathPattern.findFirstIn(m.path) match {
      case s: Some[String] => Some(CheckResult(m, getLevel(m.value)))
      case _ => None
    }
  }
}

object Level {
  case object FATAL extends Level(100)
  case object CRITICAL extends Level(80)
  case object WARN extends Level(70)
  case object SAFE extends Level(0)

  val values = Array(FATAL, CRITICAL, WARN, SAFE)
}

sealed abstract class Level(val levelOrder: Int) extends Ordered[Level] {
  val name = toString
  def compare(that: Level): Int = { this.levelOrder  - that.levelOrder }
}

object State {
  case object FAIL extends State
  case object STILL extends State
  case object RECOVER extends State
}

sealed abstract class State {
  val name = toString
}

case class Event(metric: Metric, level: Level, state: State)