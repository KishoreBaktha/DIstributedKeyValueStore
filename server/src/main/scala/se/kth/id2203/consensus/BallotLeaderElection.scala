
package se.kth.id2203.consensus

import java.io.Serializable
import java.net.InetAddress

import se.kth.id2203.bootstrapping.BootstrapServer.{Collecting, State}
import se.kth.id2203.bootstrapping.{Booted, Bootstrapping}
import se.kth.id2203.failuredetector.CheckTimeout
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.sics.kompics.network._
import se.sics.kompics.sl._
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.{KompicsEvent, Start}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer;


//object BallotLeaderElection
//{
//  sealed trait Status;
//  case object NotBooted extends Status;
//  case object BootComplete extends Status;
//}
case class HeartbeatReq(round: Long, highestBallot: Long)extends KompicsEvent;
case class HeartbeatResp(round: Long, ballot: Long) extends KompicsEvent;
class BallotLeaderElection(bleInit: Init[BallotLeaderElection]) extends ComponentDefinition  with Serializable   {

  private val ble = provides[BallotLeaderPort];
  private val net = requires[Network];
  private val timer = requires[Timer];
  //val boot = requires(Bootstrapping);
  //private var state: Status = NotBooted;

  private val (topology: ListBuffer[NetAddress], configurationId: Int) = bleInit match {
    case Init(nodes: ListBuffer[NetAddress], configId: Int) => (nodes, configId)
  }
  private val ballotOne = 0x0100000000l;
  private val self = cfg.getValue[NetAddress]("id2203.project.address")

  var delta:Long=100;
  val majority = (topology.size / 2) + 1

  def incrementBallot(ballot: Long): Long =
  {
    ballot + ballotOne;
  }

  def ballotFromNAddress(n: Int, adr: NetAddress): Long = {

    var temp = configurationId * 10000000 + n;

    //val configBytes = com.google.common.primitives.Ints.toByteArray(configurationId)
    //val nBytes = com.google.common.primitives.Ints.toByteArray(n);
    val nBytes = com.google.common.primitives.Ints.toByteArray(temp);
    val addrBytes = com.google.common.primitives.Ints.toByteArray(adr.hashCode());
    //val bytes = configBytes ++ nBytes ++ addrBytes;
    val bytes = nBytes ++ addrBytes;
    val r = com.google.common.primitives.Longs.fromByteArray(bytes);
    assert(r > 0); // should not produce negative numbers!
    return r;
  }

  // var period: Long = cfg.getValue[Long]("id2203.project.keepAlivePeriod");
  var period:Long=5000;
  var ballots = scala.collection.mutable.Map[NetAddress,Long]()

  private val minRound = configurationId * 10000000
  private val maxRound = ((configurationId + 1) * 10000000) - 1
  private var round = minRound + 0l;
  private var ballot = ballotFromNAddress(0, self);

  private var leader: Option[(NetAddress, Long)] = None;
  private var highestBallot: Long = ballot;

  private def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(period);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  private def checkLeader() {
    /* INSERT YOUR CODE HERE */
    var tempBallots=ballots;
    tempBallots+=(self->ballot);
    var (topProcess,topBallot) = tempBallots.maxBy(_._2);
    var top:(NetAddress,Long)=(topProcess,topBallot);
    if(topBallot<highestBallot)
    {
      while(ballot<=highestBallot)
      {
        ballot=incrementBallot(ballot);
      }
      leader=None;
    }
    else
    {
      if(Some(top)!=leader)
      {
        highestBallot=topBallot;
        leader=Some(top);
        println("leader is"+leader);
        trigger(BLE_Leader(topProcess,topBallot)->ble);
      }
    }
  }

  ctrl uponEvent {
    case _: Start => handle {
      println("ballot election started")
      startTimer(period);
    }
  }

  timer uponEvent {
    case CheckTimeout(_) => handle {
      /* INSERT YOUR CODE HERE */
      period = 5000
      if((ballots.size+1)>=majority)
      {
        checkLeader();
      }
      ballots = mutable.Map.empty[NetAddress, Long];
      round+=1;
      for(p<-topology)
      {
        if(p!=self)
        {
          logger.info(s"value is $p  highest ballot is $highestBallot  ")
          trigger(NetMessage(self,p,HeartbeatReq(round,highestBallot))->net);
        }
      }
      startTimer(period);
    }
  }

  net uponEvent {
    case NetMessage(header, HeartbeatReq(r, hb)) => handle {
      /* INSERT YOUR CODE HERE */
      if (r > minRound && r < maxRound) {
        logger.info("got heartbeat");
        if(hb > highestBallot)
        {
          highestBallot=hb;
        }
        trigger(NetMessage(self, header.src,HeartbeatResp(r,ballot))->net);
      }

    }
    case NetMessage(src, HeartbeatResp(r, b)) => handle {
      /* INSERT YOUR CODE HERE */
      if(r == round)
      {
        ballots+=(src.getSource()->b);
      }
      else if (r < maxRound)
      {
        period = period + delta;
      }
    }
  }
  //  boot uponEvent
  //    {
  //    case Booted(assignment: LookupTable) => handle {
  //      logger.info("lookuptable ready")
  //      val key = assignment.partitionKey(self)
  //      topology = assignment.lookup(key.get).toList
  //      majority=(assignment.lookup(key.get).toList.size/2)+1;
  //      state=BootComplete;
  //    }
  //  }
}