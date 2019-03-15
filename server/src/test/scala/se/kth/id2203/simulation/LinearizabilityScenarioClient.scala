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

import se.kth.id2203.kvstore._
import se.kth.id2203.networking._
import se.kth.id2203.overlay.RouteMsg
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.sl._
import se.sics.kompics.sl.simulator.SimulationResult
import se.sics.kompics.timer.Timer
import se.sics.kompics.simulator.util.GlobalView

import scala.collection.mutable
import scala.collection.mutable.ListBuffer;

trait SimulationHistoryEntry {
  def operation: Operation
  def createdAt: Long
}
case class SimulationHistoryRequestEntry(operation: Operation, createdAt: Long = System.currentTimeMillis()) extends SimulationHistoryEntry with Serializable
case class SimulationHistoryResponseEntry(operation: Operation, response: OpResponse, createdAt: Long = System.currentTimeMillis()) extends SimulationHistoryEntry with Serializable

class LinearizabilityScenarioClient extends ComponentDefinition {

  val rand = scala.util.Random
  val key = "favorite-food";
  val values = List("Saigon Baguette", "Fried Rice", "Kebab")

  //******* Ports ******
  val net = requires[Network];
  val timer = requires[Timer];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address");
  var historyCount: Int = 0;
  var nofCompletedOperation = 0;

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
          val nofOperations = SimulationResult[Int]("linearizabilitytest.nofoperations")
          if (nofCompletedOperation < nofOperations) {
            sendRandomOperation()
          }
        }
        case None => logger.warn("ID $id was not pending! Ignoring response.")
      }
    }
  }
}
