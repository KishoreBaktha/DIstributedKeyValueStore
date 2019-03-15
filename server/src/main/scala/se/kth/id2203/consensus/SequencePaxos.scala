package se.kth.id2203.consensus

import se.kth.id2203.bootstrapping.{BSTimeout, Booted, Bootstrapping}
import se.kth.id2203.consensus.Role._
import se.kth.id2203.consensus.SequencePaxos.{Finished, NotFinished, Status}
import se.kth.id2203.consensus.State._
import se.kth.id2203.failuredetector.CheckTimeout
import se.kth.id2203.kvstore._
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.LookupTable
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init, _}
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object SequencePaxos {

  sealed trait Status;

  case object NotFinished extends Status;

  case object Finished extends Status;
}

class SequencePaxos(paxosInit: Init[SequencePaxos]) extends ComponentDefinition {

  private val (topology: ListBuffer[NetAddress], configurationId: Int, final_sequence: ListBuffer[Operation]) = paxosInit match {
    case Init(nodes: ListBuffer[NetAddress], configId: Int, final_seq: ListBuffer[Operation]) => (nodes, configId, final_seq)
  }

  def ballotFromNAddress(configuartionId: Int, n: Int, adr: NetAddress): Long = {

    val temp = configurationId * 10000000 + n;

    val configBytes = com.google.common.primitives.Ints.toByteArray(configurationId)
    //val nBytes = com.google.common.primitives.Ints.toByteArray(n);
    val nBytes = com.google.common.primitives.Ints.toByteArray(temp);
    val addrBytes = com.google.common.primitives.Ints.toByteArray(adr.hashCode());
    //val bytes = configBytes ++ nBytes ++ addrBytes;
    val bytes = nBytes ++ addrBytes;
    val r = com.google.common.primitives.Longs.fromByteArray(bytes);
    assert(r > 0); // should not produce negative numbers!
    return r;
  }

  /** * Reconfiguration variables ***/
  val self = cfg.getValue[NetAddress]("id2203.project.address")

  val majority = (topology.size / 2) + 1
  val others = topology.filterNot(p => p.equals(self))

  val sc = provides[SequenceConsensus];
  val ble = requires[BallotLeaderPort];
  val net = requires[Network];
  //val boot = requires(Bootstrapping);
  val timer = requires[Timer];
  var defaultlimit = cfg.getValue[Long]("id2203.project.leaderLeaseDuration")
  val p = cfg.getValue[Double]("id2203.project.address.leaderLeaseClockBound")

  //var topology = List.empty[NetAddress]
  //var others = List.empty[NetAddress]
  //var majority = -1
  private var status: Status = NotFinished;
  //  val majority = (topology.size / 2) + 1;
  var state = (FOLLOWER, UNKOWN);
  var nL = ballotFromNAddress(configurationId, 0, self) - 1
  var tL = 0l;
  var tLTemp = 0l;
  var tempLeader: NetAddress = null;
  var tempNl = 0l;
  var tempLd = 0;
  var tempNa = 0l;
  var nProm = nL;
  var tProm = 0l;
  var leader: Option[NetAddress] = None;
  var na = 0l;
  var va = final_sequence;
  var ld = 0;
  // leader state
  var propCmds = ListBuffer.empty[Operation];
  var las = mutable.Map.empty[NetAddress, Int];
  var lds = mutable.Map.empty[NetAddress, Int];
  var les = mutable.Map.empty[NetAddress, Int];
//  var defaultlimit = 20000l;

  var period: Long = defaultlimit - 10000l;
  var lc = 0;
  var acks = mutable.Map.empty[NetAddress, (Long, ListBuffer[Operation])];

  var is_alone_leader = false

  def suffix(s: ListBuffer[Operation], l: Int): ListBuffer[Operation] = {
    s.drop(l)
  }

  def prefix(s: ListBuffer[Operation], l: Int): ListBuffer[Operation] = {
    s.take(l)
  }

