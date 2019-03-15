package se.kth.id2203.simulation

import org.scalatest.{FlatSpec, Matchers}
import se.sics.kompics.simulator.result.SimulationResultSingleton
import se.sics.kompics.simulator.run.LauncherComp
import se.sics.kompics.sl.simulator.SimulationResult
import se.sics.kompics.simulator.{SimulationScenario => JSimulationScenario}

class TestSuite extends FlatSpec with Matchers {
  "Leader-Lease operations" should "be performant" in {
    val seed = 123l
    JSimulationScenario.setSeed(seed);

    SimulationResult += ("linearizabilitytest.nofoperations" -> LeaderLeaseTestParams.nofOperations)
    val bootScenario = LeaderLeasePerformanceTestScenario.scenario(LeaderLeasePerformanceTestParams.nofServers, LeaderLeasePerformanceTestParams.nofClients)
    val res = SimulationResultSingleton.getInstance()

    bootScenario.simulate(classOf[LauncherComp])

    val history = ScenarioHelper.getHistoryResult(LeaderLeasePerformanceTestParams.nofClients, LeaderLeasePerformanceTestParams.nofOperations )
    for(entry <- history) { println(entry) }

    println("All operations finish in " + (history.last.createdAt - history.head.createdAt))
  }

  "Operations" should "be linearizable" in {
    val seed = 123l
    JSimulationScenario.setSeed(seed);

    SimulationResult += ("linearizabilitytest.nofoperations" -> LinearizabilityTestParams.nofOperations)
    val linearizabilityBootScenario = LinearizabilityTestScenario.scenario(LinearizabilityTestParams.nofServers, LinearizabilityTestParams.nofClients)
    val res = SimulationResultSingleton.getInstance()

    linearizabilityBootScenario.simulate(classOf[LauncherComp])

    val history = ScenarioHelper.getHistoryResult(LinearizabilityTestParams.nofClients,LinearizabilityTestParams.nofOperations )
    for(entry <- history) { println(entry) }

    SimulationResult += "isLinearizable" -> LinearizabilityTester.isLinearizable(history, new SimulatedKVStore()).toString
    SimulationResult.get[String]("isLinearizable") should be (Some("true"))
  }

//  "Operations with leader-lease during temporary partitioning" should "be linearizable" in {
//    val seed = 546l
//    JSimulationScenario.setSeed(seed);
//
//    SimulationResult += ("LeaderLeaseTest.nofoperations" -> LeaderLeaseTestParams.nofOperations)
//    val bootScenario = LeaderLeaseTestScenario.scenario(LeaderLeaseTestParams.nofServers, LeaderLeaseTestParams.nofClients)
//    val res = SimulationResultSingleton.getInstance()
//
//    bootScenario.simulate(classOf[LauncherComp])
//
//    val history = ScenarioHelper.getHistoryResult(LeaderLeaseTestParams.nofClients, LeaderLeaseTestParams.nofOperations )
//    for(entry <- history) { println(entry) }
//
//    SimulationResult += "LeaderLeaseTest.isLinearizable" -> LinearizabilityTester.isLinearizable(history, new SimulatedKVStore()).toString
//    SimulationResult.get[String]("LeaderLeaseTest.isLinearizable") should be (Some("true"))
//  }
}