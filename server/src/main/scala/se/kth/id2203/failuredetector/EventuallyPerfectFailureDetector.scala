package se.kth.id2203.failuredetector

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent
import se.sics.kompics.network.Address
import se.sics.kompics.sl.Port
import se.sics.kompics.timer.{ScheduleTimeout, Timeout}


class EventuallyPerfectFailureDetector  extends Port{
  indication[Suspect];
  indication[Restore];
}
case class Suspect(process: NetAddress) extends KompicsEvent;
case class Restore(process: NetAddress) extends KompicsEvent;

case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

case class HeartbeatReply(seq: Int) extends KompicsEvent;
case class HeartbeatRequest(seq: Int) extends KompicsEvent;
