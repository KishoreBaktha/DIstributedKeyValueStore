package se.kth.id2203.reconfiguration

import java.util.UUID

import se.kth.id2203.bootstrapping.{BootstrapClient, BootstrapServer, Bootstrapping, NodeAssignment}
import se.kth.id2203.consensus.{BallotLeaderElection, BallotLeaderPort, SequenceConsensus, SequencePaxos}
import se.kth.id2203.kvstore.{KVService, Operation}
import se.kth.id2203.networking.NetAddress
import se.kth.id2203.overlay.{LookupTable, Routing, VSOverlayManager}
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init, handle}
import se.sics.kompics.timer.Timer

import scala.collection.mutable.ListBuffer


class ReplicaComponent(replicaInit: Init[ReplicaComponent]) extends ComponentDefinition {

  private val self = cfg.getValue[NetAddress]("id2203.project.address")
  private val (configurationId: Int, topology: ListBuffer[NetAddress], final_sequence: ListBuffer[Operation]) = replicaInit match {
    case Init(configId: Int, nodes: ListBuffer[NetAddress], final_seq: ListBuffer[Operation]) => {
      (configId, nodes, final_seq)
    }
  }

  ctrl uponEvent {
    case _: Start => handle {
      logger.info("Replica started...")
    }
  }

  //******* Ports ******
  private val net = requires[Network]
  private val timer = requires[Timer]

  //******* Children ******
  private val kv = create(classOf[KVService], Init[KVService](configurationId))
  private val election = create(classOf[BallotLeaderElection], Init[BallotLeaderElection](topology, configurationId))
  private val sp = create(classOf[SequencePaxos], Init[SequencePaxos](topology, configurationId, final_sequence))  // self, topology, others

  {
    println(s"New replica with topology $topology ")
    // KV
    connect[BallotLeaderPort](election -> kv)
    connect[Network](net -> kv)
    connect[SequenceConsensus] (sp -> kv)

    // Consensus
    connect[BallotLeaderPort](election -> sp)
    connect[Network](net -> sp)
    connect[Timer](timer -> sp)

    // Election
    connect[Timer](timer -> election)
    connect[Network](net -> election)
  }
}
