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
package se.kth.id2203.kvstore

import com.larskroll.common.repl._
import com.typesafe.scalalogging.StrictLogging;
import org.apache.log4j.Layout
import util.log4j.ColoredPatternLayout;
import fastparse.all._
import concurrent.Await
import concurrent.duration._

object ClientConsole {
  // Better build this statically. Has some overhead (building a lookup table).
  val simpleStr = P(CharsWhileIn(('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z'), 1).!);
  val simpleStr2 = P(CharsWhileIn(('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z') ++ (',' to ':'), 1).!);
  val colouredLayout = new ColoredPatternLayout("%d{[HH:mm:ss,SSS]} %-5p {%c{1}} %m%n");
}

class ClientConsole(val service: ClientService) extends CommandConsole with ParsedCommands with StrictLogging {
  import ClientConsole._;

  override def layout: Layout = colouredLayout
  override def onInterrupt(): Unit = exit()

  val getCommand = parsed(P("get" ~ " " ~ simpleStr), usage = "get <key>", descr = "Executes a GET for <key>.") { key =>
    println(s"GET $key")

    val time1=System.currentTimeMillis();
    val fr = service.get(key)
    out.println("Operation sent! Awaiting response...")
    try
    {
      val r = Await.result(fr, 15.seconds);
      val time2=System.currentTimeMillis();
      out.println("Operation complete! Response was: " + r.status+ " in time - " +(time2-time1));
    }
    catch
      {
        case e: Throwable => logger.error("Error during get.", e)
      }
  };



  val casCommand = parsed[(String, String, String)](P("cas" ~ " " ~ simpleStr ~ " " ~ simpleStr  ~ " " ~ simpleStr), usage = "cas <key> <expected> <desired>", descr = "Executes a CAS for <key> with <expected> <value>.") {args =>
    val key = args._1
    val expected = args._2
    val desired = args._3
    println(s"CAS $key $expected $desired")

    val fr = service.cas(key, expected, desired)
    out.println("Operation sent! Awaiting response...")
    try
    {
      val r = Await.result(fr, 15.seconds)
      out.println("Operation complete! Response was: " + r.status)
    }
    catch
      {
        case e: Throwable => logger.error("Error during get.", e)
      }

  }

  val putCommand = parsed[(String, String)](P("put" ~ " " ~ simpleStr ~ " " ~ simpleStr), usage = "put <key> <value>", descr = "Executes a PUT for <key> <value>.")
  {
    args =>
      println(s"Put $args")
      var key=args._1
      var value=args._2
      val fr = service.put(args._1,args._2)
      out.println("Operation sent! Awaiting response...")
      try
      {
        val r = Await.result(fr, 15.seconds)
        out.println("Operation complete! Response was: " + r.status)
      }
      catch
      {
        case e: Throwable => logger.error("Error during get.", e)
      }
  }

  val reconfigureCommand = parsed[(String, String)](P("reconfigure" ~ " " ~ simpleStr2 ~ " " ~ simpleStr2), usage = "put <partition key> <nodes>", descr = "Executes a Reconfiguration for <partition key> with <nodes>.")
  {
    args =>
      println(s"Reconfigure $args")
      val partitionKey = args._1
      val nodes = args._2
      val fr = service.reconfigure(partitionKey, nodes)
      out.println("Operation sent! Awaiting response...")
      try
      {
        val r = Await.result(fr, 15.seconds)
        out.println("Operation complete! Response was: " + r.status)
      }
      catch
      {
        case e: Throwable => logger.error("Error during get.", e)
      }
  }
}
