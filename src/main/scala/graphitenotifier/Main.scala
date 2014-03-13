package graphitenotifier

import akka.actor.{Props, ActorSystem}
import java.net.InetSocketAddress
import graphitenotifier.server._
import com.typesafe.config.{Config, ConfigObject, ConfigFactory}

object Main {
  def main(args: Array[String]) {
    val config = ConfigFactory.load()
    val configurator = new Configurator(config)
    val bootstrapProps = configurator.createBootstrap

    val actorSystem = ActorSystem.create
    actorSystem.actorOf(bootstrapProps)
  }

  
}

class Configurator(val config: Config) {
  import collection.JavaConversions._

  def port: Integer = config.getInt("server.port")
  def address: String = config.getString("server.address")

  def checks: List[Check] = {
    val configObjectList = config.getObjectList("checks").toList
    configObjectList.map { (o) => {
      val c = o.toConfig
      List("path", "op", "warning", "critical").foreach {(f) => {
        if (!c.hasPath(f)) { throw new IllegalArgumentException("need attribute:%s for check array element".format(f))  }
      }}

      val path = c.getString("path")
      val op = c.getString("op")
      val warning = c.getDouble("warning")
      val critical = c.getDouble("critical")
      val f = (d: Double) => {
        op match {
          case "lessThan" => if (d<critical) { Level.CRITICAL } else if (d<warning) { Level.WARNING } else { Level.OK }
          case "greaterThan" => if (d>critical) { Level.CRITICAL } else if (d>warning) { Level.WARNING } else { Level.OK }
          case _ => throw new IllegalArgumentException("attribute:%s can accepts lessThan or greaterThan only")
        }
      }

      Check(path.r, f)
    }}
  }

  def notifierProps: List[Props] = {
    val configObjectList = config.getObjectList("notifiers").toList
    getNotifierConfigurations(configObjectList).map( (c) => {
      val (className, path, level, config) = c
      Props(Class.forName(className), path.r, level, config)
    })
  }

  def getNotifierConfigurations(configObjectList: List[ConfigObject]) = {
    configObjectList.map { (o) => {
      val c = o.toConfig

      List("path", "class").foreach {(f) => {
        if (!c.hasPath(f)) { throw new IllegalArgumentException("need attribute:%s for check array element".format(f))  }
      }}

      val className = c.getString("class")
      val path = c.getString("path")
      val level = Level.valueOf(c.getString("level"))
      Tuple4(className, path, level, c)
    }
    }
  }

  def createBootstrap: Props = {
    Props(classOf[PlaintextServer], new InetSocketAddress(address, port), checks, notifierProps)
  }
}