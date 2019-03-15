
//package se.kth.id2203.bootstrapping;


package se.kth.id2203.failuredetector

import se.sics.kompics.sl._
import se.kth.id2203.bootstrapping.{BSTimeout, CheckIn, Ready}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.sl.ComponentDefinition
import se.sics.kompics.timer.{SchedulePeriodicTimeout, Timer}

class EPFD() extends ComponentDefinition
{

  //EPFD subscriptions
  val timer = requires[Timer];
  //val pLink = requires[PerfectLink];
  val epfd = provides[EventuallyPerfectFailureDetector];
  val net = requires[Network];
  // EPDF component state and initialization
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  //configuration parameters
  //val self = epfdInit match {case Init(s: Address) => s};
  val topology = cfg.getValue[List[NetAddress]]("epfd.simulation.topology");
  //val delta = cfg.getValue[Long]("epfd.simulation.delay");

  //mutable state
  var period: Long = cfg.getValue[Long]("id2203.project.keepAlivePeriod");
  var alive = Set(cfg.getValue[List[NetAddress]]("epfd.simulation.topology"): _*);
  var suspected = Set[NetAddress]();
  var seqnum = 0;

  def startTimer(delay: Long): Unit = {
    val timeout: Long = cfg.getValue[Long]("id2203.project.keepAlivePeriod");
    val spt = new SchedulePeriodicTimeout(timeout, timeout);
    spt.setTimeoutEvent(BSTimeout(spt));
    trigger (spt -> timer);
  }

  //EPFD event handlers
  ctrl uponEvent
    {
      case _: Start => handle {
        startTimer(period);

      }
    }

  timer uponEvent {
    case CheckTimeout(_) => handle {
      if (!alive.intersect(suspected).isEmpty) {
        period=period+cfg.getValue[Long]("id2203.project.keepAlivePeriod");
      }

      seqnum = seqnum + 1;

      for (p <- topology) {
        if (!alive.contains(p) && !suspected.contains(p)) {
          suspected = suspected+p;
          trigger(Suspect(p) -> epfd);

        } else if (alive.contains(p) && suspected.contains(p)) {
          suspected = suspected - p;
          trigger(Restore(p) -> epfd);
        }
        trigger(NetMessage(self,p,HeartbeatRequest(seqnum)) -> net);
      }
      alive = Set[NetAddress]();
      startTimer(period);
    }
  }

  net uponEvent {
    case NetMessage(src, HeartbeatRequest(seq)) => handle {
      trigger(NetMessage(src, HeartbeatReply(seq)) -> net);

    }
    case NetMessage(src, HeartbeatReply(seq)) => handle {
      if(seq==seqnum||suspected.contains(src.getSource()))
        alive=alive+src.getSource();

    }
  }
};
