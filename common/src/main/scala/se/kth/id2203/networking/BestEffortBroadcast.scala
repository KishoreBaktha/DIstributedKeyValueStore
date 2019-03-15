package se.kth.id2203.networking

import se.sics.kompics.network._
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.{ComponentDefinition => _, Port => _, KompicsEvent}

import scala.collection.immutable.Set

class BestEffortBroadcast extends Port{
  indication[BEB_Deliver];
  request[BEB_Broadcast];
}

case class BEB_Deliver(source: NetAddress, payload: KompicsEvent) extends KompicsEvent;
case class BEB_Broadcast(payload: KompicsEvent) extends KompicsEvent;

class BasicBroadcast(init: Init[BasicBroadcast]) extends ComponentDefinition {
  //subscriptions
  val net = requires[Network];
  val beb = provides[BestEffortBroadcast];

  //configuration
  val (self, topology) = init match {
    case Init(s: NetAddress, t: Set[NetAddress]@unchecked) => (s, t)
  };

  //handlers
  beb uponEvent {
    case x: BEB_Broadcast => handle {
      for (q <- topology){
        //trigger(PL_Send(q, x) -> pLink)
        trigger(NetMessage(self, q, x) -> net)
      }
    }
  }

  net uponEvent{
    case NetMessage(header, BEB_Broadcast(payload)) => handle{
      trigger(BEB_Deliver(header.src, payload) -> beb)
    }
  }
}

