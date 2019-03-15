package se.kth.id2203.consensus

import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init}

class SP_Service extends ComponentDefinition
{
    val net = requires[Network];
  var ble=requires[BallotLeaderPort];
    val SP = create(classOf[SequencePaxos], Init.NONE);
    {
      connect[BallotLeaderPort](ble->SP);
      connect[Network](net -> SP);
    }
}

