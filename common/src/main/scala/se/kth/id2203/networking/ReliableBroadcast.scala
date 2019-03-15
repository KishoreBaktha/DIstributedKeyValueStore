package se.kth.id2203.networking

import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.{ComponentDefinition => _, Port => _, KompicsEvent}

class ReliableBroadcast extends Port {
  indication[RB_Deliver];
  request[RB_Broadcast];
}

case class RB_Deliver(source: NetAddress, payload: KompicsEvent) extends KompicsEvent;
case class RB_Broadcast(payload: KompicsEvent) extends KompicsEvent;

case class OriginatedData(src: NetAddress, payload: KompicsEvent) extends KompicsEvent;

class EagerReliableBroadcast(init: Init[EagerReliableBroadcast]) extends ComponentDefinition {
  //EagerReliableBroadcast Subscriptions
  val beb = requires[BestEffortBroadcast];
  val rb = provides[ReliableBroadcast];

  //EagerReliableBroadcast Component State and Initialization
  val self = init match {
    case Init(s: NetAddress) => s
  };
  val delivered = collection.mutable.Set[KompicsEvent]();

  //EagerReliableBroadcast Event Handlers
  rb uponEvent {
    case x@RB_Broadcast(payload) => handle {
      trigger(BEB_Broadcast(OriginatedData(self, payload)) -> beb)
    }
  }

  beb uponEvent {
    case BEB_Deliver(_, data@OriginatedData(origin, payload)) => handle {
      if (!(delivered contains data)){
        delivered += data
        trigger(RB_Deliver(origin, payload) -> rb)
        trigger(BEB_Broadcast(data) -> beb)
      }

    }
  }
}