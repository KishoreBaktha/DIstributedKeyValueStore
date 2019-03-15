package se.kth.id2203.simulation

import java.net.{InetAddress, UnknownHostException}
import java.io._
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.network.Address
import se.sics.kompics.sl.simulator.SimulationResult

import scala.collection.mutable.ListBuffer

object ScenarioHelper {

  def intToServerAddress(i: Int): Address = {
    try {
      NetAddress(InetAddress.getByName("192.193.0." + i), 45678);
    } catch {
      case ex: UnknownHostException => throw new RuntimeException(ex);
    }
  }

  def intToClientAddress(i: Int): Address = {
    try {
      NetAddress(InetAddress.getByName("192.193.1." + i), 45678);
    } catch {
      case ex: UnknownHostException => throw new RuntimeException(ex);
    }
  }

  def getHistoryResult(nofClients: Int, nofOperations: Int): ListBuffer[SimulationHistoryEntry] = {
    var history = ListBuffer.empty[SimulationHistoryEntry]

    for(client <- 1 to nofClients) {
      for(operationNo <- 0 to (nofOperations * 2) - 1) {
        val key = "history-" + ScenarioHelper.intToClientAddress(client) + "-" + operationNo

        SimulationResult.get[String](key) match {
          case Some(entry: String) => history += ScenarioHelper.deserialise(entry)
          case None => println("Missing history entry " + key);
        }
      }
    }

    history.sortBy(o => o.createdAt)
  }
  // Source: https://gist.github.com/laughedelic/634f1a1e5333d58085603fcff317f6b4
  def serialise(value: Any): String = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close
    new String(
      Base64.getEncoder().encode(stream.toByteArray),
      UTF_8
    )
  }

  // Source: https://gist.github.com/laughedelic/634f1a1e5333d58085603fcff317f6b4
  def deserialise[A](str: String): A = {
    val bytes = Base64.getDecoder().decode(str.getBytes(UTF_8))
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject()
    ois.close
    value.asInstanceOf[A]
  }
}
