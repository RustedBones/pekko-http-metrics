/*
 * Copyright 2019 Michel Davit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.davit.pekko.http.metrics.graphite

import java.time.{Clock, Instant}

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.stream.scaladsl.{Flow, Keep, RestartFlow, Sink, Source, Tcp}
import org.apache.pekko.stream.{OverflowStrategy, QueueOfferResult, RestartSettings}
import org.apache.pekko.util.ByteString
import fr.davit.pekko.http.metrics.core.Dimension

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

object CarbonClient {

  def apply(host: String, port: Int)(implicit system: ActorSystem): CarbonClient = new CarbonClient(host, port)
}

class CarbonClient(host: String, port: Int)(implicit system: ActorSystem) extends AutoCloseable {

  private val logger         = Logging(system.eventStream, classOf[CarbonClient])
  protected val clock: Clock = Clock.systemUTC()

  private def serialize[T](name: String, value: T, dimensions: Seq[Dimension], ts: Instant): ByteString = {
    val tags         = dimensions.map(d => d.name + "=" + d.label).toList
    val taggedMetric = (name :: tags).mkString(";")
    ByteString(s"$taggedMetric $value ${ts.getEpochSecond}\n")
  }

  private def connection: Flow[ByteString, ByteString, NotUsed] = {
    // TODO read from config
    val settings = RestartSettings(
      minBackoff = 3.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
    )
    RestartFlow.withBackoff(settings)(() => Tcp().outgoingConnection(host, port))
  }

  private val queue = Source
    .queue[ByteString](19, OverflowStrategy.dropHead)
    .via(connection)
    .toMat(Sink.ignore)(Keep.left)
    .run()

  def publish[T](
      name: String,
      value: T,
      dimensions: Seq[Dimension] = Seq.empty,
      ts: Instant = Instant
        .now(clock)
  ): Unit = {
    // it's reasonable to block until the message in enqueued
    Await.result(queue.offer(serialize(name, value, dimensions, ts)), Duration.Inf) match {
      case QueueOfferResult.Enqueued    => logger.debug("Metric {} enqueued", name)
      case QueueOfferResult.Dropped     => logger.debug("Metric {} dropped", name)
      case QueueOfferResult.Failure(e)  => logger.error(e, s"Failed publishing metric $name")
      case QueueOfferResult.QueueClosed => throw new Exception("Failed publishing metric to closed carbon client")
    }
  }

  override def close(): Unit = {
    queue.complete()
    val _ = Await.result(queue.watchCompletion(), Duration.Inf)
  }
}
