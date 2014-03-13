package graphitenotifier.notifier

import scala.util.matching.Regex
import graphitenotifier.{Event, Level}
import com.typesafe.config.Config


class ConsoleNotifier(pathPattern: Regex = ".*".r, level: Level = Level.OK, config: Config) extends Notifier(pathPattern, level) {
   def notify(event: Event): Unit = {
     println(event)
   }
 }
