package se.kth.id2203.simulation

import java.util.UUID

import scala.collection.mutable.ListBuffer

object LinearizabilityTester {

  // Implement linearizability test using Wing & Wong algorithm
  def isLinearizable(history: ListBuffer[SimulationHistoryEntry], simulatedStore: SimulatedKVStore): Boolean = {
    if (history.isEmpty) true
    else {
      val minimalRequests = history.filter(entry => isMinimalRequest(entry, history))
      for(request <- minimalRequests) {
        val actualResponseEntry = getResponse(request.operation.id, history)
        val expectedResponse = simulatedStore.execute(request.operation)

        if (actualResponseEntry.response.equals(expectedResponse)) {
          val newHistory = history -- ListBuffer(request) -- ListBuffer(actualResponseEntry)
          if (isLinearizable(newHistory, simulatedStore)) return true
        }

        simulatedStore.undo()
      }

      false
    }
  }

  private def getResponse(operationId: UUID, history: ListBuffer[SimulationHistoryEntry]): SimulationHistoryResponseEntry = {
    history.find(entry => entry.isInstanceOf[SimulationHistoryResponseEntry] && entry.operation.id.equals(operationId))
      .get.asInstanceOf[SimulationHistoryResponseEntry]
  }

  private def isMinimalRequest(request: SimulationHistoryEntry, history: ListBuffer[SimulationHistoryEntry]): Boolean = {
    if (!request.isInstanceOf[SimulationHistoryRequestEntry])
      return false

    val responses = history.filter(entry => entry.isInstanceOf[SimulationHistoryResponseEntry])
    for(response <- responses) {
      if (!response.operation.id.equals(request.operation.id) && response.createdAt < request.createdAt) {
        return false
      }
    }

    true
  }
}
