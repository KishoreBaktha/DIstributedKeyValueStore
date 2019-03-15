/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.kvstore

;

import se.kth.id2203.bootstrapping.{Booted, Bootstrapping}
import se.kth.id2203.consensus._
import se.kth.id2203.networking._
import se.kth.id2203.overlay.{LookupTable, Routing}
import se.kth.id2203.reconfiguration.ConfigurationStopped
import se.sics.kompics.Start
import se.sics.kompics.sl._
import se.sics.kompics.network.Network

import scala.collection.mutable._;

class KVService(kvInit: Init[KVService]) extends ComponentDefinition {
  //******* Ports ******
  val net = requires[Network];
  val route = requires(Routing);
  val sequence_paxos = requires[SequenceConsensus];
  val leader_election = requires[BallotLeaderPort]
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val store: HashMap[String, String] = preloadedStore;

  var my_leader: Option[NetAddress] = None
  var leader_ballot: Long = -1

  private val (configurationId: Int) = kvInit match {
    case Init(configId: Int) => (configId)
  }

  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      log.info("KVStore started...")
    }
  }

  net uponEvent {
    case NetMessage(header, op: GetOp) => handle {
      log.info("Got operation {}!", op);
      my_leader match {
        case Some(leader) if leader.equals(self) => trigger(SC_Propose(op) -> sequence_paxos)
        case None => logger.info("...but has no leader, operation getting dropped")
        case _ => trigger(NetMessage(header.src, my_leader.get, op) -> net)
      }
    }

    case NetMessage(header, op: PutOp) => handle {
      log.info("Got operation {}!", op);
      my_leader match {
        case Some(leader) if leader.equals(self) => trigger(SC_Propose(op) -> sequence_paxos)
        case None => logger.info("...but has no leader, operation getting dropped")
        case _ => trigger(NetMessage(header.src, my_leader.get, op) -> net)
      }
    }

    case NetMessage(header, op: CasOp) => handle {
      log.info("Got operation {}!", op);
      my_leader match {
        case Some(leader) if leader.equals(self) => trigger(SC_Propose(op) -> sequence_paxos)
        case None => logger.info("...but has no leader, operation getting dropped")
        case _ => trigger(NetMessage(header.src, my_leader.get, op) -> net)
      }
    }

    case NetMessage(header, ss: StopSign) => handle {
      logger.info(s"get stopsign $ss")
      if (ss.old_configurationId.equals(configurationId)){
        my_leader match {
          case Some(leader) if leader.equals(self) => trigger(SC_Propose(ss) -> sequence_paxos)// trigger SS command
          case None => logger.info("Got propose StopSign but don't have leader")
          case _ => trigger(NetMessage(header.src, my_leader.get, ss) -> net)
        }
      }
    }
  }



  leader_election uponEvent {
    case BLE_Leader(leader: NetAddress, ballot: Long) => handle {
      if (leader_ballot < ballot) {
        my_leader = Some(leader)
        leader_ballot = ballot
      }
    }
  }

  sequence_paxos uponEvent {
    case SC_Decide(op: GetOp) => handle {
      log.info("Executing operation {}!", op);
      logger.info(s"kvstore leader: $my_leader $self")
      if (my_leader.get.equals(self)) { // respond only if I am leader
        store get op.key match {
          case None => trigger(NetMessage(self, op.receiver, op.response(None, OpCode.NotFound)) -> net)
          case value => trigger(NetMessage(self, op.receiver, op.response(value, OpCode.Ok)) -> net)
        }
      }
    }

    case SC_Decide(op: PutOp) => handle {
      log.info("Executing operation {}!", op);
      store.put(op.key, op.value);
      if (my_leader.get.equals(self)) { // respond only if I am leader
        trigger(NetMessage(self, op.receiver, op.response(Option(store(op.key)), OpCode.Ok)) -> net);
      }
    }

    case SC_Decide(op: CasOp) => handle {
      log.info("Executing operation {}!", op);
      store get op.key match {
        case None => {
          if (my_leader.get.equals(self))
            trigger(NetMessage(self, op.receiver, op.response(None, OpCode.NotFound)) -> net)
        }
        case Some(old_value) => {
          if (old_value.equals(op.expected)) {
            store(op.key) = op.desired
          }
          if (my_leader.get.equals(self))
            trigger(NetMessage(self, op.receiver, op.response(Option(old_value), OpCode.Ok)) -> net);
        }
      }
    }

    case SC_Decide(ss: StopSign) => handle{
      log.info("Decided on StopSign: " + ss.final_sequence)
      if (configurationId.equals(ss.old_configurationId))
        trigger(NetMessage(self, self, ConfigurationStopped(ss)) -> net)
    }

  }

  private def preloadedStore: HashMap[String, String] = {
    val store = new HashMap[String, String].empty;
    store("foo") = "bar";
    store
  }
}
