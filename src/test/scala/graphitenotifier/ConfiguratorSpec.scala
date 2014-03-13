package graphitenotifier

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.ConfigFactory

/**
 * Created with IntelliJ IDEA.
 * User: sett4
 * Date: 2014/03/13
 * Time: 2:14
 * To change this template use File | Settings | File Templates.
 */
class ConfiguratorSpec extends FlatSpec with ShouldMatchers {
  import collection.JavaConversions._

  behavior of "configurator"
  val conf = ConfigFactory.load("sample.conf")

  it should "can load config file" in {
    conf should not equal(null)
  }

  val configurator = new Configurator(conf)
  it should "have server properties" in {
    configurator.port should equal(1234)
    configurator.address should equal("0.0.0.0")
  }
  
  it should "return check list" in {
    val checks: List[Check] = configurator.checks
    checks.size should equal(2)

    val c = checks.head
    c.toLevel(1) should equal(Level.OK)
    c.toLevel(81) should equal(Level.WARNING)
    c.toLevel(100) should equal(Level.WARNING)
    c.toLevel(100.1) should equal(Level.CRITICAL)

    c.pathPattern.toString should equal("^df".r.toString)

  }

  it should "return notifier configuration list" in {
    val notifierConfig = configurator.getNotifierConfigurations(configurator.config.getObjectList("notifiers").toList)
    notifierConfig.size should equal(1)

    val (className, path, level, config)  = notifierConfig.head
    className should equal("graphitenotifier.notifier.ConsoleNotifier")
    path should equal(".*")
    level should equal(Level.OK)
    config should not equal(null)
  }
}