  private def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(period);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  ctrl uponEvent {
    case _: Start => handle {
      log.info("Sequence Paxos started...")
      logger.info(s"final sequence $final_sequence")
      ld = final_sequence.size
      lc = final_sequence.size
      va = final_sequence

      if (final_sequence.nonEmpty){
        final_sequence.remove(final_sequence.size-1)  // don't execute stop sign
        for (op <- final_sequence){
          trigger(SC_Decide(op) -> sc)
        }
      }
    }
  }

  timer uponEvent {
    case CheckTimeout(_) => handle {
      logger.info("reached here")
      if (state == (LEADER, ACCEPT) && self.equals(leader.get) && status.equals(Finished)) {
        tLTemp = System.currentTimeMillis();
        logger.info("extending lease");
        les(self) = 1;
        for (p <- others)
          trigger(NetMessage(self, p, ExtensionRequest(tLTemp)) -> net);
        // startTimer(period);
      }
//      if (state == (FOLLOWER, WAIT) && tempLeader != null) {
//        trigger(NetMessage(self, tempLeader, Prepare(tempNl, tempLd, tempNa)) -> net);
//      }
    }
  }

  ble uponEvent {
    case BLE_Leader(l, n) => handle {
      if (others.isEmpty) {
        logger.info("I AM SINGLE")
        is_alone_leader = true
      }
      else {
        logger.info(s"BLE_Leader in consensus $n $nL")
        if (n > nL) {
          status = NotFinished;
          log.info("New leader " + (l, n));
          logger.info(s"TopologySize- $topology.size")
          logger.info(s"Others- $others")
          leader = Option(l);
          nL = n;
          if (self.equals(l) && nL > nProm) {
            log.info("I AM LEADER")
            state = (LEADER, PREPARE);
            propCmds = ListBuffer.empty[Operation];
            for (p <- topology) {
              las(p) = 0;
            }
            lds = mutable.Map.empty[NetAddress, Int];
            les = mutable.Map.empty[NetAddress, Int];
            acks = mutable.Map.empty[NetAddress, (Long, ListBuffer[Operation])];
            lc = 0;
            tL = System.currentTimeMillis();
            logger.info(s"TimeLeader is  $tL")
            // startTimer(period);
            for (p <- others) {
              trigger(NetMessage(self, p, Prepare(nL, ld, na)) -> net);
            }
            // trigger(NetMessage()->net);
            logger.info("Sent prepare messages");
            acks(l) = (na, suffix(va, ld));
            lds(self) = ld;
            nProm = nL;
           les += (self -> 1);
          }
          else
            state = (FOLLOWER, state._2);
        }
      }
    }
  }

  def containsStopSign(configurationId: Int): (Boolean, ListBuffer[Operation]) ={
    for (op <- propCmds){
      op match {
        case ss@StopSign(_, old_cid, _, _, _) => {
          if (old_cid.equals(configurationId)){
            (true, (propCmds - ss) ++ ListBuffer(ss))
          }
        }
      }
    }
    (false, propCmds)
  }

