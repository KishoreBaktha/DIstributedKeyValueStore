package se.kth.id2203.reconfiguration

import se.kth.id2203.kvstore.{Operation, StopSign}
import se.kth.id2203.overlay.LookupTable
import se.sics.kompics.KompicsEvent

import scala.collection.mutable.ListBuffer

case class CreateNewConfiguration(configurationId: Int, assignment: LookupTable) extends KompicsEvent
case class ConfigurationStopped(stopSign: StopSign) extends KompicsEvent