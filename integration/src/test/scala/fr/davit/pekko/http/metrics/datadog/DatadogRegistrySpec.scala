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

package fr.davit.pekko.http.metrics.datadog

import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import fr.davit.pekko.http.metrics.core.{Dimension, PathLabeler, StatusGroupLabeler}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.io.{IO, Udp}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.net.InetSocketAddress
import scala.concurrent.duration.*

class DatadogRegistrySpec
    extends TestKit(ActorSystem("DatadogRegistrySpec"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  val dimensions = Seq(Dimension(StatusGroupLabeler.name, "2xx"), Dimension(PathLabeler.name, "/api"))

  def withFixture(test: (TestProbe, DatadogRegistry) => Any) = {
    val statsd   = TestProbe()
    statsd.send(IO(Udp), Udp.Bind(statsd.ref, new InetSocketAddress(0)))
    val port     = statsd.expectMsgType[Udp.Bound].localAddress.getPort
    val socket   = statsd.sender()
    val client   = new NonBlockingStatsDClientBuilder()
      .hostname("localhost")
      .port(port)
      .build()
    val registry = DatadogRegistry(client)
    try {
      test(statsd, registry)
    } finally {
      client.close()
      socket ! Udp.Unbind
    }
  }

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  "DatadogRegistry" should "send requestsActive datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.requestsActive.inc()
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.requests_active:1|c\n"
  }

  it should "send requests datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.requests.inc()
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.requests_count:1|c\n"
  }

  it should "send requestsSize datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.requestsSize.update(3)
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.requests_bytes:3|d\n"

    registry.requestsSize.update(3, dimensions)
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.requests_bytes:3|d|#path:/api,status:2xx\n"
  }

  it should "send responses datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.responses.inc()
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.responses_count:1|c\n"

    registry.responses.inc(dimensions)
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.responses_count:1|c|#path:/api,status:2xx\n"
  }

  it should "send responsesErrors datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.responsesErrors.inc()
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.responses_errors_count:1|c\n"

    registry.responsesErrors.inc(dimensions)
    statsd
      .expectMsgType[Udp.Received]
      .data
      .utf8String shouldBe "pekko.http.responses_errors_count:1|c|#path:/api,status:2xx\n"
  }

  it should "send responsesDuration datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.responsesDuration.observe(3.seconds)
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.responses_duration:3000|d\n"

    registry.responsesDuration.observe(3.seconds, dimensions)
    statsd
      .expectMsgType[Udp.Received]
      .data
      .utf8String shouldBe "pekko.http.responses_duration:3000|d|#path:/api,status:2xx\n"
  }

  it should "send responsesSize datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.responsesSize.update(3)
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.responses_bytes:3|d\n"

    registry.responsesSize.update(3, dimensions)
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.responses_bytes:3|d|#path:/api,status:2xx\n"
  }

  it should "send connectionsActive datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.connectionsActive.inc()
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.connections_active:1|c\n"
  }
  it should "send connections datagrams to the statsd server" in withFixture { (statsd, registry) =>
    registry.connections.inc()
    statsd.expectMsgType[Udp.Received].data.utf8String shouldBe "pekko.http.connections_count:1|c\n"
  }
}
