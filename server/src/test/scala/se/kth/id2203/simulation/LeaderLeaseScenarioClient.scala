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
package se.kth.id2203.simulation

import java.net.InetAddress
import java.util.UUID

import se.kth.id2203.LeaderLeaseTestDisconnect
import se.kth.id2203.failuredetector.CheckTimeout
import se.kth.id2203.kvstore._
import se.kth.id2203.networking._
import se.kth.id2203.overlay.RouteMsg
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.sl._
import se.sics.kompics.sl.simulator.SimulationResult
import se.sics.kompics.timer.{ScheduleTimeout, Timer}
import se.sics.kompics.simulator.util.GlobalView

import scala.collection.mutable
import scala.collection.mutable.ListBuffer;

class LeaderLeaseScenarioClient extends ComponentDefinition {

  val rand = scala.util.Random
  val key = "favorite-food";
  val values = List("Saigon Baguette", "Fried Rice", "Kebab")
  //private val self = cfg.readValue[NetAddress]("id2203.project.address")
  private val leader = cfg.readValue[NetAddress]("id2203.project.leader-address")
  private val nofOperations = SimulationResult[Int]("LeaderLeaseTest.nofoperations")
  //******* Ports ******
  val net = requires[Network];
  val timer = requires[Timer];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address");
  var historyCount: Int = 0;
  var nofCompletedOperation = 0;
  var hasDiconnect = false;
  var hasReconnect = false;

  private val pending = mutable.Map.empty[UUID, Operation];

  def createRandomOperation(): Operation = {
    val expectedVal = values(rand.nextInt(values.size))
    val newVal = values(rand.nextInt(values.size))

    rand.nextInt(3) match {
      case 0 => GetOp(key, self)
      case 1 => PutOp(key, self, newVal)
      case 2 => CasOp(key, self, expectedVal, newVal)
    }
  }

  def sendRandomOperation(): Unit = {
    val op = createRandomOperation()
    val routeMsg = RouteMsg(key, op)

    logger.info("Sending {}", op)
    trigger(NetMessage(self, server, routeMsg) -> net)
    recordHistoryEntry(op, None)
    pending += (op.id -> op)

    if (self.equals(ScenarioHelper.intToClientAddress(5)) && historyCount > nofOperations / 2 && !hasDiconnect) {
      // Simulate disconnect
      println("try to disconnect server at " + System.currentTimeMillis())
      trigger(NetMessage(self, leader.get, LeaderLeaseTestDisconnect(1)) -> net)
      hasDiconnect = true
      startTimer(2000)
    }

  }

  private def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(delay);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  timer uponEvent {
    case CheckTimeout(_) => handle  {
      println("try to reconnect server at "+ System.currentTimeMillis())
      trigger(NetMessage(self, leader.get, LeaderLeaseTestDisconnect(1)) -> net)
      hasReconnect = true
    }
  }

  def recordHistoryEntry(operation: Operation, response: Option[OpResponse]): Unit = {
    val historyKey = "history-" + self + "-" + historyCount;

    response match {
      case Some(res) => SimulationResult += (historyKey -> ScenarioHelper.serialise(SimulationHistoryResponseEntry(operation, res)))
      case None => SimulationResult += (historyKey -> ScenarioHelper.serialise(SimulationHistoryRequestEntry(operation)))
    }

    historyCount += 1;
  }

  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      sendRandomOperation();
    }
  }

  net uponEvent {
    case NetMessage(header, opResponse @ OpResponse(id, value, status)) => handle {
      val currentTime = System.currentTimeMillis()

      logger.info(s"Got OpResponse: $opResponse at $currentTime")

      pending.remove(id) match {
        case Some(operation) => {
          recordHistoryEntry(operation, Option(opResponse))

          nofCompletedOperation += 1;
          val nofOperations = SimulationResult[Int]("LeaderLeaseTest.nofoperations")
          if (nofCompletedOperation < nofOperations) {
            sendRandomOperation()
          }
        }
        case None => logger.warn("ID $id was not pending! Ignoring response.")
      }
    }
  }
}
