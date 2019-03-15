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

import org.scalatest._
import se.kth.id2203.{LeaderLeaseScenarioServer, ParentComponent}

import se.sics.kompics.simulator.network.impl.{BinaryNetworkModel, KingLatencyModel, NetworkModels}
import se.sics.kompics.sl._
import se.sics.kompics.sl.simulator._
import se.sics.kompics.simulator.{SimulationScenario => JSimulationScenario}
import se.sics.kompics.simulator.run.LauncherComp
import se.sics.kompics.simulator.result.SimulationResultSingleton
import se.sics.kompics.simulator.util.GlobalView

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object LeaderLeaseTestParams {
  val nofOperations = 10
  val nofClients = 5
  val nofServers = 3
  val leaseDuration = 20000
  val clockBound = 0.02
}

object LeaderLeaseTestScenario {

  import Distributions._
  // needed for the distributions, but needs to be initialised after setting the seed
  implicit val random = JSimulationScenario.getRandom()

  private def isBootstrap(self: Int): Boolean = self == 1

  val setUniformLatencyNetwork = () => Op.apply((_: Unit) => ChangeNetwork(NetworkModels.withUniformRandomDelay(3, 7)))

  val startServerOp = Op { (self: Integer) =>

    val selfAddr = ScenarioHelper.intToServerAddress(self)
    val conf = if (isBootstrap(self)) {
      // don't put this at the bootstrap server, or it will act as a bootstrap client
      Map(
        "id2203.project.leader-address" -> ScenarioHelper.intToServerAddress(1),
        "id2203.project.replicationDegree" -> LeaderLeaseTestParams.nofServers,
        "id2203.project.address" -> selfAddr,
        "id2203.project.bootThreshold" -> LeaderLeaseTestParams.nofServers,
        "id2203.project.leaderLeaseDuration" -> LeaderLeaseTestParams.leaseDuration,
        "id2203.project.leaderLeaseClockBound" -> LeaderLeaseTestParams.clockBound)
    } else {
      Map(
        "id2203.project.leader-address" -> ScenarioHelper.intToServerAddress(6),
        "id2203.project.replicationDegree" -> LeaderLeaseTestParams.nofServers,
        "id2203.project.address" -> selfAddr,
        "id2203.project.bootThreshold" -> LeaderLeaseTestParams.nofServers,
        "id2203.project.leaderLeaseDuration" -> LeaderLeaseTestParams.leaseDuration,
        "id2203.project.leaderLeaseClockBound" -> LeaderLeaseTestParams.clockBound,
        "id2203.project.bootstrap-address" -> ScenarioHelper.intToServerAddress(1))
    }
    StartNode(selfAddr, Init.none[LeaderLeaseScenarioServer], conf)
  }

  val startClientOp = Op { (self: Integer) =>
    val selfAddr = ScenarioHelper.intToClientAddress(self)
    val conf = Map(
      "id2203.project.address" -> selfAddr,
      "id2203.project.leader-address" -> ScenarioHelper.intToServerAddress(1),
      "id2203.project.bootstrap-address" -> ScenarioHelper.intToServerAddress(1))
    StartNode(selfAddr, Init.none[LeaderLeaseScenarioClient], conf)
  }

  def scenario(servers: Int, clients: Int): JSimulationScenario = {
    val networkSetup = raise(1, setUniformLatencyNetwork()).arrival(constant(0))
    val startCluster = raise(servers, startServerOp, 1.toN).arrival(constant(1.second))
    val startClients = raise(clients, startClientOp, 1.toN).arrival(constant(0.second))

    networkSetup andThen
      0.seconds afterTermination startCluster andThen
      100.seconds afterTermination startClients andThen
      200.seconds afterTermination Terminate
  }
}
