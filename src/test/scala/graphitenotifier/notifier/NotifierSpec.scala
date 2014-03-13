package graphitenotifier.notifier

import akka.actor.{ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import org.scalatest.matchers.ShouldMatchers
import graphitenotifier.{Metric, Level, Event, State}
import java.util.Date

class NotifierSpec (_system: ActorSystem) extends TestKit(_system)
with ImplicitSender with WordSpecLike with ShouldMatchers with BeforeAndAfterAll{
  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  var eventList = List.empty[Event]

  class DummyNotifier(level: Level) extends Notifier(".*".r, level) {
    def notify(event: Event): Unit = {
      eventList = event :: eventList
    }
  }


  "OK level notifier " should {
    "notify all" in {
      eventList = List.empty[Event]
      eventList.size should equal(0)

      val actorRef = TestActorRef(new DummyNotifier(Level.OK))
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.WARNING, State.FAIL)
      eventList.size should equal(1)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.CRITICAL, State.LEVEL_CHANGED)
      eventList.size should equal(2)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.CRITICAL, State.STILL)
      eventList.size should equal(3)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.OK, State.RECOVER)
      eventList.size should equal(4)
    }
  }

  "WARNING level notifier " should {
    "notify warning or higher level" in {
      eventList = List.empty[Event]
      eventList.size should equal(0)

      val actorRef = TestActorRef(new DummyNotifier(Level.WARNING))
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.WARNING, State.FAIL)
      eventList.size should equal(1)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.CRITICAL, State.LEVEL_CHANGED)
      eventList.size should equal(2)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.CRITICAL, State.STILL)
      eventList.size should equal(3)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.OK, State.RECOVER)
      eventList.size should equal(4)
    }
  }

  "CRITICAL level notifier " should {
    "notify critical level" in {
      eventList = List.empty[Event]
      eventList.size should equal(0)

      val actorRef = TestActorRef(new DummyNotifier(Level.CRITICAL))
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.WARNING, State.FAIL)
      eventList.size should equal(0)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.CRITICAL, State.LEVEL_CHANGED)
      eventList.size should equal(1)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.CRITICAL, State.STILL)
      eventList.size should equal(2)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.WARNING, State.LEVEL_CHANGED)
      eventList.size should equal(3)
      actorRef ! Event(Metric("hoge", 1, new Date()), Level.OK, State.RECOVER)
      eventList.size should equal(3)
    }
  }
}