  net uponEvent {
    case NetMessage(header, Prepare(np, ldp, n)) => handle {
      /* INSERT YOUR CODE HERE */
      if(state!=(LEADER,PREPARE)) {
        /* INSERT YOUR CODE HERE */
        logger.info("received prepare messages");
        var currtime = System.currentTimeMillis();
        logger.info(s"Current Time- $currtime")
        logger.info(s"Time Promise- $tProm")
         //  if (nProm < np)
        if (nProm < np && (System.currentTimeMillis() - tProm) > (defaultlimit * (1 + p)))
           {
          tProm = System.currentTimeMillis();
           logger.info(s"TimePromise is  $tProm ")
          nProm = np;
          state = (FOLLOWER, PREPARE);
          var sfx = ListBuffer.empty[Operation];

          if (na >= n) {
            sfx = suffix(va, ld);
          }
          logger.info(s"Sent promises to $header.src");
          trigger(NetMessage(self, header.src, Promise(np, na, sfx, ld)) -> net);
        }
        else if ((System.currentTimeMillis() - tProm) <= (defaultlimit * (1 + p))) {
             logger.info("Follower wait")
             state = (FOLLOWER, WAIT);
                tempLeader = header.src;
              tempNl = np;
             tempLd = ldp;
             tempNa = n;
             var m: Double = defaultlimit * (1 + p) - System.currentTimeMillis() - tProm;
             startTimer(m.toLong);
           }
      }
    }

    case NetMessage(header, Promise(n, na, sfxa, lda)) => handle {
      if ((n == nL) && (state == (LEADER, PREPARE))) {
        /* INSERT YOUR CODE HERE */
        acks(header.getSource()) = (na, sfxa);
        lds(header.getSource()) = lda;
        logger.info("received promise. acks=" + acks.size)
        if (acks.keySet.size >= majority) {
          logger.info("MAJORITY")
          status = Finished;
          startTimer(period);
          var sfx = ListBuffer.empty[Operation];
          var maxRound = 0l;
          for (ack <- acks) {
            if ((maxRound < ack._2._1) || (maxRound == ack._2._1 && sfx.size < ack._2._2.size)) {
              maxRound = ack._2._1;
              sfx = ack._2._2;
            }
          }

          logger.info(s"va = $va")
          va = prefix(va, ld) ++ sfx
          logger.info(s"(va, ld, sfx) = ($va,$ld, $sfx)")
          var lastAcceptOp: Option[Operation] = None
          if (va.nonEmpty){
            lastAcceptOp = Some(va.last)
          }
          logger.info(s"lastAcceptOp $lastAcceptOp")
          lastAcceptOp match {
            case Some(StopSign(cid, old_cid, _, _, _)) if old_cid.equals(configurationId) => {
                propCmds = ListBuffer.empty[Operation]
            }
            case None => {
              las(self) = va.size;
              state = (LEADER, ACCEPT);
            }
            case _ => {
              containsStopSign(configurationId) match {
                case (true, excluded_propCmds) => {
                  va = va ++ excluded_propCmds
                }
                case (false, _) => {
                  va = va ++ propCmds
                }
              }
              propCmds = ListBuffer.empty[Operation];
              las(self) = va.size;
              state = (LEADER, ACCEPT);
            }
          }

          for (p <- topology) {
            if (lds.contains(p) && p != self) {
              var sfxp = suffix(va, lds(p));
              trigger(NetMessage(self, p, AcceptSync(nL, sfxp, lds(p))) -> net);
            }
          }
        }
      }
      else if ((n == nL) && (state == (LEADER, ACCEPT))) {
        /* INSERT YOUR CODE HERE */
        lds(header.getSource()) = lda;
        var sfx = suffix(va, lds(header.getSource()));
        trigger(NetMessage(self, header.src, AcceptSync(nL, sfx, lds(header.getSource()))) -> net);
        if (lc != 0) {
          logger.info("DECIDE!!!!")
          trigger(NetMessage(self, header.src, Decide(ld, nL)) -> net);
        }
      }
    }
    case NetMessage(header, AcceptSync(nL, sfx, ldp)) => handle {
      if ((nProm == nL) && (state == (FOLLOWER, PREPARE))) {
        /* INSERT YOUR CODE HERE */
        logger.info(s"AcceptSync va = $va")
        na = nL;
        va = prefix(va, ld) ++ sfx;
        logger.info(s"AcceptSync after va = $va")
        trigger(NetMessage(self, header.src, Accepted(nL, va.size)) -> net);
        state = (FOLLOWER, ACCEPT);
      }
    }
    case NetMessage(header, Accept(nL, c)) => handle {
      if ((nProm == nL) && (state == (FOLLOWER, ACCEPT))) {
        /* INSERT YOUR CODE HERE */
        logger.info("PUT OPERATION ACCEPT")
        va = va ++ List(c);
        trigger(NetMessage(self, header.src, Accepted(nL, va.size)) -> net);
      }
    }
    case NetMessage(_, Decide(l, nL)) => handle {
      /* INSERT YOUR CODE HERE */
      if (nProm == nL) {
        while (ld < l) {
          val op = va(ld)
          op match {
            case ss@StopSign(configId, old_configurationId, _, _, _) => {
              ss.final_sequence = va
              trigger(SC_Decide(ss) -> sc);
              ld += 1;
            }
            case _ => {
              trigger(SC_Decide(va(ld)) -> sc);
              ld += 1;
            }
          }

        }
      }
    }
    case NetMessage(header, Accepted(n, m)) => handle {
      if ((n == nL) && (state == (LEADER, ACCEPT))) {
        /* INSERT YOUR CODE HERE */
        las(header.getSource()) = m;
        var P = topology.filter(las(_) >= m);
        if (lc < m && P.size >= majority) {
          lc = m;
          for (p <- topology.filter(lds.contains(_))) {
            trigger(NetMessage(self, p, Decide(lc, nL)) -> net);
          }
        }
      }

    }
    case NetMessage(header, ExtensionRequest(time)) => handle {
      trigger(NetMessage(self, header.src, ExtensionRequestPromise(time)) -> net)
    }
    case NetMessage(header, ExtensionRequestPromise(time)) => handle {
      if (state == (LEADER, ACCEPT)) {
        les(header.getSource()) = 1;
        var size = les.keySet.size;
        logger.info(s"My size is $size")
        if (size >= (topology.size / 2) + 1) {
          startTimer(period);
          les = mutable.Map.empty[NetAddress, Int];
          tL = time;
          logger.info("extended")
        }
      }
    }
  }

