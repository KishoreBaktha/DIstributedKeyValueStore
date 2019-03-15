package se.kth.id2203.reconfiguration
import java.util.UUID
import se.kth.id2203.kvstore.{Operation, StopSign}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.LookupTable
import se.sics.kompics.{Component, Kill, Kompics, Start}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init, handle}
import se.sics.kompics.timer.Timer
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random
class ConfigurationManager extends ComponentDefinition {
  //******* Ports ******
  private val net = requires[Network]
  private val timer = requires[Timer]
  private val self = cfg.getValue[NetAddress]("id2203.project.address")
  //******* Variables ******
  private var configurations = mutable.Map.empty[Int, (ListBuffer[NetAddress], Boolean, Component)] // boolean is to mark started

  def startReplica(configurationId: Int, final_sequence: ListBuffer[Operation]) = {
    log.info(s"Creating new configuration $configurationId...")
    val replica = create[ReplicaComponent](classOf[ReplicaComponent], Init[ReplicaComponent](configurationId, configurations(configurationId)._1, final_sequence))
    connect[Timer](timer -> replica)
    connect[Network](net -> replica)
    //logger.info(s"new replica state " + replica.state())
    trigger(Start.event -> replica.getControl)
    replica
  }
  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      logger.info("ConfigurationManager started...")
    }
  }
  net uponEvent {
    case NetMessage(_, CreateNewConfiguration(configurationId: Int, assignment: LookupTable)) => handle {
      createNewConfiguration(configurationId, assignment)
    }
    case NetMessage(_, ConfigurationStopped(stopSign: StopSign)) if configurations.contains(stopSign.configurationId) => handle {
      if (!configurations(stopSign.configurationId)._2) {
        val configurationId = stopSign.configurationId
        if (configurations.contains(configurationId)) {
          val old_configurationId = stopSign.old_configurationId
          if (configurations.contains(old_configurationId)) {
            val old_topology = configurations(old_configurationId)._1
            val new_nodes = configurations(configurationId)._1 -- old_topology
            for (p <- new_nodes) {
              trigger(NetMessage(self, p, ConfigurationStopped(stopSign)) -> net)
            }
            if (configurations(old_configurationId)._3 != null)
              trigger(Kill.event -> configurations(old_configurationId)._3.getControl)
            configurations -= old_configurationId
          }
          if (configurations(configurationId)._1.contains(self)){
            val newReplica = startReplica(configurationId, stopSign.final_sequence)
            configurations(configurationId) = (configurations(configurationId)._1, true, newReplica)
          }
          else configurations(configurationId) = (configurations(configurationId)._1, true, null)
        }
      }
    }
  }
  //******* Methods ******
  private def createNewConfiguration(configurationId: Int, asgm: LookupTable) = {
    val nodes: ListBuffer[NetAddress] = asgm.lookup(asgm.partitionKey(self).get).to[ListBuffer]
    if (configurationId == 1) {
      configurations(configurationId) = (nodes, true, null)
      startReplica(1, ListBuffer.empty[Operation]) // first config has empty sequence
    }
    else {
      if (configurations.nonEmpty) {
        logger.info(s"current configurations: $configurations")
        val (old_configId, old_nodes) = configurations.maxBy(_._1)
        val old_config = old_nodes._1
        val i = Random.nextInt(old_config.size)
        val target: NetAddress = old_config.drop(i).head
        logger.info(s"sending stopsign to $target")
        trigger(NetMessage(self, target, StopSign(configurationId, old_configId)) -> net)
      }
      configurations(configurationId) = (nodes, false, null)
    }
  }
}
