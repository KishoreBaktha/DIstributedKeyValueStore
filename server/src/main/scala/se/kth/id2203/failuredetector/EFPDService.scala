package se.kth.id2203.failuredetector

import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, Init}
import se.sics.kompics.timer.Timer

class  EFPDService() extends ComponentDefinition
{
  val net = requires[Network];
  val timer = requires[Timer];
  val Epfd = create(classOf[EPFD], Init.NONE);

  {
    connect[Timer](timer -> Epfd);
    connect[Network](net -> Epfd);
  }
}