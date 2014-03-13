package graphitenotifier.server

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.ConfigFactory
import collection.JavaConversions._


class ConfigStudySpec extends FlatSpec with ShouldMatchers {
  behavior of "config"
  val conf = ConfigFactory.load("test.conf")
  it should "can load config file" in {
    conf should not equal(null)
  }
  it should "get int" in {
    conf.getInt("hoge") should equal(1)
  }
  it should "get array" in {
    val b = conf.getIntList("objectTest.key2")
    b.isInstanceOf[java.util.List[Integer]] should equal(true)
    b.toList should equal(List(1,2,3))
    val obj = conf.getObject("objectTest").toConfig
    obj.getIntList("key2") should equal(b)
  }
}
