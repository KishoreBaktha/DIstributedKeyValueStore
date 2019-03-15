package se.kth.id2203.consensus

import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init}
import se.sics.kompics.timer.Timer

class  BLEService() extends ComponentDefinition
{
  val net = requires[Network];
  val timer = requires[Timer];
  val BLE = create(classOf[BallotLeaderElection], Init.NONE);

  {
    connect[Timer](timer -> BLE);
    connect[Network](net -> BLE);
  }
}