  def stopped: Boolean = {
    if (va.isEmpty || ld == 0) false
    else {
      va(ld-1) match {
        case StopSign(_, old_configurationId, _, _, _) if old_configurationId.equals(configurationId)=> {
          true
        }
        case _ => false
      }
//      val a = va.size
//      logger.info(s"va: $a, ld: $ld")
//      va(ld).isInstanceOf[StopSign]
    }
  }

  sc uponEvent {
  case SC_Propose(c: Operation) => handle {
    if (is_alone_leader) {
      logger.info("I AM ALONE")
      if (c.isInstanceOf[StopSign]){
        val ss: StopSign = c.asInstanceOf[StopSign]
        ss.final_sequence = va
        trigger (SC_Decide (ss) -> sc);
      }
      else
        trigger(SC_Decide(c) -> sc);
    }
    else {
      if (state == (LEADER, PREPARE)) {
        logger.info("PROPOSING OPERATION")
        /* INSERT YOUR CODE HERE */
        propCmds += c
      }
      else if (state == (LEADER, ACCEPT) && !stopped) {
        /* INSERT YOUR CODE HERE */
        var diff = System.currentTimeMillis() - tL;
        logger.info(s"Leader time is: $tL");
        logger.info(s"Time difference is $diff ")
        c match {
          case GetOp(_, _, _) => {
            if (diff < defaultlimit * (1 - p)) {
              logger.info("worked");
              trigger(SC_Decide(c) -> sc);
              logger.info("No Majority required");
            }
            else {
              logger.info("not worked");
              va += c;
              las(self) += 1;
              var P = others.filter(lds.contains(_));
              for (p <- P) {
                trigger(NetMessage(self, p, Accept(nL, c)) -> net);
              }
            }
          }
          case _ => {
            va += c;
            las(self) += 1;
            var P = others.filter(lds.contains(_));
            logger.info(s"Sending Accept($nL, $c) to $P")
            for (p <- P) {
              trigger(NetMessage(self, p, Accept(nL, c)) -> net);
            }
          }
        }
      }
    }
  }

}

}


