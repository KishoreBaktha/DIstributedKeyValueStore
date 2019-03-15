package se.kth.id2203.consensus

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent
import se.sics.kompics.sl.Port

class BallotLeaderPort extends Port {
  indication[BLE_Leader];
}

case class BLE_Leader(leader: NetAddress, ballot: Long)  extends KompicsEvent with Serializable;

