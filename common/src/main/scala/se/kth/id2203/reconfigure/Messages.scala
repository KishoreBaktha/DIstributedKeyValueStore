package se.kth.id2203.reconfigure

import java.util.UUID

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

import scala.collection.mutable.ListBuffer

object ReconfigStatus
{
  sealed trait ReconfigStatus
  case object Ok extends ReconfigStatus
}

case class ReconfigurationResponse(status: ReconfigStatus.ReconfigStatus, id: UUID = UUID.randomUUID) extends KompicsEvent
case class ReconfigurationRequest(partitionKey: Int, nodes: ListBuffer[NetAddress], id: UUID = UUID.randomUUID) extends KompicsEvent
