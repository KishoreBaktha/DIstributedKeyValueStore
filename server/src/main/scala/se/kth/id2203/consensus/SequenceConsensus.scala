package se.kth.id2203.consensus

import se.kth.id2203.kvstore.Operation
import se.sics.kompics.KompicsEvent
import se.sics.kompics.sl.Port

import scala.collection.mutable.ListBuffer

class SequenceConsensus extends Port {
  request[SC_Propose];
  indication[SC_Decide];
}
case class SC_Propose(value: Operation) extends KompicsEvent;
case class SC_Decide(value: Operation) extends KompicsEvent;


case class Prepare(nL: Long, ld: Int, na: Long) extends KompicsEvent;
case class Promise(nL: Long, na: Long, suffix: ListBuffer[Operation], ld: Int) extends KompicsEvent;
case class AcceptSync(nL: Long, suffix: ListBuffer[Operation], ld: Int) extends KompicsEvent;
case class Accept(nL: Long, c: Operation) extends KompicsEvent;
case class Accepted(nL: Long, m: Int) extends KompicsEvent;
case class Decide(ld: Int, nL: Long) extends KompicsEvent;
case class ExtensionRequest(Time: Long) extends KompicsEvent;
case class ExtensionRequestPromise(Time: Long) extends KompicsEvent;

object State extends Enumeration
{
  type State = Value;
  val WAIT,PREPARE, ACCEPT, UNKOWN = Value;
}

object Role extends Enumeration {
  type Role = Value;
  val LEADER, FOLLOWER = Value;
}
