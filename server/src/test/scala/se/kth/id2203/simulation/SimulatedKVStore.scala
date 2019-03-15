package se.kth.id2203.simulation

import java.util.UUID

import se.kth.id2203.kvstore._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class SimulatedKVStore {
  private val store = mutable.Map.empty[String, String]
  private var history = ListBuffer.empty[(String, Option[String])]

  def execute(operation: Operation): OpResponse = {
    operation match {
      case GetOp(key: String, _, id: UUID) => {
        get(key) match {
          case Some(value: String) => OpResponse(id, Some(value), OpCode.Ok)
          case None => OpResponse(id, None, OpCode.NotFound)
        }
      }
      case PutOp(key: String, _, value: String, id: UUID) => OpResponse(id, Some(put(key, value)), OpCode.Ok)
      case CasOp(key: String, _, expected: String, desired: String, id: UUID) => {
        cas(key, expected, desired) match {
          case Some(value: String) => OpResponse(id, Some(value), OpCode.Ok)
          case None => OpResponse(id, None, OpCode.NotFound)
        }
      }
    }
  }

  def undo(): Unit = {
    if (history.nonEmpty) {
      history.remove(history.size - 1) match {
        case (key, Some(value: String)) => store(key) = value
        case (key, None) => store.remove(key)
      }
    }
  }

  private def recordHistory(key: String): Unit = {
    if (store contains key) {
      history += ((key, Some(store(key))))
    }
    else {
      history += ((key, None))
    }
  }

  private def get(key: String): Option[String] = {
    recordHistory(key)
    if (store contains key) {
      Some(store(key))
    }
    else {
      None
    }
  }

  private def put(key: String, value: String): String = {
    recordHistory(key)
    store(key) = value;
    value
  }

  private def cas(key: String, expected: String, desired: String): Option[String] = {
    recordHistory(key)
    if (store.contains(key)) {
      val old_value = store(key)
      if (old_value.equals(expected)){
        store(key) = desired
        Some(old_value)
      }
      else {
        Some(old_value)
      }
    }
    else {
      None
    }
  }
}
