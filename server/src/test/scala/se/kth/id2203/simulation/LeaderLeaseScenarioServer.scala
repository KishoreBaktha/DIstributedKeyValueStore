package se.kth.id2203;

import java.util.UUID

import se.kth.id2203.bootstrapping._
import se.kth.id2203.consensus.{HeartbeatReq, HeartbeatResp}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay._
import se.kth.id2203.reconfiguration.ConfigurationManager
import se.sics.kompics.sl._
import se.sics.kompics.{Channel, Init, KompicsEvent}
import se.sics.kompics.network.Network
import se.sics.kompics.timer.{SchedulePeriodicTimeout, Timeout, Timer};

case class LeaderLeaseTestDisconnect(t: Int) extends KompicsEvent

class LeaderLeaseScenarioServer extends ComponentDefinition {

  private val self = cfg.readValue[NetAddress]("id2203.project.address")
  private val leader = cfg.readValue[NetAddress]("id2203.project.leader-address")
  private var timeoutId: Option[UUID] = None
  private var hasDisconnected = false
  private var configurationManagerNetworkChanel: Option[Channel[Network]] = None;

  //******* Ports ******
  private val net = requires[Network]
  private val timer = requires[Timer]
  //******* Children ******
  private val overlay = create(classOf[VSOverlayManager], Init.NONE);
  private val configurationManager = create(classOf[ConfigurationManager], Init.NONE)
  private val boot = cfg.readValue[NetAddress]("id2203.project.bootstrap-address") match {
    case Some(_) => create(classOf[BootstrapClient], Init.NONE) // start in client mode
    case None    => create(classOf[BootstrapServer], Init.NONE) // start in server mode
  }
  {
    // Boot
    connect[Timer](timer -> boot)
    connect[Network](net -> boot)

    // Overlay
    connect(Bootstrapping)(boot -> overlay)
    connect[Network](net -> overlay)
    connect[Timer](timer -> overlay)

    // Configuration Manager
    configurationManagerNetworkChanel = Some(connect[Network](net -> configurationManager))
    connect[Timer](timer -> configurationManager)
  }

  net uponEvent {
    case NetMessage(_, LeaderLeaseTestDisconnect(_)) => handle {
      println("test server received disconnect message")
      if (hasDisconnected) {
        println("reconnect network for leader")
        configurationManagerNetworkChanel = Some(connect[Network](net -> configurationManager))
        hasDisconnected = false
      }
      else {
        println("disconnect network from leader")
        disconnect[Network](configurationManagerNetworkChanel.get)
        hasDisconnected = true
      }
    }
  }
}