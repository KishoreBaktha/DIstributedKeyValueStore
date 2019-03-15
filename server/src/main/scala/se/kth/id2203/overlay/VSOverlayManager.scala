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
package se.kth.id2203.overlay;

import java.net.InetAddress

import se.kth.id2203.bootstrapping._
import se.kth.id2203.consensus.{BLE_Leader, BallotLeaderPort}
import se.kth.id2203.networking._
import se.kth.id2203.reconfigure.{ReconfigStatus, ReconfigurationRequest, ReconfigurationResponse}
import se.sics.kompics.sl._
import se.sics.kompics.network.Network
import se.sics.kompics.timer.{ScheduleTimeout, Timer}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
  * The V(ery)S(imple)OverlayManager.
  * <p>
  * Keeps all nodes in a single partition in one replication group.
  * <p>
  * Note: This implementation does not fulfill the project task. You have to
  * support multiple partitions!
  * <p>
  *
  * @author Lars Kroll <lkroll@kth.se>
  */
class VSOverlayManager extends ComponentDefinition
{
  //******* Ports ******
  val boot = requires(Bootstrapping)
  val net = requires[Network]
  val timer = requires[Timer]

  //******* Fields ******
  private val self = cfg.getValue[NetAddress]("id2203.project.address")
  private val replicationDegree = cfg.getValue[Int]("id2203.project.replicationDegree")
  private var lut: Option[LookupTable] = None


  //******* Handlers ******
  boot uponEvent {
    case GetInitialAssignments(nodes) => handle {
      log.info(s"Generating LookupTable with replication degree of $replicationDegree...")
      lut = Some(LookupTable.generate(nodes, replicationDegree))
      logger.debug("Generated LookupTable: " + lut.get.partitions.toString())

      trigger(InitialAssignments(lut.get) -> boot)
    }

    case Booted(assignment: LookupTable) => handle {
      log.info("Got NodeAssignment, overlay ready.")
      lut = Some(assignment)
    }
  }

  net uponEvent {
    case NetMessage(header, RouteMsg(key, msg)) => handle {
      logger.info("op with key's hashcode: " + key.hashCode())
      val nodes = lut.get.lookup(key)
      val i = Random.nextInt(nodes.size)
      val target = nodes.drop(i).head

      log.info(s"Forwarding message for key $key to $target")
      trigger(NetMessage(header.src, target, msg) -> net)
    }

    case NetMessage(header, msg: Connect) => handle {
      lut match {
        case Some(l) => {
          log.debug(s"Accepting connection request from ${header.src}");
          val size = l.getNodes().size
          trigger(NetMessage(self, header.src, msg.ack(size)) -> net);
          log.debug("SENT ACK")
        }
        case None => log.info(s"Rejecting connection request from ${header.src}, as system is not ready, yet.");
      }
    }

    case NetMessage(header, ReconfigurationRequest(partitionKey, nodes, id)) => handle {
      logger.info(s"Received reconfiguration request ($partitionKey, $nodes)")
      lut.get.updatePartition(partitionKey, nodes.toList)
      trigger(ReconfigurationAssignments(lut.get, partitionKey) -> boot)
      trigger(NetMessage(self, header.src, ReconfigurationResponse(ReconfigStatus.Ok, id)) -> net)
    }
  }
}
