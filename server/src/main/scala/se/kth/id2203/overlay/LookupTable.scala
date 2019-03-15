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
package se.kth.id2203.overlay;

import com.larskroll.common.collections._;
import java.util.Collection;
import se.kth.id2203.bootstrapping.NodeAssignment;
import se.kth.id2203.networking.NetAddress;

@SerialVersionUID(0x57bdfad1eceeeaaeL)
class LookupTable extends NodeAssignment with Serializable {
  val partitions = TreeSetMultiMap.empty[Int, NetAddress]

  def lookup(key: String): Iterable[NetAddress] = {
    partitions(partitionKey(key).get)
  }

  def lookup(partitionKey: Int): Iterable[NetAddress] = {
    partitions(partitionKey)
  }

  // get partitionKey from key
  def partitionKey(key: String): Option[Int] = {
    val keyHash = key.hashCode();
    partitions.floor(keyHash) match {
      case Some(k) => Some(k)
      case None    => Some(partitions.lastKey)
    }
  }

  // get partitionKey from address
  def partitionKey(address: NetAddress): Option[Int] = {
    for (partition <- partitions) {
      if (partition._2.exists(p => p == address))
        return Some(partition._1)
    }

    None
  }

  def getNodes(): Set[NetAddress] = partitions.foldLeft(Set.empty[NetAddress]) {
    case (acc, kv) => acc ++ kv._2
  }

  def updatePartition(partitionKey: Int, nodes: List[NetAddress]): Unit = {
    partitions.remove(partitionKey)
    partitions ++= (partitionKey -> nodes)
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("LookupTable(\n")
    sb.append(partitions.mkString(","))
    sb.append(")")
    sb.toString()
  }

}

object LookupTable {
  def generate(nodes: Set[NetAddress], replicationDegree: Int): LookupTable = {
    val lut = new LookupTable()
    val keySpaceSize: Long = getKeySpaceSize
    val numberOfPartitions: Int = nodes.size / replicationDegree
    val partitionSize: Long = keySpaceSize / numberOfPartitions

    var index: Int = 0
    for(node <- nodes) {
        val partitionIndex = index % numberOfPartitions
        val partitionKey = getPartitionKey(partitionIndex, partitionSize)

        if (!lut.partitions.contains(partitionKey)) {
          lut.partitions ++= (partitionKey -> Set.empty)
        }

        lut.partitions(partitionKey) += node
        index += 1
    }

    lut
  }

  private def getKeySpaceSize: Long = Int.MaxValue.toLong * 2
  private def getPartitionKey(index: Int, partitionSize: Long): Int = (Int.MinValue.toLong + (partitionSize * index)).toInt
}
